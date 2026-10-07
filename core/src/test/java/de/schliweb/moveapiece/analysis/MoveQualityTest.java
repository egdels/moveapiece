/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.analysis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MoveQualityTest {

    @Test
    public void of_gradesByCentipawnLoss() {
        assertNull(MoveQuality.of(0));
        assertNull(MoveQuality.of(49));
        assertEquals(MoveQuality.INACCURACY, MoveQuality.of(50));
        assertEquals(MoveQuality.INACCURACY, MoveQuality.of(149));
        assertEquals(MoveQuality.MISTAKE, MoveQuality.of(150));
        assertEquals(MoveQuality.MISTAKE, MoveQuality.of(299));
        assertEquals(MoveQuality.BLUNDER, MoveQuality.of(300));
        assertEquals(
                MoveQuality.BLUNDER, MoveQuality.mateToCp(3) > 0 ? MoveQuality.of(99700) : null);
    }

    @Test
    public void mateToCp_dominatesNormalEvalsAndPrefersTheShorterMate() {
        assertEquals(99900, MoveQuality.mateToCp(1));
        assertEquals(-99900, MoveQuality.mateToCp(-1));
        assertTrue(MoveQuality.mateToCp(2) < MoveQuality.mateToCp(1));
        assertTrue(MoveQuality.mateToCp(-1) < MoveQuality.mateToCp(-2));
        // "mate 0": the side to move is mated; counted as won for the side that gave it.
        assertEquals(100000, MoveQuality.mateToCp(0));
        // Capped, so even a very long mate stays far above any centipawn score.
        assertEquals(90000, MoveQuality.mateToCp(500));
    }
}
