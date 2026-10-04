/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.analysis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class PostGameReportTest {

    private static final double EPS = 1e-9;

    @Test
    public void of_gradesEveryMoveFromTheMoversPerspective() {
        // Evals are from the side to move. White: +30, after e4 Black sees -30 (no loss);
        // after ...f6 White sees +200 (Black lost 170: a mistake); after Qh5 Black sees +150,
        // i.e. White threw away 350: a blunder.
        List<String> uci = Arrays.asList("e2e4", "f7f6", "d1h5");
        List<Integer> evals = Arrays.asList(30, -30, 200, 150);
        List<String> san = Arrays.asList("e4", "f6", "Qh5");

        PostGameReport report = PostGameReport.of(uci, evals, san);

        assertEquals(0, report.white().count(MoveQuality.INACCURACY));
        assertEquals(0, report.white().count(MoveQuality.MISTAKE));
        assertEquals(1, report.white().count(MoveQuality.BLUNDER));
        assertEquals((0 + 350) / 2 / 100.0, report.white().averageLossPawns(), EPS);
        assertEquals(1, report.black().count(MoveQuality.MISTAKE));
        assertEquals(1.7, report.black().averageLossPawns(), EPS);

        List<PostGameReport.FlaggedMove> flagged = report.flaggedMoves();
        assertEquals(2, flagged.size());
        assertEquals("1...", flagged.get(0).plyLabel());
        assertEquals("f6", flagged.get(0).san());
        assertEquals(MoveQuality.MISTAKE, flagged.get(0).quality());
        assertEquals(-1.7, flagged.get(0).lossPawns(), EPS);
        assertEquals("2.", flagged.get(1).plyLabel());
        assertEquals("Qh5", flagged.get(1).san());
        assertEquals(MoveQuality.BLUNDER, flagged.get(1).quality());
    }

    @Test
    public void of_neverCountsAnImprovementAsNegativeLoss() {
        // The eval swings in the mover's favour (engine depth noise): loss 0, not -40.
        PostGameReport report =
                PostGameReport.of(
                        Collections.singletonList("e2e4"),
                        Arrays.asList(10, -50),
                        Collections.singletonList("e4"));

        assertEquals(0.0, report.white().averageLossPawns(), EPS);
        assertTrue(report.flaggedMoves().isEmpty());
    }

    @Test
    public void of_fallsBackToUciWhereSanIsMissingAndHandlesASideWithoutMoves() {
        PostGameReport report =
                PostGameReport.of(
                        Collections.singletonList("e2e4"),
                        Arrays.asList(400, 0),
                        Collections.emptyList());

        assertEquals("e2e4", report.flaggedMoves().get(0).san());
        assertEquals("1.", report.flaggedMoves().get(0).plyLabel());
        assertEquals(0.0, report.black().averageLossPawns(), EPS);
    }

    @Test
    public void sanMoveList_dropsMoveNumbers() {
        assertEquals(
                Arrays.asList("e4", "e5", "Nf3", "O-O"),
                PostGameReport.sanMoveList("1. e4 e5 2. Nf3  3. O-O"));
        assertTrue(PostGameReport.sanMoveList("").isEmpty());
    }
}
