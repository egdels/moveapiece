/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.pegasus.core.movedetect;

import de.schliweb.pegasus.core.protocol.BoardState;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Difference between the expected occupancy (projection of the logical position) and the actual
 * physical occupancy.
 */
public final class BoardMismatch {

    private final List<Integer> missingOccupied;
    private final List<Integer> unexpectedOccupied;

    private BoardMismatch(List<Integer> missingOccupied, List<Integer> unexpectedOccupied) {
        this.missingOccupied = Collections.unmodifiableList(missingOccupied);
        this.unexpectedOccupied = Collections.unmodifiableList(unexpectedOccupied);
    }

    /** Computes the diff between expected and actual occupancy. */
    public static BoardMismatch between(BoardState expected, BoardState actual) {
        List<Integer> missing = new ArrayList<>();
        List<Integer> unexpected = new ArrayList<>();
        for (int square = 0; square < BoardState.SQUARE_COUNT; square++) {
            boolean expectedOccupied = expected.isOccupied(square);
            boolean actualOccupied = actual.isOccupied(square);
            if (expectedOccupied && !actualOccupied) {
                missing.add(square);
            } else if (!expectedOccupied && actualOccupied) {
                unexpected.add(square);
            }
        }
        return new BoardMismatch(missing, unexpected);
    }

    /** Squares expected occupied but physically empty (DGT indices). */
    public List<Integer> missingOccupied() {
        return missingOccupied;
    }

    /** Squares expected empty but physically occupied (DGT indices). */
    public List<Integer> unexpectedOccupied() {
        return unexpectedOccupied;
    }

    public boolean isEmpty() {
        return missingOccupied.isEmpty() && unexpectedOccupied.isEmpty();
    }

    public int differenceCount() {
        return missingOccupied.size() + unexpectedOccupied.size();
    }

    /** e.g. "missing: e2 g1 | unexpected: a4". */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("missing:");
        for (int square : missingOccupied) {
            sb.append(' ').append(BoardState.squareName(square));
        }
        sb.append(" | unexpected:");
        for (int square : unexpectedOccupied) {
            sb.append(' ').append(BoardState.squareName(square));
        }
        return sb.toString();
    }
}
