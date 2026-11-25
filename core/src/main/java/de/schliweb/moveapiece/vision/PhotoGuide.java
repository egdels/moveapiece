/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.vision;

/**
 * Tells from the playing area found in a picture how the camera is held, and what the user should
 * change before the photo is taken.
 *
 * <p>The limits come from photos of a real Pegasus (see {@code training/README.md}): seen from less
 * than about 45 degrees above the table the pieces hide each other and the recogniser gets whole
 * rows wrong; from 48 degrees on it was right. From about 70 degrees on, looking down on the
 * pieces, it becomes unsure of many squares and now and then swaps a rook and a bishop. With the
 * board on less than a quarter of the picture results were poor (7 of 8 photos wrong, though taken
 * against the light); good photos shrunk on the computer stay right down to about a sixth of the
 * picture and fall apart below an eighth.
 */
public final class PhotoGuide {

    /** What to tell the user. */
    public enum Advice {
        /** The photo can be taken like this. */
        GOOD,
        /** The camera looks too flat over the board; hold it higher and look down more. */
        STEEPER,
        /** The camera looks straight down; the pieces are then seen only from above. */
        FLATTER,
        /** The board is small in the picture. */
        CLOSER,
        /** The board or the pieces on it touch the edge of the picture. */
        FARTHER
    }

    /** Below this many degrees above the table the view is too flat. */
    static final double MIN_ELEVATION = 50;

    /** Above this many degrees the view is too steep. */
    static final double MAX_ELEVATION = 70;

    /**
     * A view that was not good has to come this many degrees inside the limits to become good, so
     * that the advice does not flicker when the camera is held right at a limit.
     */
    static final double SLACK = 2;

    /** The playing area should cover at least this share of the picture. */
    static final double MIN_SHARE = 0.25;

    /** As {@link #SLACK}, for the share of the picture. */
    static final double SHARE_SLACK = 0.02;

    /**
     * The playing area keeps at least this distance from the edge, as a share of the short side.
     */
    private static final double MARGIN = 0.02;

    /** The tallest piece in squares; its top has to be in the picture too. */
    private static final double TALLEST_PIECE = 1.75;

    public final Advice advice;

    /** Degrees above the table from which the camera looks at the middle of the board. */
    public final double elevation;

    /** Share of the picture the playing area covers, 0..1. */
    public final double share;

    private PhotoGuide(Advice advice, double elevation, double share) {
        this.advice = advice;
        this.elevation = elevation;
        this.share = share;
    }

    /**
     * @param corners the playing area as {@link BoardFrameFinder.Result#corners} gives it
     * @param width width of the picture the corners are in
     * @param height height of that picture
     */
    public static PhotoGuide of(double[][] corners, int width, int height) {
        return of(corners, width, height, true);
    }

    /**
     * As {@link #of(double[][], int, int)} for one picture after another of a moving camera.
     *
     * @param wasGood whether the picture before this one was {@link Advice#GOOD}; if not, the board
     *     has to be {@link #SHARE_SLACK} larger and the angle has to be {@link #SLACK} degrees
     *     inside its limits
     */
    public static PhotoGuide of(double[][] corners, int width, int height, boolean wasGood) {
        double[] h = Quads.homography(8, corners);
        double focal = BoardFrameFinder.FOCAL * Math.hypot(width, height);
        double[] r1 = Quads.cameraColumn(h, 0, width, height, focal);
        double[] r2 = Quads.cameraColumn(h, 1, width, height, focal);
        double[] t = Quads.cameraColumn(h, 2, width, height, focal);
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
        if (dot(up, t) > 0) {
            scale(up, -1);
        }
        double[] centre = point(r1, r2, t, 4, 4);
        double elevation =
                Math.toDegrees(Math.asin(Math.min(1.0, Math.abs(dot(up, centre)) / norm(centre))));
        double share = Quads.area(corners) / ((double) width * height);

        double margin = MARGIN * Math.min(width, height);
        boolean inside = true;
        for (int i = 0; i < 4; i++) {
            inside &= isInside(corners[i][0], corners[i][1], width, height, margin);
            // The top of a king standing on the corner square.
            double[] top = point(r1, r2, t, i == 1 || i == 2 ? 7.5 : 0.5, i >= 2 ? 7.5 : 0.5);
            for (int k = 0; k < 3; k++) {
                top[k] += up[k] * TALLEST_PIECE;
            }
            inside &=
                    top[2] > 0
                            && isInside(
                                    width / 2.0 + focal * top[0] / top[2],
                                    height / 2.0 + focal * top[1] / top[2],
                                    width,
                                    height,
                                    0);
        }

        double slack = wasGood ? 0 : SLACK;
        Advice advice;
        if (!inside) {
            advice = Advice.FARTHER;
        } else if (elevation < MIN_ELEVATION + slack) {
            advice = Advice.STEEPER;
        } else if (elevation > MAX_ELEVATION - slack) {
            advice = Advice.FLATTER;
        } else if (share < MIN_SHARE + (wasGood ? 0 : SHARE_SLACK)) {
            advice = Advice.CLOSER;
        } else {
            advice = Advice.GOOD;
        }
        return new PhotoGuide(advice, elevation, share);
    }

    private static boolean isInside(double x, double y, int width, int height, double margin) {
        return x >= margin && y >= margin && x <= width - margin && y <= height - margin;
    }

    private static double[] point(double[] r1, double[] r2, double[] t, double u, double v) {
        return new double[] {
            r1[0] * u + r2[0] * v + t[0], r1[1] * u + r2[1] * v + t[1], r1[2] * u + r2[2] * v + t[2]
        };
    }

    private static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private static double norm(double[] a) {
        return Math.sqrt(dot(a, a));
    }

    private static void scale(double[] a, double factor) {
        for (int i = 0; i < a.length; i++) {
            a[i] *= factor;
        }
    }
}
