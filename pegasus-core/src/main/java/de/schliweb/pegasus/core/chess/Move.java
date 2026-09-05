/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.pegasus.core.chess;

import de.schliweb.pegasus.core.protocol.BoardState;
import java.util.Objects;

/**
 * Immutable chess move in from/to(/promotion) form.
 *
 * <p>Squares use the DGT numbering shared with {@link BoardState}: index 0 = a8 … 63 = h1. The
 * canonical textual representation is UCI ("e2e4", "e7e8q", castling as king move "e1g1").
 */
public final class Move {

    private final int from;
    private final int to;
    private final PieceType promotion;

    public Move(int from, int to) {
        this(from, to, null);
    }

    public Move(int from, int to, PieceType promotion) {
        checkSquare(from);
        checkSquare(to);
        if (promotion == PieceType.PAWN || promotion == PieceType.KING) {
            throw new IllegalArgumentException("Invalid promotion piece: " + promotion);
        }
        this.from = from;
        this.to = to;
        this.promotion = promotion;
    }

    /** Origin square index (0 = a8 … 63 = h1). */
    public int from() {
        return from;
    }

    /** Destination square index (0 = a8 … 63 = h1). */
    public int to() {
        return to;
    }

    /** Promotion piece type or {@code null} for non-promotion moves. */
    public PieceType promotion() {
        return promotion;
    }

    /** Canonical UCI string, e.g. "e2e4", "e7e8q", "e1g1". */
    public String uci() {
        String base = BoardState.squareName(from) + BoardState.squareName(to);
        return promotion == null ? base : base + promotion.letter();
    }

    /** Parses "e2e4" / "e7e8q" style UCI strings. */
    public static Move fromUci(String uci) {
        if (uci == null || (uci.length() != 4 && uci.length() != 5)) {
            throw new IllegalArgumentException("Invalid UCI move: " + uci);
        }
        int from = BoardState.squareIndex(uci.substring(0, 2));
        int to = BoardState.squareIndex(uci.substring(2, 4));
        PieceType promotion = uci.length() == 5 ? PieceType.fromLetter(uci.charAt(4)) : null;
        return new Move(from, to, promotion);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Move)) {
            return false;
        }
        Move other = (Move) o;
        return from == other.from && to == other.to && promotion == other.promotion;
    }

    @Override
    public int hashCode() {
        return Objects.hash(from, to, promotion);
    }

    @Override
    public String toString() {
        return uci();
    }

    private static void checkSquare(int square) {
        if (square < 0 || square >= BoardState.SQUARE_COUNT) {
            throw new IllegalArgumentException("Square index out of range: " + square);
        }
    }
}
