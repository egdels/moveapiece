/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.pegasus.core.movedetect;

import de.schliweb.pegasus.core.chess.ChessPosition;
import de.schliweb.pegasus.core.chess.OccupancyProjection;
import de.schliweb.pegasus.core.protocol.BoardState;

/**
 * Simulation layer for tests: emulates the physical Pegasus board purely as occupancy (no piece
 * identity, like the real hardware) and drives a {@link MoveDetector} with lift/place operations.
 * No Android dependency.
 */
final class FakePhysicalBoard {

    private final MoveDetector detector;
    private BoardState physical;
    private MoveDetectionResult lastResult;

    FakePhysicalBoard(MoveDetector detector, ChessPosition initialPosition) {
        this.detector = detector;
        this.physical = OccupancyProjection.occupancyOf(initialPosition);
    }

    /** Sends the current physical state (like an initial board dump). */
    MoveDetectionResult sync() {
        lastResult = detector.onPhysicalBoard(physical);
        return lastResult;
    }

    /** Lifts the piece from the given square and reports the new state. */
    MoveDetectionResult lift(String square) {
        physical = physical.withSquare(BoardState.squareIndex(square), OccupancyProjection.EMPTY);
        return sync();
    }

    /** Places a piece on the given square and reports the new state. */
    MoveDetectionResult place(String square) {
        physical =
                physical.withSquare(BoardState.squareIndex(square), OccupancyProjection.OCCUPIED);
        return sync();
    }

    BoardState state() {
        return physical;
    }

    MoveDetectionResult lastResult() {
        return lastResult;
    }
}
