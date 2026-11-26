/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.vision;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Connected regions of a black-and-white picture, their outline and its simplification. */
final class Regions {

    /** Offsets of a 5x5 disc, the shape used to remove thin bridges and specks. */
    private static final int[][] DISC = {
        {0, -2}, {-2, -1}, {-1, -1}, {0, -1}, {1, -1}, {2, -1}, {-2, 0}, {-1, 0}, {0, 0}, {1, 0},
        {2, 0}, {-2, 1}, {-1, 1}, {0, 1}, {1, 1}, {2, 1}, {0, 2}
    };

    private Regions() {}

    static final class Region {
        /** Area enclosed by the outline of the region, holes included, in pixels. */
        final double filledArea;

        /** Convex hull of the region. */
        final double[][] hull;

        Region(double filledArea, double[][] hull) {
            this.filledArea = filledArea;
            this.hull = hull;
        }
    }

    /** Erosion followed by dilation with a 5x5 disc. */
    static boolean[] opened(boolean[] mask, int w, int h) {
        boolean[] eroded = new boolean[mask.length];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                boolean all = true;
                for (int[] d : DISC) {
                    int xx = x + d[0];
                    int yy = y + d[1];
                    if (xx >= 0 && yy >= 0 && xx < w && yy < h && !mask[yy * w + xx]) {
                        all = false;
                        break;
                    }
                }
                eroded[y * w + x] = all;
            }
        }
        boolean[] out = new boolean[mask.length];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (eroded[y * w + x]) {
                    for (int[] d : DISC) {
                        int xx = x + d[0];
                        int yy = y + d[1];
                        if (xx >= 0 && yy >= 0 && xx < w && yy < h) {
                            out[yy * w + xx] = true;
                        }
                    }
                }
            }
        }
        return out;
    }

    /**
     * The 8-connected regions of {@code mask} whose bounding box covers at least {@code minBox}.
     */
    static List<Region> find(boolean[] mask, int w, int h, double minBox) {
        List<Region> regions = new ArrayList<>();
        boolean[] seen = new boolean[mask.length];
        int[] stack = new int[mask.length];
        int[] pixels = new int[mask.length];
        for (int start = 0; start < mask.length; start++) {
            if (!mask[start] || seen[start]) {
                continue;
            }
            int count = 0;
            int top = 0;
            stack[top++] = start;
            seen[start] = true;
            int minX = w;
            int minY = h;
            int maxX = 0;
            int maxY = 0;
            while (top > 0) {
                int p = stack[--top];
                pixels[count++] = p;
                int x = p % w;
                int y = p / w;
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int xx = x + dx;
                        int yy = y + dy;
                        if (xx >= 0 && yy >= 0 && xx < w && yy < h) {
                            int q = yy * w + xx;
                            if (mask[q] && !seen[q]) {
                                seen[q] = true;
                                stack[top++] = q;
                            }
                        }
                    }
                }
            }
            int bw = maxX - minX + 1;
            int bh = maxY - minY + 1;
            if ((double) bw * bh < minBox) {
                continue;
            }
            regions.add(describe(pixels, count, w, minX, minY, bw, bh));
        }
        return regions;
    }

    private static Region describe(
            int[] pixels, int count, int w, int minX, int minY, int bw, int bh) {
        // The region inside its bounding box with a margin of one pixel around it.
        int gw = bw + 2;
        int gh = bh + 2;
        boolean[] inside = new boolean[gw * gh];
        for (int i = 0; i < count; i++) {
            int x = pixels[i] % w - minX + 1;
            int y = pixels[i] / w - minY + 1;
            inside[y * gw + x] = true;
        }
        // Everything reachable from the margin without crossing the region is outside it; the rest
        // is the region and its holes.
        boolean[] outside = new boolean[gw * gh];
        int[] stack = new int[gw * gh];
        int top = 0;
        stack[top++] = 0;
        outside[0] = true;
        int outsideCount = 0;
        while (top > 0) {
            int p = stack[--top];
            outsideCount++;
            int x = p % gw;
            int y = p / gw;
            if (x > 0) {
                top = visit(p - 1, inside, outside, stack, top);
            }
            if (x < gw - 1) {
                top = visit(p + 1, inside, outside, stack, top);
            }
            if (y > 0) {
                top = visit(p - gw, inside, outside, stack, top);
            }
            if (y < gh - 1) {
                top = visit(p + gw, inside, outside, stack, top);
            }
        }
        double filled = (double) gw * gh - outsideCount;

        // Hull of the pixels on the rim of the region.
        List<double[]> rim = new ArrayList<>();
        for (int y = 1; y < gh - 1; y++) {
            for (int x = 1; x < gw - 1; x++) {
                int p = y * gw + x;
                if (inside[p]
                        && (!inside[p - 1]
                                || !inside[p + 1]
                                || !inside[p - gw]
                                || !inside[p + gw])) {
                    rim.add(new double[] {x - 1 + minX, y - 1 + minY});
                }
            }
        }
        return new Region(filled, hull(rim));
    }

    private static int visit(int q, boolean[] inside, boolean[] outside, int[] stack, int top) {
        if (!inside[q] && !outside[q]) {
            outside[q] = true;
            stack[top++] = q;
        }
        return top;
    }

    /** Convex hull (monotone chain), counter-clockwise in picture coordinates. */
    static double[][] hull(List<double[]> points) {
        double[][] pts = points.toArray(new double[0][]);
        Arrays.sort(
                pts,
                (a, b) -> a[0] != b[0] ? Double.compare(a[0], b[0]) : Double.compare(a[1], b[1]));
        int n = pts.length;
        if (n < 3) {
            return pts;
        }
        double[][] out = new double[2 * n][];
        int k = 0;
        for (int i = 0; i < n; i++) {
            while (k >= 2 && cross(out[k - 2], out[k - 1], pts[i]) <= 0) {
                k--;
            }
            out[k++] = pts[i];
        }
        for (int i = n - 2, lower = k + 1; i >= 0; i--) {
            while (k >= lower && cross(out[k - 2], out[k - 1], pts[i]) <= 0) {
                k--;
            }
            out[k++] = pts[i];
        }
        return Arrays.copyOf(out, k - 1);
    }

    private static double cross(double[] o, double[] a, double[] b) {
        return (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0]);
    }

    static double polygonArea(double[][] polygon) {
        double s = 0;
        for (int i = 0; i < polygon.length; i++) {
            double[] a = polygon[i];
            double[] b = polygon[(i + 1) % polygon.length];
            s += a[0] * b[1] - b[0] * a[1];
        }
        return Math.abs(0.5 * s);
    }

    /**
     * The hull simplified to four corners, or no result (a null reference) if it does not come down
     * to four. The tolerance is raised step by step from 1 % to 8 % of the perimeter; the first one
     * that leaves four corners is taken.
     */
    static double[][] quadOf(double[][] hull) {
        if (hull.length < 4) {
            return null;
        }
        double perimeter = 0;
        for (int i = 0; i < hull.length; i++) {
            perimeter += Quads.distance(hull[i], hull[(i + 1) % hull.length]);
        }
        for (int step = 0; step < 15; step++) {
            double eps = (0.01 + 0.07 * step / 14.0) * perimeter;
            double[][] simple = simplified(hull, eps);
            if (simple.length == 4) {
                return simple;
            }
        }
        return null;
    }

    /**
     * Douglas-Peucker for a closed polygon: split at two far-apart corners, simplify both halves.
     */
    private static double[][] simplified(double[][] polygon, double eps) {
        int n = polygon.length;
        int first = 0;
        for (int round = 0; round < 3; round++) {
            int far = first;
            for (int i = 0; i < n; i++) {
                if (Quads.distance(polygon[first], polygon[i])
                        > Quads.distance(polygon[first], polygon[far])) {
                    far = i;
                }
            }
            first = far;
        }
        int second = first;
        for (int i = 0; i < n; i++) {
            if (Quads.distance(polygon[first], polygon[i])
                    > Quads.distance(polygon[first], polygon[second])) {
                second = i;
            }
        }
        boolean[] keep = new boolean[n];
        keep[first] = true;
        keep[second] = true;
        simplify(polygon, first, second, eps, keep);
        simplify(polygon, second, first, eps, keep);
        List<double[]> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (keep[i]) {
                out.add(polygon[i].clone());
            }
        }
        return out.toArray(new double[0][]);
    }

    /**
     * Keeps the corners between {@code from} and {@code to} (going up, wrapping) that stick out.
     */
    private static void simplify(double[][] polygon, int from, int to, double eps, boolean[] keep) {
        int n = polygon.length;
        int span = ((to - from) % n + n) % n;
        if (span < 2) {
            return;
        }
        double[] a = polygon[from];
        double[] b = polygon[to];
        double length = Quads.distance(a, b);
        double worst = -1;
        int index = -1;
        for (int k = 1; k < span; k++) {
            double[] p = polygon[(from + k) % n];
            double d =
                    length < 1e-9
                            ? Quads.distance(a, p)
                            : Math.abs(
                                            (b[0] - a[0]) * (p[1] - a[1])
                                                    - (b[1] - a[1]) * (p[0] - a[0]))
                                    / length;
            if (d > worst) {
                worst = d;
                index = (from + k) % n;
            }
        }
        if (worst > eps) {
            keep[index] = true;
            simplify(polygon, from, index, eps, keep);
            simplify(polygon, index, to, eps, keep);
        }
    }
}
