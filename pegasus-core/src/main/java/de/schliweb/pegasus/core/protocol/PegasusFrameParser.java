/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.pegasus.core.protocol;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Streaming parser for Pegasus messages (board → app).
 *
 * <p>Framing (CONFIRMED_BY_REFERENCE_IMPLEMENTATION, docs/PEGASUS_PROTOCOL.md):
 *
 * <pre>
 * byte 0: message type (bit 7 set, e.g. 0x86)
 * byte 1: length high  ((totalLen &gt;&gt; 7) &amp; 0x7F)
 * byte 2: length low   ( totalLen        &amp; 0x7F)
 * byte 3…: payload     (totalLen = payload length + 3)
 * </pre>
 *
 * Messages may arrive fragmented across multiple BLE notifications, and one notification may
 * contain several messages; this parser keeps an internal reassembly buffer. Invalid header bytes
 * (type without bit 7, length bytes with bit 7, totalLen &lt; 3) cause a one-byte resync so a
 * single corrupted byte cannot stall the stream. Not thread-safe; feed from one thread.
 */
public final class PegasusFrameParser {

    /** Defensive upper bound; the largest known message (board dump) is 67 bytes. */
    static final int MAX_TOTAL_LENGTH = 1024;

    private static final int HEADER_LENGTH = 3;

    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private long droppedBytes;

    /**
     * Appends raw notification bytes and returns all frames completed by them (possibly empty,
     * never null).
     */
    public List<PegasusFrame> feed(byte[] data) {
        if (data != null && data.length > 0) {
            buffer.write(data, 0, data.length);
        }
        List<PegasusFrame> frames = new ArrayList<>();
        byte[] buf = buffer.toByteArray();
        int pos = 0;
        while (true) {
            // Resync: skip until a plausible header starts at pos.
            while (pos < buf.length && !isPlausibleHeaderAt(buf, pos)) {
                pos++;
                droppedBytes++;
            }
            if (buf.length - pos < HEADER_LENGTH) {
                break;
            }
            int totalLen = totalLengthAt(buf, pos);
            if (buf.length - pos < totalLen) {
                break; // wait for more fragments
            }
            byte[] payload = new byte[totalLen - HEADER_LENGTH];
            System.arraycopy(buf, pos + HEADER_LENGTH, payload, 0, payload.length);
            frames.add(new PegasusFrame(buf[pos] & 0xFF, payload));
            pos += totalLen;
        }
        buffer.reset();
        buffer.write(buf, pos, buf.length - pos);
        return frames;
    }

    /** Bytes currently buffered while waiting for the rest of a message. */
    public int pendingByteCount() {
        return buffer.size();
    }

    /** Total bytes discarded during resync since construction (diagnostics). */
    public long droppedByteCount() {
        return droppedBytes;
    }

    /** Discards any partial message, e.g. after a reconnect. */
    public void reset() {
        buffer.reset();
    }

    private static boolean isPlausibleHeaderAt(byte[] buf, int pos) {
        if ((buf[pos] & 0x80) == 0) {
            return false; // type byte must have bit 7 set
        }
        if (pos + 1 < buf.length && (buf[pos + 1] & 0x80) != 0) {
            return false; // length bytes are 7-bit
        }
        if (pos + 2 < buf.length) {
            if ((buf[pos + 2] & 0x80) != 0) {
                return false;
            }
            int totalLen = totalLengthAt(buf, pos);
            if (totalLen < HEADER_LENGTH || totalLen > MAX_TOTAL_LENGTH) {
                return false;
            }
        }
        return true;
    }

    private static int totalLengthAt(byte[] buf, int pos) {
        return ((buf[pos + 1] & 0x7F) << 7) | (buf[pos + 2] & 0x7F);
    }
}
