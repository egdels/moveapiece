/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.pegasus.core.chess;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import de.schliweb.pegasus.core.protocol.BoardState;
import org.junit.Test;

public class OccupancyProjectionTest {

    @Test
    public void startingPositionOccupiesRanks1278() {
        BoardState occupancy = OccupancyProjection.occupancyOf(ChessPosition.starting());
        assertEquals(32, occupancy.occupiedCount());
        for (String square : new String[] {"a1", "h1", "e2", "d7", "a8", "h8"}) {
            assertTrue(square, occupancy.isOccupied(BoardState.squareIndex(square)));
        }
        for (String square : new String[] {"a3", "e4", "d5", "h6"}) {
            assertFalse(square, occupancy.isOccupied(BoardState.squareIndex(square)));
        }
    }

    @Test
    public void projectionDiscardsPieceIdentity() {
        // Same occupancy for completely different piece sets.
        BoardState a =
                OccupancyProjection.occupancyOf(
                        ChessPosition.fromFen("8/8/8/8/8/8/8/KQk5 w - - 0 1"));
        BoardState b =
                OccupancyProjection.occupancyOf(
                        ChessPosition.fromFen("8/8/8/8/8/8/8/KPk5 w - - 0 1"));
        assertEquals(a, b);
    }

    @Test
    public void normalizeMapsAnyNonZeroCodeToOccupied() {
        byte[] codes = new byte[BoardState.SQUARE_COUNT];
        codes[0] = 0x01;
        codes[1] = 0x7F; // locked-board code seen on real hardware
        codes[2] = 0x06;
        BoardState normalized =
                OccupancyProjection.normalize(BoardState.fromBoardDumpPayload(codes));
        assertEquals(OccupancyProjection.OCCUPIED, normalized.pieceCodeAt(0));
        assertEquals(OccupancyProjection.OCCUPIED, normalized.pieceCodeAt(1));
        assertEquals(OccupancyProjection.OCCUPIED, normalized.pieceCodeAt(2));
        assertEquals(OccupancyProjection.EMPTY, normalized.pieceCodeAt(3));
    }
}
