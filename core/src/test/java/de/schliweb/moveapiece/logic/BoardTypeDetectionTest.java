/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.logic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
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
    public void displayNameFor_namesTheChessnutModelButNotARenamedPegasus() {
        assertEquals("Chessnut Air", BoardType.CHESSNUT.displayNameFor("Chessnut Air\n"));
        assertEquals("Chessnut Pro", BoardType.CHESSNUT.displayNameFor("Chessnut Pro"));
        assertEquals("Chessnut", BoardType.CHESSNUT.displayNameFor("Smart Chess"));
        assertEquals("Chessnut", BoardType.CHESSNUT.displayNameFor(null));
        assertEquals("DGT Pegasus", BoardType.PEGASUS.displayNameFor("DGT_PEGASUS_12345"));
        assertEquals("DGT Pegasus", BoardType.PEGASUS.displayNameFor("Testbrett"));
    }

    @Test
    public void guessFromAdvertisement_recognisesRenamedPegasusByUartService() {
        List<String> uart = List.of("6E400001-B5A3-F393-E0A9-E50E24DCCA9E");
        assertEquals(
                BoardType.PEGASUS, BoardType.guessFromAdvertisement("Christian's board", uart));
        assertEquals(BoardType.PEGASUS, BoardType.guessFromAdvertisement(null, uart));
        // The name wins when it says something; the service only breaks ties.
        assertEquals(BoardType.CHESSNUT, BoardType.guessFromAdvertisement("Chessnut Air", uart));
        assertNull(BoardType.guessFromAdvertisement("Christian's board", List.of()));
        assertNull(BoardType.guessFromAdvertisement("Christian's board", null));
        assertNull(
                BoardType.guessFromAdvertisement(
                        "BF700", List.of("0000ffe0-0000-1000-8000-00805f9b34fb")));
    }

    @Test
    public void start_triesRenamedPegasusFirstByUartService() {
        BoardTypeDetection d =
                BoardTypeDetection.start(
                        "AA:BB",
                        "Christian's board",
                        List.of("6e400001-b5a3-f393-e0a9-e50e24dcca9e"),
                        BoardType.CHESSNUT);
        assertEquals(BoardType.PEGASUS, d.current());
        assertEquals(BoardType.CHESSNUT, d.next());
        assertNull(d.next());
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
