/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.chessnut.core.game;

import de.schliweb.pegasus.core.chess.ChessPosition;
import de.schliweb.pegasus.core.chess.Move;
import de.schliweb.pegasus.core.movedetect.MoveDetectionState;
import de.schliweb.pegasus.core.protocol.BoardState;
import java.util.ArrayList;
import java.util.List;

/**
 * Move detection for a board that reports piece identity (Chessnut Air). Same principle as the
 * occupancy-based {@code MoveDetector}: for every legal move the resulting board is projected and
 * compared with the physical one, and an exact match confirms the move. Because the projection
 * includes piece identity, an exact match is always unique: captures are proven by the attacker
 * standing on the destination, promotions by the piece that was set down, so there is no pending,
 * promotion or ambiguity state and no host-side proof logic.
 *
 * <p>Between moves the board passes through intermediate states (pieces lifted, king moved before
 * the rook when castling, pawn moved before the captured pawn was removed en passant). Those are
 * reported as {@link IdentityDetectionResult.Kind#IN_PROGRESS} when they can be explained as
 * progress towards some legal move with at most a few unrelated pieces lifted; anything else is a
 * {@link IdentityDetectionResult.Kind#BOARD_MISMATCH}.
 *
 * <p>A mismatch is left either by restoring the position or by an exact legal-move match, which
 * with piece identities is unambiguous (a pawn pushed to the back rank is a mismatch until the
 * promotion piece replaces it). Before the first sync of a connection only a restore counts.
 *
 * <p>Only the {@link MoveDetectionState} values AWAITING_BOARD, SYNCHRONIZED, MOVE_IN_PROGRESS and
 * BOARD_MISMATCH occur. Deterministic and single-threaded by design; repeated identical inputs are
 * idempotent.
 */
public final class IdentityMoveDetector {

    /** Max simultaneously lifted pieces still treated as "move in progress". */
    static final int MAX_LIFTED_PIECES = 3;

    /** Max lifted pieces unrelated to a candidate move (e.g. accidental lift). */
    static final int MAX_UNRELATED_LIFTS = 2;

    private ChessPosition position;
    private BoardState expected;
    private BoardState lastPhysical;
    private MoveDetectionState state = MoveDetectionState.AWAITING_BOARD;
    private List<Move> legalMoves;
    private List<BoardState> legalBoards;

    public IdentityMoveDetector(ChessPosition initialPosition) {
        setPosition(initialPosition);
    }

    /** Current logical position (advanced atomically on confirmed moves). */
    public ChessPosition position() {
        return position;
    }

    /** Expected physical board for the current position, piece identities included. */
    public BoardState expected() {
        return expected;
    }

    /** Last processed physical board or {@code null} before the first input. */
    public BoardState lastPhysical() {
        return lastPhysical;
    }

    public MoveDetectionState state() {
        return state;
    }

    /**
     * Controlled resynchronisation (new game, FEN load, reconnect): drops all transient state and
     * re-evaluates {@code physical} (SYNCHRONIZED or BOARD_MISMATCH), or only sets the position and
     * waits for the next board if {@code physical} is {@code null}.
     */
    public IdentityDetectionResult reset(ChessPosition newPosition, BoardState physical) {
        setPosition(newPosition);
        lastPhysical = null;
        state = MoveDetectionState.AWAITING_BOARD;
        if (physical == null) {
            return IdentityDetectionResult.noChange();
        }
        return onPhysicalBoard(physical);
    }

    /** Feeds a new physical board (with piece identities) and evaluates it. */
    public IdentityDetectionResult onPhysicalBoard(BoardState physical) {
        if (physical == null) {
            throw new IllegalArgumentException("physical must not be null");
        }
        if (physical.equals(lastPhysical)) {
            return IdentityDetectionResult.noChange();
        }
        lastPhysical = physical;
        return evaluate(physical);
    }

    private IdentityDetectionResult evaluate(BoardState physical) {
        if (physical.equals(expected)) {
            boolean wasSynchronized =
                    state == MoveDetectionState.SYNCHRONIZED
                            || state == MoveDetectionState.AWAITING_BOARD;
            state = MoveDetectionState.SYNCHRONIZED;
            return wasSynchronized
                    ? IdentityDetectionResult.synchronizedResult()
                    : IdentityDetectionResult.positionRestored();
        }
        IdentityDiff diff = IdentityDiff.between(expected, physical);
        // No detection before the board matched the position once (first report, reconnect):
        // require a full sync first.
        if (state == MoveDetectionState.AWAITING_BOARD) {
            state = MoveDetectionState.BOARD_MISMATCH;
            return IdentityDetectionResult.boardMismatch(diff);
        }
        // An exact match of a legal move's resulting board (all 64 squares, identities included)
        // is unambiguous, so it also resolves a mismatch without restoring the position first.
        // That matters for promotions: a pawn pushed to the back rank is a mismatch until the
        // pawn is swapped for the promotion piece, which must then confirm the move directly.
        for (int i = 0; i < legalMoves.size(); i++) {
            if (legalBoards.get(i).equals(physical)) {
                return confirm(legalMoves.get(i));
            }
        }
        if (state == MoveDetectionState.BOARD_MISMATCH) {
            return IdentityDetectionResult.boardMismatch(diff);
        }
        if (isPlausibleIntermediate(diff, physical)) {
            state = MoveDetectionState.MOVE_IN_PROGRESS;
            return IdentityDetectionResult.inProgress(diff);
        }
        state = MoveDetectionState.BOARD_MISMATCH;
        return IdentityDetectionResult.boardMismatch(diff);
    }

    /**
     * A state is a plausible intermediate if it is the current position with up to {@link
     * #MAX_LIFTED_PIECES} pieces lifted, or progress towards one legal move: every piece standing
     * where it should not must be exactly the piece that move puts there, pieces can only have been
     * set down from squares the move frees, and at most {@link #MAX_UNRELATED_LIFTS} further pieces
     * may be lifted.
     */
    private boolean isPlausibleIntermediate(IdentityDiff diff, BoardState physical) {
        List<Integer> missing = diff.missing();
        List<Integer> placed = diff.placed();
        if (placed.isEmpty()) {
            return !missing.isEmpty() && missing.size() <= MAX_LIFTED_PIECES;
        }
        for (BoardState after : legalBoards) {
            boolean placedExplained = true;
            for (int square : placed) {
                if (after.pieceCodeAt(square) != physical.pieceCodeAt(square)) {
                    placedExplained = false;
                    break;
                }
            }
            if (!placedExplained) {
                continue;
            }
            int unrelatedLifts = 0;
            for (int square : missing) {
                if (after.isOccupied(square)) {
                    unrelatedLifts++;
                }
            }
            int coveredLifts = missing.size() - unrelatedLifts;
            if (unrelatedLifts <= MAX_UNRELATED_LIFTS && placed.size() <= coveredLifts) {
                return true;
            }
        }
        return false;
    }

    private IdentityDetectionResult confirm(Move move) {
        ChessPosition newPosition = position.apply(move);
        setPosition(newPosition);
        state = MoveDetectionState.SYNCHRONIZED;
        return IdentityDetectionResult.confirmed(move, newPosition);
    }

    private void setPosition(ChessPosition newPosition) {
        if (newPosition == null) {
            throw new IllegalArgumentException("position must not be null");
        }
        position = newPosition;
        expected = IdentityProjection.of(newPosition);
        legalMoves = new ArrayList<>(newPosition.legalMoves());
        legalBoards = new ArrayList<>(legalMoves.size());
        for (Move move : legalMoves) {
            legalBoards.add(IdentityProjection.of(newPosition.apply(move)));
        }
    }
}
