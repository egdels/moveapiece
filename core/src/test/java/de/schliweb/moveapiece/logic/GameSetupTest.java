/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.logic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.github.bhlangonijr.chesslib.Side;
import de.schliweb.moveapiece.training.OpeningLine;
import de.schliweb.moveapiece.training.OpeningRepository;
import org.junit.Test;

/**
 * The persisted "last game setup" must survive whatever an older build (or a hand-edited prefs
 * file) left behind: every field falls back to something the app can start rather than throwing.
 */
public class GameSetupTest {

    @Test
    public void fromKeys_roundTripsEveryOpponent() {
        for (Opponent opponent : Opponent.values()) {
            OpeningLine line = OpeningRepository.ALL.get(2);
            GameSetup original =
                    opponent == Opponent.TRAINER
                            ? GameSetup.training(line, Side.BLACK, false)
                            : GameSetup.of(opponent, Side.BLACK);
            GameSetup restored =
                    GameSetup.fromKeys(
                            original.opponent().key(),
                            original.side().name(),
                            original.openingId(),
                            original.hintsEnabled());
            assertEquals(original, restored);
        }
    }

    @Test
    public void fromKeys_nothingPersistedYet_isTheDefault() {
        assertEquals(GameSetup.DEFAULT, GameSetup.fromKeys(null, null, null, true));
    }

    @Test
    public void fromKeys_unknownOpponentAndSide_fallBackToStockfishAsWhite() {
        GameSetup setup = GameSetup.fromKeys("gnuchess", "PINK", null, false);
        assertSame(Opponent.STOCKFISH, setup.opponent());
        assertSame(Side.WHITE, setup.side());
        assertNull(setup.opening());
        assertFalse(setup.hintsEnabled());
    }

    @Test
    public void fromKeys_trainerWithRemovedOpening_drillsFirstLineInstead() {
        GameSetup setup = GameSetup.fromKeys("trainer", "WHITE", "no-such-line", true);
        assertSame(Opponent.TRAINER, setup.opponent());
        assertSame(OpeningRepository.ALL.get(0), setup.opening());
        assertTrue(setup.hintsEnabled());
    }

    @Test
    public void fromKeys_trainerWithoutOpeningId_drillsFirstLineInstead() {
        GameSetup setup = GameSetup.fromKeys("trainer", "BLACK", null, true);
        assertSame(OpeningRepository.ALL.get(0), setup.opening());
        assertSame(Side.BLACK, setup.side());
    }

    @Test
    public void nonTrainerSetup_dropsAnyOpening() {
        GameSetup setup =
                new GameSetup(Opponent.MAIA, Side.WHITE, OpeningRepository.ALL.get(0), true);
        assertNull(setup.opening());
        assertNull(setup.openingId());
    }

    @Test(expected = NullPointerException.class)
    public void trainerSetup_requiresAnOpening() {
        new GameSetup(Opponent.TRAINER, Side.WHITE, null, true);
    }

    @Test
    public void opponentFromKey_unknown_isStockfish() {
        assertSame(Opponent.STOCKFISH, Opponent.fromKey(null));
        assertSame(Opponent.STOCKFISH, Opponent.fromKey("whatever"));
        assertSame(Opponent.MAIA, Opponent.fromKey("maia"));
    }
}
