/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.move.Move;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

/**
 * Slot numbers below follow the reference implementation's {@code get_all_possible_moves}: {@code
 * from * 64 + to} with a1 = 0 .. h8 = 63, then the promotions from 4096 on.
 */
public class MaiaMoveIndexerTest {

    private static int index(String uci, Side side) {
        return MaiaMoveIndexer.indexOf(new Move(uci, side), side == Side.BLACK);
    }

    @Test
    public void whiteMove_isFromTimes64PlusTo() {
        // e2 = 12, e4 = 28
        assertEquals(12 * 64 + 28, index("e2e4", Side.WHITE));
    }

    /** Black's move is flipped top to bottom, so e7e5 lands on the slot of e2e4. */
    @Test
    public void blackMove_isMirrored() {
        assertEquals(index("e2e4", Side.WHITE), index("e7e5", Side.BLACK));
        assertEquals(index("g1f3", Side.WHITE), index("g8f6", Side.BLACK));
    }

    /** Castling is the plain king move, not "king takes rook". */
    @Test
    public void castling_isTheKingMove() {
        // e1 = 4, g1 = 6, c1 = 2
        assertEquals(4 * 64 + 6, index("e1g1", Side.WHITE));
        assertEquals(4 * 64 + 2, index("e8c8", Side.BLACK));
    }

    private static int index(String fen, String uci) {
        Board board = new Board();
        board.loadFromFen(fen, true);
        return MaiaMoveIndexer.indexOf(board, new Move(uci, board.getSideToMove()));
    }

    /** A Chess960 castle (king onto its rook) takes the slot of the king's move to g1/c1. */
    @Test
    public void chess960Castling_isTheKingMoveToItsCastlingSquare() {
        String kingB1 = "rk4r1/pppppppp/8/8/8/8/PPPPPPPP/RK4R1 w GAga - 0 1";
        // b1 = 1, c1 = 2, g1 = 6
        assertEquals(1 * 64 + 2, index(kingB1, "b1a1")); // O-O-O
        assertEquals(1 * 64 + 6, index(kingB1, "b1g1")); // O-O
        // The plain king step b1c1 shares that slot: Maia never learnt to tell them apart.
        assertEquals(1 * 64 + 2, index(kingB1, "b1c1"));
        // Black, mirrored: b8a8 lands on the slot of b1c1.
        String blackToMove = "rk4r1/pppppppp/8/8/8/8/PPPPPPPP/RK4R1 b GAga - 0 1";
        assertEquals(1 * 64 + 2, index(blackToMove, "b8a8"));
    }

    /** With the king already on its castling square, the castle takes the rook's move slot. */
    @Test
    public void chess960CastlingWithTheKingStaying_isTheRookMove() {
        // g1 = 6, h1 = 7, f1 = 5
        assertEquals(7 * 64 + 5, index("1r4kr/8/8/8/8/8/8/1R4KR w HBhb - 0 1", "g1h1"));
    }

    /**
     * A Chess960 castle ranks with the bonus; the king step on its slot does not, so with the same
     * logit the castle wins whatever the order of the move list.
     */
    @Test
    public void chess960Castling_getsTheBonusAndBeatsTheKingStepOnItsSlot() {
        Board board = new Board();
        board.loadFromFen("rk4r1/pppppppp/8/8/8/8/PPPPPPPP/RK4R1 w GAga - 0 1", true);
        float[] policy = new float[MaiaMoveIndexer.POLICY_SIZE];
        policy[1 * 64 + 2] = 3f; // b1c1, shared by O-O-O (b1a1) and the plain step
        policy[1 * 64 + 6] = 2f; // b1g1 (O-O)
        policy[8 * 64 + 16] = 1f; // a2a3
        Move castle = new Move("b1a1", Side.WHITE);
        Move step = new Move("b1c1", Side.WHITE);
        assertEquals(
                3f + MaiaMoveIndexer.CHESS960_CASTLE_BONUS,
                MaiaMoveIndexer.logitOf(board, castle, policy),
                0f);
        assertEquals(3f, MaiaMoveIndexer.logitOf(board, step, policy), 0f);
        assertEquals(
                2f + MaiaMoveIndexer.CHESS960_CASTLE_BONUS,
                MaiaMoveIndexer.logitOf(board, new Move("b1g1", Side.WHITE), policy),
                0f);
        assertEquals(1f, MaiaMoveIndexer.logitOf(board, new Move("a2a3", Side.WHITE), policy), 0f);
    }

    /** Standard castling learnt from standard games gets no bonus. */
    @Test
    public void standardCastling_getsNoBonus() {
        Board board = new Board();
        board.loadFromFen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1");
        float[] policy = new float[MaiaMoveIndexer.POLICY_SIZE];
        policy[4 * 64 + 6] = 2f;
        assertEquals(2f, MaiaMoveIndexer.logitOf(board, new Move("e1g1", Side.WHITE), policy), 0f);
    }

    /** In a standard game the board-aware lookup is the plain one. */
    @Test
    public void standardCastling_unchangedWithTheBoard() {
        Board board = new Board();
        board.loadFromFen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1");
        assertEquals(4 * 64 + 6, MaiaMoveIndexer.indexOf(board, new Move("e1g1", Side.WHITE)));
    }

    @Test
    public void promotions_haveTheirOwnSlots() {
        assertEquals(4096, index("a7a8q", Side.WHITE));
        assertEquals(4097, index("a7a8r", Side.WHITE));
        assertEquals(4098, index("a7a8b", Side.WHITE));
        assertEquals(4099, index("a7a8n", Side.WHITE));
        assertEquals(4096 + 4, index("a7b8q", Side.WHITE));
        // b7 -> a8, queen: from file 1, to file 0
        assertEquals(4096 + 32, index("b7a8q", Side.WHITE));
        assertEquals(4096 + 7 * 32 + 7 * 4 + 3, index("h7h8n", Side.WHITE));
        assertEquals(MaiaMoveIndexer.POLICY_SIZE - 1, index("h7h8n", Side.WHITE));
    }

    @Test
    public void blackPromotion_usesTheMirroredSlot() {
        assertEquals(index("g7h8q", Side.WHITE), index("g2h1q", Side.BLACK));
        assertEquals(index("a7a8n", Side.WHITE), index("a2a1n", Side.BLACK));
    }

    /** No two legal moves of a position may share a slot, or one would shadow the other. */
    @Test
    public void legalMoves_getDistinctSlots() {
        String[] fens = {
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
            "r3k2r/pppq1ppp/2npbn2/2b1p3/2B1P3/2NPBN2/PPPQ1PPP/R3K2R w KQkq - 0 9",
            "r3k2r/pppq1ppp/2npbn2/2b1p3/2B1P3/2NPBN2/PPPQ1PPP/R3K2R b KQkq - 0 9",
            "rn1q1bnr/pPpk2pp/8/3pp3/8/8/PP1PPPpP/RNBQKB1R w KQ - 0 8",
            "rn1q1bnr/pPpk2pp/8/3pp3/8/8/PP1PPPpP/RNBQKB1R b KQ - 0 8",
            "rnbqkbnr/1pp1pppp/p7/3pP3/8/8/PPPP1PPP/RNBQKBNR w KQkq d6 0 3",
        };
        for (String fen : fens) {
            Board board = new Board();
            board.loadFromFen(fen);
            boolean blackToMove = board.getSideToMove() == Side.BLACK;
            Set<Integer> seen = new HashSet<>();
            for (Move move : board.legalMoves()) {
                int index = MaiaMoveIndexer.indexOf(move, blackToMove);
                assertTrue(fen + " " + move, index >= 0 && index < MaiaMoveIndexer.POLICY_SIZE);
                assertTrue("slot used twice in " + fen + ": " + move, seen.add(index));
            }
        }
    }
}
