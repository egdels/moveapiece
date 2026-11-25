/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.vision;

/** Drawn pictures of a board like the Pegasus: black frame, thin white line, 8x8 squares. */
final class TestBoards {

    /** The piece recogniser the app ships, as it lies in the repository. */
    static final java.io.File SHIPPED_RECOGNISER =
            new java.io.File("../app/src/main/assets/vision/piece_recogniser.onnx");

    private TestBoards() {}

    /**
     * A picture of a board whose outer corners are {@code board}, on a mottled table.
     *
     * @param frame width of the black frame as a fraction of the board
     * @param farPieces number of dark pieces standing on the far rank and rising over the far edge
     */
    static Photo draw(int width, int height, double[][] board, double frame, int farPieces) {
        int[] rgb = new int[width * height];
        double[] toBoard = inverse(Quads.homography(1, board));
        double line = 0.006; // the white line between frame and squares
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double[] p = Quads.apply(toBoard, x, y);
                int v;
                if (p[0] < 0 || p[1] < 0 || p[0] > 1 || p[1] > 1) {
                    v = 120 + ((x * 7 + y * 13) % 23); // table
                } else {
                    double edge = Math.min(Math.min(p[0], p[1]), Math.min(1 - p[0], 1 - p[1]));
                    if (edge < frame) {
                        v = 25;
                    } else if (edge < frame + line) {
                        v = 235;
                    } else {
                        double inner = 1 - 2 * (frame + line);
                        int col = (int) Math.min(7, (p[0] - frame - line) / inner * 8);
                        int row = (int) Math.min(7, (p[1] - frame - line) / inner * 8);
                        v = (col + row) % 2 == 0 ? 215 : 160;
                    }
                }
                rgb[y * width + x] = (v << 16) | (v << 8) | v;
            }
        }
        // Pieces on the far rank: dark upright bars from their square up over the frame.
        double[] toPhoto = Quads.homography(1, board);
        double inner = 1 - 2 * (frame + line);
        for (int i = 0; i < farPieces; i++) {
            double u = frame + line + inner * (i + 0.5) / 8;
            double[] foot = Quads.apply(toPhoto, u, frame + line + inner * 0.5 / 8);
            double[] next = Quads.apply(toPhoto, u + inner / 8, frame + line + inner * 0.5 / 8);
            int half = (int) (0.3 * (next[0] - foot[0]));
            int rise = (int) (1.6 * (next[0] - foot[0]));
            for (int y = (int) foot[1] - rise; y <= (int) foot[1]; y++) {
                for (int x = (int) foot[0] - half; x <= (int) foot[0] + half; x++) {
                    if (x >= 0 && y >= 0 && x < width && y < height) {
                        rgb[y * width + x] = 0x202020;
                    }
                }
            }
        }
        return new Photo(width, height, rgb);
    }

    /** The corners of the 8x8 squares of a board drawn with {@link #draw}. */
    static double[][] playingArea(double[][] board, double frame) {
        return Quads.inset(board, frame + 0.006);
    }

    /** The corners of a square lying on a table, seen from in front at {@code degrees} above it. */
    static double[][] squareSeenAtAnAngle(int width, int height, double degrees) {
        return rectangleSeenAtAnAngle(width, height, degrees, 1.0);
    }

    /** As {@link #squareSeenAtAnAngle}, for a rectangle {@code depth} times as deep as wide. */
    static double[][] rectangleSeenAtAnAngle(int width, int height, double degrees, double depth) {
        double focal = BoardFrameFinder.FOCAL * Math.hypot(width, height);
        double tilt = Math.toRadians(degrees);
        double distance = 2.6; // camera to the middle of the board, in board widths
        double[][] corners = {
            {-0.5, depth / 2}, {0.5, depth / 2}, {0.5, -depth / 2}, {-0.5, -depth / 2}
        };
        double[][] out = new double[4][2];
        for (int i = 0; i < 4; i++) {
            double x = corners[i][0];
            double along = corners[i][1]; // away from the camera
            // Camera looks down at the board centre; picture y grows downwards.
            double yc = -along * Math.sin(tilt);
            double zc = distance + along * Math.cos(tilt);
            out[i][0] = width / 2.0 + focal * x / zc;
            out[i][1] = height / 2.0 + focal * yc / zc;
        }
        return out;
    }

    private static double[] inverse(double[] m) {
        double a = m[0];
        double b = m[1];
        double c = m[2];
        double d = m[3];
        double e = m[4];
        double f = m[5];
        double g = m[6];
        double h = m[7];
        double i = m[8];
        double det = a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g);
        return new double[] {
            (e * i - f * h) / det, (c * h - b * i) / det, (b * f - c * e) / det,
            (f * g - d * i) / det, (a * i - c * g) / det, (c * d - a * f) / det,
            (d * h - e * g) / det, (b * g - a * h) / det, (a * e - b * d) / det
        };
    }
}
