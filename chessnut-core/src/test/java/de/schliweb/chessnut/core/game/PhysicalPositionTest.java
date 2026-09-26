/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.chessnut.core.game;

import static de.schliweb.chessnut.core.game.Boards.lift;
import static de.schliweb.chessnut.core.game.Boards.move;
import static de.schliweb.chessnut.core.game.Boards.put;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import de.schliweb.chessnut.core.game.InvalidPositionException.Reason;
import de.schliweb.pegasus.core.chess.ChessPosition;
import de.schliweb.pegasus.core.chess.PieceColor;
import de.schliweb.pegasus.core.protocol.BoardState;
import de.schliweb.pegasus.core.protocol.PieceCodes;
import org.junit.Test;

public class PhysicalPositionTest {

    private static Reason reasonOf(BoardState board, PieceColor side) {
        try {
            PhysicalPosition.fenOf(board, side);
            fail("expected InvalidPositionException");
            return null;
        } catch (InvalidPositionException e) {
            return e.reason();
        }
    }

    @Test
    public void startPositionRoundTrips() throws InvalidPositionException {
        assertEquals(
                ChessPosition.STARTING_FEN,
                PhysicalPosition.fenOf(Boards.start(), PieceColor.WHITE));
        assertEquals(
                "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR b KQkq - 0 1",
                PhysicalPosition.fenOf(Boards.start(), PieceColor.BLACK));
    }

    @Test
    public void castlingRightsFollowKingsAndRooksOnTheirSquares() throws InvalidPositionException {
        String rooksAndKings = "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1";
        BoardState kingBack = move(move(Boards.of(rooksAndKings), "e1", "e2"), "e2", "e1");
        assertEquals(rooksAndKings, PhysicalPosition.fenOf(kingBack, PieceColor.WHITE));

        BoardState noRookH1 = lift(Boards.start(), "h1");
        assertEquals(
                "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBN1 w Qkq - 0 1",
                PhysicalPosition.fenOf(noRookH1, PieceColor.WHITE));

        String fen = "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1";
        BoardState blackKingOff = move(Boards.of(fen), "e8", "d8");
        assertEquals(
                "r2k3r/8/8/8/8/8/8/R3K2R w KQ - 0 1",
                PhysicalPosition.fenOf(blackKingOff, PieceColor.WHITE));
    }

    @Test
    public void rejectsUnplayableBoards() {
        assertEquals(Reason.NO_BOARD, reasonOf(null, PieceColor.WHITE));
        assertEquals(Reason.KINGS, reasonOf(lift(Boards.start(), "e8"), PieceColor.WHITE));
        assertEquals(
                Reason.KINGS,
                reasonOf(put(Boards.start(), "e4", PieceCodes.WKING), PieceColor.WHITE));
        assertEquals(
                Reason.PAWN_ON_BACK_RANK,
                reasonOf(move(Boards.start(), "a2", "a1"), PieceColor.WHITE));
        // White queen gives check to the black king while White is to move: Black could not have
        // left the king there, so the board is only playable with Black to move.
        BoardState check = Boards.of("4k3/8/8/8/8/8/8/4KQ2 w - - 0 1");
        BoardState queenOnE2 = move(check, "f1", "e2");
        assertEquals(Reason.OPPONENT_IN_CHECK, reasonOf(queenOnE2, PieceColor.WHITE));
        assertEquals("4k3/8/8/8/8/8/4Q3/4K3 b - - 0 1", safeFen(queenOnE2, PieceColor.BLACK));
    }

    private static String safeFen(BoardState board, PieceColor side) {
        try {
            return PhysicalPosition.fenOf(board, side);
        } catch (InvalidPositionException e) {
            fail("unexpected " + e.reason());
            return null;
        }
    }
}
