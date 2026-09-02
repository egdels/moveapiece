/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.training;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.github.bhlangonijr.chesslib.Side;
import java.util.Arrays;
import org.junit.Test;

public class TrainingSessionTest {

    private static final OpeningLine LINE =
            new OpeningLine("test", Arrays.asList("e2e4", "e7e5", "g1f3", "b8c6"));

    @Test
    public void isHumanTurnNow_whiteTrainee_alternatesStartingTrue() {
        TrainingSession session = new TrainingSession(LINE, Side.WHITE, true);
        assertTrue(session.isHumanTurnNow());
        session.advance();
        assertFalse(session.isHumanTurnNow());
        session.advance();
        assertTrue(session.isHumanTurnNow());
        session.advance();
        assertFalse(session.isHumanTurnNow());
    }

    @Test
    public void isHumanTurnNow_blackTrainee_alternatesStartingFalse() {
        TrainingSession session = new TrainingSession(LINE, Side.BLACK, true);
        assertFalse(session.isHumanTurnNow());
        session.advance();
        assertTrue(session.isHumanTurnNow());
    }

    @Test
    public void advance_progressesThroughLineAndCompletes() {
        TrainingSession session = new TrainingSession(LINE, Side.WHITE, true);
        assertFalse(session.isComplete());
        assertEquals("e2e4", session.currentExpectedUci());

        for (int i = 0; i < LINE.uciMoves().size(); i++) {
            assertFalse(session.isComplete());
            session.advance();
        }

        assertTrue(session.isComplete());
        assertNull(session.currentExpectedUci());
    }

    @Test
    public void totalPlies_matchesLineLength() {
        TrainingSession session = new TrainingSession(LINE, Side.WHITE, true);
        assertEquals(LINE.uciMoves().size(), session.totalPlies());
    }

    @Test
    public void hintsEnabled_reflectsConstructorArgument() {
        assertTrue(new TrainingSession(LINE, Side.WHITE, true).hintsEnabled());
        assertFalse(new TrainingSession(LINE, Side.WHITE, false).hintsEnabled());
    }
}
