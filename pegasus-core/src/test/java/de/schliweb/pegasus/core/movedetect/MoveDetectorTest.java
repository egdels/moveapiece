/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.pegasus.core.movedetect;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import de.schliweb.pegasus.core.chess.ChessPosition;
import de.schliweb.pegasus.core.chess.Move;
import de.schliweb.pegasus.core.chess.OccupancyProjection;
import de.schliweb.pegasus.core.chess.PieceType;
import de.schliweb.pegasus.core.protocol.BoardState;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class MoveDetectorTest {

    /** Records confirmed moves for atomicity/idempotency assertions. */
    private static final class RecordingListener implements MoveDetectionListener {
        final List<Move> confirmed = new ArrayList<>();
        final List<MoveDetectionState> states = new ArrayList<>();
        ChessPosition lastNewPosition;

        @Override
        public void onMoveConfirmed(Move move, ChessPosition newPosition) {
            confirmed.add(move);
            lastNewPosition = newPosition;
        }

        @Override
        public void onStateChanged(MoveDetectionState state) {
            states.add(state);
        }
    }

    private static FakePhysicalBoard synced(MoveDetector detector, ChessPosition position) {
        FakePhysicalBoard board = new FakePhysicalBoard(detector, position);
        assertEquals(MoveDetectionResult.Kind.SYNCHRONIZED, board.sync().kind());
        assertEquals(MoveDetectionState.SYNCHRONIZED, detector.state());
        return board;
    }

    // ------------------------------------------------------ synchronization

    @Test
    public void startPositionSynchronizes() {
        ChessPosition start = ChessPosition.starting();
        MoveDetector detector = new MoveDetector(start, null);
        assertEquals(MoveDetectionState.AWAITING_BOARD, detector.state());
        synced(detector, start);
    }

    @Test
    public void initialMismatchIsReported() {
        ChessPosition start = ChessPosition.starting();
        MoveDetector detector = new MoveDetector(start, null);
        BoardState physical =
                OccupancyProjection.occupancyOf(start).withSquare(BoardState.squareIndex("e2"), 0);
        MoveDetectionResult result = detector.onPhysicalBoard(physical);
        assertEquals(MoveDetectionResult.Kind.BOARD_MISMATCH, result.kind());
        assertEquals(
                Collections.singletonList(BoardState.squareIndex("e2")),
                result.mismatch().missingOccupied());
        assertEquals(MoveDetectionState.BOARD_MISMATCH, detector.state());
    }

    @Test
    public void mismatchRecoversWhenBoardIsRestored() {
        ChessPosition start = ChessPosition.starting();
        MoveDetector detector = new MoveDetector(start, null);
        BoardState physical =
                OccupancyProjection.occupancyOf(start).withSquare(BoardState.squareIndex("e2"), 0);
        detector.onPhysicalBoard(physical);
        MoveDetectionResult result =
                detector.onPhysicalBoard(OccupancyProjection.occupancyOf(start));
        assertEquals(MoveDetectionResult.Kind.POSITION_RESTORED, result.kind());
        assertEquals(MoveDetectionState.SYNCHRONIZED, detector.state());
    }

    @Test
    public void noMoveDetectionWhileInMismatch() {
        // A "completed move" arriving while unsynchronized must not confirm.
        ChessPosition start = ChessPosition.starting();
        MoveDetector detector = new MoveDetector(start, null);
        BoardState wrong =
                OccupancyProjection.occupancyOf(start).withSquare(BoardState.squareIndex("a5"), 1);
        detector.onPhysicalBoard(wrong);
        assertEquals(MoveDetectionState.BOARD_MISMATCH, detector.state());
        BoardState afterMove = OccupancyProjection.occupancyOf(start.apply(Move.fromUci("e2e4")));
        MoveDetectionResult result = detector.onPhysicalBoard(afterMove);
        assertEquals(MoveDetectionResult.Kind.BOARD_MISMATCH, result.kind());
    }

    // ------------------------------------------------------------ normal move

    @Test
    public void normalMoveIsConfirmed() {
        ChessPosition start = ChessPosition.starting();
        RecordingListener listener = new RecordingListener();
        MoveDetector detector = new MoveDetector(start, listener);
        FakePhysicalBoard board = synced(detector, start);

        assertEquals(MoveDetectionResult.Kind.IN_PROGRESS, board.lift("e2").kind());
        MoveDetectionResult result = board.place("e4");
        assertEquals(MoveDetectionResult.Kind.CONFIRMED, result.kind());
        assertEquals("e2e4", result.move().uci());
        assertEquals(1, listener.confirmed.size());
        assertEquals(MoveDetectionState.SYNCHRONIZED, detector.state());
        // Logical position updated atomically and consistent with the board.
        assertEquals(detector.position().toFen(), listener.lastNewPosition.toFen());
        assertEquals(detector.expectedOccupancy(), board.state());
        assertTrue(
                detector.position()
                        .toFen()
                        .startsWith("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3"));
    }

    @Test
    public void secondMoveIsConfirmedToo() {
        ChessPosition start = ChessPosition.starting();
        MoveDetector detector = new MoveDetector(start, null);
        FakePhysicalBoard board = synced(detector, start);
        board.lift("e2");
        board.place("e4");
        board.lift("g8");
        MoveDetectionResult result = board.place("f6");
        assertEquals(MoveDetectionResult.Kind.CONFIRMED, result.kind());
        assertEquals("g8f6", result.move().uci());
    }

    @Test
    public void liftAndReplaceProducesNoMove() {
        ChessPosition start = ChessPosition.starting();
        RecordingListener listener = new RecordingListener();
        MoveDetector detector = new MoveDetector(start, listener);
        FakePhysicalBoard board = synced(detector, start);
        assertEquals(MoveDetectionResult.Kind.IN_PROGRESS, board.lift("e2").kind());
        MoveDetectionResult result = board.place("e2");
        assertEquals(MoveDetectionResult.Kind.POSITION_RESTORED, result.kind());
        assertEquals(MoveDetectionState.SYNCHRONIZED, detector.state());
        assertEquals(0, listener.confirmed.size());
        assertEquals(ChessPosition.STARTING_FEN, detector.position().toFen());
    }

    @Test
    public void undoBeforeConfirmationProducesNoMove() {
        ChessPosition start = ChessPosition.starting();
        RecordingListener listener = new RecordingListener();
        MoveDetector detector = new MoveDetector(start, listener);
        FakePhysicalBoard board = synced(detector, start);
        board.lift("e2");
        // e4 placement confirms e2e4 immediately (unambiguous final state).
        assertEquals(MoveDetectionResult.Kind.CONFIRMED, board.place("e4").kind());
        // Physically moving back after confirmation is NOT a silent undo.
        board.lift("e4");
        MoveDetectionResult result = board.place("e2");
        assertEquals(MoveDetectionResult.Kind.BOARD_MISMATCH, result.kind());
        assertEquals(1, listener.confirmed.size());
    }

    @Test
    public void illegalTargetIsRejected() {
        ChessPosition start = ChessPosition.starting();
        MoveDetector detector = new MoveDetector(start, null);
        FakePhysicalBoard board = synced(detector, start);
        board.lift("e2");
        MoveDetectionResult result = board.place("e5"); // e2e5 is not legal
        assertEquals(MoveDetectionResult.Kind.BOARD_MISMATCH, result.kind());
    }

    @Test
    public void multipleLiftsRecoverIntoValidMove() {
        ChessPosition start = ChessPosition.starting();
        MoveDetector detector = new MoveDetector(start, null);
        FakePhysicalBoard board = synced(detector, start);
        assertEquals(MoveDetectionResult.Kind.IN_PROGRESS, board.lift("e2").kind());
        assertEquals(MoveDetectionResult.Kind.IN_PROGRESS, board.lift("g1").kind());
        assertEquals(MoveDetectionResult.Kind.IN_PROGRESS, board.place("g1").kind());
        MoveDetectionResult result = board.place("e4");
        assertEquals(MoveDetectionResult.Kind.CONFIRMED, result.kind());
        assertEquals("e2e4", result.move().uci());
    }

    @Test
    public void restoreAfterSeveralWrongChanges() {
        ChessPosition start = ChessPosition.starting();
        MoveDetector detector = new MoveDetector(start, null);
        FakePhysicalBoard board = synced(detector, start);
        board.lift("e2");
        board.place("a5"); // nonsense
        assertEquals(MoveDetectionState.BOARD_MISMATCH, detector.state());
        board.lift("a5");
        MoveDetectionResult result = board.place("e2");
        assertEquals(MoveDetectionResult.Kind.POSITION_RESTORED, result.kind());
        assertEquals(MoveDetectionState.SYNCHRONIZED, detector.state());
    }

    // -------------------------------------------------------------- captures

    private static final String CAPTURE_FEN =
            "rnbqkbnr/ppp1pppp/8/3p4/4P3/8/PPPP1PPP/RNBQKBNR w KQkq d6 0 2";

    @Test
    public void captureOrderAIsDetected() {
        ChessPosition position = ChessPosition.fromFen(CAPTURE_FEN);
        MoveDetector detector = new MoveDetector(position, null);
        FakePhysicalBoard board = synced(detector, position);
        board.lift("e4");
        board.lift("d5"); // captured piece removed second
        // Occupancy-wise the final capture state equals "e4 still lifted";
        // confirmation happens after the host's stabilization window.
        assertEquals(MoveDetectionResult.Kind.IN_PROGRESS, board.place("d5").kind());
        MoveDetectionResult result = detector.commitPending();
        assertEquals(MoveDetectionResult.Kind.CONFIRMED, result.kind());
        assertEquals("e4d5", result.move().uci());
    }

    @Test
    public void captureOrderBIsDetected() {
        ChessPosition position = ChessPosition.fromFen(CAPTURE_FEN);
        MoveDetector detector = new MoveDetector(position, null);
        FakePhysicalBoard board = synced(detector, position);
        board.lift("d5"); // captured piece removed first
        board.lift("e4");
        assertEquals(MoveDetectionResult.Kind.IN_PROGRESS, board.place("d5").kind());
        MoveDetectionResult result = detector.commitPending();
        assertEquals(MoveDetectionResult.Kind.CONFIRMED, result.kind());
        assertEquals("e4d5", result.move().uci());
    }

    @Test
    public void followUpMoveRecoversAnUnconfirmedCapture() {
        // White plays e4xd5 but the destination is never independently
        // observed empty (see captureOrderAIsDetected/B), so it stays an
        // unconfirmed pending candidate - normally requiring an explicit
        // commitPending()/tap. If the opponent's reply arrives instead,
        // without any confirmation in between, that move is itself proof
        // the capture was completed: wouldResolveIfCommitted +
        // commitRecoveredCapture let a host recover it without a tap.
        ChessPosition position = ChessPosition.fromFen(CAPTURE_FEN);
        MoveDetector detector = new MoveDetector(position, null);
        FakePhysicalBoard board = synced(detector, position);
        board.lift("e4");
        board.lift("d5");
        assertEquals(MoveDetectionResult.Kind.IN_PROGRESS, board.place("d5").kind());
        List<Move> pending = detector.pendingCandidates();
        assertEquals(1, pending.size());
        assertEquals("e4d5", pending.get(0).uci());

        // Black replies Ng8-f6 directly - no confirmation of White's
        // capture happened in between.
        BoardState afterBlackReply =
                board.state()
                        .withSquare(BoardState.squareIndex("g8"), 0)
                        .withSquare(BoardState.squareIndex("f6"), 1);
        assertEquals(
                MoveDetectionResult.Kind.BOARD_MISMATCH,
                detector.onPhysicalBoard(afterBlackReply).kind());

        assertTrue(detector.wouldResolveIfCommitted(pending.get(0), afterBlackReply));
        MoveDetectionResult confirmedCapture = detector.commitRecoveredCapture(pending.get(0));
        assertEquals(MoveDetectionResult.Kind.CONFIRMED, confirmedCapture.kind());
        assertEquals("e4d5", confirmedCapture.move().uci());

        MoveDetectionResult confirmedReply = detector.onPhysicalBoard(afterBlackReply);
        assertEquals(MoveDetectionResult.Kind.CONFIRMED, confirmedReply.kind());
        assertEquals("g8f6", confirmedReply.move().uci());
    }

    @Test
    public void wouldResolveIfCommittedIsFalseWhenNothingExplainsIt() {
        // A physical state that fits neither the current position nor the
        // hypothetical post-capture one - a genuine mismatch, not a
        // recoverable follow-up.
        ChessPosition position = ChessPosition.fromFen(CAPTURE_FEN);
        MoveDetector detector = new MoveDetector(position, null);
        FakePhysicalBoard board = synced(detector, position);
        board.lift("e4");
        board.lift("d5");
        assertEquals(MoveDetectionResult.Kind.IN_PROGRESS, board.place("d5").kind());
        Move pending = detector.pendingCandidates().get(0);

        BoardState nonsense =
                board.state()
                        .withSquare(BoardState.squareIndex("a7"), 0)
                        .withSquare(BoardState.squareIndex("a3"), 1)
                        .withSquare(BoardState.squareIndex("h7"), 0)
                        .withSquare(BoardState.squareIndex("h3"), 1);
        assertEquals(
                MoveDetectionResult.Kind.BOARD_MISMATCH, detector.onPhysicalBoard(nonsense).kind());
        assertFalse(detector.wouldResolveIfCommitted(pending, nonsense));
    }

    @Test
    public void plainRookMoveConfirmsImmediately() {
        // h1f1 also happens to be castling's rook-first intermediate step,
        // but it is itself a complete, unambiguous legal move (something
        // was actually placed on f1, unlike a capture where the
        // destination's occupancy never observably changes) - castling
        // requires touching the king first, so this confirms right away
        // rather than waiting for a possible king move that may never come.
        ChessPosition position = ChessPosition.fromFen(CASTLING_FEN);
        MoveDetector detector = new MoveDetector(position, null);
        FakePhysicalBoard board = synced(detector, position);
        board.lift("h1");
        MoveDetectionResult result = board.place("f1");
        assertEquals(MoveDetectionResult.Kind.CONFIRMED, result.kind());
        assertEquals("h1f1", result.move().uci());
    }

    // -------------------------------------------------------------- castling

    private static final String CASTLING_FEN = "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1";

    @Test
    public void whiteKingsideCastlingKingFirst() {
        assertCastling(CASTLING_FEN, "e1g1", new String[] {"-e1", "+g1", "-h1", "+f1"});
    }

    @Test
    public void rookMovedAloneForfeitsCastlingRatherThanWaitingForKing() {
        // Castling requires touching the king first (see
        // plainRookMoveConfirmsImmediately). Moving only the rook confirms
        // it as a final, standalone move - which also spends that side's
        // castling rights, same as any other rook move - so a king move
        // that follows is evaluated against the resulting position (rook
        // already moved, turn already passed) rather than retroactively
        // reinterpreted as completing a castling.
        ChessPosition position = ChessPosition.fromFen(CASTLING_FEN);
        MoveDetector detector = new MoveDetector(position, null);
        FakePhysicalBoard board = synced(detector, position);
        board.lift("h1");
        MoveDetectionResult rookResult = board.place("f1");
        assertEquals(MoveDetectionResult.Kind.CONFIRMED, rookResult.kind());
        assertEquals("h1f1", rookResult.move().uci());

        board.lift("e1");
        MoveDetectionResult kingResult = board.place("g1");
        assertEquals(MoveDetectionResult.Kind.BOARD_MISMATCH, kingResult.kind());
    }

    @Test
    public void whiteQueensideCastling() {
        assertCastling(CASTLING_FEN, "e1c1", new String[] {"-e1", "-a1", "+c1", "+d1"});
    }

    @Test
    public void blackKingsideCastling() {
        String fen = "r3k2r/8/8/8/8/8/8/R3K2R b KQkq - 0 1";
        assertCastling(fen, "e8g8", new String[] {"-e8", "-h8", "+g8", "+f8"});
    }

    @Test
    public void blackQueensideCastling() {
        // King-first, like the other three castling tests: touching the
        // rook alone first (see rookMovedAloneForfeitsCastlingRatherThan-
        // WaitingForKing) confirms it as a standalone move instead.
        String fen = "r3k2r/8/8/8/8/8/8/R3K2R b KQkq - 0 1";
        assertCastling(fen, "e8c8", new String[] {"-e8", "-a8", "+c8", "+d8"});
    }

    private static void assertCastling(String fen, String expectedUci, String[] steps) {
        ChessPosition position = ChessPosition.fromFen(fen);
        MoveDetector detector = new MoveDetector(position, null);
        FakePhysicalBoard board = synced(detector, position);
        MoveDetectionResult result = null;
        for (String step : steps) {
            String square = step.substring(1);
            result = step.charAt(0) == '-' ? board.lift(square) : board.place(square);
        }
        assertNotNull(result);
        assertEquals(MoveDetectionResult.Kind.CONFIRMED, result.kind());
        assertEquals(expectedUci, result.move().uci());
    }

    // ------------------------------------------------------------ en passant

    @Test
    public void whiteEnPassantIsDetected() {
        // White pawn e5, black just played d7d5.
        ChessPosition position =
                ChessPosition.fromFen(
                        "rnbqkbnr/ppp1pppp/8/3pP3/8/8/PPPP1PPP/RNBQKBNR w KQkq d6 0 3");
        MoveDetector detector = new MoveDetector(position, null);
        FakePhysicalBoard board = synced(detector, position);
        board.lift("e5");
        board.lift("d5"); // captured pawn removed
        MoveDetectionResult result = board.place("d6");
        assertEquals(MoveDetectionResult.Kind.CONFIRMED, result.kind());
        assertEquals("e5d6", result.move().uci());
    }

    @Test
    public void blackEnPassantIsDetected() {
        // Black pawn e4, white just played d2d4.
        ChessPosition position =
                ChessPosition.fromFen(
                        "rnbqkbnr/pppp1ppp/8/8/3Pp3/8/PPP1PPPP/RNBQKBNR b KQkq d3 0 3");
        MoveDetector detector = new MoveDetector(position, null);
        FakePhysicalBoard board = synced(detector, position);
        board.lift("d4"); // captured pawn removed first this time
        board.lift("e4");
        MoveDetectionResult result = board.place("d3");
        assertEquals(MoveDetectionResult.Kind.CONFIRMED, result.kind());
        assertEquals("e4d3", result.move().uci());
    }

    // ------------------------------------------------------------- promotion

    @Test
    public void promotionRequiresUiSelection() {
        ChessPosition position = ChessPosition.fromFen("8/4P3/8/8/8/8/8/K1k5 w - - 0 1");
        MoveDetector detector = new MoveDetector(position, null);
        FakePhysicalBoard board = synced(detector, position);
        board.lift("e7");
        MoveDetectionResult result = board.place("e8");
        assertEquals(MoveDetectionResult.Kind.PROMOTION_REQUIRED, result.kind());
        assertEquals(MoveDetectionState.PROMOTION_PENDING, detector.state());
        List<String> ucis = new ArrayList<>();
        for (Move candidate : result.candidates()) {
            ucis.add(candidate.uci());
        }
        Collections.sort(ucis);
        assertEquals(Arrays.asList("e7e8b", "e7e8n", "e7e8q", "e7e8r"), ucis);

        MoveDetectionResult confirmed = detector.selectPromotion(PieceType.KNIGHT);
        assertEquals(MoveDetectionResult.Kind.CONFIRMED, confirmed.kind());
        assertEquals("e7e8n", confirmed.move().uci());
        assertEquals(MoveDetectionState.SYNCHRONIZED, detector.state());
        assertTrue(detector.position().toFen().startsWith("4N3/8/8/8/8/8/8/K1k5 b"));
    }

    @Test
    public void capturePromotionIsAmbiguousInPromotionPieceOnly() {
        // e7 pawn captures f8 rook and promotes.
        ChessPosition position = ChessPosition.fromFen("5r2/4P3/8/8/8/8/8/K1k5 w - - 0 1");
        MoveDetector detector = new MoveDetector(position, null);
        FakePhysicalBoard board = synced(detector, position);
        board.lift("e7");
        board.lift("f8");
        // Final state equals "e7 still lifted" occupancy-wise: pending until
        // the stabilization window commits it.
        assertEquals(MoveDetectionResult.Kind.IN_PROGRESS, board.place("f8").kind());
        MoveDetectionResult result = detector.commitPending();
        assertEquals(MoveDetectionResult.Kind.PROMOTION_REQUIRED, result.kind());
        assertEquals(4, result.candidates().size());
        MoveDetectionResult confirmed = detector.selectPromotion(PieceType.QUEEN);
        assertEquals("e7f8q", confirmed.move().uci());
    }

    @Test(expected = IllegalStateException.class)
    public void selectPromotionRequiresPendingState() {
        MoveDetector detector = new MoveDetector(ChessPosition.starting(), null);
        detector.selectPromotion(PieceType.QUEEN);
    }

    // ------------------------------------------------- idempotency/duplicates

    @Test
    public void identicalStatesAreIdempotent() {
        ChessPosition start = ChessPosition.starting();
        RecordingListener listener = new RecordingListener();
        MoveDetector detector = new MoveDetector(start, listener);
        FakePhysicalBoard board = synced(detector, start);
        board.lift("e2");
        board.place("e4");
        assertEquals(1, listener.confirmed.size());
        // Same physical state delivered again (e.g. duplicated events).
        assertEquals(MoveDetectionResult.Kind.NO_CHANGE, board.sync().kind());
        assertEquals(MoveDetectionResult.Kind.NO_CHANGE, board.sync().kind());
        assertEquals(1, listener.confirmed.size());
    }

    @Test
    public void duplicateFieldEventsKeepStateDeterministic() {
        ChessPosition start = ChessPosition.starting();
        MoveDetector detector = new MoveDetector(start, null);
        FakePhysicalBoard board = synced(detector, start);
        assertEquals(MoveDetectionResult.Kind.IN_PROGRESS, board.lift("e2").kind());
        // Duplicate "e2 -> empty" leaves the state unchanged.
        assertEquals(MoveDetectionResult.Kind.NO_CHANGE, board.lift("e2").kind());
        assertEquals(MoveDetectionResult.Kind.CONFIRMED, board.place("e4").kind());
    }

    // ------------------------------------------------------------------ reset

    @Test
    public void resetLoadsNewPositionAndResynchronizes() {
        MoveDetector detector = new MoveDetector(ChessPosition.starting(), null);
        ChessPosition custom = ChessPosition.fromFen("8/4P3/8/8/8/8/8/K1k5 w - - 0 1");
        MoveDetectionResult result =
                detector.reset(custom, OccupancyProjection.occupancyOf(custom));
        assertEquals(MoveDetectionResult.Kind.SYNCHRONIZED, result.kind());
        assertEquals(custom.toFen(), detector.position().toFen());
    }

    @Test
    public void resetDropsTransientMoveState() {
        ChessPosition start = ChessPosition.starting();
        MoveDetector detector = new MoveDetector(start, null);
        FakePhysicalBoard board = synced(detector, start);
        board.lift("e2"); // move in progress, then "reconnect"
        MoveDetectionResult result = detector.reset(start, OccupancyProjection.occupancyOf(start));
        assertEquals(MoveDetectionResult.Kind.SYNCHRONIZED, result.kind());
        assertEquals(MoveDetectionState.SYNCHRONIZED, detector.state());
    }

    @Test
    public void resetWithMismatchedBoardReportsMismatch() {
        MoveDetector detector = new MoveDetector(ChessPosition.starting(), null);
        ChessPosition custom = ChessPosition.fromFen("8/4P3/8/8/8/8/8/K1k5 w - - 0 1");
        MoveDetectionResult result =
                detector.reset(custom, OccupancyProjection.occupancyOf(ChessPosition.starting()));
        assertEquals(MoveDetectionResult.Kind.BOARD_MISMATCH, result.kind());
    }

    // -------------------------------------- generic legal-move occupancy test

    @Test
    public void everyLegalMoveIsIdentifiedFromItsFinalOccupancy() {
        String[] fens = {
            ChessPosition.STARTING_FEN,
            "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1",
            "rnbqkbnr/ppp1pppp/8/3pP3/8/8/PPPP1PPP/RNBQKBNR w KQkq d6 0 3",
            "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1",
            "r3k2r/8/8/8/8/8/8/R3K2R b KQkq - 0 1",
        };
        for (String fen : fens) {
            ChessPosition position = ChessPosition.fromFen(fen);
            for (Move move : position.legalMoves()) {
                MoveDetector detector = new MoveDetector(position, null);
                detector.reset(position, OccupancyProjection.occupancyOf(position));
                BoardState after = OccupancyProjection.occupancyOf(position.apply(move));
                MoveDetectionResult result = detector.onPhysicalBoard(after);
                if (result.kind() == MoveDetectionResult.Kind.IN_PROGRESS) {
                    // Also a plausible intermediate of another move: commit
                    // after the (simulated) stabilization window.
                    result = detector.commitPending();
                }
                if (result.kind() == MoveDetectionResult.Kind.CONFIRMED) {
                    assertEquals(fen + " -> " + move.uci(), move, result.move());
                } else {
                    // Only allowed ambiguity: identical occupancy candidates
                    // (e.g. promotion piece choice) that include the move.
                    assertTrue(
                            fen + " -> " + move.uci() + " got " + result,
                            result.kind() == MoveDetectionResult.Kind.PROMOTION_REQUIRED
                                    || result.kind() == MoveDetectionResult.Kind.AMBIGUOUS);
                    assertTrue(result.candidates().contains(move));
                }
            }
        }
    }

    // ------------------------------------------------------------ performance

    @Test
    public void candidateMatchingIsInteractive() {
        ChessPosition position =
                ChessPosition.fromFen(
                        "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1");
        BoardState startOccupancy = OccupancyProjection.occupancyOf(position);
        BoardState after =
                OccupancyProjection.occupancyOf(position.apply(position.legalMoves().get(0)));
        long[] samples = new long[100];
        for (int i = 0; i < samples.length; i++) {
            long t0 = System.nanoTime();
            MoveDetector detector = new MoveDetector(position, null);
            detector.reset(position, startOccupancy);
            detector.onPhysicalBoard(after);
            samples[i] = System.nanoTime() - t0;
        }
        Arrays.sort(samples);
        long p50 = samples[50] / 1000;
        long p95 = samples[95] / 1000;
        long max = samples[samples.length - 1] / 1000;
        System.out.println(
                "[DEBUG_LOG] candidate matching (incl. position setup):"
                        + " p50="
                        + p50
                        + "us p95="
                        + p95
                        + "us max="
                        + max
                        + "us");
        // Generous bound: must stay far below anything user-noticeable.
        assertTrue("p95 too slow: " + p95 + "us", p95 < 100_000);
    }
}
