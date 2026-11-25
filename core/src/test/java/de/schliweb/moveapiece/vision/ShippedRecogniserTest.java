/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.vision;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.util.Collections;
import org.junit.Test;

/**
 * The recogniser in the repository loads and runs through the whole chain. Needs no photos: the
 * board is a drawn one, which says nothing about how well real pieces are read ({@code
 * training/EVALUATION.md} does).
 */
public class ShippedRecogniserTest {

    @Test
    public void loadsAndReadsADrawnBoard() throws Exception {
        assertTrue(
                "the recogniser is missing: " + TestBoards.SHIPPED_RECOGNISER,
                TestBoards.SHIPPED_RECOGNISER.isFile());
        Photo photo =
                TestBoards.draw(1200, 900, TestBoards.squareSeenAtAnAngle(1200, 900, 60), 0.03, 0);
        try (BoardPhotoRecognizer recognizer =
                new BoardPhotoRecognizer(
                        Files.readAllBytes(TestBoards.SHIPPED_RECOGNISER.toPath()))) {
            BoardPhotoRecognizer.Reading reading = recognizer.read(photo);
            assertNotNull("the drawn board is not found", reading);
            // A board that reports nothing as occupied is read as empty, whatever is seen.
            BoardPhotoRecognizer.Result result =
                    recognizer.recognize(Collections.singletonList(reading), new boolean[8][8]);
            assertEquals("8/8/8/8/8/8/8/8", result.placement);
            // ... and on the drawn empty board the network sees next to nothing standing.
            int occupied = 0;
            for (boolean[] row : recognizer.seenOccupied(photo)) {
                for (boolean square : row) {
                    occupied += square ? 1 : 0;
                }
            }
            assertTrue("squares seen as occupied on an empty board: " + occupied, occupied <= 8);
        }
    }
}
