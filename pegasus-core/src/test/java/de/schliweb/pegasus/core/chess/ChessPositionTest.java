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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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

    // --- Chess960 -------------------------------------------------------------------------------
    // Perft values computed with python-chess (chess960=True); the whole rules layer was also
    // compared move by move with python-chess on about 526,000 positions from random Chess960
    // games.

    @Test
    public void chess960_perft() {
        ChessPosition position =
                ChessPosition.fromFen(
                        "bqnb1rkr/pp3ppp/3ppn2/2p5/5P2/P2P4/NPP1P1PP/BQ1BNRKR w HFhf - 2 9");
        assertTrue(position.isChess960());
        assertEquals(21, perft(position, 1));
        assertEquals(528, perft(position, 2));
        assertEquals(12189, perft(position, 3));

        ChessPosition kingNextToRook =
                ChessPosition.fromFen("rk4r1/pppppppp/8/8/8/8/PPPPPPPP/RK4R1 w GAga - 0 1");
        assertEquals(24, perft(kingNextToRook, 1));
        assertEquals(576, perft(kingNextToRook, 2));
        assertEquals(13494, perft(kingNextToRook, 3));
    }

    @Test
    public void chess960_castleIsTheKingOntoItsRook() {
        ChessPosition position =
                ChessPosition.fromFen("rk4r1/pppppppp/8/8/8/8/PPPPPPPP/RK4R1 w GAga - 0 1");
        List<Move> legal = position.legalMoves();
        assertTrue(legal.contains(Move.fromUci("b1a1"))); // O-O-O
        assertTrue(legal.contains(Move.fromUci("b1g1"))); // O-O
        assertTrue(legal.contains(Move.fromUci("b1c1"))); // plain king step

        ChessPosition long960 = position.apply(Move.fromUci("b1a1"));
        assertEquals("rk4r1/pppppppp/8/8/8/8/PPPPPPPP/2KR2R1 b ga - 1 1", long960.toFen());
        ChessPosition step = position.apply(Move.fromUci("b1c1"));
        assertEquals("rk4r1/pppppppp/8/8/8/8/PPPPPPPP/R1K3R1 b ga - 1 1", step.toFen());
    }

    @Test
    public void destinationsFrom_listsTheKingsCastlingSquaresNotTheRooks() {
        ChessPosition position =
                ChessPosition.fromFen("rk4r1/pppppppp/8/8/8/8/PPPPPPPP/RK4R1 w GAga - 0 1");
        // c1 is a plain king step and the O-O-O square at once; g1 (O-O) holds the rook.
        assertEquals(Set.of("c1", "g1"), squareNames(position.destinationsFrom(sq("b1"))));
    }

    @Test
    public void destinationsFrom_skipsACastleThatLeavesTheKingWhereItStands() {
        ChessPosition position = ChessPosition.fromFen("1r4kr/8/8/8/8/8/8/1R4KR w HBhb - 0 1");
        // O-O (g1h1) only moves the rook; O-O-O (g1b1) puts the king on c1.
        assertEquals(
                Set.of("c1", "f1", "f2", "g2"), // h2 is covered by the rook on h8
                squareNames(position.destinationsFrom(sq("g1"))));
    }

    @Test
    public void destinationsFrom_keepsStandardCastling() {
        ChessPosition position = ChessPosition.fromFen(KIWIPETE);
        assertEquals(
                Set.of("c1", "d1", "f1", "g1"), squareNames(position.destinationsFrom(sq("e1"))));
    }

    private static int sq(String name) {
        return BoardState.squareIndex(name);
    }

    private static Set<String> squareNames(List<Integer> squares) {
        assertEquals("no square twice", squares.size(), new HashSet<>(squares).size());
        Set<String> names = new HashSet<>();
        for (int square : squares) {
            names.add(BoardState.squareName(square));
        }
        return names;
    }

    @Test
    public void chess960_kingAlreadyOnItsCastlingSquare() {
        ChessPosition position = ChessPosition.fromFen("1r4kr/8/8/8/8/8/8/1R4KR w HBhb - 0 1");
        assertEquals(
                "1r4kr/8/8/8/8/8/8/1R3RK1 b hb - 1 1",
                position.apply(Move.fromUci("g1h1")).toFen());
        assertEquals(24, perft(position, 1));
        assertEquals(479, perft(position, 2));
        assertEquals(10954, perft(position, 3));
    }

    @Test
    public void chess960_castlingRookMayNotShieldTheKingsDestination() {
        // The rook on b8 shields c8 from the queen on a8; after O-O-O the king would be in check.
        ChessPosition position =
                ChessPosition.fromFen(
                        "Qr1k4/nb1p1rq1/4pb1p/2NP1pp1/p3BP1P/PNp1K1P1/1P2P1R1/1RB5 b b - 6 22");
        assertFalse(position.legalMoves().contains(Move.fromUci("d8b8")));
    }

    @Test
    public void chess960_xFenAndForcedChess960() {
        // X-FEN "KQkq" with rooks on b and f: a Chess960 position, written back in Shredder form.
        ChessPosition xFen =
                ChessPosition.fromFen("nrbqkrnb/pppppppp/8/8/8/8/PPPPPPPP/NRBQKRNB w KQkq - 0 1");
        assertTrue(xFen.isChess960());
        assertEquals("nrbqkrnb/pppppppp/8/8/8/8/PPPPPPPP/NRBQKRNB w FBfb - 0 1", xFen.toFen());

        ChessPosition standard = ChessPosition.starting();
        assertFalse(standard.isChess960());
        ChessPosition array518 = ChessPosition.fromFen(ChessPosition.STARTING_FEN, true);
        assertTrue(array518.isChess960());
        assertEquals("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w HAha - 0 1", array518.toFen());
        assertEquals(
                ChessPosition.STARTING_FEN,
                ChessPosition.fromFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w HAha - 0 1")
                        .toFen()
                        .replace("HAha", "KQkq"));
    }

    @Test
    public void chess960_movingARookEndsOnlyItsRight() {
        ChessPosition position =
                ChessPosition.fromFen("rk4r1/pppppppp/8/8/8/8/PPPPPPPP/RK4R1 w GAga - 0 1");
        assertEquals(
                "rk4r1/pppppppp/8/8/8/8/PPPPPPPP/RK3R2 b Aga - 1 1",
                position.apply(Move.fromUci("g1f1")).toFen());
    }
}
