/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.logic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.Square;
import java.util.List;
import org.junit.Test;

/**
 * Covers the chesslib wiring in ChessGame, in particular the special moves (castling, en passant,
 * promotion) that are easy to get wrong when mapping UI taps to board moves.
 */
public class ChessGameTest {

    @Test
    public void initialPosition_whiteToMoveWithStandardSetup() {
        ChessGame game = new ChessGame();
        assertEquals(Side.WHITE, game.sideToMove());
        assertEquals(Piece.WHITE_PAWN, game.pieceAt(Square.E2));
        assertEquals(Piece.BLACK_KING, game.pieceAt(Square.E8));
        assertEquals(Piece.NONE, game.pieceAt(Square.E4));
    }

    @Test
    public void legalDestinationsFrom_knightOnB1() {
        ChessGame game = new ChessGame();
        List<Square> destinations = game.legalDestinationsFrom(Square.B1);
        assertTrue(destinations.contains(Square.A3));
        assertTrue(destinations.contains(Square.C3));
        assertEquals(2, destinations.size());
    }

    @Test
    public void applyMove_pawnAdvanceSwitchesSideToMove() {
        ChessGame game = new ChessGame();
        assertTrue(game.applyMove(Square.E2, Square.E4, null));
        assertEquals(Side.BLACK, game.sideToMove());
        assertEquals(Piece.WHITE_PAWN, game.pieceAt(Square.E4));
        assertEquals(Piece.NONE, game.pieceAt(Square.E2));
    }

    @Test
    public void applyMove_illegalMoveIsRejectedAndStateUnchanged() {
        ChessGame game = new ChessGame();
        assertFalse(game.applyMove(Square.E2, Square.E5, null));
        assertEquals(Side.WHITE, game.sideToMove());
        assertEquals(Piece.WHITE_PAWN, game.pieceAt(Square.E2));
    }

    @Test
    public void undoLastMove_restoresPositionAndSideToMove() {
        ChessGame game = new ChessGame();
        game.applyMove(Square.E2, Square.E4, null);
        assertTrue(game.undoLastMove());
        assertEquals(Side.WHITE, game.sideToMove());
        assertEquals(Piece.WHITE_PAWN, game.pieceAt(Square.E2));
        assertEquals(Piece.NONE, game.pieceAt(Square.E4));
        assertEquals(0, game.moveCount());
    }

    @Test
    public void undoLastMove_onEmptyHistoryReturnsFalse() {
        ChessGame game = new ChessGame();
        assertFalse(game.undoLastMove());
    }

    @Test
    public void redoMove_reappliesTheUndoneMove() {
        ChessGame game = new ChessGame();
        game.applyMove(Square.E2, Square.E4, null);
        game.undoLastMove();

        assertTrue(game.canRedo());
        assertTrue(game.redoMove());
        assertEquals(Side.BLACK, game.sideToMove());
        assertEquals(Piece.WHITE_PAWN, game.pieceAt(Square.E4));
        assertEquals(Piece.NONE, game.pieceAt(Square.E2));
        assertEquals(1, game.moveCount());
        assertFalse(game.canRedo());
    }

    @Test
    public void redoMove_onEmptyRedoStackReturnsFalse() {
        ChessGame game = new ChessGame();
        assertFalse(game.canRedo());
        assertFalse(game.redoMove());
    }

    @Test
    public void applyingANewMoveAfterUndoDiscardsTheRedoStack() {
        ChessGame game = new ChessGame();
        game.applyMove(Square.E2, Square.E4, null);
        game.undoLastMove();
        assertTrue(game.canRedo());

        // A different move than the undone one branches off - the old "future" is gone.
        assertTrue(game.applyMove(Square.D2, Square.D4, null));

        assertFalse(game.canRedo());
        assertFalse(game.redoMove());
        assertEquals(Piece.WHITE_PAWN, game.pieceAt(Square.D4));
    }

    @Test
    public void multipleUndoRedoRoundTripRestoresTheFullLine() {
        ChessGame game = new ChessGame();
        game.applyUciMove("e2e4");
        game.applyUciMove("e7e5");
        game.applyUciMove("g1f3");

        assertTrue(game.undoLastMove());
        assertTrue(game.undoLastMove());
        assertTrue(game.undoLastMove());
        assertEquals(0, game.moveCount());

        assertTrue(game.redoMove());
        assertTrue(game.redoMove());
        assertTrue(game.redoMove());
        assertFalse(game.canRedo());
        assertEquals(3, game.moveCount());
        assertEquals(Piece.WHITE_KNIGHT, game.pieceAt(Square.F3));
        assertEquals(Piece.NONE, game.pieceAt(Square.G1));
    }

    @Test
    public void jumpToPly_backwardsFromTheCurrentPosition() {
        ChessGame game = new ChessGame();
        game.applyUciMove("e2e4");
        game.applyUciMove("e7e5");
        game.applyUciMove("g1f3");

        assertEquals(1, game.jumpToPly(1));
        assertEquals(1, game.moveCount());
        assertEquals(Piece.WHITE_PAWN, game.pieceAt(Square.E4));
        assertEquals(Piece.NONE, game.pieceAt(Square.F3));
        assertTrue(game.canRedo());
    }

    @Test
    public void jumpToPly_forwardsUsesTheRedoStack() {
        ChessGame game = new ChessGame();
        game.applyUciMove("e2e4");
        game.applyUciMove("e7e5");
        game.applyUciMove("g1f3");
        game.jumpToPly(0);

        assertEquals(3, game.jumpToPly(3));
        assertEquals(3, game.moveCount());
        assertEquals(Piece.WHITE_KNIGHT, game.pieceAt(Square.F3));
        assertFalse(game.canRedo());
    }

    @Test
    public void jumpToPly_clampsToWhatIsReachable() {
        ChessGame game = new ChessGame();
        game.applyUciMove("e2e4");

        assertEquals(0, game.jumpToPly(-5));
        assertEquals(1, game.jumpToPly(50));
        assertEquals(1, game.moveCount());
    }

    @Test
    public void toFullSan_keepsShowingRedoableMovesAfterUndo() {
        ChessGame game = new ChessGame();
        game.applyUciMove("e2e4");
        game.applyUciMove("e7e5");
        game.applyUciMove("g1f3");

        String beforeUndo = game.toFullSan();
        game.undoLastMove();
        game.undoLastMove();

        assertEquals(1, game.moveCount());
        assertEquals(3, game.totalPlyCount());
        // toSan() only reflects what's currently applied - shrinks on undo.
        assertEquals("1. e4", game.toSan());
        // toFullSan() keeps showing the still-redoable future moves too.
        assertEquals(beforeUndo, game.toFullSan());
    }

    @Test
    public void toFullSan_dropsRedoableMovesOnceANewMoveBranchesOff() {
        ChessGame game = new ChessGame();
        game.applyUciMove("e2e4");
        game.applyUciMove("e7e5");
        game.undoLastMove();

        // Black's turn again - a different reply than the undone e7e5 branches off.
        assertTrue(game.applyUciMove("g8f6"));

        assertEquals("1. e4 Nf6", game.toFullSan());
        assertEquals(2, game.totalPlyCount());
    }

    @Test
    public void applyUciMove_matchesApplyMoveBySquare() {
        ChessGame game = new ChessGame();
        assertTrue(game.applyUciMove("e2e4"));
        assertEquals(Piece.WHITE_PAWN, game.pieceAt(Square.E4));
        assertEquals(1, game.moveCount());
    }

    /**
     * Regression test for a crash seen with a physical DGT Pegasus board: PegasusGameBridge tracks
     * its own parallel position and can confirm a move (e.g. after a reconnect resync) whose origin
     * square has since drifted from MoveAPiece's actual board - applyUciMove must reject that
     * gracefully rather than crash. chesslib's Board.doMove(move) (no full validation) skips the
     * null-piece guard in isMoveLegal() and NPEs when the origin square is empty; applyUciMove must
     * use full validation.
     */
    @Test
    public void applyUciMove_withEmptyOriginSquareIsRejectedNotCrash() {
        ChessGame game = new ChessGame();
        assertFalse(game.applyUciMove("e4e5"));
        assertEquals(Side.WHITE, game.sideToMove());
        assertEquals(0, game.moveCount());
    }

    @Test
    public void castlingKingside_movesRookToo() {
        ChessGame game = new ChessGame();
        game.loadFen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1");

        assertTrue(game.legalDestinationsFrom(Square.E1).contains(Square.G1));
        assertTrue(game.applyMove(Square.E1, Square.G1, null));

        assertEquals(Piece.WHITE_KING, game.pieceAt(Square.G1));
        assertEquals(Piece.WHITE_ROOK, game.pieceAt(Square.F1));
        assertEquals(Piece.NONE, game.pieceAt(Square.E1));
        assertEquals(Piece.NONE, game.pieceAt(Square.H1));
    }

    @Test
    public void castlingQueenside_movesRookToo() {
        ChessGame game = new ChessGame();
        game.loadFen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1");

        assertTrue(game.applyMove(Square.E1, Square.C1, null));

        assertEquals(Piece.WHITE_KING, game.pieceAt(Square.C1));
        assertEquals(Piece.WHITE_ROOK, game.pieceAt(Square.D1));
        assertEquals(Piece.NONE, game.pieceAt(Square.A1));
    }

    @Test
    public void enPassantCapture_removesCapturedPawn() {
        ChessGame game = new ChessGame();
        // White just pushed e2-e4 past a black pawn on d4; black captures en passant.
        game.loadFen("rnbqkbnr/ppp1pppp/8/8/3pP3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 2");

        assertTrue(game.legalDestinationsFrom(Square.D4).contains(Square.E3));
        assertTrue(game.applyMove(Square.D4, Square.E3, null));

        assertEquals(Piece.BLACK_PAWN, game.pieceAt(Square.E3));
        assertEquals(Piece.NONE, game.pieceAt(Square.E4));
        assertEquals(Piece.NONE, game.pieceAt(Square.D4));
    }

    @Test
    public void promotion_isDetectedAndAppliesChosenPiece() {
        ChessGame game = new ChessGame();
        game.loadFen("8/P6k/8/8/8/8/7K/8 w - - 0 1");

        assertTrue(game.isPromotion(Square.A7, Square.A8));
        assertTrue(game.applyMove(Square.A7, Square.A8, Piece.WHITE_QUEEN));

        assertEquals(Piece.WHITE_QUEEN, game.pieceAt(Square.A8));
    }

    @Test
    public void foolsMate_isDetectedAsCheckmate() {
        ChessGame game = new ChessGame();
        game.applyMove(Square.F2, Square.F3, null);
        game.applyMove(Square.E7, Square.E5, null);
        game.applyMove(Square.G2, Square.G4, null);
        assertFalse(game.isGameOver());

        game.applyMove(Square.D8, Square.H4, null);

        assertTrue(game.isCheckmate());
        assertTrue(game.isGameOver());
        assertFalse(game.isStalemate());
    }

    @Test
    public void stalemate_isDetectedAndIsNotCheckmate() {
        ChessGame game = new ChessGame();
        game.loadFen("5k2/5P2/5K2/8/8/8/8/8 b - - 0 1");

        assertTrue(game.isStalemate());
        assertTrue(game.isDraw());
        assertTrue(game.isGameOver());
        assertFalse(game.isCheckmate());
        assertFalse(game.isCheck());
    }

    @Test
    public void insufficientMaterial_isADraw() {
        ChessGame game = new ChessGame();
        game.loadFen("8/8/4k3/8/8/8/4K3/8 w - - 0 1");

        assertTrue(game.isDraw());
        assertTrue(game.isGameOver());
        assertFalse(game.isCheckmate());
    }

    @Test
    public void toSan_rendersMoveNumbersAndAlgebraicNotation() {
        ChessGame game = new ChessGame();
        assertEquals("", game.toSan());

        game.applyMove(Square.E2, Square.E4, null);
        game.applyMove(Square.E7, Square.E5, null);

        assertEquals("1. e4 e5", game.toSan());
    }

    /** A position taken over from the board with Black to move: the first move is Black's. */
    @Test
    public void toSan_numbersAGameBlackOpensFromItsFirstMove() {
        ChessGame game = new ChessGame();
        game.loadFen("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1");
        assertEquals("", game.toSan());

        game.applyUciMove("c7c6");
        assertEquals("1... c6", game.toSan());

        game.applyUciMove("g1f3");
        game.applyUciMove("d7d6");
        game.applyUciMove("f1c4");
        game.applyUciMove("d6d5");
        assertEquals("1... c6 2. Nf3 d6 3. Bc4 d5", game.toSan());
        assertEquals("1... c6 2. Nf3 d6 3. Bc4 d5", game.toFullSan());
    }

    @Test
    public void toSan_countsFromTheMoveNumberOfTheStartPosition() {
        ChessGame game = new ChessGame();
        game.loadFen("r1bqkbnr/pppp1ppp/2n5/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R w KQkq - 2 3");
        game.applyUciMove("f1b5");
        game.applyUciMove("a7a6");
        game.applyUciMove("b5a4");

        assertEquals("3. Bb5 a6 4. Ba4", game.toSan());
    }

    @Test
    public void moveNumberToken_matchesBothNumberForms() {
        assertTrue("1.".matches(ChessGame.MOVE_NUMBER_TOKEN));
        assertTrue("12...".matches(ChessGame.MOVE_NUMBER_TOKEN));
        assertFalse("e4".matches(ChessGame.MOVE_NUMBER_TOKEN));
        assertFalse("O-O".matches(ChessGame.MOVE_NUMBER_TOKEN));
    }

    @Test
    public void toUciMoveList_isSpaceSeparatedUciMoves() {
        ChessGame game = new ChessGame();
        game.applyMove(Square.E2, Square.E4, null);
        game.applyMove(Square.E7, Square.E5, null);

        assertEquals("e2e4 e7e5", game.toUciMoveList());
    }

    @Test
    public void reset_restoresStandardStartingPosition() {
        ChessGame game = new ChessGame();
        game.applyMove(Square.E2, Square.E4, null);

        game.reset();

        assertEquals(Side.WHITE, game.sideToMove());
        assertEquals(Piece.WHITE_PAWN, game.pieceAt(Square.E2));
        assertEquals(0, game.moveCount());
        assertEquals("", game.toSan());
    }

    /**
     * PegasusGameBridge.syncBoardToPosition() feeds this FEN straight into pegasus-core's own
     * ChessPosition.fromFen() to resynchronize a physical board after a reconnect; this only works
     * if the two libraries agree on FEN format.
     */
    @Test
    public void toFen_isParsableByPegasusCoreChessPosition() {
        ChessGame game = new ChessGame();
        game.applyMove(Square.E2, Square.E4, null);
        game.applyMove(Square.E7, Square.E5, null);

        String fen = game.toFen();
        de.schliweb.pegasus.core.chess.ChessPosition parsed =
                de.schliweb.pegasus.core.chess.ChessPosition.fromFen(fen);

        assertEquals(de.schliweb.pegasus.core.chess.PieceColor.WHITE, parsed.sideToMove());
        assertEquals(
                de.schliweb.pegasus.core.chess.Piece.WHITE_PAWN,
                parsed.pieceAt(de.schliweb.pegasus.core.protocol.BoardState.squareIndex("e4")));
        assertEquals(
                de.schliweb.pegasus.core.chess.Piece.BLACK_PAWN,
                parsed.pieceAt(de.schliweb.pegasus.core.protocol.BoardState.squareIndex("e5")));
        assertNull(parsed.pieceAt(de.schliweb.pegasus.core.protocol.BoardState.squareIndex("e2")));
    }

    @Test
    public void toPgn_ongoingGame_hasOpenResultAndMovetext() {
        ChessGame game = new ChessGame();
        game.applyMove(Square.E2, Square.E4, null);
        game.applyMove(Square.E7, Square.E5, null);

        String pgn = game.toPgn("Weiß", "Schwarz");

        assertTrue(pgn.contains("[White \"Weiß\"]"));
        assertTrue(pgn.contains("[Black \"Schwarz\"]"));
        assertTrue(pgn.contains("[Result \"*\"]"));
        assertTrue(pgn.contains("1. e4 e5 *"));
    }

    /** Without the start position in the header no reader could replay such a game. */
    @Test
    public void toPgn_fromATakenOverPosition_carriesTheStartFenAndReimports() {
        ChessGame game = new ChessGame();
        String fen = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1";
        game.loadFen(fen);
        game.applyUciMove("c7c6");
        game.applyUciMove("g1f3");

        String pgn = game.toPgn("Weiß", "Schwarz");

        assertTrue(pgn.contains("[SetUp \"1\"]"));
        assertTrue(pgn.contains("[FEN \"" + fen + "\"]"));
        assertTrue(pgn.contains("1... c6 2. Nf3 *"));

        ChessGame imported = new ChessGame();
        assertTrue(imported.loadPgn(pgn));
        assertEquals(fen, imported.startFen());
        assertEquals("c7c6 g1f3", imported.toUciMoveList());
        assertEquals(Side.BLACK, imported.sideToMove());
    }

    @Test
    public void toPgn_fromTheStartingPosition_hasNoFenHeader() {
        ChessGame game = new ChessGame();
        game.applyUciMove("e2e4");

        assertFalse(game.toPgn("Weiß", "Schwarz").contains("[FEN"));
    }

    @Test
    public void toPgn_afterCheckmate_recordsCorrectResult() {
        ChessGame game = new ChessGame();
        game.applyMove(Square.F2, Square.F3, null);
        game.applyMove(Square.E7, Square.E5, null);
        game.applyMove(Square.G2, Square.G4, null);
        game.applyMove(Square.D8, Square.H4, null);

        String pgn = game.toPgn("Weiß", "Schwarz");

        assertTrue(pgn.contains("[Result \"0-1\"]"));
        assertTrue(pgn.endsWith("0-1\n"));
    }

    @Test
    public void toPgn_atStalemate_recordsDrawResult() {
        ChessGame game = new ChessGame();
        game.loadFen("5k2/5P2/5K2/8/8/8/8/8 b - - 0 1");

        String pgn = game.toPgn("Weiß", "Schwarz");

        assertTrue(pgn.contains("[Result \"1/2-1/2\"]"));
    }

    @Test
    public void loadPgn_roundTripsThroughToPgn() {
        ChessGame original = new ChessGame();
        original.applyMove(Square.E2, Square.E4, null);
        original.applyMove(Square.E7, Square.E5, null);
        original.applyMove(Square.G1, Square.F3, null);
        original.applyMove(Square.B8, Square.C6, null);
        original.applyMove(Square.F1, Square.B5, null);

        String pgn = original.toPgn("Weiß", "Schwarz");

        ChessGame loaded = new ChessGame();
        assertTrue(loaded.loadPgn(pgn));
        assertEquals(original.toFen(), loaded.toFen());
        assertEquals(5, loaded.moveCount());
    }

    @Test
    public void loadPgn_withGarbageTextFailsAndLeavesStartPosition() {
        ChessGame game = new ChessGame();
        game.applyMove(Square.E2, Square.E4, null);

        assertFalse(game.loadPgn("this is not a pgn file at all"));

        assertEquals(0, game.moveCount());
        assertEquals(Side.WHITE, game.sideToMove());
        assertEquals(Piece.WHITE_PAWN, game.pieceAt(Square.E2));
    }

    /**
     * Regression test: a stray byte before the very first "[Event" tag (seen in the wild in real
     * PGN database exports) used to make chesslib's PgnIterator throw a PgnException straight out
     * of its constructor, before loadPgn's own try/catch even started - crashing the caller instead
     * of returning false like every other malformed-input case.
     */
    @Test
    public void loadPgn_withStrayCharacterBeforeFirstEventTagFailsGracefully() {
        ChessGame game = new ChessGame();
        game.applyMove(Square.E2, Square.E4, null);

        String corrupted =
                "I[Event \"Test\"]\n[Site \"?\"]\n[Date \"????.??.??\"]\n"
                        + "[Round \"?\"]\n[White \"A\"]\n[Black \"B\"]\n[Result \"*\"]\n\n1.e4 e5 *\n";

        assertFalse(game.loadPgn(corrupted));

        assertEquals(0, game.moveCount());
        assertEquals(Side.WHITE, game.sideToMove());
        assertEquals(Piece.WHITE_PAWN, game.pieceAt(Square.E2));
    }

    private static ChessGame gameAfter(String... uciMoves) {
        ChessGame game = new ChessGame();
        for (String uci : uciMoves) {
            assertTrue(uci, game.applyUciMove(uci));
        }
        return game;
    }

    @Test
    public void undoAgainstAnAutomaticOpponent_takesItsReplyBackToo() {
        ChessGame game = gameAfter("e2e4", "e7e5", "g1f3", "b8c6");

        assertTrue(game.undoLastMove(Side.BLACK));

        // Black's reply and White's own move are both gone: White is to move again.
        assertEquals(2, game.moveCount());
        assertEquals(Side.WHITE, game.sideToMove());
    }

    @Test
    public void undoAgainstAnAutomaticOpponent_stopsAtTheStartWhenItMovedFirst() {
        ChessGame game = gameAfter("e2e4");

        assertTrue(game.undoLastMove(Side.WHITE));

        assertEquals(0, game.moveCount());
        assertFalse(game.undoLastMove(Side.WHITE));
    }

    @Test
    public void undoWithoutAnAutomaticOpponent_takesOneMoveBack() {
        ChessGame game = gameAfter("e2e4", "e7e5");

        assertTrue(game.undoLastMove(null));

        assertEquals(1, game.moveCount());
    }

    @Test
    public void redoAgainstAnAutomaticOpponent_reappliesItsReplyToo() {
        ChessGame game = gameAfter("e2e4", "e7e5", "g1f3", "b8c6");
        game.undoLastMove(Side.BLACK);

        assertTrue(game.redoMove(Side.BLACK));

        assertEquals(4, game.moveCount());
        assertFalse(game.redoMove(Side.BLACK));
    }

    @Test
    public void jumpToPlyAgainstAnAutomaticOpponent_landsOnThePlayersTurn() {
        ChessGame game = gameAfter("e2e4", "e7e5", "g1f3", "b8c6");

        // Ply 1 has Black, the automatic side, to move: go on to its reply.
        assertEquals(2, game.jumpToPly(1, Side.BLACK));
        assertEquals("e7e5", game.lastMoveUci());
        // Same entry again: same position, however it was reached.
        assertEquals(2, game.jumpToPly(2, Side.BLACK));
        assertEquals(3, game.jumpToPly(3, null));
        assertEquals("g1f3", game.lastMoveUci());
    }

    @Test
    public void jumpToPly_reportsATargetOutOfRange() {
        ChessGame game = gameAfter("e2e4", "e7e5");

        assertEquals(-1, game.jumpToPly(5, null));
        assertEquals(0, game.jumpToPly(0, Side.BLACK));
        assertNull(game.lastMoveUci());
    }

    @Test
    public void checkedKingSquare_isTheKingOfTheSideInCheck() {
        assertNull(gameAfter("e2e4").checkedKingSquare());

        ChessGame game = gameAfter("e2e4", "f7f6", "d1h5");

        assertEquals(Square.E8, game.checkedKingSquare());
    }

    @Test
    public void hasSideToMovePieceOn_knowsWhoseTurnItIs() {
        ChessGame game = gameAfter("e2e4");

        assertTrue(game.hasSideToMovePieceOn(Square.E7));
        assertFalse(game.hasSideToMovePieceOn(Square.E4));
        assertFalse(game.hasSideToMovePieceOn(Square.E5));
    }

    @Test
    public void startPly_countsTheHalfMovesBeforeTheFirstMoveOfTheGame() {
        ChessGame game = new ChessGame();
        assertEquals(0, game.startPly());

        // A position taken over from the board with Black to move.
        game.loadFen("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1");
        assertEquals(1, game.startPly());

        game.loadFen("4k3/8/8/8/8/8/4P3/4K3 w - - 3 17");
        assertEquals(32, game.startPly());

        // No move counters: counted from move 1.
        game.loadFen("4k3/8/8/8/8/8/4P3/4K3 b - -");
        assertEquals(1, game.startPly());
    }
}
