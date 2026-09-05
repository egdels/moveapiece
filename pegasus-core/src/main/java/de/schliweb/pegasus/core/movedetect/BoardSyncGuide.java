/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.pegasus.core.movedetect;

import de.schliweb.pegasus.core.chess.OccupancyProjection;
import de.schliweb.pegasus.core.protocol.BoardState;
import java.util.ArrayList;
import java.util.List;

/**
 * Guides the user to bring the physical board to a target occupancy, e.g. to reproduce an
 * opponent's (remote) move or to correct a BOARD_MISMATCH.
 *
 * <p>The guide is deliberately occupancy-only: it does not know which pieces are involved — the
 * caller derives the target from the logical position (docs/MOVE_DETECTION.md). While active,
 * physical board states should be routed here instead of the {@link MoveDetector}; once the target
 * is reached the caller resynchronizes the detector.
 *
 * <p>Not thread-safe; drive from one thread.
 */
public final class BoardSyncGuide {

    /** Callbacks; invoked synchronously from {@link #onPhysicalBoard}. */
    public interface Listener {

        /**
         * The physical board deviates from the target; the given DGT square indices (missing first,
         * then unexpected) should be indicated to the user (e.g. via LEDs). Fired only when the set
         * changes.
         */
        void onIndicate(List<Integer> squares);

        /** The physical board now matches the target occupancy. */
        void onTargetReached();
    }

    private final Listener listener;

    private BoardState target;
    private List<Integer> lastIndicated;

    public BoardSyncGuide(Listener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener must not be null");
        }
        this.listener = listener;
    }

    /**
     * Starts guiding toward {@code targetOccupancy}. If {@code physical} is non-null it is
     * evaluated immediately (may complete synchronously).
     */
    public void start(BoardState targetOccupancy, BoardState physical) {
        if (targetOccupancy == null) {
            throw new IllegalArgumentException("targetOccupancy must not be null");
        }
        this.target = OccupancyProjection.normalize(targetOccupancy);
        this.lastIndicated = null;
        if (physical != null) {
            onPhysicalBoard(physical);
        }
    }

    /** Whether a guidance session is in progress. */
    public boolean isActive() {
        return target != null;
    }

    /** Aborts the current guidance session without reaching the target. */
    public void cancel() {
        target = null;
        lastIndicated = null;
    }

    /**
     * Evaluates a new physical state against the target. Returns the remaining diff, or {@code
     * null} when the guide is inactive.
     */
    public BoardMismatch onPhysicalBoard(BoardState physical) {
        if (target == null || physical == null) {
            return null;
        }
        BoardMismatch diff = BoardMismatch.between(target, OccupancyProjection.normalize(physical));
        if (diff.isEmpty()) {
            target = null;
            lastIndicated = null;
            listener.onTargetReached();
            return diff;
        }
        List<Integer> squares = new ArrayList<>(diff.missingOccupied());
        squares.addAll(diff.unexpectedOccupied());
        if (!squares.equals(lastIndicated)) {
            lastIndicated = squares;
            listener.onIndicate(squares);
        }
        return diff;
    }
}
