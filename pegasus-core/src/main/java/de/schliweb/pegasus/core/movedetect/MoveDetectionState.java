/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.pegasus.core.movedetect;

/** Explicit states of the {@link MoveDetector} state machine. */
public enum MoveDetectionState {

    /** No physical board state received yet (e.g. before the first dump). */
    AWAITING_BOARD,

    /** Physical occupancy equals the projection of the logical position. */
    SYNCHRONIZED,

    /**
     * Physical board deviates from the logical position, but the deviation is a plausible
     * intermediate state of a legal move (lifted pieces and/or a piece already placed on a legal
     * destination).
     */
    MOVE_IN_PROGRESS,

    /**
     * Physical occupancy matches multiple legal moves that differ only in the promotion piece; UI
     * selection required ({@link MoveDetector#selectPromotion}).
     */
    PROMOTION_PENDING,

    /**
     * Physical occupancy matches multiple legal moves that are not a pure promotion choice;
     * external disambiguation required ({@link MoveDetector#selectCandidate}).
     */
    AMBIGUOUS,

    /**
     * Physical board can no longer be interpreted as the current position or as an
     * intermediate/final state of any legal move. The user must restore the board; recovery back to
     * SYNCHRONIZED is automatic.
     */
    BOARD_MISMATCH
}
