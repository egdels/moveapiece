/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.chessnut.core.protocol;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Reassembles Chessnut messages ({@code <type> <length> <payload[length]>}) from BLE notification
 * chunks. With the negotiated MTU the board delivers one whole message per notification, but a
 * small MTU (Android default 23) would split the 38-byte board report, so fragments are buffered
 * until the announced length is complete.
 *
 * <p>The protocol has no sync marker, so a corrupted stream cannot be resynchronised reliably; an
 * oversize length byte (above {@link #MAX_PAYLOAD_LENGTH}) is skipped and counted, and a transport
 * should call {@link #reset()} on every reconnect, after which notifications start on a message
 * boundary again.
 *
 * <p>Not thread-safe; use one parser per characteristic and feed it from one thread.
 */
public final class ChessnutFrameParser {

    /** Largest known payload is the 36-byte board report; leave headroom for unknown messages. */
    static final int MAX_PAYLOAD_LENGTH = 64;

    private static final int HEADER_LENGTH = 2;

    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private long droppedBytes;

    /** Appends notification bytes and returns every frame they complete (possibly empty). */
    public List<ChessnutFrame> feed(byte[] data) {
        if (data != null && data.length > 0) {
            buffer.write(data, 0, data.length);
        }
        List<ChessnutFrame> frames = new ArrayList<>();
        byte[] buf = buffer.toByteArray();
        int pos = 0;
        while (buf.length - pos >= HEADER_LENGTH) {
            int length = buf[pos + 1] & 0xFF;
            if (length > MAX_PAYLOAD_LENGTH) {
                pos++;
                droppedBytes++;
                continue;
            }
            if (buf.length - pos < HEADER_LENGTH + length) {
                break; // wait for the rest
            }
            byte[] payload = new byte[length];
            System.arraycopy(buf, pos + HEADER_LENGTH, payload, 0, length);
            frames.add(new ChessnutFrame(buf[pos] & 0xFF, payload));
            pos += HEADER_LENGTH + length;
        }
        buffer.reset();
        buffer.write(buf, pos, buf.length - pos);
        return frames;
    }

    /** Bytes buffered while waiting for the rest of a message. */
    public int pendingByteCount() {
        return buffer.size();
    }

    /** Bytes skipped during resync since construction (diagnostics). */
    public long droppedByteCount() {
        return droppedBytes;
    }

    /** Drops any partial message, e.g. after a reconnect. */
    public void reset() {
        buffer.reset();
    }
}
