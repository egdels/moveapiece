/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.vision;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import de.schliweb.moveapiece.vision.PositionDecoder.WhiteSide;
import java.util.Random;
import org.junit.Test;

public class PositionDecoderTest {

    private static final String START = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR";
    private static final String MIDDLEGAME =
            "r1bq1rk1/pp2ppbp/2np1np1/8/3NP3/2N1BP2/PPPQ2PP/R3KB1R";

    @Test
    public void findsWhereWhiteSitsFromTheOccupancy() {
        int[][] board = board(MIDDLEGAME);
        for (WhiteSide side : WhiteSide.values()) {
            double[][] seen = seen(board, side);
            PositionDecoder.Orientation found = PositionDecoder.whiteSide(seen, occupancy(board));
            assertEquals(side, found.side);
            assertFalse(found.ambiguous);
            assertEquals(
                    MIDDLEGAME, PositionDecoder.decode(seen, found.side, occupancy(board)).fen());
        }
    }

    @Test
    public void inTheStartingPositionTheColoursDecideAndThatIsReported() {
        int[][] board = board(START);
        for (WhiteSide side : WhiteSide.values()) {
            double[][] seen = seen(board, side);
            PositionDecoder.Orientation found = PositionDecoder.whiteSide(seen, occupancy(board));
            assertEquals(side, found.side);
            assertTrue(found.ambiguous);
        }
    }

    @Test
    public void emptySquaresComeFromTheBoardNotFromThePhoto() {
        int[][] board = board(MIDDLEGAME);
        double[][] seen = seen(board, WhiteSide.BOTTOM);
        // The recogniser is sure there is a white queen on the empty square e5.
        int[] cell = WhiteSide.BOTTOM.viewCell(4, 4);
        seen[cell[0] * 8 + cell[1]] = certain(5);
        assertEquals(
                MIDDLEGAME, PositionDecoder.decode(seen, WhiteSide.BOTTOM, occupancy(board)).fen());
    }

    @Test
    public void theUsualPiecesSortOutLookAlikes() {
        int[][] board = board(START);
        double[][] seen = seen(board, WhiteSide.BOTTOM);
        // Both white rooks look a little more like queens than like rooks; square by square that
        // would be three white queens.
        seen[cellIndex(0, 0)] = split(5, 0.55, 4, 0.45);
        seen[cellIndex(7, 0)] = split(5, 0.55, 4, 0.45);
        PositionDecoder.Position position =
                PositionDecoder.decode(seen, WhiteSide.BOTTOM, occupancy(board));
        assertEquals(START, position.fen());
        assertTrue(position.confidence[0][0] < 0.9);
        assertEquals(1.0, position.confidence[3][3], 0.0); // an empty square
    }

    @Test
    public void pawnsDoNotStandOnTheBackRanks() {
        int[][] board = board("4k3/8/8/8/8/8/8/4K2R");
        double[][] seen = seen(board, WhiteSide.BOTTOM);
        seen[cellIndex(7, 0)] = split(1, 0.7, 4, 0.3); // the rook on h1 looks like a pawn
        assertEquals(
                "4k3/8/8/8/8/8/8/4K2R",
                PositionDecoder.decode(seen, WhiteSide.BOTTOM, occupancy(board)).fen());
    }

    @Test
    public void aConfirmedPlacementBecomesAPositionTheGameCanLoad() throws Exception {
        assertEquals(START + " w KQkq - 0 1", BoardPhotoRecognizer.fen(START, true));
        assertEquals(
                "4k3/8/8/8/8/8/8/4K2R b K - 0 1",
                BoardPhotoRecognizer.fen("4k3/8/8/8/8/8/8/4K2R", false));
    }

    @Test
    public void aPlacementWithoutBothKingsIsRefused() {
        try {
            BoardPhotoRecognizer.fen("8/8/8/8/8/8/8/4K2R", true);
            org.junit.Assert.fail("accepted a position without a black king");
        } catch (de.schliweb.chessnut.core.game.InvalidPositionException e) {
            assertEquals(
                    de.schliweb.chessnut.core.game.InvalidPositionException.Reason.KINGS,
                    e.reason());
        }
    }

    @Test
    public void assignmentIsTheCheapestOne() {
        Random random = new Random(1);
        for (int round = 0; round < 200; round++) {
            double[][] cost = new double[4][6];
            for (double[] row : cost) {
                for (int j = 0; j < 6; j++) {
                    row[j] = random.nextDouble();
                }
            }
            int[] columns = PositionDecoder.assign(cost);
            double total = 0;
            boolean[] used = new boolean[6];
            for (int i = 0; i < 4; i++) {
                assertFalse(used[columns[i]]);
                used[columns[i]] = true;
                total += cost[i][columns[i]];
            }
            assertEquals(cheapest(cost, 0, new boolean[6]), total, 1e-9);
        }
    }

    private static double cheapest(double[][] cost, int row, boolean[] used) {
        if (row == cost.length) {
            return 0;
        }
        double best = Double.MAX_VALUE;
        for (int j = 0; j < used.length; j++) {
            if (!used[j]) {
                used[j] = true;
                best = Math.min(best, cost[row][j] + cheapest(cost, row + 1, used));
                used[j] = false;
            }
        }
        return best;
    }

    private static int cellIndex(int file, int rank) {
        int[] cell = WhiteSide.BOTTOM.viewCell(file, rank);
        return cell[0] * 8 + cell[1];
    }

    static int[][] board(String placement) {
        int[][] board = new int[8][8];
        String[] rows = placement.split("/");
        for (int i = 0; i < 8; i++) {
            int file = 0;
            for (char c : rows[i].toCharArray()) {
                if (Character.isDigit(c)) {
                    file += c - '0';
                } else {
                    board[7 - i][file++] = PositionDecoder.SYMBOLS.indexOf(c);
                }
            }
        }
        return board;
    }

    static boolean[][] occupancy(int[][] board) {
        boolean[][] occupied = new boolean[8][8];
        for (int rank = 0; rank < 8; rank++) {
            for (int file = 0; file < 8; file++) {
                occupied[rank][file] = board[rank][file] != 0;
            }
        }
        return occupied;
    }

    /** What a good recogniser sees when White sits at {@code side}: 64 windows in view order. */
    private static double[][] seen(int[][] board, WhiteSide side) {
        double[][] seen = new double[64][];
        for (int rank = 0; rank < 8; rank++) {
            for (int file = 0; file < 8; file++) {
                int[] cell = side.viewCell(file, rank);
                seen[cell[0] * 8 + cell[1]] = certain(board[rank][file]);
            }
        }
        return seen;
    }

    private static double[] certain(int cls) {
        return split(cls, 0.94, cls, 0.0);
    }

    private static double[] split(int first, double p, int second, double q) {
        double[] out = new double[PositionDecoder.CLASSES];
        java.util.Arrays.fill(out, (1 - p - q) / (PositionDecoder.CLASSES - 2));
        out[first] = p;
        if (second != first) {
            out[second] = q;
        }
        return out;
    }

    @Test
    public void photosFromAllSidesAreLaidOnEachOther() {
        // A window that is sure of one class per square: the class is the number of the square.
        for (PositionDecoder.WhiteSide side : PositionDecoder.WhiteSide.values()) {
            double[][] view = new double[64][];
            for (int rank = 0; rank < 8; rank++) {
                for (int file = 0; file < 8; file++) {
                    int[] cell = side.viewCell(file, rank);
                    view[cell[0] * 8 + cell[1]] = new double[] {rank * 8 + file};
                }
            }
            double[][] turned = PositionDecoder.seenFromWhite(view, side);
            for (int rank = 0; rank < 8; rank++) {
                for (int file = 0; file < 8; file++) {
                    assertEquals(rank * 8 + file, turned[(7 - rank) * 8 + file][0], 0.0);
                }
            }
        }
    }
}
