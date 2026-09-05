/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.pegasus.core.movedetect;

import de.schliweb.pegasus.core.chess.ChessPosition;
import de.schliweb.pegasus.core.chess.Move;
import java.util.List;

/**
 * Event API for higher layers (UI, game controller). All callbacks are invoked synchronously from
 * the thread that feeds the {@link MoveDetector}; the logical position is always updated before
 * {@link #onMoveConfirmed}.
 */
public interface MoveDetectionListener {

    /** Fired whenever the state machine changes state. */
    default void onStateChanged(MoveDetectionState state) {}

    /** A plausible move is physically in progress (lifted pieces etc.). */
    default void onMoveInProgress(BoardMismatch pendingDiff) {}

    /** Exactly one legal move explains the physical board; position updated. */
    default void onMoveConfirmed(Move move, ChessPosition newPosition) {}

    /** Candidates differ only in the promotion piece; UI must ask the user. */
    default void onPromotionRequired(List<Move> candidates) {}

    /** Multiple non-promotion candidates; external disambiguation required. */
    default void onAmbiguousMove(List<Move> candidates) {}

    /** Physical board no longer matches any legal interpretation. */
    default void onBoardMismatch(BoardMismatch mismatch) {}

    /** Board returned to the expected occupancy without a move (recovery/undo). */
    default void onPositionRestored() {}
}
