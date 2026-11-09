/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.puzzle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PuzzleRatingTest {

    @Test
    public void start_is1200WithNothingPlayed() {
        assertEquals(new PuzzleRating(1200, 0), PuzzleRating.START);
    }

    @Test
    public void evenPuzzle_movesHalfTheKFactor() {
        PuzzleRating start = PuzzleRating.START;

        assertEquals(20, start.delta(1200, true));
        assertEquals(-20, start.delta(1200, false));
        assertEquals(new PuzzleRating(1220, 1), start.after(1200, true));
    }

    @Test
    public void afterProvisionalPhase_kFactorHalves() {
        PuzzleRating settled = new PuzzleRating(1500, PuzzleRating.PROVISIONAL_PUZZLES);

        assertEquals(10, settled.delta(1500, true));
        assertEquals(-10, settled.delta(1500, false));
    }

    @Test
    public void harderPuzzle_givesMoreForSolvingAndCostsLessForFailing() {
        PuzzleRating rating = new PuzzleRating(1500, 100);

        int solvedHard = rating.delta(1900, true);
        int failedHard = rating.delta(1900, false);

        assertTrue(solvedHard > 10);
        assertTrue(failedHard > -10 && failedHard < 0);
        assertEquals(18, solvedHard); // 20 * (1 - 1 / (1 + 10^1))
    }

    @Test
    public void rating_staysWithinRange() {
        assertEquals(PuzzleRating.MIN, new PuzzleRating(410, 0).after(410, false).rating());
        assertEquals(-10, new PuzzleRating(410, 0).delta(410, false));
        assertEquals(PuzzleRating.MAX, new PuzzleRating(2995, 0).after(2995, true).rating());
        assertEquals(PuzzleRating.MIN, new PuzzleRating(100, -5).rating());
        assertEquals(0, new PuzzleRating(100, -5).played());
    }

    @Test
    public void key_roundTripsAndForgivesGarbage() {
        PuzzleRating rating = new PuzzleRating(1734, 57);

        assertEquals(rating, PuzzleRating.fromKey(rating.toKey()));
        assertEquals(PuzzleRating.START, PuzzleRating.fromKey(null));
        assertEquals(PuzzleRating.START, PuzzleRating.fromKey(""));
        assertEquals(PuzzleRating.START, PuzzleRating.fromKey("abc:1"));
        assertEquals(PuzzleRating.START, PuzzleRating.fromKey("1:2:3"));
    }
}
