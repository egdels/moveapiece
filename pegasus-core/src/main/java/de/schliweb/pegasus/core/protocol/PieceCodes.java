/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.pegasus.core.protocol;

/**
 * DGT piece codes (INFERRED from the classic DGT serial protocol, see docs/PEGASUS_PROTOCOL.md).
 * The Pegasus senses only occupancy, so real hardware may report generic codes; consumers should
 * primarily rely on {@link #isOccupied(int)}.
 */
public final class PieceCodes {

    public static final int EMPTY = 0x00;
    public static final int WPAWN = 0x01;
    public static final int WROOK = 0x02;
    public static final int WKNIGHT = 0x03;
    public static final int WBISHOP = 0x04;
    public static final int WKING = 0x05;
    public static final int WQUEEN = 0x06;
    public static final int BPAWN = 0x07;
    public static final int BROOK = 0x08;
    public static final int BKNIGHT = 0x09;
    public static final int BBISHOP = 0x0A;
    public static final int BKING = 0x0B;
    public static final int BQUEEN = 0x0C;

    private PieceCodes() {}

    /** Any non-zero code counts as occupied (occupancy-first strategy). */
    public static boolean isOccupied(int pieceCode) {
        return pieceCode != EMPTY;
    }

    /** FEN-style char: white upper case, black lower case, '.' empty, '?' unknown. */
    public static char toChar(int pieceCode) {
        switch (pieceCode) {
            case EMPTY:
                return '.';
            case WPAWN:
                return 'P';
            case WROOK:
                return 'R';
            case WKNIGHT:
                return 'N';
            case WBISHOP:
                return 'B';
            case WKING:
                return 'K';
            case WQUEEN:
                return 'Q';
            case BPAWN:
                return 'p';
            case BROOK:
                return 'r';
            case BKNIGHT:
                return 'n';
            case BBISHOP:
                return 'b';
            case BKING:
                return 'k';
            case BQUEEN:
                return 'q';
            default:
                return '?';
        }
    }
}
