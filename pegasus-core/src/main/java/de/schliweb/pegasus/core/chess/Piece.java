/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.pegasus.core.chess;

/** A logical chess piece (color + type). Never derived from Pegasus hardware. */
public enum Piece {
    WHITE_PAWN(PieceColor.WHITE, PieceType.PAWN),
    WHITE_KNIGHT(PieceColor.WHITE, PieceType.KNIGHT),
    WHITE_BISHOP(PieceColor.WHITE, PieceType.BISHOP),
    WHITE_ROOK(PieceColor.WHITE, PieceType.ROOK),
    WHITE_QUEEN(PieceColor.WHITE, PieceType.QUEEN),
    WHITE_KING(PieceColor.WHITE, PieceType.KING),
    BLACK_PAWN(PieceColor.BLACK, PieceType.PAWN),
    BLACK_KNIGHT(PieceColor.BLACK, PieceType.KNIGHT),
    BLACK_BISHOP(PieceColor.BLACK, PieceType.BISHOP),
    BLACK_ROOK(PieceColor.BLACK, PieceType.ROOK),
    BLACK_QUEEN(PieceColor.BLACK, PieceType.QUEEN),
    BLACK_KING(PieceColor.BLACK, PieceType.KING);

    private final PieceColor color;
    private final PieceType type;

    Piece(PieceColor color, PieceType type) {
        this.color = color;
        this.type = type;
    }

    public PieceColor color() {
        return color;
    }

    public PieceType type() {
        return type;
    }

    /** FEN character: uppercase for white, lowercase for black. */
    public char fenChar() {
        char c = type.letter();
        return color == PieceColor.WHITE ? Character.toUpperCase(c) : c;
    }

    public static Piece of(PieceColor color, PieceType type) {
        for (Piece piece : values()) {
            if (piece.color == color && piece.type == type) {
                return piece;
            }
        }
        throw new IllegalArgumentException("No piece for " + color + " " + type);
    }

    public static Piece fromFenChar(char c) {
        PieceColor color = Character.isUpperCase(c) ? PieceColor.WHITE : PieceColor.BLACK;
        return of(color, PieceType.fromLetter(c));
    }
}
