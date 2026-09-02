/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.training;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import de.schliweb.moveapiece.logic.ChessGame;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

/**
 * Guards the curated opening data: every line must be a sequence of legal moves from the start
 * position (catches typos in the hand-written UCI strings before they ship), and ids must be
 * unique.
 */
public class OpeningRepositoryTest {

    @Test
    public void everyLine_isLegalFromStartPosition() {
        for (OpeningLine line : OpeningRepository.ALL) {
            ChessGame game = new ChessGame();
            for (String uci : line.uciMoves()) {
                boolean applied = game.applyUciMove(uci);
                if (!applied) {
                    fail("Illegal move \"" + uci + "\" in opening \"" + line.id() + "\"");
                }
            }
        }
    }

    @Test
    public void ids_areUnique() {
        Set<String> ids = new HashSet<>();
        for (OpeningLine line : OpeningRepository.ALL) {
            assertTrue("Duplicate opening id: " + line.id(), ids.add(line.id()));
        }
    }

    @Test
    public void everyLine_hasAtLeastOneMove() {
        for (OpeningLine line : OpeningRepository.ALL) {
            assertFalse("Opening \"" + line.id() + "\" has no moves", line.uciMoves().isEmpty());
        }
    }

    @Test
    public void byId_findsKnownLineAndReturnsNullForUnknown() {
        OpeningLine first = OpeningRepository.ALL.get(0);
        assertTrue(OpeningRepository.byId(first.id()) == first);
        assertTrue(OpeningRepository.byId("does-not-exist") == null);
    }
}
