/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.analysis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class MultiPvCandidatesTest {

    private final MultiPvCandidates candidates = new MultiPvCandidates(3);

    @Test
    public void capture_keepsTheLatestLinePerRank() {
        candidates.capture("info depth 10 multipv 1 score cp 35 nodes 1 pv e2e4 e7e5");
        candidates.capture("info depth 10 multipv 2 score cp 20 nodes 1 pv d2d4 d7d5");
        candidates.capture("info depth 11 multipv 1 score cp 41 nodes 2 pv g1f3 d7d5");

        assertEquals("g1f3", candidates.move(0));
        assertEquals(41, candidates.cp(0));
        assertEquals("d2d4", candidates.move(1));
        assertEquals(20, candidates.cp(1));
        assertEquals("d2-d4", candidates.squares(1));
        assertNull(candidates.move(2));
    }

    @Test
    public void capture_turnsAMateScoreIntoCentipawns() {
        candidates.capture("info depth 12 multipv 1 score mate 2 nodes 1 pv d1h5 g6h5");

        assertEquals("d1h5", candidates.move(0));
        assertEquals(MoveQuality.mateToCp(2), candidates.cp(0));
    }

    @Test
    public void capture_skipsLinesWithoutRankMoveOrScoreAndRanksOutOfRange() {
        candidates.capture("info depth 10 score cp 35 pv e2e4");
        candidates.capture("info depth 10 multipv 1 score cp 35");
        candidates.capture("info depth 10 multipv 1 pv e2e4");
        candidates.capture("info depth 10 multipv 4 score cp 35 pv e2e4");
        candidates.capture("info string hello");

        assertNull(candidates.move(0));
        assertNull(candidates.move(1));
        assertNull(candidates.move(2));
    }

    @Test
    public void clear_forgetsEveryLine() {
        candidates.capture("info depth 10 multipv 1 score cp 35 pv e2e4");

        candidates.clear();

        assertNull(candidates.move(0));
        assertEquals(3, candidates.lines());
    }
}
