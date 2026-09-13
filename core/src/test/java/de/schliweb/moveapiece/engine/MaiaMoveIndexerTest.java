/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.engine;

import static org.junit.Assert.assertEquals;

import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.move.Move;
import com.github.bhlangonijr.chesslib.move.MoveConversionException;
import org.junit.Test;

public class MaiaMoveIndexerTest {

    /** White's own move needs no transform - real UCI and network-space notation coincide. */
    @Test
    public void whiteNormalMove_isUnchanged() {
        Board board = new Board();
        assertEquals("e2e4", MaiaMoveIndexer.toNetworkMove(board, new Move("e2e4", com.github.bhlangonijr.chesslib.Side.WHITE)));
    }

    /**
     * Black's move gets rank-mirrored (file unchanged) so the network always sees "itself" as White
     * sitting at the bottom of the board - the same convention lc0 uses, see
     * MAIA_PROVENANCE_TEMPLATE.md.
     */
    @Test
    public void blackNormalMove_isRankMirrored() throws MoveConversionException {
        Board board = new Board();
        board.doMove(new Move("e2e4", com.github.bhlangonijr.chesslib.Side.WHITE));
        // e7e5 for Black mirrors to e2e4 in network space (rank 7 -> rank 2, rank 5 -> rank 4).
        assertEquals(
                "e2e4",
                MaiaMoveIndexer.toNetworkMove(board, new Move("e7e5", com.github.bhlangonijr.chesslib.Side.BLACK)));
    }

    /**
     * White's kingside castle is "king captures own rook" in network space (e1h1, not e1g1) - see
     * MAIA_PROVENANCE_TEMPLATE.md, confirmed against lc0's own {@code policy_index} table
     * (e1g1 -> 102, e1h1 -> 103; only e1h1 is ever used for a real castling move).
     */
    @Test
    public void whiteKingsideCastle_isKingCapturesRook() throws MoveConversionException {
        Board board = new Board();
        board.loadFromFen("rnbqk2r/pppp1ppp/5n2/4p3/1b2P3/2N2N2/PPPPBPPP/R1BQK2R w KQkq - 4 5");
        assertEquals(
                "e1h1",
                MaiaMoveIndexer.toNetworkMove(
                        board, new Move("e1g1", com.github.bhlangonijr.chesslib.Side.WHITE)));
    }

    /** White's queenside castle: rook is on the a-file, so the network sees "e1a1", not "e1c1". */
    @Test
    public void whiteQueensideCastle_isKingCapturesRook() throws MoveConversionException {
        Board board = new Board();
        board.loadFromFen(
                "r3kbnr/pppqpppp/2n5/3p1b2/3P1B2/2N5/PPPQPPPP/R3KBNR w KQkq - 6 5");
        assertEquals(
                "e1a1",
                MaiaMoveIndexer.toNetworkMove(
                        board, new Move("e1c1", com.github.bhlangonijr.chesslib.Side.WHITE)));
    }

    /**
     * Black's kingside castle: king-captures-rook first (e8h8), then rank-mirrored for Black
     * (rank 8 -> rank 1) - ends up as the exact same string as White's kingside castle, "e1h1",
     * which is the intended effect of always presenting the network with "itself as White".
     */
    @Test
    public void blackKingsideCastle_isMirroredKingCapturesRook() throws MoveConversionException {
        Board board = new Board();
        board.loadFromFen("rnbqk2r/pppp1ppp/5n2/4p3/1b2P3/2N2N2/PPPPBPPP/R1BQK2R b kq - 4 5");
        assertEquals(
                "e1h1",
                MaiaMoveIndexer.toNetworkMove(
                        board, new Move("e8g8", com.github.bhlangonijr.chesslib.Side.BLACK)));
    }

    /** Promotion appends the lowercase piece letter, same as standard UCI notation. */
    @Test
    public void promotion_appendsPieceLetter() throws MoveConversionException {
        Board board = new Board();
        board.loadFromFen("8/4P3/8/8/8/8/k6K/8 w - - 0 1");
        assertEquals(
                "e7e8q",
                MaiaMoveIndexer.toNetworkMove(
                        board, new Move("e7e8q", com.github.bhlangonijr.chesslib.Side.WHITE)));
    }

    /** Every network-space string this class can produce must be a real slot in the 1858 table. */
    @Test
    public void everyLegalMoveFromStartpos_resolvesToAKnownPolicyIndex() {
        Board board = new Board();
        for (Move move : board.legalMoves()) {
            String networkMove = MaiaMoveIndexer.toNetworkMove(board, move);
            org.junit.Assert.assertTrue(
                    "no policy slot for " + networkMove + " (from " + move + ")",
                    MaiaPolicyIndex.indexOf(networkMove) >= 0);
        }
    }
}
