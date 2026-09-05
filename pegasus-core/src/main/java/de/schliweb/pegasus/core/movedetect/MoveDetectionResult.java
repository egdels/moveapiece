/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.pegasus.core.movedetect;

import de.schliweb.pegasus.core.chess.ChessPosition;
import de.schliweb.pegasus.core.chess.Move;
import java.util.Collections;
import java.util.List;

/**
 * Result of evaluating one physical board state against the logical position. No {@code null}-based
 * meaning: the {@link Kind} determines which accessors carry data.
 */
public final class MoveDetectionResult {

    public enum Kind {
        /** Same physical state as before; nothing to do. */
        NO_CHANGE,
        /** Physical board matches the logical position (already synchronized). */
        SYNCHRONIZED,
        /** Board was out of sync / mid-move and is back to the logical position. */
        POSITION_RESTORED,
        /** Plausible intermediate state of a legal move; keep waiting. */
        IN_PROGRESS,
        /** Exactly one legal move explains the physical state. */
        CONFIRMED,
        /** Candidates differ only in the promotion piece; UI selection needed. */
        PROMOTION_REQUIRED,
        /** Multiple non-promotion candidates produce this occupancy. */
        AMBIGUOUS,
        /** Physical state matches no legal interpretation. */
        BOARD_MISMATCH
    }

    private final Kind kind;
    private final Move move;
    private final ChessPosition newPosition;
    private final List<Move> candidates;
    private final BoardMismatch mismatch;

    private MoveDetectionResult(
            Kind kind,
            Move move,
            ChessPosition newPosition,
            List<Move> candidates,
            BoardMismatch mismatch) {
        this.kind = kind;
        this.move = move;
        this.newPosition = newPosition;
        this.candidates = candidates;
        this.mismatch = mismatch;
    }

    static MoveDetectionResult noChange() {
        return new MoveDetectionResult(Kind.NO_CHANGE, null, null, Collections.emptyList(), null);
    }

    static MoveDetectionResult synchronizedResult() {
        return new MoveDetectionResult(
                Kind.SYNCHRONIZED, null, null, Collections.emptyList(), null);
    }

    static MoveDetectionResult positionRestored() {
        return new MoveDetectionResult(
                Kind.POSITION_RESTORED, null, null, Collections.emptyList(), null);
    }

    static MoveDetectionResult inProgress(BoardMismatch pendingDiff) {
        return new MoveDetectionResult(
                Kind.IN_PROGRESS, null, null, Collections.emptyList(), pendingDiff);
    }

    static MoveDetectionResult confirmed(Move move, ChessPosition newPosition) {
        return new MoveDetectionResult(
                Kind.CONFIRMED, move, newPosition, Collections.singletonList(move), null);
    }

    static MoveDetectionResult promotionRequired(List<Move> candidates) {
        return new MoveDetectionResult(
                Kind.PROMOTION_REQUIRED,
                null,
                null,
                Collections.unmodifiableList(candidates),
                null);
    }

    static MoveDetectionResult ambiguous(List<Move> candidates) {
        return new MoveDetectionResult(
                Kind.AMBIGUOUS, null, null, Collections.unmodifiableList(candidates), null);
    }

    static MoveDetectionResult boardMismatch(BoardMismatch mismatch) {
        return new MoveDetectionResult(
                Kind.BOARD_MISMATCH, null, null, Collections.emptyList(), mismatch);
    }

    public Kind kind() {
        return kind;
    }

    /** Confirmed move; only for {@link Kind#CONFIRMED}. */
    public Move move() {
        return move;
    }

    /** Logical position after the confirmed move; only for {@link Kind#CONFIRMED}. */
    public ChessPosition newPosition() {
        return newPosition;
    }

    /** Candidate moves; for CONFIRMED, PROMOTION_REQUIRED and AMBIGUOUS. */
    public List<Move> candidates() {
        return candidates;
    }

    /** Occupancy diff vs. expected; for IN_PROGRESS and BOARD_MISMATCH. */
    public BoardMismatch mismatch() {
        return mismatch;
    }

    @Override
    public String toString() {
        switch (kind) {
            case CONFIRMED:
                return "CONFIRMED " + move.uci();
            case PROMOTION_REQUIRED:
            case AMBIGUOUS:
                return kind + " " + candidates;
            case IN_PROGRESS:
            case BOARD_MISMATCH:
                return kind + " (" + mismatch + ")";
            default:
                return kind.toString();
        }
    }
}
