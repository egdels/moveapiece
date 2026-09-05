/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.pegasus.core.protocol;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

public class PegasusFrameParserTest {

    private final PegasusFrameParser parser = new PegasusFrameParser();

    private static byte[] frame(int type, byte[] payload) {
        int totalLen = payload.length + 3;
        byte[] out = new byte[totalLen];
        out[0] = (byte) type;
        out[1] = (byte) ((totalLen >> 7) & 0x7F);
        out[2] = (byte) (totalLen & 0x7F);
        System.arraycopy(payload, 0, out, 3, payload.length);
        return out;
    }

    @Test
    public void parsesSingleCompleteFrame() {
        byte[] payload = new byte[] {0x0A, 0x01};
        List<PegasusFrame> frames = parser.feed(frame(0x8E, payload));

        assertEquals(1, frames.size());
        assertEquals(0x8E, frames.get(0).type());
        assertArrayEquals(payload, frames.get(0).payload());
        assertEquals(0, parser.pendingByteCount());
    }

    @Test
    public void parsesEmptyPayloadFrame() {
        List<PegasusFrame> frames = parser.feed(frame(0x8F, new byte[0]));

        assertEquals(1, frames.size());
        assertEquals(0, frames.get(0).payloadLength());
    }

    @Test
    public void reassemblesFragmentedFrame() {
        byte[] payload = new byte[64];
        for (int i = 0; i < 64; i++) {
            payload[i] = (byte) (i % 13);
        }
        byte[] full = frame(0x86, payload);

        // Split into three fragments, header itself split too.
        byte[] f1 = new byte[2];
        byte[] f2 = new byte[20];
        byte[] f3 = new byte[full.length - 22];
        System.arraycopy(full, 0, f1, 0, 2);
        System.arraycopy(full, 2, f2, 0, 20);
        System.arraycopy(full, 22, f3, 0, f3.length);

        assertTrue(parser.feed(f1).isEmpty());
        assertTrue(parser.feed(f2).isEmpty());
        List<PegasusFrame> frames = parser.feed(f3);

        assertEquals(1, frames.size());
        assertEquals(0x86, frames.get(0).type());
        assertArrayEquals(payload, frames.get(0).payload());
    }

    @Test
    public void parsesMultipleFramesInOneNotification() {
        byte[] a = frame(0x8E, new byte[] {0x34, 0x00});
        byte[] b = frame(0x8E, new byte[] {0x2C, 0x01});
        byte[] combined = new byte[a.length + b.length];
        System.arraycopy(a, 0, combined, 0, a.length);
        System.arraycopy(b, 0, combined, a.length, b.length);

        List<PegasusFrame> frames = parser.feed(combined);

        assertEquals(2, frames.size());
        assertEquals(0x34, frames.get(0).payload()[0]);
        assertEquals(0x2C, frames.get(1).payload()[0]);
    }

    @Test
    public void resyncsAfterGarbageBytes() {
        byte[] valid = frame(0xA0, new byte[] {0x58, 0, 0, 0, 0, 0, 0, 0, 2});
        byte[] data = new byte[3 + valid.length];
        data[0] = 0x12; // no bit 7 → cannot be a type byte
        data[1] = 0x7F;
        data[2] = 0x01;
        System.arraycopy(valid, 0, data, 3, valid.length);

        List<PegasusFrame> frames = parser.feed(data);

        assertEquals(1, frames.size());
        assertEquals(0xA0, frames.get(0).type());
        assertEquals(3, parser.droppedByteCount());
    }

    @Test
    public void skipsHeaderWithInvalidLengthBytes() {
        // 0x86 followed by a length byte with bit 7 set is not a valid header.
        byte[] valid = frame(0x8E, new byte[] {0x00, 0x01});
        byte[] data = new byte[2 + valid.length];
        data[0] = (byte) 0x86;
        data[1] = (byte) 0x80;
        System.arraycopy(valid, 0, data, 2, valid.length);

        List<PegasusFrame> frames = parser.feed(data);

        assertEquals(1, frames.size());
        assertEquals(0x8E, frames.get(0).type());
    }

    @Test
    public void resetDiscardsPartialFrame() {
        byte[] full = frame(0x86, new byte[64]);
        byte[] partial = new byte[10];
        System.arraycopy(full, 0, partial, 0, 10);

        assertTrue(parser.feed(partial).isEmpty());
        assertEquals(10, parser.pendingByteCount());

        parser.reset();
        assertEquals(0, parser.pendingByteCount());

        // A fresh complete frame parses normally after reset.
        List<PegasusFrame> frames = parser.feed(frame(0x8E, new byte[] {1, 2}));
        assertEquals(1, frames.size());
    }

    @Test
    public void handlesNullAndEmptyInput() {
        assertTrue(parser.feed(null).isEmpty());
        assertTrue(parser.feed(new byte[0]).isEmpty());
    }
}
