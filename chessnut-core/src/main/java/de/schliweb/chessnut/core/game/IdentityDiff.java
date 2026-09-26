/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.chessnut.core.game;

import de.schliweb.pegasus.core.protocol.BoardState;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Square-by-square difference between an expected and a physical {@link BoardState}, piece identity
 * included. {@link #missing()} lists squares that should hold a piece but are empty, {@link
 * #placed()} squares that hold a piece they should not (either expected empty, or a different piece
 * than expected). Both in {@link BoardState} numbering, ascending.
 */
public final class IdentityDiff {

    private final List<Integer> missing;
    private final List<Integer> placed;

    private IdentityDiff(List<Integer> missing, List<Integer> placed) {
        this.missing = Collections.unmodifiableList(missing);
        this.placed = Collections.unmodifiableList(placed);
    }

    public static IdentityDiff between(BoardState expected, BoardState physical) {
        List<Integer> missing = new ArrayList<>();
        List<Integer> placed = new ArrayList<>();
        for (int square = 0; square < BoardState.SQUARE_COUNT; square++) {
            int want = expected.pieceCodeAt(square);
            int have = physical.pieceCodeAt(square);
            if (want == have) {
                continue;
            }
            if (!physical.isOccupied(square)) {
                missing.add(square);
            } else {
                placed.add(square);
            }
        }
        return new IdentityDiff(missing, placed);
    }

    /** Expected a piece, physically empty. */
    public List<Integer> missing() {
        return missing;
    }

    /** Physically holds a piece that does not belong there (wrong piece or expected empty). */
    public List<Integer> placed() {
        return placed;
    }

    /** All differing squares, missing first then placed: what the LEDs should show. */
    public List<Integer> squares() {
        List<Integer> all = new ArrayList<>(missing.size() + placed.size());
        all.addAll(missing);
        all.addAll(placed);
        return all;
    }

    public boolean isEmpty() {
        return missing.isEmpty() && placed.isEmpty();
    }

    public int size() {
        return missing.size() + placed.size();
    }

    /** e.g. "missing: e2 | placed: e4". */
    @Override
    public String toString() {
        return "missing: " + names(missing) + " | placed: " + names(placed);
    }

    private static String names(List<Integer> squares) {
        if (squares.isEmpty()) {
            return "-";
        }
        StringBuilder sb = new StringBuilder();
        for (int square : squares) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(BoardState.squareName(square));
        }
        return sb.toString();
    }
}
