/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.chessnut.core.protocol;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import de.schliweb.pegasus.core.protocol.BoardState;
import de.schliweb.pegasus.core.protocol.PieceCodes;
import java.util.Arrays;
import org.junit.Test;

public class ChessnutBoardReportTest {

    private static ChessnutBoardReport decode(byte[] frame) {
        return ChessnutBoardReport.fromPayload(Arrays.copyOfRange(frame, 2, frame.length));
    }

    @Test
    public void decodesStartPositionWithPieceIdentity() {
        ChessnutBoardReport report = decode(Fixtures.START_POSITION);

        assertEquals(Fixtures.START_POSITION_TEXT, report.state().toString());
        assertEquals(32, report.state().occupiedCount());
        assertEquals(PieceCodes.WKING, report.state().pieceCodeAt(BoardState.squareIndex("e1")));
        assertEquals(PieceCodes.BQUEEN, report.state().pieceCodeAt(BoardState.squareIndex("d8")));
        assertEquals(459, report.uptimeSeconds());
    }

    @Test
    public void decodesEmptyBoard() {
        ChessnutBoardReport report = decode(Fixtures.EMPTY_BOARD);

        assertEquals(0, report.state().occupiedCount());
        assertEquals(67, report.uptimeSeconds());
    }

    @Test
    public void decodesLiftAndPlacementOfE2E4() {
        BoardState lifted = decode(Fixtures.E2_LIFTED).state();
        BoardState placed = decode(Fixtures.AFTER_E4).state();

        assertFalse(lifted.isOccupied(BoardState.squareIndex("e2")));
        assertEquals(31, lifted.occupiedCount());
        assertEquals(PieceCodes.WPAWN, placed.pieceCodeAt(BoardState.squareIndex("e4")));
        assertFalse(placed.isOccupied(BoardState.squareIndex("e2")));
        assertEquals(32, placed.occupiedCount());
        assertEquals(492, decode(Fixtures.AFTER_E4).uptimeSeconds());
    }

    @Test
    public void unknownNibbleDecodesAsEmpty() {
        byte[] payload = new byte[36];
        payload[0] = (byte) 0xFD; // nibbles 0xD (unknown) and 0xF (unknown)

        BoardState state = ChessnutBoardReport.fromPayload(payload).state();

        assertEquals(0, state.occupiedCount());
        assertTrue(state.equals(BoardState.empty()));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsWrongLength() {
        ChessnutBoardReport.fromPayload(new byte[35]);
    }
}
