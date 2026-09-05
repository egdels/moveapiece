/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.pegasus.core.chess;

/** Type of a logical chess piece. */
public enum PieceType {
    PAWN('p'),
    KNIGHT('n'),
    BISHOP('b'),
    ROOK('r'),
    QUEEN('q'),
    KING('k');

    private final char letter;

    PieceType(char letter) {
        this.letter = letter;
    }

    /** Lowercase letter as used in UCI promotion suffixes and FEN (black case). */
    public char letter() {
        return letter;
    }

    /** Inverse of {@link #letter()}, case insensitive. */
    public static PieceType fromLetter(char c) {
        char lower = Character.toLowerCase(c);
        for (PieceType type : values()) {
            if (type.letter == lower) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown piece letter: " + c);
    }
}
