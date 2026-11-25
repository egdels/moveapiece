/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.vision;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PositionCheckTest {

    private static final String START = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR";
    private static final String AFTER_E4 = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR";

    /** The occupied squares of a position as a photo taken from White's side shows them. */
    private static boolean[][] seen(String placement) {
        boolean[][] out = new boolean[8][8];
        String[] rows = placement.split("/");
        for (int row = 0; row < 8; row++) {
            int col = 0;
            for (char c : rows[row].toCharArray()) {
                if (Character.isDigit(c)) {
                    col += c - '0';
                } else {
                    out[row][col++] = true;
                }
            }
        }
        return out;
    }

    private static boolean[][] quarterTurn(boolean[][] grid) {
        boolean[][] out = new boolean[8][8];
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                out[col][7 - row] = grid[row][col];
            }
        }
        return out;
    }

    @Test
    public void thePositionItselfDiffersNowhere() {
        assertEquals(0, PositionCheck.squaresThatDiffer(seen(AFTER_E4), AFTER_E4));
    }

    @Test
    public void aMoveChangesTwoSquares() {
        assertEquals(2, PositionCheck.squaresThatDiffer(seen(AFTER_E4), START));
    }

    @Test
    public void itDoesNotMatterFromWhichSideThePhotoIsTaken() {
        boolean[][] grid = seen(AFTER_E4);
        for (int turn = 0; turn < 4; turn++) {
            assertEquals(0, PositionCheck.squaresThatDiffer(grid, AFTER_E4));
            grid = quarterTurn(grid);
        }
    }
}
