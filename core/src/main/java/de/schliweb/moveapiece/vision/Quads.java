/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.vision;

import java.util.Arrays;

/**
 * Quadrilaterals as four points {@code double[4][2]} and the projective maps between them.
 *
 * <p>Corner order of a canonical quad: top-left, top-right, bottom-right, bottom-left - geometric,
 * as seen in the photo (sorted by angle around the centroid, starting at the smallest x+y). It says
 * nothing about where a1 is; that follows from the occupancy the board reports.
 */
final class Quads {

    private Quads() {}

    /** The quad as top-left, top-right, bottom-right, bottom-left. */
    static double[][] canonical(double[][] quad) {
        double cx = 0;
        double cy = 0;
        for (double[] p : quad) {
            cx += p[0] / 4;
            cy += p[1] / 4;
        }
        Integer[] order = {0, 1, 2, 3};
        final double fcx = cx;
        final double fcy = cy;
        Arrays.sort(
                order,
                (i, j) -> {
                    int byAngle =
                            Double.compare(
                                    Math.atan2(quad[i][1] - fcy, quad[i][0] - fcx),
                                    Math.atan2(quad[j][1] - fcy, quad[j][0] - fcx));
                    return byAngle != 0 ? byAngle : Integer.compare(i, j);
                });
        int start = 0;
        for (int k = 1; k < 4; k++) {
            if (quad[order[k]][0] + quad[order[k]][1]
                    < quad[order[start]][0] + quad[order[start]][1]) {
                start = k;
            }
        }
        double[][] out = new double[4][];
        for (int k = 0; k < 4; k++) {
            out[k] = quad[order[(start + k) % 4]].clone();
        }
        return out;
    }

    static double area(double[][] quad) {
        double s = 0;
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4;
            s += quad[i][0] * quad[j][1] - quad[j][0] * quad[i][1];
        }
        return Math.abs(0.5 * s);
    }

    static boolean isConvex(double[][] quad) {
        int sign = 0;
        for (int i = 0; i < 4; i++) {
            double[] a = quad[i];
            double[] b = quad[(i + 1) % 4];
            double[] c = quad[(i + 2) % 4];
            double cross = (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]);
            if (Math.abs(cross) <= 1e-9) {
                return false;
            }
            int s = cross > 0 ? 1 : -1;
            if (sign == 0) {
                sign = s;
            } else if (s != sign) {
                return false;
            }
        }
        return true;
    }

    static double distance(double[] a, double[] b) {
        return Math.hypot(b[0] - a[0], b[1] - a[1]);
    }

    /** Shortest and longest side of the quad. */
    static double[] sideRange(double[][] quad) {
        double min = Double.MAX_VALUE;
        double max = 0;
        for (int i = 0; i < 4; i++) {
            double d = distance(quad[i], quad[(i + 1) % 4]);
            min = Math.min(min, d);
            max = Math.max(max, d);
        }
        return new double[] {min, max};
    }

    /**
     * The projective map (row-major 3x3) that takes the corners (0,0), (size,0), (size,size),
     * (0,size) to the corners of {@code quad}.
     */
    static double[] homography(double size, double[][] quad) {
        double[][] src = {{0, 0}, {size, 0}, {size, size}, {0, size}};
        double[][] m = new double[8][9];
        for (int i = 0; i < 4; i++) {
            double x = src[i][0];
            double y = src[i][1];
            double u = quad[i][0];
            double v = quad[i][1];
            m[2 * i] = new double[] {x, y, 1, 0, 0, 0, -u * x, -u * y, u};
            m[2 * i + 1] = new double[] {0, 0, 0, x, y, 1, -v * x, -v * y, v};
        }
        // Gaussian elimination with partial pivoting on the 8x8 system.
        for (int col = 0; col < 8; col++) {
            int pivot = col;
            for (int row = col + 1; row < 8; row++) {
                if (Math.abs(m[row][col]) > Math.abs(m[pivot][col])) {
                    pivot = row;
                }
            }
            double[] swap = m[col];
            m[col] = m[pivot];
            m[pivot] = swap;
            double p = m[col][col];
            if (Math.abs(p) < 1e-12) {
                return null;
            }
            for (int row = 0; row < 8; row++) {
                if (row != col) {
                    double factor = m[row][col] / p;
                    for (int k = col; k < 9; k++) {
                        m[row][k] -= factor * m[col][k];
                    }
                }
            }
        }
        double[] h = new double[9];
        for (int i = 0; i < 8; i++) {
            h[i] = m[i][8] / m[i][i];
        }
        h[8] = 1;
        return h;
    }

    static double[] apply(double[] h, double x, double y) {
        double w = h[6] * x + h[7] * y + h[8];
        return new double[] {(h[0] * x + h[1] * y + h[2]) / w, (h[3] * x + h[4] * y + h[5]) / w};
    }

    /**
     * Moves every side of the quad inwards by {@code margin} of the side length, in perspective.
     */
    static double[][] inset(double[][] quad, double margin) {
        double[] h = homography(1, quad);
        return new double[][] {
            apply(h, margin, margin),
            apply(h, 1 - margin, margin),
            apply(h, 1 - margin, 1 - margin),
            apply(h, margin, 1 - margin)
        };
    }

    /**
     * 0 if the quad can be the picture of a square taken with focal length {@code focal} (in
     * pixels, principal point in the middle of a {@code width} x {@code height} picture), larger
     * the less it can.
     *
     * <p>With the camera matrix K = diag(f, f, 1) about the image centre, K^-1 H maps the plane of
     * the square into camera coordinates; for a square its first two columns have the same length
     * and are perpendicular. The result is |log(length ratio)| + |cosine of the angle|.
     */
    static double offSquare(double[][] quad, double width, double height, double focal) {
        double[] h = homography(1, quad);
        if (h == null) {
            return Double.MAX_VALUE;
        }
        double[] a = cameraColumn(h, 0, width, height, focal);
        double[] b = cameraColumn(h, 1, width, height, focal);
        double na = Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]);
        double nb = Math.sqrt(b[0] * b[0] + b[1] * b[1] + b[2] * b[2]);
        double dot = a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
        return Math.abs(Math.log(na / nb)) + Math.abs(dot) / (na * nb);
    }

    /** Column {@code col} of K^-1 H. */
    static double[] cameraColumn(double[] h, int col, double width, double height, double focal) {
        double x = h[col];
        double y = h[3 + col];
        double z = h[6 + col];
        return new double[] {(x - width / 2 * z) / focal, (y - height / 2 * z) / focal, z};
    }
}
