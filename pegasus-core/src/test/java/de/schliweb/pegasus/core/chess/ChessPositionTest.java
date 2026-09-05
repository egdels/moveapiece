/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.pegasus.core.chess;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import de.schliweb.pegasus.core.protocol.BoardState;
import java.util.List;
import org.junit.Test;

/**
 * Rules-layer tests. Perft node counts are the standard published reference values
 * (chessprogramming.org) and validate move generation including castling, en passant, promotion and
 * pins.
 */
public class ChessPositionTest {

    private static final String KIWIPETE =
            "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1";
    private static final String PERFT_POSITION_3 = "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1";
    private static final String PERFT_POSITION_4 =
            "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1";

    private static long perft(ChessPosition position, int depth) {
        if (depth == 0) {
            return 1;
        }
        long nodes = 0;
        for (Move move : position.legalMoves()) {
            nodes += perft(position.apply(move), depth - 1);
        }
        return nodes;
    }

    @Test
    public void perftStartingPosition() {
        ChessPosition start = ChessPosition.starting();
        assertEquals(20, perft(start, 1));
        assertEquals(400, perft(start, 2));
        assertEquals(8902, perft(start, 3));
        assertEquals(197281, perft(start, 4));
    }

    @Test
    public void perftKiwipete() {
        ChessPosition position = ChessPosition.fromFen(KIWIPETE);
        assertEquals(48, perft(position, 1));
        assertEquals(2039, perft(position, 2));
        assertEquals(97862, perft(position, 3));
    }

    @Test
    public void perftPosition3EnPassantHeavy() {
        ChessPosition position = ChessPosition.fromFen(PERFT_POSITION_3);
        assertEquals(14, perft(position, 1));
        assertEquals(191, perft(position, 2));
        assertEquals(2812, perft(position, 3));
        assertEquals(43238, perft(position, 4));
    }

    @Test
    public void perftPosition4PromotionHeavy() {
        ChessPosition position = ChessPosition.fromFen(PERFT_POSITION_4);
        assertEquals(6, perft(position, 1));
        assertEquals(264, perft(position, 2));
        assertEquals(9467, perft(position, 3));
    }

    @Test
    public void startingFenRoundTrip() {
        assertEquals(ChessPosition.STARTING_FEN, ChessPosition.starting().toFen());
    }

    @Test
    public void fenRoundTripPreservesAllFields() {
        String fen = "r3k2r/8/8/3Pp3/8/8/8/R3K2R w Kq e6 4 23";
        assertEquals(fen, ChessPosition.fromFen(fen).toFen());
    }

    @Test
    public void startingPositionBasics() {
        ChessPosition start = ChessPosition.starting();
        assertEquals(PieceColor.WHITE, start.sideToMove());
        assertEquals(Piece.WHITE_KING, start.pieceAt(BoardState.squareIndex("e1")));
        assertEquals(Piece.BLACK_QUEEN, start.pieceAt(BoardState.squareIndex("d8")));
        assertNull(start.pieceAt(BoardState.squareIndex("e4")));
        assertFalse(start.inCheck());
        assertEquals(-1, start.enPassantSquare());
    }

    @Test
    public void applyPawnDoublePushSetsEnPassant() {
        ChessPosition next = ChessPosition.starting().apply(Move.fromUci("e2e4"));
        assertEquals(BoardState.squareIndex("e3"), next.enPassantSquare());
        assertEquals(PieceColor.BLACK, next.sideToMove());
        assertEquals(Piece.WHITE_PAWN, next.pieceAt(BoardState.squareIndex("e4")));
        assertNull(next.pieceAt(BoardState.squareIndex("e2")));
    }

    @Test
    public void enPassantCaptureRemovesCapturedPawn() {
        // White pawn e5, black plays d7d5, white captures e5d6 e.p.
        ChessPosition position =
                ChessPosition.fromFen("rnbqkbnr/pppppppp/8/4P3/8/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1")
                        .apply(Move.fromUci("d7d5"));
        assertTrue(position.legalMoves().contains(Move.fromUci("e5d6")));
        ChessPosition after = position.apply(Move.fromUci("e5d6"));
        assertEquals(Piece.WHITE_PAWN, after.pieceAt(BoardState.squareIndex("d6")));
        assertNull(after.pieceAt(BoardState.squareIndex("d5")));
        assertNull(after.pieceAt(BoardState.squareIndex("e5")));
    }

    @Test
    public void castlingMovesRookToo() {
        ChessPosition position = ChessPosition.fromFen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1");
        List<Move> legal = position.legalMoves();
        assertTrue(legal.contains(Move.fromUci("e1g1")));
        assertTrue(legal.contains(Move.fromUci("e1c1")));
        ChessPosition afterShort = position.apply(Move.fromUci("e1g1"));
        assertEquals(Piece.WHITE_KING, afterShort.pieceAt(BoardState.squareIndex("g1")));
        assertEquals(Piece.WHITE_ROOK, afterShort.pieceAt(BoardState.squareIndex("f1")));
        assertNull(afterShort.pieceAt(BoardState.squareIndex("h1")));
        assertTrue(afterShort.legalMoves().contains(Move.fromUci("e8c8")));
        ChessPosition afterLong = afterShort.apply(Move.fromUci("e8c8"));
        assertEquals(Piece.BLACK_KING, afterLong.pieceAt(BoardState.squareIndex("c8")));
        assertEquals(Piece.BLACK_ROOK, afterLong.pieceAt(BoardState.squareIndex("d8")));
    }

    @Test
    public void castlingForbiddenThroughAttackedSquare() {
        // Black rook on f8 attacks f1: white may not castle kingside.
        ChessPosition position = ChessPosition.fromFen("5r2/8/8/8/8/8/8/4K2R w K - 0 1");
        assertFalse(position.legalMoves().contains(Move.fromUci("e1g1")));
    }

    @Test
    public void promotionGeneratesAllFourPieces() {
        ChessPosition position = ChessPosition.fromFen("8/4P3/8/8/8/8/8/K1k5 w - - 0 1");
        List<Move> legal = position.legalMoves();
        assertTrue(legal.contains(Move.fromUci("e7e8q")));
        assertTrue(legal.contains(Move.fromUci("e7e8r")));
        assertTrue(legal.contains(Move.fromUci("e7e8b")));
        assertTrue(legal.contains(Move.fromUci("e7e8n")));
        ChessPosition after = position.apply(Move.fromUci("e7e8n"));
        assertEquals(Piece.WHITE_KNIGHT, after.pieceAt(BoardState.squareIndex("e8")));
    }

    @Test
    public void pinnedPieceMayNotMove() {
        // White knight on d2 is pinned by the black rook on d8.
        ChessPosition position = ChessPosition.fromFen("3r4/8/8/8/8/8/3N4/3K4 w - - 0 1");
        for (Move move : position.legalMoves()) {
            assertFalse(
                    "Pinned knight moved: " + move.uci(),
                    move.from() == BoardState.squareIndex("d2"));
        }
    }

    @Test
    public void checkmateHasNoLegalMoves() {
        // Back-rank mate against black.
        ChessPosition position = ChessPosition.fromFen("R5k1/5ppp/8/8/8/8/8/6K1 b - - 0 1");
        assertTrue(position.inCheck());
        assertEquals(0, position.legalMoves().size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void applyRejectsIllegalMove() {
        ChessPosition.starting().apply(Move.fromUci("e2e5"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void fromFenRejectsGarbage() {
        ChessPosition.fromFen("not a fen");
    }

    @Test
    public void moveUciRoundTrip() {
        assertEquals("e2e4", Move.fromUci("e2e4").uci());
        assertEquals("e7e8q", Move.fromUci("e7e8q").uci());
        assertEquals(PieceType.KNIGHT, Move.fromUci("a7a8n").promotion());
        assertEquals("e1g1", Move.fromUci("e1g1").uci());
    }
}
