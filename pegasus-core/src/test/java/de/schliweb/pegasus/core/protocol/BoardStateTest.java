/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.pegasus.core.protocol;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BoardStateTest {

    /** Board dump payload for the standard chess starting position. */
    private static byte[] startingPositionPayload() {
        byte[] p = new byte[64];
        int[] blackBack = {
            PieceCodes.BROOK,
            PieceCodes.BKNIGHT,
            PieceCodes.BBISHOP,
            PieceCodes.BQUEEN,
            PieceCodes.BKING,
            PieceCodes.BBISHOP,
            PieceCodes.BKNIGHT,
            PieceCodes.BROOK
        };
        int[] whiteBack = {
            PieceCodes.WROOK,
            PieceCodes.WKNIGHT,
            PieceCodes.WBISHOP,
            PieceCodes.WQUEEN,
            PieceCodes.WKING,
            PieceCodes.WBISHOP,
            PieceCodes.WKNIGHT,
            PieceCodes.WROOK
        };
        for (int f = 0; f < 8; f++) {
            p[f] = (byte) blackBack[f]; // rank 8
            p[8 + f] = (byte) PieceCodes.BPAWN; // rank 7
            p[48 + f] = (byte) PieceCodes.WPAWN; // rank 2
            p[56 + f] = (byte) whiteBack[f]; // rank 1
        }
        return p;
    }

    @Test
    public void squareNamingMatchesDgtNumbering() {
        assertEquals("a8", BoardState.squareName(0));
        assertEquals("h8", BoardState.squareName(7));
        assertEquals("a7", BoardState.squareName(8));
        assertEquals("e2", BoardState.squareName(52));
        assertEquals("h1", BoardState.squareName(63));
    }

    @Test
    public void squareIndexIsInverseOfSquareName() {
        for (int i = 0; i < BoardState.SQUARE_COUNT; i++) {
            assertEquals(i, BoardState.squareIndex(BoardState.squareName(i)));
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsInvalidSquareName() {
        BoardState.squareIndex("i9");
    }

    @Test
    public void decodesStartingPosition() {
        BoardState state = BoardState.fromBoardDumpPayload(startingPositionPayload());

        assertEquals(32, state.occupiedCount());
        assertEquals(PieceCodes.BKING, state.pieceCodeAt(BoardState.squareIndex("e8")));
        assertEquals(PieceCodes.WKING, state.pieceCodeAt(BoardState.squareIndex("e1")));
        assertEquals(PieceCodes.WPAWN, state.pieceCodeAt(BoardState.squareIndex("e2")));
        assertTrue(state.isOccupied(BoardState.squareIndex("a1")));
        assertFalse(state.isOccupied(BoardState.squareIndex("e4")));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsWrongPayloadLength() {
        BoardState.fromBoardDumpPayload(new byte[63]);
    }

    @Test
    public void withSquareIsImmutableUpdate() {
        BoardState empty = BoardState.empty();
        int e4 = BoardState.squareIndex("e4");

        BoardState updated = empty.withSquare(e4, PieceCodes.WPAWN);

        assertFalse(empty.isOccupied(e4));
        assertTrue(updated.isOccupied(e4));
        assertEquals(1, updated.occupiedCount());
        assertNotEquals(empty, updated);
    }

    @Test
    public void equalsAndHashCodeBasedOnContent() {
        BoardState a = BoardState.fromBoardDumpPayload(startingPositionPayload());
        BoardState b = BoardState.fromBoardDumpPayload(startingPositionPayload());

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void toStringRendersEightRanks() {
        String s = BoardState.fromBoardDumpPayload(startingPositionPayload()).toString();
        String[] lines = s.split("\n");

        assertEquals(8, lines.length);
        assertEquals("rnbqkbnr", lines[0]);
        assertEquals("PPPPPPPP", lines[6]);
        assertEquals("RNBQKBNR", lines[7]);
    }
}
