/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.chessnut.core.game;

import static de.schliweb.chessnut.core.game.Boards.lift;
import static de.schliweb.chessnut.core.game.Boards.move;
import static de.schliweb.chessnut.core.game.Boards.put;
import static de.schliweb.chessnut.core.game.Boards.sq;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import de.schliweb.chessnut.core.game.IdentityDetectionResult.Kind;
import de.schliweb.pegasus.core.chess.ChessPosition;
import de.schliweb.pegasus.core.movedetect.MoveDetectionState;
import de.schliweb.pegasus.core.protocol.BoardState;
import de.schliweb.pegasus.core.protocol.PieceCodes;
import java.util.Arrays;
import org.junit.Test;

public class IdentityMoveDetectorTest {

    private final IdentityMoveDetector detector =
            new IdentityMoveDetector(ChessPosition.starting());

    private IdentityMoveDetector synced() {
        assertEquals(Kind.SYNCHRONIZED, detector.onPhysicalBoard(Boards.start()).kind());
        return detector;
    }

    private static IdentityMoveDetector syncedAt(String fen) {
        IdentityMoveDetector d = new IdentityMoveDetector(ChessPosition.fromFen(fen));
        assertEquals(Kind.SYNCHRONIZED, d.onPhysicalBoard(Boards.of(fen)).kind());
        return d;
    }

    @Test
    public void firstMatchingBoardSynchronises() {
        assertEquals(MoveDetectionState.AWAITING_BOARD, detector.state());
        assertNull(detector.lastPhysical());

        synced();

        assertEquals(MoveDetectionState.SYNCHRONIZED, detector.state());
        assertEquals(Kind.NO_CHANGE, detector.onPhysicalBoard(Boards.start()).kind());
    }

    @Test
    public void firstBoardOffIsMismatchUntilRestoredAndNoDetectionMeanwhile() {
        BoardState off = move(Boards.start(), "e2", "e4");

        IdentityDetectionResult first = detector.onPhysicalBoard(off);
        assertEquals(Kind.BOARD_MISMATCH, first.kind());
        assertEquals(Arrays.asList(sq("e2"), sq("e4")), first.diff().squares());

        // A board equal to a legal move's result must not be detected while mismatched.
        assertEquals(
                Kind.BOARD_MISMATCH,
                detector.onPhysicalBoard(move(Boards.start(), "d2", "d4")).kind());
        assertEquals(Kind.POSITION_RESTORED, detector.onPhysicalBoard(Boards.start()).kind());
        assertEquals(
                Kind.CONFIRMED, detector.onPhysicalBoard(move(Boards.start(), "d2", "d4")).kind());
    }

    @Test
    public void liftThenPlaceConfirmsTheMove() {
        synced();
        BoardState lifted = lift(Boards.start(), "e2");

        IdentityDetectionResult inProgress = detector.onPhysicalBoard(lifted);
        assertEquals(Kind.IN_PROGRESS, inProgress.kind());
        assertEquals(Arrays.asList(sq("e2")), inProgress.diff().missing());

        IdentityDetectionResult confirmed =
                detector.onPhysicalBoard(put(lifted, "e4", PieceCodes.WPAWN));
        assertEquals(Kind.CONFIRMED, confirmed.kind());
        assertEquals("e2e4", confirmed.move().uci());
        assertEquals(confirmed.newPosition().toFen(), detector.position().toFen());
        assertEquals(MoveDetectionState.SYNCHRONIZED, detector.state());
    }

    @Test
    public void captureIsProvenByTheAttackerOnTheDestination() {
        String fen = "rnbqkbnr/ppp1pppp/8/3p4/4P3/8/PPPP1PPP/RNBQKBNR w KQkq d6 0 2";
        IdentityMoveDetector d = syncedAt(fen);
        BoardState board = Boards.of(fen);

        // Lifting the attacker alone is only in progress (the Pegasus could not tell).
        assertEquals(Kind.IN_PROGRESS, d.onPhysicalBoard(lift(board, "e4")).kind());
        // Removing the captured pawn as well: still in progress.
        assertEquals(Kind.IN_PROGRESS, d.onPhysicalBoard(lift(lift(board, "e4"), "d5")).kind());
        IdentityDetectionResult result = d.onPhysicalBoard(move(board, "e4", "d5"));
        assertEquals(Kind.CONFIRMED, result.kind());
        assertEquals("e4d5", result.move().uci());
    }

    @Test
    public void captureVictimLiftedFirstAlsoConfirms() {
        String fen = "rnbqkbnr/ppp1pppp/8/3p4/4P3/8/PPPP1PPP/RNBQKBNR w KQkq d6 0 2";
        IdentityMoveDetector d = syncedAt(fen);
        BoardState board = Boards.of(fen);

        assertEquals(Kind.IN_PROGRESS, d.onPhysicalBoard(lift(board, "d5")).kind());
        assertEquals("e4d5", d.onPhysicalBoard(move(board, "e4", "d5")).move().uci());
    }

    @Test
    public void castlingKingFirstIsInProgressThenConfirmed() {
        String fen = "r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R3K2R w KQkq - 0 1";
        IdentityMoveDetector d = syncedAt(fen);
        BoardState board = Boards.of(fen);

        BoardState kingMoved = move(board, "e1", "g1");
        IdentityDetectionResult step = d.onPhysicalBoard(kingMoved);
        assertEquals(Kind.IN_PROGRESS, step.kind());
        assertEquals(Arrays.asList(sq("g1")), step.diff().placed());

        IdentityDetectionResult done = d.onPhysicalBoard(move(kingMoved, "h1", "f1"));
        assertEquals(Kind.CONFIRMED, done.kind());
        assertEquals("e1g1", done.move().uci());
    }

    @Test
    public void rookFirstIsAPlainRookMove() {
        // Touch-move: a rook standing on f1 with the king unmoved is the legal move Rf1.
        String fen = "r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R3K2R w KQkq - 0 1";
        IdentityMoveDetector d = syncedAt(fen);

        IdentityDetectionResult result = d.onPhysicalBoard(move(Boards.of(fen), "h1", "f1"));
        assertEquals(Kind.CONFIRMED, result.kind());
        assertEquals("h1f1", result.move().uci());
    }

    @Test
    public void enPassantPawnMovedBeforeCapturedPawnRemoved() {
        String fen = "rnbqkbnr/ppp1p1pp/8/3pPp2/8/8/PPPP1PPP/RNBQKBNR w KQkq f6 0 3";
        IdentityMoveDetector d = syncedAt(fen);
        BoardState board = Boards.of(fen);

        BoardState pawnMoved = move(board, "e5", "f6");
        assertEquals(Kind.IN_PROGRESS, d.onPhysicalBoard(pawnMoved).kind());
        IdentityDetectionResult done = d.onPhysicalBoard(lift(pawnMoved, "f5"));
        assertEquals(Kind.CONFIRMED, done.kind());
        assertEquals("e5f6", done.move().uci());
    }

    @Test
    public void promotionPieceIsReadOffTheBoard() {
        String fen = "4k3/P7/8/8/8/8/8/4K3 w - - 0 1";
        BoardState board = Boards.of(fen);

        IdentityMoveDetector knight = syncedAt(fen);
        IdentityDetectionResult n =
                knight.onPhysicalBoard(put(lift(board, "a7"), "a8", PieceCodes.WKNIGHT));
        assertEquals(Kind.CONFIRMED, n.kind());
        assertEquals("a7a8n", n.move().uci());

        IdentityMoveDetector queen = syncedAt(fen);
        IdentityDetectionResult q =
                queen.onPhysicalBoard(put(lift(board, "a7"), "a8", PieceCodes.WQUEEN));
        assertEquals("a7a8q", q.move().uci());
    }

    @Test
    public void wrongSquareOrWrongPieceIsAMismatch() {
        synced();

        IdentityDetectionResult wrongSquare =
                detector.onPhysicalBoard(move(Boards.start(), "e2", "e5"));
        assertEquals(Kind.BOARD_MISMATCH, wrongSquare.kind());
        assertEquals(Arrays.asList(sq("e2"), sq("e5")), wrongSquare.diff().squares());

        assertEquals(Kind.POSITION_RESTORED, detector.onPhysicalBoard(Boards.start()).kind());

        IdentityDetectionResult wrongPiece =
                detector.onPhysicalBoard(put(lift(Boards.start(), "e2"), "e4", PieceCodes.BPAWN));
        assertEquals(Kind.BOARD_MISMATCH, wrongPiece.kind());
        assertEquals(Arrays.asList(sq("e4")), wrongPiece.diff().placed());
    }

    @Test
    public void upToThreeLiftsAreInProgressFourAreNot() {
        synced();
        BoardState three = lift(lift(lift(Boards.start(), "e2"), "d2"), "g1");
        assertEquals(Kind.IN_PROGRESS, detector.onPhysicalBoard(three).kind());
        assertEquals(Kind.BOARD_MISMATCH, detector.onPhysicalBoard(lift(three, "b1")).kind());
    }

    @Test
    public void resetEvaluatesTheGivenBoardOrWaits() {
        assertEquals(Kind.NO_CHANGE, detector.reset(ChessPosition.starting(), null).kind());
        assertEquals(MoveDetectionState.AWAITING_BOARD, detector.state());

        ChessPosition afterE4 =
                ChessPosition.starting().apply(de.schliweb.pegasus.core.chess.Move.fromUci("e2e4"));
        assertEquals(
                Kind.SYNCHRONIZED,
                detector.reset(afterE4, move(Boards.start(), "e2", "e4")).kind());
        assertEquals(
                Kind.BOARD_MISMATCH,
                detector.reset(ChessPosition.starting(), move(Boards.start(), "e2", "e4")).kind());
    }
}
