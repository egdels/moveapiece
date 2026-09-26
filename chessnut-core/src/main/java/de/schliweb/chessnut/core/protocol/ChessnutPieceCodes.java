/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.chessnut.core.protocol;

import de.schliweb.pegasus.core.protocol.PieceCodes;

/**
 * Piece nibbles of a Chessnut board report, VERIFIED on hardware against the start position, and
 * their translation to the {@link PieceCodes} used by {@link
 * de.schliweb.pegasus.core.protocol.BoardState}.
 *
 * <p>Unlike the Pegasus, the Chessnut identifies every piece; the translated {@link BoardState}
 * therefore carries real piece codes, which occupancy-based move detection ignores and stricter
 * consumers may use.
 */
public final class ChessnutPieceCodes {

    public static final int EMPTY = 0x0;
    public static final int BQUEEN = 0x1;
    public static final int BKING = 0x2;
    public static final int BBISHOP = 0x3;
    public static final int BPAWN = 0x4;
    public static final int BKNIGHT = 0x5;
    public static final int WROOK = 0x6;
    public static final int WPAWN = 0x7;
    public static final int BROOK = 0x8;
    public static final int WBISHOP = 0x9;
    public static final int WKNIGHT = 0xA;
    public static final int WQUEEN = 0xB;
    public static final int WKING = 0xC;

    private ChessnutPieceCodes() {}

    /**
     * Translates a Chessnut nibble to the corresponding {@link PieceCodes} value. Unknown nibbles
     * (0xD–0xF, never observed) map to {@link PieceCodes#EMPTY} so a glitch can never be mistaken
     * for a piece.
     */
    public static int toPieceCode(int nibble) {
        switch (nibble & 0xF) {
            case BQUEEN:
                return PieceCodes.BQUEEN;
            case BKING:
                return PieceCodes.BKING;
            case BBISHOP:
                return PieceCodes.BBISHOP;
            case BPAWN:
                return PieceCodes.BPAWN;
            case BKNIGHT:
                return PieceCodes.BKNIGHT;
            case WROOK:
                return PieceCodes.WROOK;
            case WPAWN:
                return PieceCodes.WPAWN;
            case BROOK:
                return PieceCodes.BROOK;
            case WBISHOP:
                return PieceCodes.WBISHOP;
            case WKNIGHT:
                return PieceCodes.WKNIGHT;
            case WQUEEN:
                return PieceCodes.WQUEEN;
            case WKING:
                return PieceCodes.WKING;
            default:
                return PieceCodes.EMPTY;
        }
    }

    /** FEN-style char for a Chessnut nibble (white upper case, '.' empty, '?' unknown). */
    public static char toChar(int nibble) {
        int n = nibble & 0xF;
        if (n > WKING) {
            return '?';
        }
        return PieceCodes.toChar(toPieceCode(n));
    }
}
