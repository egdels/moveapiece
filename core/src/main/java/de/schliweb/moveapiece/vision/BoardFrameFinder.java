/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.vision;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * Finds the playing area of a DGT Pegasus in a photo from its frame, without a model.
 *
 * <p>The Pegasus has a black frame, and between frame and playing area a thin white line. Where
 * that line meets the frame is the strongest straight edge of the whole board, and it runs right
 * along the playing area.
 *
 * <ol>
 *   <li>The inside of the frame is a light area enclosed by dark: threshold at several levels and
 *       take light regions that look like a quadrilateral and are surrounded by a dark band. The
 *       outline of a dark quadrilateral region - the frame itself - serves as well, for when light
 *       pieces lean across the frame and the light region leaks out.
 *   <li>For each side of that rough quadrilateral, walk along it and look across it for the step
 *       from light to dark; find the straight lines with many such steps on them.
 *   <li>The board is a square. With the focal length of a phone camera, a quadrilateral in the
 *       photo is the picture of a square or it is not, and that is checked. In a photo taken at an
 *       angle the far edge is hidden behind the pieces standing in front of it; then the other
 *       three edges and "it is a square" give the fourth.
 *   <li>Among the remaining possibilities the 8x8 squares decide: they have to fit inside.
 *   <li>Move in by the fixed width of the white line: the playing area.
 * </ol>
 *
 * <p>This is the Java counterpart of {@code training/find_board_frame.py}, where the method was
 * developed and measured; the constants are the ones found there.
 */
public final class BoardFrameFinder {

    /** The photo is shrunk to this (longest side) for the search. */
    private static final int WORK_SIZE = 1000;

    /**
     * If nothing is found at that size, the search is repeated on smaller and smaller pictures of a
     * photo. Where the sun glares on the far side of the frame, the frame is not dark there and the
     * fine picture gives no closed outline; shrunk further, the glare and the pieces in front of
     * the frame weigh less. At which size a board then shows differs from photo to photo (of eight
     * photos of the dev app with the whole board in them and nothing found at 1000, two were found
     * at 800, one at 640, three at 500, one not at all). The corners are less exact that way - a
     * few pixels as a rule, a quarter of a square at worst - which is better than no board. The
     * live picture of the camera is small already and searched once.
     */
    private static final int[] SMALLER_WORK_SIZES = {800, 640, 500};

    /**
     * Distance from the inner edge of the frame to the playing area, as a fraction of the side of
     * the inner frame edge (the white line and the gap behind it).
     */
    private static final double INSET = 0.009;

    /** A quad counts as the board only if the squares fit into it at least this well. */
    private static final double MIN_GRID_SCORE = 6.0;

    /**
     * ... and only if it can be the picture of a square. Labelled playing areas are within 0.03.
     */
    private static final double MAX_OFF_SQUARE = 0.07;

    /**
     * Focal length as a fraction of the image diagonal: the main camera of a phone is equivalent to
     * 24-27 mm, i.e. 0.55-0.62 of the diagonal.
     */
    static final double FOCAL = 0.6;

    private static final int MAX_CANDIDATES = 6;

    /** The playing area found in a photo. */
    public static final class Result {
        /** Top-left, top-right, bottom-right, bottom-left as seen in the photo, in photo pixels. */
        public final double[][] corners;

        /**
         * How well the 8x8 squares fit; wrong results seen so far were all below 13, right ones
         * mostly between 14 and 26.
         */
        public final double fit;

        Result(double[][] corners, double fit) {
            this.corners = corners;
            this.fit = fit;
        }
    }

    private BoardFrameFinder() {}

    /** The playing area in the photo, or {@code null} if no board is found. */
    public static Result find(Photo photo) {
        Result found = find(photo, WORK_SIZE);
        if (Math.max(photo.width, photo.height) > WORK_SIZE) {
            for (int i = 0; found == null && i < SMALLER_WORK_SIZES.length; i++) {
                found = find(photo, SMALLER_WORK_SIZES[i]);
            }
        }
        return found;
    }

    private static Result find(Photo photo, int workSize) {
        GrayImage gray = GrayImage.shrunk(photo, workSize);
        double scale = (double) gray.width / photo.width;

        // The rough guesses only have to land near the frame, so they are made at half the
        // resolution; the sides are then searched in the full one.
        GrayImage coarse = gray.halved().blurred(1.0);
        List<double[][]> guesses = new ArrayList<>(lightCandidates(coarse));
        guesses.addAll(darkCandidates(coarse));
        List<double[][]> roughs = new ArrayList<>();
        double near = 0.02 * Math.max(gray.width, gray.height);
        for (double[][] guess : guesses) {
            double[][] quad = Quads.canonical(guess);
            for (double[] p : quad) {
                // A pixel of the small picture covers two of the large one.
                p[0] = 2 * p[0] + 0.5;
                p[1] = 2 * p[1] + 0.5;
            }
            // The same region turns up at several brightness levels; one of each is enough.
            boolean known = false;
            for (double[][] other : roughs) {
                double worst = 0;
                for (int i = 0; i < 4; i++) {
                    worst = Math.max(worst, Quads.distance(quad[i], other[i]));
                }
                known |= worst < near;
            }
            if (!known) {
                roughs.add(quad);
            }
        }
        // The guesses are independent of each other, so they are refined side by side.
        List<Scored> refinements =
                roughs.parallelStream()
                        .map(rough -> refine(gray, rough))
                        .collect(Collectors.toList());
        double[][] best = null;
        double bestScore = MIN_GRID_SCORE;
        for (Scored refined : refinements) {
            if (refined != null && refined.grid > bestScore) {
                best = refined.quad;
                bestScore = refined.grid;
            }
        }
        if (best == null) {
            return null;
        }
        double[][] area = Quads.canonical(Quads.inset(Quads.canonical(best), INSET));
        for (double[] p : area) {
            p[0] /= scale;
            p[1] /= scale;
        }
        return new Result(area, bestScore);
    }

    // --- rough quadrilaterals ----------------------------------------------------------------

    private static final class Scored {
        final double[][] quad;
        final double grid;
        final double value;

        Scored(double[][] quad, double grid, double value) {
            this.quad = quad;
            this.grid = grid;
            this.value = value;
        }
    }

    /** Light, quadrilateral regions with a dark band around them, best first. */
    private static List<double[][]> lightCandidates(GrayImage blurred) {
        int w = blurred.width;
        int h = blurred.height;
        List<Scored> found = new ArrayList<>();
        for (int level = 70; level <= 230; level += 20) {
            boolean[] light = new boolean[w * h];
            for (int i = 0; i < light.length; i++) {
                light[i] = blurred.data[i] > level;
            }
            light = Regions.opened(light, w, h);
            for (Regions.Region region : Regions.find(light, w, h, 0.04 * w * h)) {
                if (region.filledArea < 0.04 * w * h) {
                    continue;
                }
                double[][] quad = Regions.quadOf(region.hull);
                if (quad == null || !Quads.isConvex(quad)) {
                    continue;
                }
                // Pieces make holes at the rim; the hull covers them.
                double fill = region.filledArea / Math.max(Quads.area(quad), 1.0);
                double[] sides = Quads.sideRange(quad);
                if (fill < 0.6 || sides[0] < 0.25 * sides[1]) {
                    continue;
                }
                // The signature of the frame: just outside the quad it is much darker than inside.
                double contrast = bandContrast(blurred, quad);
                if (contrast < 25) {
                    continue;
                }
                found.add(new Scored(quad, 0, contrast * Math.min(1.0, fill / 0.85)));
            }
        }
        found.sort((a, b) -> Double.compare(b.value, a.value));
        List<double[][]> out = new ArrayList<>();
        for (int i = 0; i < Math.min(MAX_CANDIDATES, found.size()); i++) {
            out.add(found.get(i).quad);
        }
        return out;
    }

    /** Rough quads from the black frame itself: a dark, quadrilateral outline, moved in. */
    private static List<double[][]> darkCandidates(GrayImage blurred) {
        int w = blurred.width;
        int h = blurred.height;
        List<Scored> found = new ArrayList<>();
        for (int level = 50; level <= 110; level += 20) {
            boolean[] dark = new boolean[w * h];
            for (int i = 0; i < dark.length; i++) {
                dark[i] = blurred.data[i] < level;
            }
            for (Regions.Region region : Regions.find(dark, w, h, 0.05 * w * h)) {
                double hullArea = Regions.polygonArea(region.hull);
                if (hullArea < 0.05 * w * h) {
                    continue;
                }
                double[][] quad = Regions.quadOf(region.hull);
                if (quad == null || !Quads.isConvex(quad)) {
                    continue;
                }
                double[] sides = Quads.sideRange(quad);
                if (sides[0] > 0.25 * sides[1]) {
                    found.add(new Scored(Quads.inset(quad, 0.03), 0, hullArea));
                }
            }
        }
        found.sort((a, b) -> Double.compare(b.value, a.value));
        List<double[][]> out = new ArrayList<>();
        for (int i = 0; i < Math.min(MAX_CANDIDATES, found.size()); i++) {
            out.add(found.get(i).quad);
        }
        return out;
    }

    private static double bandContrast(GrayImage blurred, double[][] quad) {
        double cx = 0;
        double cy = 0;
        for (double[] p : quad) {
            cx += p[0] / 4;
            cy += p[1] / 4;
        }
        double[] inner = new double[160];
        double[] outer = new double[160];
        int n = 0;
        for (int i = 0; i < 4; i++) {
            double[] a = quad[i];
            double[] b = quad[(i + 1) % 4];
            double side = Quads.distance(a, b);
            double nx = (b[1] - a[1]) / (side + 1e-9);
            double ny = (a[0] - b[0]) / (side + 1e-9);
            if (nx * ((a[0] + b[0]) / 2 - cx) + ny * ((a[1] + b[1]) / 2 - cy) < 0) {
                nx = -nx;
                ny = -ny;
            }
            for (int k = 0; k < 40; k++) {
                double t = 0.1 + 0.8 * k / 39.0;
                double x = a[0] + t * (b[0] - a[0]);
                double y = a[1] + t * (b[1] - a[1]);
                inner[n] = blurred.at(x - nx * 0.02 * side, y - ny * 0.02 * side);
                outer[n] = blurred.at(x + nx * 0.02 * side, y + ny * 0.02 * side);
                n++;
            }
        }
        // Medians: pieces on the rim and whatever lies next to the board must not decide.
        return median(inner) - median(outer);
    }

    private static double median(double[] values) {
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        int n = sorted.length;
        return n % 2 == 1 ? sorted[n / 2] : (sorted[n / 2 - 1] + sorted[n / 2]) / 2;
    }

    // --- the four sides ----------------------------------------------------------------------

    /** A line as point (x, y), unit direction (dx, dy) and the share of profiles supporting it. */
    private static final class Line {
        final double x;
        final double y;
        final double dx;
        final double dy;
        final double share;

        Line(double x, double y, double dx, double dy, double share) {
            this.x = x;
            this.y = y;
            this.dx = dx;
            this.dy = dy;
            this.share = share;
        }
    }

    /**
     * Up to three lines along the side a-b where the white line may meet the frame, best supported
     * first.
     *
     * <p>Across the side, every step from light to dark that is followed by a dark stretch as wide
     * as the frame is a candidate. A row of pieces near the side produces such steps too, but not
     * on one straight line; lines with many candidates on them are found by RANSAC. In a photo
     * taken at an angle the far edge shows only between the pieces standing in front of it, so a
     * line may have little support; which of the lines is the frame is decided by the squares.
     */
    private static List<Line> sideLines(
            GrayImage gray, double[] a, double[] b, double[] centre, double reachFraction) {
        double side = Quads.distance(a, b);
        double dirX = (b[0] - a[0]) / side;
        double dirY = (b[1] - a[1]) / side;
        double nx = dirY;
        double ny = -dirX;
        if (nx * ((a[0] + b[0]) / 2 - centre[0]) + ny * ((a[1] + b[1]) / 2 - centre[1]) < 0) {
            nx = -nx;
            ny = -ny;
        }
        int reach = Math.max(6, (int) (reachFraction * side));
        int frame = Math.max(3, (int) (0.018 * side)); // the frame is about 3 % of a side wide
        int length = 2 * reach + frame + 1; // offsets -reach .. reach + frame
        int profiles = 110;
        List<double[]> candidates = new ArrayList<>();
        double[] raw = new double[length];
        double[] profile = new double[length];
        double[] drop = new double[length - 2];
        for (int p = 0; p < profiles; p++) {
            double t = 0.04 + 0.92 * p / (profiles - 1.0);
            double baseX = a[0] + t * (b[0] - a[0]);
            double baseY = a[1] + t * (b[1] - a[1]);
            for (int i = 0; i < length; i++) {
                int offset = i - reach;
                raw[i] = gray.at(baseX + offset * nx, baseY + offset * ny);
            }
            for (int i = 0; i < length; i++) {
                double sum = raw[i];
                if (i > 0) {
                    sum += raw[i - 1];
                }
                if (i < length - 1) {
                    sum += raw[i + 1];
                }
                profile[i] = sum / 3;
            }
            for (int i = 0; i < drop.length; i++) {
                drop[i] = profile[i] - profile[i + 2]; // positive where it gets darker outwards
            }
            int[] taken = new int[3];
            int takenCount = 0;
            boolean[] used = new boolean[drop.length];
            for (int round = 0; round < 6 && takenCount < 3; round++) {
                int k = -1;
                for (int i = 1; i < drop.length - frame; i++) {
                    if (!used[i] && (k < 0 || drop[i] > drop[k])) {
                        k = i;
                    }
                }
                if (k < 0) {
                    break;
                }
                used[k] = true;
                if (drop[k] < 28) {
                    break; // the rest is smaller still
                }
                boolean close = false;
                for (int j = 0; j < takenCount; j++) {
                    close |= Math.abs(k - taken[j]) < 3;
                }
                if (close) {
                    continue;
                }
                double inside = 0;
                for (int i = Math.max(0, k - 2); i <= k; i++) {
                    inside = Math.max(inside, profile[i]);
                }
                double beyondSum = 0;
                double beyondMax = 0;
                for (int i = k + 3; i < k + 3 + frame; i++) {
                    beyondSum += profile[i];
                    beyondMax = Math.max(beyondMax, profile[i]);
                }
                if (beyondSum / frame < 0.62 * inside && beyondMax < 0.8 * inside) {
                    taken[takenCount++] = k;
                    int offset = k + 1 - reach;
                    candidates.add(new double[] {baseX + offset * nx, baseY + offset * ny});
                }
            }
        }

        List<Line> lines = new ArrayList<>();
        Random random = new Random(0);
        for (int round = 0; round < 3 && candidates.size() >= 12; round++) {
            int n = candidates.size();
            int bestCount = 0;
            double[] bestPoint = null;
            double[] bestDirection = null;
            for (int trial = 0; trial < 300; trial++) {
                int i = random.nextInt(n);
                int j = random.nextInt(n - 1);
                if (j >= i) {
                    j++;
                }
                double[] pi = candidates.get(i);
                double[] pj = candidates.get(j);
                double span = Quads.distance(pi, pj);
                if (span < 0.2 * side) {
                    continue;
                }
                double dx = (pj[0] - pi[0]) / span;
                double dy = (pj[1] - pi[1]) / span;
                int count = 0;
                for (double[] p : candidates) {
                    if (Math.abs((p[0] - pi[0]) * dy - (p[1] - pi[1]) * dx) < 1.6) {
                        count++;
                    }
                }
                if (count > bestCount) {
                    bestCount = count;
                    bestPoint = pi;
                    bestDirection = new double[] {dx, dy};
                }
            }
            if (bestPoint == null || bestCount < Math.max(12, 0.1 * profiles)) {
                break;
            }
            List<double[]> inliers = new ArrayList<>();
            for (double[] p : candidates) {
                double residual =
                        (p[0] - bestPoint[0]) * bestDirection[1]
                                - (p[1] - bestPoint[1]) * bestDirection[0];
                if (Math.abs(residual) < 1.6) {
                    inliers.add(p);
                }
            }
            Line line = fit(inliers, (double) bestCount / profiles);
            lines.add(line);
            List<double[]> rest = new ArrayList<>();
            for (double[] p : candidates) {
                if (Math.abs((p[0] - line.x) * line.dy - (p[1] - line.y) * line.dx) >= 4.0) {
                    rest.add(p);
                }
            }
            candidates = rest;
        }
        return lines;
    }

    /** The straight line with the smallest squared distances to the points. */
    private static Line fit(List<double[]> points, double share) {
        double mx = 0;
        double my = 0;
        for (double[] p : points) {
            mx += p[0] / points.size();
            my += p[1] / points.size();
        }
        double sxx = 0;
        double sxy = 0;
        double syy = 0;
        for (double[] p : points) {
            sxx += (p[0] - mx) * (p[0] - mx);
            sxy += (p[0] - mx) * (p[1] - my);
            syy += (p[1] - my) * (p[1] - my);
        }
        double angle = 0.5 * Math.atan2(2 * sxy, sxx - syy); // direction of the largest spread
        return new Line(mx, my, Math.cos(angle), Math.sin(angle), share);
    }

    private static double[] intersect(Line l1, Line l2) {
        double det = l1.dx * -l2.dy - (-l2.dx) * l1.dy;
        if (Math.abs(det) < 1e-9) {
            return null;
        }
        double qx = l2.x - l1.x;
        double qy = l2.y - l1.y;
        double t = (qx * -l2.dy - (-l2.dx) * qy) / det;
        return new double[] {l1.x + t * l1.dx, l1.y + t * l1.dy};
    }

    /**
     * The line of side {@code missing} that makes a square with the other three, or {@code null}.
     *
     * <p>The two neighbouring sides and the opposite side are given. The two unknown corners lie on
     * the neighbouring sides; they are moved along them until the quad is the picture of a square.
     */
    private static Line fourthSide(Line[] lines, int missing, int width, int height) {
        Line opposite = lines[(missing + 2) % 4];
        Line left = lines[(missing + 3) % 4];
        Line right = lines[(missing + 1) % 4];
        double[] a = intersect(left, opposite);
        double[] b = intersect(opposite, right);
        if (a == null || b == null) {
            return null;
        }
        double base = Quads.distance(a, b);
        double focal = FOCAL * Math.hypot(width, height);
        double[] da = towards(left, a, lines[missing]);
        double[] db = towards(right, b, lines[missing]);
        // Without a hint where the side is, both directions along the neighbours are tried.
        int[] signs = lines[missing] == null ? new int[] {1, -1} : new int[] {1};
        double bestCost = Double.MAX_VALUE;
        double[] bestC = null;
        double[] bestD = null;
        for (int sign : signs) {
            double loS = 0.2 * base;
            double hiS = 4.0 * base;
            double loT = loS;
            double hiT = hiS;
            for (int pass = 0; pass < 4; pass++) {
                double localCost = Double.MAX_VALUE;
                double localS = 0;
                double localT = 0;
                double[] localC = null;
                double[] localD = null;
                for (int i = 0; i < 13; i++) {
                    double s = loS + (hiS - loS) * i / 12.0;
                    double[] d = {a[0] + sign * s * da[0], a[1] + sign * s * da[1]};
                    for (int j = 0; j < 13; j++) {
                        double t = loT + (hiT - loT) * j / 12.0;
                        double[] c = {b[0] + sign * t * db[0], b[1] + sign * t * db[1]};
                        double[][] quad = {a, b, c, d};
                        if (!Quads.isConvex(quad)) {
                            continue;
                        }
                        double cost = Quads.offSquare(quad, width, height, focal);
                        if (cost < localCost) {
                            localCost = cost;
                            localS = s;
                            localT = t;
                            localC = c;
                            localD = d;
                        }
                    }
                }
                if (localC == null) {
                    break;
                }
                double stepS = (hiS - loS) / 12.0;
                double stepT = (hiT - loT) / 12.0;
                loS = Math.max(localS - stepS, 0.05 * base);
                hiS = localS + stepS;
                loT = Math.max(localT - stepT, 0.05 * base);
                hiT = localT + stepT;
                if (localCost < bestCost) {
                    bestCost = localCost;
                    bestC = localC;
                    bestD = localD;
                }
            }
        }
        if (bestC == null || bestCost > 0.02) {
            return null;
        }
        double length = Quads.distance(bestD, bestC);
        return new Line(
                bestD[0],
                bestD[1],
                (bestC[0] - bestD[0]) / length,
                (bestC[1] - bestD[1]) / length,
                0.0);
    }

    /** The direction along {@code line} from {@code corner} towards where it meets {@code hint}. */
    private static double[] towards(Line line, double[] corner, Line hint) {
        double[] d = {line.dx, line.dy};
        if (hint != null) {
            double[] other = intersect(line, hint);
            if (other != null && Quads.distance(other, corner) > 1e-6) {
                double along = d[0] * (other[0] - corner[0]) + d[1] * (other[1] - corner[1]);
                if (along < 0) {
                    d[0] = -d[0];
                    d[1] = -d[1];
                }
            }
        }
        return d;
    }

    // --- the squares decide ------------------------------------------------------------------

    /**
     * How well the 8x8 squares fit inside the frame quad: contrast across the lines between
     * squares, minus the contrast across lines through the middle of the squares.
     *
     * <p>Neighbouring squares differ in brightness, so with the right quad there is an edge on
     * every line between two squares and none in between. Pieces hide some of it, in both terms
     * alike.
     */
    private static double gridScore(GrayImage gray, double[][] frameQuad) {
        double[] h = Quads.homography(1, Quads.inset(frameQuad, INSET));
        if (h == null) {
            return -Double.MAX_VALUE;
        }
        double onLines = 0;
        double between = 0;
        for (int k = 1; k < 8; k++) {
            onLines += lineContrast(gray, h, k / 8.0);
        }
        for (int k = 0; k < 8; k++) {
            between += lineContrast(gray, h, (k + 0.5) / 8.0);
        }
        return onLines / 7 - between / 8;
    }

    /** Mean contrast across the two lines (one in each direction) at position {@code k}. */
    private static double lineContrast(GrayImage gray, double[] h, double k) {
        double delta = 0.2 / 8;
        double sum = 0;
        for (int i = 0; i < 8; i++) {
            double t = (i + 0.5) / 8;
            sum += Math.min(80, Math.abs(at(gray, h, k - delta, t) - at(gray, h, k + delta, t)));
            sum += Math.min(80, Math.abs(at(gray, h, t, k - delta) - at(gray, h, t, k + delta)));
        }
        return sum / 16;
    }

    private static double at(GrayImage gray, double[] h, double u, double v) {
        double[] p = Quads.apply(h, u, v);
        return gray.at(p[0], p[1]);
    }

    /**
     * The best combination of one line per side, or {@code null}. A combination has to be the
     * picture of a square; among those, the squares and the support of the lines decide.
     */
    private static Scored bestQuad(GrayImage gray, double[][] quad, double reach) {
        double[] centre = {0, 0};
        for (double[] p : quad) {
            centre[0] += p[0] / 4;
            centre[1] += p[1] / 4;
        }
        List<List<Line>> sides = new ArrayList<>();
        Line[] firsts = new Line[4];
        for (int i = 0; i < 4; i++) {
            List<Line> lines = sideLines(gray, quad[i], quad[(i + 1) % 4], centre, reach);
            sides.add(lines);
            firsts[i] = lines.isEmpty() ? null : lines.get(0);
        }
        // A side that is hidden (the far edge behind a row of pieces) can be worked out from the
        // other three when those are well supported.
        for (int i = 0; i < 4; i++) {
            boolean supported = true;
            for (int k = 1; k <= 3; k++) {
                Line other = firsts[(i + k) % 4];
                supported &= other != null && other.share >= 0.25;
            }
            if (supported) {
                Line inferred = fourthSide(firsts, i, gray.width, gray.height);
                if (inferred != null) {
                    sides.get(i).add(inferred);
                }
            }
        }
        for (List<Line> lines : sides) {
            if (lines.isEmpty()) {
                return null;
            }
        }
        double focal = FOCAL * Math.hypot(gray.width, gray.height);
        Scored best = null;
        for (Line l0 : sides.get(0)) {
            for (Line l1 : sides.get(1)) {
                for (Line l2 : sides.get(2)) {
                    for (Line l3 : sides.get(3)) {
                        Line[] lines = {l0, l1, l2, l3};
                        double[][] candidate = new double[4][];
                        boolean ok = true;
                        for (int i = 0; i < 4 && ok; i++) {
                            candidate[i] = intersect(lines[(i + 3) % 4], lines[i]);
                            ok = candidate[i] != null;
                        }
                        if (!ok || !Quads.isConvex(candidate)) {
                            continue;
                        }
                        double[] range = Quads.sideRange(candidate);
                        if (range[0] < 0.2 * range[1]
                                || Quads.area(candidate) < 0.03 * gray.width * gray.height) {
                            continue;
                        }
                        double offSquare =
                                Quads.offSquare(candidate, gray.width, gray.height, focal);
                        if (offSquare > MAX_OFF_SQUARE) {
                            continue;
                        }
                        double grid = gridScore(gray, candidate);
                        // The squares decide between clearly different lines (the frame or a row
                        // of pieces); between lines a few pixels apart they cannot, there the
                        // better supported one is the frame edge.
                        double support = l0.share + l1.share + l2.share + l3.share;
                        double value = grid + 20.0 * support - 150.0 * offSquare;
                        if (best == null || value > best.value) {
                            best = new Scored(candidate, grid, value);
                        }
                    }
                }
            }
        }
        return best;
    }

    private static Scored refine(GrayImage gray, double[][] rough) {
        // Wide first: the rough side may sit on a row of pieces.
        Scored first = bestQuad(gray, rough, 0.2);
        if (first == null) {
            return null;
        }
        Scored second = bestQuad(gray, first.quad, 0.05);
        return second != null ? second : first;
    }
}
