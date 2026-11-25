/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.vision;

/**
 * Cuts one window per square out of a photo, as the piece recogniser expects it.
 *
 * <p>Nothing is warped: the window around a square is a plain piece of the photo, 2.6 squares
 * across, so a piece looks in it the way it looks in any photo of it. The window is turned so that
 * the piece stands upright, with its foot always at the same place. Which way a piece leans in the
 * photo follows from the four corners of the playing area: they give the position of the camera
 * relative to the board (with an assumed focal length), and with it the direction in which "up from
 * the board" points at every square.
 *
 * <p>Windows are in view order: row by row as the board lies in the photo, far side first, left to
 * right. This is the Java counterpart of {@code training/piece_windows.py}; the recogniser is
 * trained on windows cut there, so the geometry has to stay the same.
 */
final class PieceWindows {

    /** Width and height of a window in pixels. */
    static final int SIZE = 224;

    /** Width of a window in squares. */
    private static final double EXTENT = 2.6;

    /** Where the foot of the piece is in the window, as fractions of its size. */
    private static final double FOOT_X = 0.5;

    private static final double FOOT_Y = 0.7;

    /** In squares; only the direction of the lean is taken from it. */
    private static final double PIECE_HEIGHT = 1.5;

    private PieceWindows() {}

    /**
     * The 64 windows as one block of numbers ready for the recogniser: window after window, each as
     * red, green and blue plane of {@link #SIZE} x {@link #SIZE} values between 0 and 1.
     */
    static float[] cut(Photo photo, double[][] corners) {
        double[] h = Quads.homography(8, corners);
        double focal = BoardFrameFinder.FOCAL * Math.hypot(photo.width, photo.height);
        double[] r1 = Quads.cameraColumn(h, 0, photo.width, photo.height, focal);
        double[] r2 = Quads.cameraColumn(h, 1, photo.width, photo.height, focal);
        double[] t = Quads.cameraColumn(h, 2, photo.width, photo.height, focal);
        double unit = 2.0 / (norm(r1) + norm(r2));
        if (t[2] < 0) {
            unit = -unit;
        }
        scale(r1, unit);
        scale(r2, unit);
        scale(t, unit);
        // The board normal that points towards the camera is "up".
        double[] up = {
            r1[1] * r2[2] - r1[2] * r2[1],
            r1[2] * r2[0] - r1[0] * r2[2],
            r1[0] * r2[1] - r1[1] * r2[0]
        };
        scale(up, 1 / norm(up));
        if (up[0] * t[0] + up[1] * t[1] + up[2] * t[2] > 0) {
            scale(up, -1);
        }

        // One shrink for the whole photo, so that a square is about as large as in the windows.
        double middle = squareLength(h, 3.5, 3.5);
        double shrink = Math.min(1.0, 2.0 * (SIZE / EXTENT) / middle);
        Planes planes = Planes.shrunk(photo, shrink);

        float[] out = new float[64 * 3 * SIZE * SIZE];
        // The windows do not depend on each other; they are cut side by side.
        java.util.stream.IntStream.range(0, 64)
                .parallel()
                .forEach(
                        cell -> {
                            int row = cell / 8;
                            int col = cell % 8;
                            double u = col + 0.5;
                            double v = row + 0.5;
                            double[] foot = Quads.apply(h, u, v);
                            double square = squareLength(h, u, v) * shrink;
                            // Where the top of a piece standing here ends up in the photo.
                            double[] base = {
                                r1[0] * u + r2[0] * v + t[0],
                                r1[1] * u + r2[1] * v + t[1],
                                r1[2] * u + r2[2] * v + t[2]
                            };
                            double[] top = {
                                base[0] + up[0] * PIECE_HEIGHT,
                                base[1] + up[1] * PIECE_HEIGHT,
                                base[2] + up[2] * PIECE_HEIGHT
                            };
                            double leanX = top[0] / top[2] - base[0] / base[2];
                            double leanY = top[1] / top[2] - base[1] / base[2];
                            double lean = Math.hypot(leanX, leanY) * focal;
                            double ux = lean > 1e-6 ? leanX * focal / lean : 0;
                            double uy = lean > 1e-6 ? leanY * focal / lean : -1;

                            double side = EXTENT * square;
                            // The foot sits at (FOOT_X, FOOT_Y) of the window, whose "up" is the
                            // lean.
                            double centreX =
                                    foot[0] * shrink
                                            + ux * (FOOT_Y - 0.5) * side
                                            + uy * (FOOT_X - 0.5) * side;
                            double centreY =
                                    foot[1] * shrink
                                            + uy * (FOOT_Y - 0.5) * side
                                            - ux * (FOOT_X - 0.5) * side;
                            planes.window(
                                    centreX,
                                    centreY,
                                    side,
                                    Math.atan2(ux, -uy),
                                    out,
                                    (row * 8 + col) * 3 * SIZE * SIZE);
                        });
        return out;
    }

    /** Length of one square in the photo around board point (u, v). */
    private static double squareLength(double[] h, double u, double v) {
        double[] left = Quads.apply(h, u - 0.5, v);
        double[] right = Quads.apply(h, u + 0.5, v);
        double[] above = Quads.apply(h, u, v - 0.5);
        double[] below = Quads.apply(h, u, v + 0.5);
        return (Quads.distance(left, right) + Quads.distance(above, below)) / 2;
    }

    private static double norm(double[] v) {
        return Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
    }

    private static void scale(double[] v, double factor) {
        v[0] *= factor;
        v[1] *= factor;
        v[2] *= factor;
    }

    /** A colour picture as three planes of values 0..255. */
    private static final class Planes {
        final int width;
        final int height;
        final float[][] planes;

        Planes(int width, int height, float[][] planes) {
            this.width = width;
            this.height = height;
            this.planes = planes;
        }

        /** The photo shrunk by {@code factor} (at most 1); every pixel averages what it covers. */
        static Planes shrunk(Photo photo, double factor) {
            int w = photo.width;
            int h = photo.height;
            int dw = Math.max(1, (int) Math.round(w * factor));
            int dh = Math.max(1, (int) Math.round(h * factor));
            double sx = (double) w / dw;
            double sy = (double) h / dh;
            float[][] out = new float[3][dw * dh];
            float[][] rows = new float[3][dw];
            double[] sums = new double[3];
            // Rows of the photo are squeezed horizontally and added to the output rows they cover.
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < dw; x++) {
                    double x0 = x * sx;
                    double x1 = x0 + sx;
                    int last = Math.min(w - 1, (int) Math.ceil(x1) - 1);
                    sums[0] = 0;
                    sums[1] = 0;
                    sums[2] = 0;
                    for (int i = (int) x0; i <= last; i++) {
                        double weight = Math.min(x1, i + 1) - Math.max(x0, i);
                        int rgb = photo.rgb[y * w + i];
                        sums[0] += weight * ((rgb >> 16) & 0xFF);
                        sums[1] += weight * ((rgb >> 8) & 0xFF);
                        sums[2] += weight * (rgb & 0xFF);
                    }
                    rows[0][x] = (float) (sums[0] / sx);
                    rows[1][x] = (float) (sums[1] / sx);
                    rows[2][x] = (float) (sums[2] / sx);
                }
                int first = Math.min(dh - 1, (int) (y / sy));
                int lastRow = Math.min(dh - 1, (int) ((y + 1) / sy));
                for (int row = first; row <= lastRow; row++) {
                    double weight = Math.min(y + 1, (row + 1) * sy) - Math.max(y, row * sy);
                    if (weight <= 0) {
                        continue;
                    }
                    float share = (float) (weight / sy);
                    for (int c = 0; c < 3; c++) {
                        for (int x = 0; x < dw; x++) {
                            out[c][row * dw + x] += share * rows[c][x];
                        }
                    }
                }
            }
            return new Planes(dw, dh, out);
        }

        /**
         * Writes the window of {@code side} pixels around (cx, cy), turned by {@code angle}
         * (radians, the direction that becomes "up"), into {@code out} from {@code offset}.
         */
        void window(double cx, double cy, double side, double angle, float[] out, int offset) {
            double scale = SIZE / side;
            double alpha = scale * Math.cos(angle);
            double beta = scale * Math.sin(angle);
            double det = alpha * alpha + beta * beta;
            // Where the window is much smaller than its piece of the photo, four samples per pixel.
            int taps = scale < 0.5 ? 2 : 1;
            int plane = SIZE * SIZE;
            for (int j = 0; j < SIZE; j++) {
                for (int i = 0; i < SIZE; i++) {
                    float r = 0;
                    float g = 0;
                    float b = 0;
                    for (int tj = 0; tj < taps; tj++) {
                        for (int ti = 0; ti < taps; ti++) {
                            double dx = i - SIZE / 2.0 + (taps == 1 ? 0 : ti * 0.5 - 0.25);
                            double dy = j - SIZE / 2.0 + (taps == 1 ? 0 : tj * 0.5 - 0.25);
                            double x = cx + (alpha * dx - beta * dy) / det;
                            double y = cy + (beta * dx + alpha * dy) / det;
                            int x0 = (int) Math.floor(x);
                            int y0 = (int) Math.floor(y);
                            float fx = (float) (x - x0);
                            float fy = (float) (y - y0);
                            int xa = clamp(x0, width);
                            int xb = clamp(x0 + 1, width);
                            int ya = clamp(y0, height) * width;
                            int yb = clamp(y0 + 1, height) * width;
                            float w00 = (1 - fx) * (1 - fy);
                            float w10 = fx * (1 - fy);
                            float w01 = (1 - fx) * fy;
                            float w11 = fx * fy;
                            r +=
                                    w00 * planes[0][ya + xa]
                                            + w10 * planes[0][ya + xb]
                                            + w01 * planes[0][yb + xa]
                                            + w11 * planes[0][yb + xb];
                            g +=
                                    w00 * planes[1][ya + xa]
                                            + w10 * planes[1][ya + xb]
                                            + w01 * planes[1][yb + xa]
                                            + w11 * planes[1][yb + xb];
                            b +=
                                    w00 * planes[2][ya + xa]
                                            + w10 * planes[2][ya + xb]
                                            + w01 * planes[2][yb + xa]
                                            + w11 * planes[2][yb + xb];
                        }
                    }
                    float norm = 255f * taps * taps;
                    int p = offset + j * SIZE + i;
                    out[p] = r / norm;
                    out[p + plane] = g / norm;
                    out[p + 2 * plane] = b / norm;
                }
            }
        }

        private static int clamp(int i, int n) {
            return i < 0 ? 0 : (i >= n ? n - 1 : i);
        }
    }
}
