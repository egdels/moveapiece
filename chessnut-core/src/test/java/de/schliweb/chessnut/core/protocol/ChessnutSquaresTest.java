/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.chessnut.core.protocol;

import static org.junit.Assert.assertEquals;

import de.schliweb.pegasus.core.protocol.BoardState;
import org.junit.Test;

public class ChessnutSquaresTest {

    @Test
    public void cornersMatchHardwareObservations() {
        // LED bit 0 lit h8; nibble 63 was a1, nibble 62 b1, nibble 56 h1 (capture 05:34).
        assertEquals(BoardState.squareIndex("h8"), ChessnutSquares.toBoardIndex(0));
        assertEquals(BoardState.squareIndex("a8"), ChessnutSquares.toBoardIndex(7));
        assertEquals(BoardState.squareIndex("h1"), ChessnutSquares.toBoardIndex(56));
        assertEquals(BoardState.squareIndex("b1"), ChessnutSquares.toBoardIndex(62));
        assertEquals(BoardState.squareIndex("a1"), ChessnutSquares.toBoardIndex(63));
        assertEquals(BoardState.squareIndex("e2"), ChessnutSquares.toBoardIndex(51));
        assertEquals(BoardState.squareIndex("e4"), ChessnutSquares.toBoardIndex(35));
    }

    @Test
    public void mappingIsAnInvolution() {
        for (int i = 0; i < 64; i++) {
            assertEquals(i, ChessnutSquares.toChessnutIndex(ChessnutSquares.toBoardIndex(i)));
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsOutOfRange() {
        ChessnutSquares.toBoardIndex(64);
    }
}
