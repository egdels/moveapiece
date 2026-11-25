/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.vision;

/**
 * Turns what the recogniser sees on 64 squares, together with the occupancy the board reports, into
 * a position.
 *
 * <p>The Pegasus reports which squares are occupied, reliably, but not by what. That turns the task
 * from "13 classes on 64 squares" into "12 classes on the occupied squares", and it also tells how
 * the board lies in the photo: of the four possible places for White, the right one is the one
 * where the squares the recogniser sees as occupied match the squares the board reports. On the
 * occupied squares each side gets at most the pieces a game starts with, handed out so that it fits
 * all squares at once.
 *
 * <p>Classes are numbered as the recogniser outputs them: 0 empty, 1-6 white pawn, knight, bishop,
 * rook, queen, king, 7-12 the same in black. Boards are indexed {@code [rank][file]} with rank 0
 * the first rank; views {@code [row][col]} as the board lies in the photo, far side first.
 */
final class PositionDecoder {

    static final int CLASSES = 13;
    static final String SYMBOLS = ".PNBRQKpnbrqk";

    /** The pieces of one side in a normal game, as kinds 1..6 (pawn .. king). */
    private static final int[] USUAL_PIECES = {1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 3, 3, 4, 4, 5, 6};

    private PositionDecoder() {}

    /** Where White sits as seen in the photo. */
    enum WhiteSide {
        BOTTOM,
        RIGHT,
        TOP,
        LEFT;

        /** Row and column in the view of the square with 0-based file and rank. */
        int[] viewCell(int file, int rank) {
            switch (this) {
                case BOTTOM:
                    return new int[] {7 - rank, file};
                case TOP:
                    return new int[] {rank, 7 - file};
                case LEFT:
                    return new int[] {file, rank};
                default:
                    return new int[] {7 - file, 7 - rank};
            }
        }
    }

    static final class Orientation {
        final WhiteSide side;

        /** The occupancy could not tell; the colours of the pieces decided. */
        final boolean ambiguous;

        Orientation(WhiteSide side, boolean ambiguous) {
            this.side = side;
            this.ambiguous = ambiguous;
        }
    }

    static final class Position {
        /** Class per square, {@code [rank][file]}. */
        final int[][] board = new int[8][8];

        /** How sure the recogniser is of the piece, per square; 1 on empty squares. */
        final double[][] confidence = new double[8][8];

        /** The piece placement as in a FEN. */
        String fen() {
            StringBuilder out = new StringBuilder();
            for (int rank = 7; rank >= 0; rank--) {
                int gap = 0;
                for (int file = 0; file < 8; file++) {
                    if (board[rank][file] == 0) {
                        gap++;
                    } else {
                        if (gap > 0) {
                            out.append(gap);
                            gap = 0;
                        }
                        out.append(SYMBOLS.charAt(board[rank][file]));
                    }
                }
                if (gap > 0) {
                    out.append(gap);
                }
                if (rank > 0) {
                    out.append('/');
                }
            }
            return out.toString();
        }
    }

    /** The probabilities in view order, rearranged to {@code [rank][file][class]}. */
    private static double[][][] toBoard(double[][] viewProbabilities, WhiteSide side) {
        double[][][] board = new double[8][8][];
        for (int rank = 0; rank < 8; rank++) {
            for (int file = 0; file < 8; file++) {
                int[] cell = side.viewCell(file, rank);
                board[rank][file] = viewProbabilities[cell[0] * 8 + cell[1]];
            }
        }
        return board;
    }

    /**
     * The probabilities of a photo rearranged as if White sat at the bottom of it, so that several
     * photos of one position, taken from different sides, can be laid on each other.
     */
    static double[][] seenFromWhite(double[][] viewProbabilities, WhiteSide side) {
        double[][] out = new double[64][];
        for (int rank = 0; rank < 8; rank++) {
            for (int file = 0; file < 8; file++) {
                int[] from = side.viewCell(file, rank);
                int[] to = WhiteSide.BOTTOM.viewCell(file, rank);
                out[to[0] * 8 + to[1]] = viewProbabilities[from[0] * 8 + from[1]];
            }
        }
        return out;
    }

    /**
     * Finds where White sits by matching the occupancy seen in the photo against the reported one.
     *
     * <p>The occupancy cannot tell a position from its half turn when the occupancy pattern looks
     * the same after it (the starting position, for one). Such ties are broken by colour: White's
     * pieces are, on average, nearer to the first rank than Black's.
     *
     * @param viewProbabilities 64 windows in view order, 13 class probabilities each
     * @param occupancy {@code [rank][file]}, as the board reports it
     */
    static Orientation whiteSide(double[][] viewProbabilities, boolean[][] occupancy) {
        WhiteSide[] sides = WhiteSide.values();
        double[] fit = new double[sides.length];
        double[] colour = new double[sides.length];
        double best = -Double.MAX_VALUE;
        for (int s = 0; s < sides.length; s++) {
            double[][][] probabilities = toBoard(viewProbabilities, sides[s]);
            for (int rank = 0; rank < 8; rank++) {
                for (int file = 0; file < 8; file++) {
                    double[] p = probabilities[rank][file];
                    double occupied = Math.min(1 - 1e-6, Math.max(1e-6, 1 - p[0]));
                    fit[s] += Math.log(occupancy[rank][file] ? occupied : 1 - occupied);
                    double white = 0;
                    double black = 0;
                    for (int k = 1; k <= 6; k++) {
                        white += p[k];
                        black += p[k + 6];
                    }
                    colour[s] += (white - black) * (3.5 - rank);
                }
            }
            best = Math.max(best, fit[s]);
        }
        int chosen = -1;
        int tied = 0;
        for (int s = 0; s < sides.length; s++) {
            if (best - fit[s] < 1e-3 * Math.max(1.0, Math.abs(best))) {
                tied++;
                if (chosen < 0 || colour[s] > colour[chosen]) {
                    chosen = s;
                }
            }
        }
        return new Orientation(sides[chosen], tied > 1);
    }

    /**
     * The position: empty squares come from the board, and on the occupied ones each side gets at
     * most the pieces a game starts with.
     *
     * <p>Two pieces that look alike from where the photo was taken (rook and queen, say) are often
     * mixed up square by square. Over the whole board there is only one way to hand out one queen
     * and two rooks that fits what is seen on all squares at once; that assignment is taken. A side
     * with more than 16 pieces, or a position after a promotion to a second queen, does not fit
     * this; such squares come out wrong and with low confidence, so the user must be able to
     * correct them.
     */
    static Position decode(double[][] viewProbabilities, WhiteSide side, boolean[][] occupancy) {
        double[][][] probabilities = toBoard(viewProbabilities, side);
        Position position = new Position();
        for (double[] row : position.confidence) {
            java.util.Arrays.fill(row, 1.0);
        }
        for (int colour = 0; colour < 2; colour++) {
            int first = colour == 0 ? 1 : 7;
            int[] ranks = new int[64];
            int[] files = new int[64];
            int n = 0;
            for (int rank = 0; rank < 8; rank++) {
                for (int file = 0; file < 8; file++) {
                    if (!occupancy[rank][file]) {
                        continue;
                    }
                    double white = 0;
                    double black = 0;
                    for (int k = 1; k <= 6; k++) {
                        white += Math.max(1e-9, probabilities[rank][file][k]);
                        black += Math.max(1e-9, probabilities[rank][file][k + 6]);
                    }
                    if ((white >= black) == (colour == 0)) {
                        ranks[n] = rank;
                        files[n] = file;
                        n++;
                    }
                }
            }
            if (n == 0) {
                continue;
            }
            double[][] share = new double[n][6];
            for (int i = 0; i < n; i++) {
                double total = 0;
                for (int k = 0; k < 6; k++) {
                    share[i][k] = Math.max(1e-9, probabilities[ranks[i]][files[i]][first + k]);
                    total += share[i][k];
                }
                for (int k = 0; k < 6; k++) {
                    share[i][k] /= total;
                }
            }
            int[] kinds = new int[n];
            if (n > USUAL_PIECES.length) {
                for (int i = 0; i < n; i++) {
                    for (int k = 1; k < 6; k++) {
                        if (share[i][k] > share[i][kinds[i]]) {
                            kinds[i] = k;
                        }
                    }
                    kinds[i]++;
                }
            } else {
                double[][] cost = new double[n][USUAL_PIECES.length];
                for (int i = 0; i < n; i++) {
                    boolean backRank = ranks[i] == 0 || ranks[i] == 7;
                    for (int j = 0; j < USUAL_PIECES.length; j++) {
                        int kind = USUAL_PIECES[j];
                        // Pawns cannot stand on the first or last rank.
                        cost[i][j] = kind == 1 && backRank ? 1e6 : -Math.log(share[i][kind - 1]);
                    }
                }
                int[] slots = assign(cost);
                for (int i = 0; i < n; i++) {
                    kinds[i] = USUAL_PIECES[slots[i]];
                }
            }
            for (int i = 0; i < n; i++) {
                position.board[ranks[i]][files[i]] = first + kinds[i] - 1;
                position.confidence[ranks[i]][files[i]] = share[i][kinds[i] - 1];
            }
        }
        return position;
    }

    /**
     * Hungarian method: for each row of {@code cost} a different column, at minimal total cost.
     * There must be at least as many columns as rows.
     */
    static int[] assign(double[][] cost) {
        int n = cost.length;
        int m = cost[0].length;
        double[] u = new double[n + 1];
        double[] v = new double[m + 1];
        int[] match = new int[m + 1]; // row matched to each column, 1-based; 0 = none
        int[] way = new int[m + 1];
        for (int i = 1; i <= n; i++) {
            match[0] = i;
            int j0 = 0;
            double[] minv = new double[m + 1];
            java.util.Arrays.fill(minv, Double.MAX_VALUE);
            boolean[] used = new boolean[m + 1];
            do {
                used[j0] = true;
                int i0 = match[j0];
                double delta = Double.MAX_VALUE;
                int j1 = 0;
                for (int j = 1; j <= m; j++) {
                    if (!used[j]) {
                        double cur = cost[i0 - 1][j - 1] - u[i0] - v[j];
                        if (cur < minv[j]) {
                            minv[j] = cur;
                            way[j] = j0;
                        }
                        if (minv[j] < delta) {
                            delta = minv[j];
                            j1 = j;
                        }
                    }
                }
                for (int j = 0; j <= m; j++) {
                    if (used[j]) {
                        u[match[j]] += delta;
                        v[j] -= delta;
                    } else {
                        minv[j] -= delta;
                    }
                }
                j0 = j1;
            } while (match[j0] != 0);
            do {
                int j1 = way[j0];
                match[j0] = match[j1];
                j0 = j1;
            } while (j0 != 0);
        }
        int[] columns = new int[n];
        for (int j = 1; j <= m; j++) {
            if (match[j] != 0) {
                columns[match[j] - 1] = j - 1;
            }
        }
        return columns;
    }
}
