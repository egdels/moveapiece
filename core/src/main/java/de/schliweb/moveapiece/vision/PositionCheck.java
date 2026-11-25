/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.vision;

/** Tells whether a board in a photo can be showing a given position, from its occupied squares. */
public final class PositionCheck {

    private PositionCheck() {}

    /**
     * In how many squares the board looks differently occupied than the position, with White on the
     * side of the photo where it fits best.
     *
     * @param seen which squares look occupied, as {@link BoardPhotoRecognizer#seenOccupied} gives
     *     them: {@code [row][col]} as seen in the photo
     * @param placement the position as the piece placement of a FEN
     */
    public static int squaresThatDiffer(boolean[][] seen, String placement) {
        // The position as seen from White's side, then turned by a quarter three times.
        boolean[][] expected = new boolean[8][8];
        String[] rows = placement.split("/");
        for (int row = 0; row < 8; row++) {
            int col = 0;
            for (char c : rows[row].toCharArray()) {
                if (Character.isDigit(c)) {
                    col += c - '0';
                } else {
                    expected[row][col++] = true;
                }
            }
        }
        int best = 64;
        for (int turn = 0; turn < 4; turn++) {
            int differ = 0;
            boolean[][] turned = new boolean[8][8];
            for (int row = 0; row < 8; row++) {
                for (int col = 0; col < 8; col++) {
                    differ += seen[row][col] != expected[row][col] ? 1 : 0;
                    turned[col][7 - row] = expected[row][col];
                }
            }
            best = Math.min(best, differ);
            expected = turned;
        }
        return best;
    }
}
