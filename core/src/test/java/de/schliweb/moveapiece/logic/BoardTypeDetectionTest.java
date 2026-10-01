/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.logic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BoardTypeDetectionTest {

    @Test
    public void guessFromDeviceName_recognisesKnownPrefixes() {
        assertEquals(BoardType.CHESSNUT, BoardType.guessFromDeviceName("Chessnut Air\n"));
        assertEquals(BoardType.CHESSNUT, BoardType.guessFromDeviceName("Chessnut Pro"));
        assertEquals(BoardType.CHESSNUT, BoardType.guessFromDeviceName("Smart Chess"));
        assertEquals(BoardType.PEGASUS, BoardType.guessFromDeviceName("PCS-REVII-081500"));
        assertEquals(BoardType.PEGASUS, BoardType.guessFromDeviceName("DGT_PEGASUS_12345"));
        assertEquals(BoardType.PEGASUS, BoardType.guessFromDeviceName("dgt pegasus"));
    }

    @Test
    public void guessFromDeviceName_unknownOrMissingNameGivesNull() {
        assertNull(BoardType.guessFromDeviceName(null));
        assertNull(BoardType.guessFromDeviceName(""));
        assertNull(BoardType.guessFromDeviceName("d4"));
        assertNull(BoardType.guessFromDeviceName("Christian's board"));
    }

    @Test
    public void start_triesGuessedTypeFirstEvenWhenAnotherIsConfigured() {
        BoardTypeDetection d =
                BoardTypeDetection.start("AA:BB", "Chessnut Air\n", BoardType.PEGASUS);
        assertEquals(BoardType.CHESSNUT, d.current());
        assertEquals(BoardType.PEGASUS, d.configured());
        assertFalse(d.isLastCandidate());
        assertEquals(BoardType.PEGASUS, d.next());
        assertTrue(d.isLastCandidate());
        assertNull(d.next());
        assertEquals(BoardType.PEGASUS, d.current());
    }

    @Test
    public void start_withoutUsableNameTriesConfiguredTypeFirst() {
        BoardTypeDetection d = BoardTypeDetection.start("AA:BB", "My board", BoardType.CHESSNUT);
        assertEquals(BoardType.CHESSNUT, d.current());
        assertEquals(BoardType.PEGASUS, d.next());
        assertNull(d.next());

        BoardTypeDetection unnamed = BoardTypeDetection.start("AA:BB", null, BoardType.PEGASUS);
        assertEquals(BoardType.PEGASUS, unnamed.current());
        assertEquals(BoardType.CHESSNUT, unnamed.next());
    }

    @Test
    public void everyTypeIsTriedExactlyOnce() {
        BoardTypeDetection d = BoardTypeDetection.start("AA:BB", null, BoardType.PEGASUS);
        int tried = 1;
        while (d.next() != null) {
            tried++;
        }
        assertEquals(BoardType.values().length, tried);
    }

    @Test(expected = IllegalArgumentException.class)
    public void start_rejectsMissingAddress() {
        BoardTypeDetection.start(null, "Chessnut Air", BoardType.PEGASUS);
    }
}
