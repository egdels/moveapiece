/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.chessnut.core.game;

import de.schliweb.pegasus.core.chess.ChessPosition;
import de.schliweb.pegasus.core.chess.Move;

/**
 * Outcome of feeding one physical board to {@link IdentityMoveDetector}. Compared with the
 * occupancy detector there is no promotion or ambiguity outcome: the board reports which piece
 * stands where, so every completed legal move is uniquely identified.
 */
public final class IdentityDetectionResult {

    public enum Kind {
        /** Same physical state as before, or nothing to evaluate yet. */
        NO_CHANGE,
        /** Board equals the position and did before as well. */
        SYNCHRONIZED,
        /** Board equals the position again after a lift, an in-progress move or a mismatch. */
        POSITION_RESTORED,
        /** Pieces lifted or a legal move half done; nothing to act on. */
        IN_PROGRESS,
        /** Exactly one legal move explains the board; the position has been advanced. */
        CONFIRMED,
        /** Board differs from the position in a way no legal move explains. */
        BOARD_MISMATCH
    }

    private final Kind kind;
    private final Move move;
    private final ChessPosition newPosition;
    private final IdentityDiff diff;

    private IdentityDetectionResult(
            Kind kind, Move move, ChessPosition newPosition, IdentityDiff diff) {
        this.kind = kind;
        this.move = move;
        this.newPosition = newPosition;
        this.diff = diff;
    }

    static IdentityDetectionResult noChange() {
        return new IdentityDetectionResult(Kind.NO_CHANGE, null, null, null);
    }

    static IdentityDetectionResult synchronizedResult() {
        return new IdentityDetectionResult(Kind.SYNCHRONIZED, null, null, null);
    }

    static IdentityDetectionResult positionRestored() {
        return new IdentityDetectionResult(Kind.POSITION_RESTORED, null, null, null);
    }

    static IdentityDetectionResult inProgress(IdentityDiff diff) {
        return new IdentityDetectionResult(Kind.IN_PROGRESS, null, null, diff);
    }

    static IdentityDetectionResult confirmed(Move move, ChessPosition newPosition) {
        return new IdentityDetectionResult(Kind.CONFIRMED, move, newPosition, null);
    }

    static IdentityDetectionResult boardMismatch(IdentityDiff diff) {
        return new IdentityDetectionResult(Kind.BOARD_MISMATCH, null, null, diff);
    }

    public Kind kind() {
        return kind;
    }

    /** Confirmed move, else {@code null}. */
    public Move move() {
        return move;
    }

    /** Position after a confirmed move, else {@code null}. */
    public ChessPosition newPosition() {
        return newPosition;
    }

    /** Difference to the expected board for IN_PROGRESS and BOARD_MISMATCH, else {@code null}. */
    public IdentityDiff diff() {
        return diff;
    }

    @Override
    public String toString() {
        switch (kind) {
            case CONFIRMED:
                return kind + "(" + move.uci() + ")";
            case IN_PROGRESS:
            case BOARD_MISMATCH:
                return kind + "(" + diff + ")";
            default:
                return kind.toString();
        }
    }
}
