/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.pegasus.core.protocol;

import java.util.Arrays;

/**
 * Immutable snapshot of the 64 board squares as reported by a DGT_MSG_BOARD_DUMP (0x86) payload.
 *
 * <p>Square numbering (INFERRED, classic DGT protocol, to be verified on real hardware): index 0 =
 * a8, 1 = b8, …, 7 = h8, 8 = a7, …, 63 = h1.
 */
public final class BoardState {

    public static final int SQUARE_COUNT = 64;

    private final byte[] pieceCodes;

    private BoardState(byte[] pieceCodes) {
        this.pieceCodes = pieceCodes;
    }

    /** Creates a state from a 64-byte board dump payload. */
    public static BoardState fromBoardDumpPayload(byte[] payload) {
        if (payload == null || payload.length != SQUARE_COUNT) {
            throw new IllegalArgumentException(
                    "Board dump payload must be 64 bytes, got "
                            + (payload == null ? "null" : payload.length));
        }
        return new BoardState(payload.clone());
    }

    /** Empty board (all squares EMPTY). */
    public static BoardState empty() {
        return new BoardState(new byte[SQUARE_COUNT]);
    }

    /** Copy of this state with one square changed (field update applied). */
    public BoardState withSquare(int squareIndex, int pieceCode) {
        checkIndex(squareIndex);
        byte[] copy = pieceCodes.clone();
        copy[squareIndex] = (byte) pieceCode;
        return new BoardState(copy);
    }

    public int pieceCodeAt(int squareIndex) {
        checkIndex(squareIndex);
        return pieceCodes[squareIndex] & 0xFF;
    }

    public boolean isOccupied(int squareIndex) {
        return PieceCodes.isOccupied(pieceCodeAt(squareIndex));
    }

    public int occupiedCount() {
        int count = 0;
        for (byte code : pieceCodes) {
            if (PieceCodes.isOccupied(code & 0xFF)) {
                count++;
            }
        }
        return count;
    }

    /** Algebraic square name, e.g. index 0 → "a8", 63 → "h1". */
    public static String squareName(int squareIndex) {
        checkIndex(squareIndex);
        char file = (char) ('a' + (squareIndex % 8));
        char rank = (char) ('8' - (squareIndex / 8));
        return new String(new char[] {file, rank});
    }

    /** Inverse of {@link #squareName(int)}, e.g. "e2" → 52. */
    public static int squareIndex(String squareName) {
        if (squareName == null || squareName.length() != 2) {
            throw new IllegalArgumentException("Invalid square name: " + squareName);
        }
        int file = squareName.charAt(0) - 'a';
        int rank = squareName.charAt(1) - '1';
        if (file < 0 || file > 7 || rank < 0 || rank > 7) {
            throw new IllegalArgumentException("Invalid square name: " + squareName);
        }
        return (7 - rank) * 8 + file;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BoardState)) {
            return false;
        }
        return Arrays.equals(pieceCodes, ((BoardState) o).pieceCodes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(pieceCodes);
    }

    /** 8 lines of 8 chars, rank 8 first, using {@link PieceCodes#toChar(int)}. */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(72);
        for (int i = 0; i < SQUARE_COUNT; i++) {
            sb.append(PieceCodes.toChar(pieceCodes[i] & 0xFF));
            if (i % 8 == 7 && i != SQUARE_COUNT - 1) {
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    private static void checkIndex(int squareIndex) {
        if (squareIndex < 0 || squareIndex >= SQUARE_COUNT) {
            throw new IllegalArgumentException("Square index out of range: " + squareIndex);
        }
    }
}
