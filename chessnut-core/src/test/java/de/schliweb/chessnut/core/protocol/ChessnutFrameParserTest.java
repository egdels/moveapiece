/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.chessnut.core.protocol;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class ChessnutFrameParserTest {

    private final ChessnutFrameParser parser = new ChessnutFrameParser();

    @Test
    public void parsesWholeBoardReport() {
        List<ChessnutFrame> frames = parser.feed(Fixtures.START_POSITION);

        assertEquals(1, frames.size());
        assertEquals(ChessnutMessageType.BOARD_REPORT, frames.get(0).type());
        assertEquals(36, frames.get(0).payloadLength());
        assertArrayEquals(
                Arrays.copyOfRange(Fixtures.START_POSITION, 2, 38), frames.get(0).payload());
        assertEquals(0, parser.pendingByteCount());
    }

    @Test
    public void parsesShortReplies() {
        assertEquals(
                new ChessnutFrame(ChessnutMessageType.ACK, new byte[] {0}),
                parser.feed(Fixtures.ACK).get(0));
        assertEquals(
                new ChessnutFrame(ChessnutMessageType.BATTERY_STATUS, new byte[] {0x64, 0}),
                parser.feed(Fixtures.BATTERY_100).get(0));
        assertEquals(
                new ChessnutFrame(ChessnutMessageType.BUTTON, new byte[] {2}),
                parser.feed(Fixtures.NEW_GAME).get(0));
    }

    @Test
    public void reassemblesReportSplitAtAndroidDefaultMtu() {
        byte[] full = Fixtures.START_POSITION;
        byte[] first = Arrays.copyOfRange(full, 0, 20);
        byte[] second = Arrays.copyOfRange(full, 20, full.length);

        assertTrue(parser.feed(first).isEmpty());
        assertEquals(20, parser.pendingByteCount());
        List<ChessnutFrame> frames = parser.feed(second);

        assertEquals(1, frames.size());
        assertArrayEquals(Arrays.copyOfRange(full, 2, 38), frames.get(0).payload());
        assertEquals(0, parser.pendingByteCount());
    }

    @Test
    public void reassemblesHeaderSplitAcrossChunks() {
        assertTrue(parser.feed(new byte[] {0x2a}).isEmpty());
        List<ChessnutFrame> frames = parser.feed(new byte[] {0x02, 0x5f, 0x00});

        assertEquals(1, frames.size());
        assertEquals(ChessnutMessageType.BATTERY_STATUS, frames.get(0).type());
    }

    @Test
    public void parsesTwoMessagesInOneChunk() {
        byte[] both = new byte[Fixtures.ACK.length + Fixtures.BATTERY_95.length];
        System.arraycopy(Fixtures.ACK, 0, both, 0, Fixtures.ACK.length);
        System.arraycopy(
                Fixtures.BATTERY_95, 0, both, Fixtures.ACK.length, Fixtures.BATTERY_95.length);

        List<ChessnutFrame> frames = parser.feed(both);

        assertEquals(2, frames.size());
        assertEquals(ChessnutMessageType.ACK, frames.get(0).type());
        assertEquals(ChessnutMessageType.BATTERY_STATUS, frames.get(1).type());
    }

    @Test
    public void skipsImplausibleLengthBytesWithoutBlowingUp() {
        // No sync marker exists, so garbage cannot be reliably resynced; the guarantee is
        // merely that oversize length bytes are dropped and counted, and reset() recovers.
        List<ChessnutFrame> frames =
                parser.feed(new byte[] {(byte) 0xff, (byte) 0xff, (byte) 0xff});

        assertTrue(frames.isEmpty());
        assertEquals(2, parser.droppedByteCount());
        assertEquals(1, parser.pendingByteCount());

        parser.reset();
        assertEquals(ChessnutMessageType.ACK, parser.feed(Fixtures.ACK).get(0).type());
    }

    @Test
    public void resetDropsPartialMessage() {
        parser.feed(Arrays.copyOfRange(Fixtures.START_POSITION, 0, 10));
        parser.reset();

        assertEquals(0, parser.pendingByteCount());
        assertEquals(1, parser.feed(Fixtures.ACK).size());
    }

    @Test
    public void toleratesNullAndEmptyInput() {
        assertTrue(parser.feed(null).isEmpty());
        assertTrue(parser.feed(new byte[0]).isEmpty());
    }
}
