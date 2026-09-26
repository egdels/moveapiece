/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.chessnut.core.game;

import de.schliweb.chessnut.core.protocol.ChessnutLedController;
import de.schliweb.pegasus.core.chess.ChessPosition;
import de.schliweb.pegasus.core.chess.Move;
import de.schliweb.pegasus.core.chess.Piece;
import de.schliweb.pegasus.core.chess.PieceColor;
import de.schliweb.pegasus.core.movedetect.MoveDetectionState;
import de.schliweb.pegasus.core.protocol.BoardState;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Platform-independent game logic between a Chessnut Air's board reports and a host's own game
 * state: physical move detection, LED feedback (mismatch squares, check indicator), guiding the
 * player through a move the host decided (engine reply, book move) and resynchronisation. The
 * Android and desktop bridges only add threading, transport and battery handling on top.
 *
 * <p>Keeps its own {@link ChessPosition}, kept in step with the host's game purely by the moves
 * that pass through here ({@link #guideEngineMove}, confirmed physical moves) and by {@link
 * #syncBoardToPosition} for everything else (on-screen moves, undo, PGN load).
 *
 * <p>Not thread-safe; drive from one thread.
 */
public final class ChessnutGameFlow {

    /** Physical-board events relevant to a host's game flow. */
    public interface Listener {
        void onPhysicalMoveConfirmed(String uci);

        /** True while the physical board disagrees with the logical position. */
        void onBoardMismatch(boolean mismatched);

        /** The move handed to {@link #guideEngineMove} has been executed on the board. */
        void onEngineMoveGuidanceComplete();

        /**
         * While a guide is in progress, pieces stand or are missing on squares other than the
         * guided move's own. Fired on every evaluation while deviating and once when it clears.
         */
        default void onGuideDeviation(boolean deviating) {}
    }

    private final IdentityMoveDetector detector =
            new IdentityMoveDetector(ChessPosition.starting());
    private final ChessnutLedController leds;
    private final Listener listener;

    private BoardState physicalBoard;

    private Move guideMove;
    private ChessPosition guideTarget;
    private BoardState guideTargetBoard;
    private Set<Integer> guideFootprint = Collections.emptySet();
    private boolean guideShowLed = true;
    private boolean guideDeviating;

    public ChessnutGameFlow(ChessnutLedController leds, Listener listener) {
        if (leds == null || listener == null) {
            throw new IllegalArgumentException("leds and listener must not be null");
        }
        this.leds = leds;
        this.listener = listener;
    }

    // ------------------------------------------------------------ connection

    /**
     * Reconnect-safe reset: drops the physical state, any guide and LED tracking, but keeps the
     * logical position (an ongoing game must survive a reconnect). The next board report
     * re-evaluates against it.
     */
    public void onConnected() {
        physicalBoard = null;
        abortGuide();
        leds.resetTracking();
        detector.reset(detector.position(), null);
    }

    // ---------------------------------------------------------------- input

    /** A new physical board (piece identities included), typically from the device layer. */
    public void onPhysicalBoard(BoardState board) {
        if (board == null) {
            return;
        }
        physicalBoard = board;
        if (isGuideActive()) {
            evaluateGuide();
            return;
        }
        dispatch(detector.onPhysicalBoard(board));
    }

    private void dispatch(IdentityDetectionResult result) {
        switch (result.kind()) {
            case CONFIRMED:
                updateCheckIndicator();
                listener.onPhysicalMoveConfirmed(result.move().uci());
                listener.onBoardMismatch(false);
                return;
            case BOARD_MISMATCH:
                leds.showSquares(result.diff().squares());
                listener.onBoardMismatch(true);
                return;
            case SYNCHRONIZED:
            case POSITION_RESTORED:
                updateCheckIndicator();
                listener.onBoardMismatch(false);
                return;
            default:
                return;
        }
    }

    /** Lights the side-to-move's king while in check, otherwise switches the LEDs off. */
    private void updateCheckIndicator() {
        ChessPosition position = detector.position();
        if (!position.inCheck()) {
            leds.off();
            return;
        }
        Piece king =
                position.sideToMove() == PieceColor.WHITE ? Piece.WHITE_KING : Piece.BLACK_KING;
        for (int square = 0; square < BoardState.SQUARE_COUNT; square++) {
            if (position.pieceAt(square) == king) {
                leds.showSquares(square);
                return;
            }
        }
    }

    // ---------------------------------------------------------------- guide

    /** Equivalent to {@link #guideEngineMove(String, boolean)} with LEDs enabled. */
    public void guideEngineMove(String uciMove) {
        guideEngineMove(uciMove, true);
    }

    /**
     * Waits for the player to execute {@code uciMove} on the board, lighting its origin and
     * destination unless {@code showLed} is {@code false} (opening-trainer quiz mode: validates
     * silently, only deviations beyond the move light up). Completes with {@link
     * Listener#onEngineMoveGuidanceComplete()} once the board shows the resulting position.
     * Silently ignored if the move is illegal in the tracked position, no board was received yet,
     * or pieces stand on squares they should not (left to the mismatch LEDs, the host retries once
     * the board is restored). Pieces merely lifted are fine, the player may already hold the piece.
     */
    public void guideEngineMove(String uciMove, boolean showLed) {
        Move move;
        try {
            move = Move.fromUci(uciMove == null ? null : uciMove.trim());
        } catch (IllegalArgumentException e) {
            return;
        }
        ChessPosition current = detector.position();
        if (!current.legalMoves().contains(move) || physicalBoard == null) {
            return;
        }
        IdentityDiff diff = IdentityDiff.between(detector.expected(), physicalBoard);
        if (!diff.placed().isEmpty()) {
            return;
        }
        abortGuide();
        guideMove = move;
        guideTarget = current.apply(move);
        guideTargetBoard = IdentityProjection.of(guideTarget);
        guideShowLed = showLed;
        Set<Integer> footprint = new HashSet<>();
        footprint.add(move.from());
        footprint.add(move.to());
        footprint.addAll(IdentityDiff.between(detector.expected(), guideTargetBoard).squares());
        guideFootprint = footprint;
        evaluateGuide();
    }

    private void evaluateGuide() {
        if (physicalBoard.equals(guideTargetBoard)) {
            ChessPosition reached = guideTarget;
            BoardState board = physicalBoard;
            clearGuide();
            detector.reset(reached, board);
            updateCheckIndicator();
            listener.onEngineMoveGuidanceComplete();
            return;
        }
        List<Integer> deviation = guideDeviationSquares();
        List<Integer> lit = new ArrayList<>();
        if (guideShowLed) {
            lit.add(guideMove.from());
            lit.add(guideMove.to());
        }
        lit.addAll(deviation);
        leds.showSquares(lit);
        boolean deviating = !deviation.isEmpty();
        boolean changed = deviating != guideDeviating;
        guideDeviating = deviating;
        if (deviating || changed) {
            listener.onGuideDeviation(deviating);
        }
    }

    /** Squares differing from the guide's target outside the guided move's own footprint. */
    private List<Integer> guideDeviationSquares() {
        if (!isGuideActive() || physicalBoard == null) {
            return Collections.emptyList();
        }
        List<Integer> squares = new ArrayList<>();
        for (int square : IdentityDiff.between(guideTargetBoard, physicalBoard).squares()) {
            if (!guideFootprint.contains(square)) {
                squares.add(square);
            }
        }
        return squares;
    }

    private void clearGuide() {
        guideMove = null;
        guideTarget = null;
        guideTargetBoard = null;
        guideFootprint = Collections.emptySet();
        guideShowLed = true;
        if (guideDeviating) {
            guideDeviating = false;
            listener.onGuideDeviation(false);
        }
    }

    private void abortGuide() {
        clearGuide();
    }

    /** Whether a {@link #guideEngineMove} is in progress. */
    public boolean isGuideActive() {
        return guideTarget != null;
    }

    // ---------------------------------------------------------------- state

    /** FEN of the tracked position, for the host to compare with its authoritative game. */
    public String trackedFen() {
        return detector.position().toFen();
    }

    /**
     * Whether the board is known to match the tracked position closely enough for play to continue:
     * synchronised or merely mid-move. False before the first board of a connection and while
     * mismatched; the host holds automatic moves back until true.
     */
    public boolean isBoardInSync() {
        MoveDetectionState state = detector.state();
        return state != MoveDetectionState.AWAITING_BOARD
                && state != MoveDetectionState.BOARD_MISMATCH;
    }

    /** Whether the LEDs currently show a disagreement between board and position. */
    public boolean isBoardMismatched() {
        return detector.state() == MoveDetectionState.BOARD_MISMATCH
                || !guideDeviationSquares().isEmpty();
    }

    /**
     * The squares the board disagrees on while {@link #isBoardMismatched()}: missing pieces first,
     * then misplaced ones; during a guide only the deviation beyond the guided move. Empty when in
     * sync.
     */
    public List<Integer> mismatchSquares() {
        if (isGuideActive()) {
            return guideDeviationSquares();
        }
        BoardState physical = detector.lastPhysical();
        if (detector.state() != MoveDetectionState.BOARD_MISMATCH || physical == null) {
            return Collections.emptyList();
        }
        return IdentityDiff.between(detector.expected(), physical).squares();
    }

    /**
     * The back-rank square on which a pawn is standing that should be the promotion piece, or -1.
     * The board identifies pieces, so a promotion is only recognised once the promoted piece is set
     * down; a pawn pushed to the last rank (or capturing onto it) looks like a plain mismatch to
     * the detector. This spots exactly that shape, outside a guide (missing pawn on the seventh
     * rank, same-coloured pawn on the eighth, promotion legal between them) and during a guide of a
     * promotion move (pawn on the guided destination), so the host can say "replace the pawn"
     * instead of "board does not match".
     */
    public int promotionSquareAwaitingPiece() {
        if (physicalBoard == null) {
            return -1;
        }
        ChessPosition position = detector.position();
        int pawn =
                position.sideToMove() == PieceColor.WHITE
                        ? IdentityProjection.codeOf(Piece.WHITE_PAWN)
                        : IdentityProjection.codeOf(Piece.BLACK_PAWN);
        if (isGuideActive()) {
            return guideMove.promotion() != null
                            && physicalBoard.pieceCodeAt(guideMove.to()) == pawn
                    ? guideMove.to()
                    : -1;
        }
        if (detector.state() != MoveDetectionState.BOARD_MISMATCH) {
            return -1;
        }
        IdentityDiff diff = IdentityDiff.between(detector.expected(), physicalBoard);
        if (diff.missing().size() != 1 || diff.placed().size() != 1) {
            return -1;
        }
        int from = diff.missing().get(0);
        int to = diff.placed().get(0);
        if (detector.expected().pieceCodeAt(from) != pawn
                || physicalBoard.pieceCodeAt(to) != pawn) {
            return -1;
        }
        for (Move move : position.legalMoves()) {
            if (move.from() == from && move.to() == to && move.promotion() != null) {
                return to;
            }
        }
        return -1;
    }

    /**
     * FEN of whatever currently stands on the board, with {@code sideToMove} supplied by the host,
     * for taking a position over from the board (see {@link PhysicalPosition#fenOf}). Does not
     * change the tracked position; call {@link #syncBoardToPosition} with the result once the host
     * has loaded it.
     */
    public String physicalPositionFen(PieceColor sideToMove) throws InvalidPositionException {
        return PhysicalPosition.fenOf(physicalBoard, sideToMove);
    }

    // ------------------------------------------------------------ resync

    /** Tracks the starting position; a board that does not show it lights up right away. */
    public void resetForNewGame() {
        abortGuide();
        leds.off();
        dispatch(detector.reset(ChessPosition.starting(), physicalBoard));
    }

    /**
     * Overwrites the tracked position with the host's authoritative {@code fen} and re-evaluates
     * the board against it; deviating squares light up like any mismatch. Malformed FEN is ignored.
     * Call after every (re)connect and whenever the host's game changes without the board.
     */
    public void syncBoardToPosition(String fen) {
        ChessPosition target;
        try {
            target = ChessPosition.fromFen(fen);
        } catch (IllegalArgumentException e) {
            return;
        }
        abortGuide();
        dispatch(detector.reset(target, physicalBoard));
    }
}
