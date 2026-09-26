/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.chessnut.core.protocol;

import de.schliweb.pegasus.core.protocol.BoardState;

/**
 * Square numbering of the Chessnut Air, VERIFIED on hardware.
 *
 * <p>The board enumerates squares from h8 across to a8, then h7 to a7, down to a1: index 0 = h8, 7
 * = a8, 56 = h1, 63 = a1. The same index is used for the nibbles of a board report (low nibble of
 * byte 0 is index 0) and for the bits of the LED mask (byte 0 bit 0 is index 0).
 *
 * <p>{@link BoardState} numbers a8 = 0 … h1 = 63, so the two only differ in file direction within
 * each rank. The mapping is its own inverse.
 */
public final class ChessnutSquares {

    private ChessnutSquares() {}

    /** Chessnut index → {@link BoardState} index (and vice versa, the mapping is an involution). */
    public static int toBoardIndex(int chessnutIndex) {
        check(chessnutIndex);
        return (chessnutIndex & ~7) + 7 - (chessnutIndex & 7);
    }

    /** {@link BoardState} index → Chessnut index. */
    public static int toChessnutIndex(int boardIndex) {
        return toBoardIndex(boardIndex);
    }

    private static void check(int index) {
        if (index < 0 || index >= BoardState.SQUARE_COUNT) {
            throw new IllegalArgumentException("Square index out of range: " + index);
        }
    }
}
