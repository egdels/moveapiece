/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.pegasus.core.chess;

import de.schliweb.pegasus.core.protocol.BoardState;

/**
 * Projects a logical {@link ChessPosition} onto physical occupancy (occupied/empty), deliberately
 * discarding piece type and color — the Pegasus reports occupancy only (docs/PEGASUS_PROTOCOL.md).
 */
public final class OccupancyProjection {

    /** Occupancy code used for occupied squares (matches real hardware). */
    public static final int OCCUPIED = 0x01;

    /** Occupancy code used for empty squares (matches real hardware). */
    public static final int EMPTY = 0x00;

    private OccupancyProjection() {}

    /** Occupancy of the given logical position as a {@link BoardState}. */
    public static BoardState occupancyOf(ChessPosition position) {
        byte[] codes = new byte[BoardState.SQUARE_COUNT];
        for (int square = 0; square < BoardState.SQUARE_COUNT; square++) {
            codes[square] = position.pieceAt(square) != null ? (byte) OCCUPIED : (byte) EMPTY;
        }
        return BoardState.fromBoardDumpPayload(codes);
    }

    /**
     * Normalizes any physical {@link BoardState} to pure occupancy codes (every non-zero code
     * becomes {@link #OCCUPIED}) so that states from board dumps and field updates compare
     * deterministically.
     */
    public static BoardState normalize(BoardState state) {
        byte[] codes = new byte[BoardState.SQUARE_COUNT];
        for (int square = 0; square < BoardState.SQUARE_COUNT; square++) {
            codes[square] = state.isOccupied(square) ? (byte) OCCUPIED : (byte) EMPTY;
        }
        return BoardState.fromBoardDumpPayload(codes);
    }
}
