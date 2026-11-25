/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.vision;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

public class PhotoGuideTest {

    private static final int WIDTH = 900;
    private static final int HEIGHT = 1200;

    /**
     * A board lying on a table, seen from in front at {@code degrees} above it by a camera that
     * looks at its middle from {@code distance} board widths away.
     */
    private static double[][] board(double degrees, double distance) {
        double focal = BoardFrameFinder.FOCAL * Math.hypot(WIDTH, HEIGHT);
        double tilt = Math.toRadians(degrees);
        double[][] corners = {{-0.5, 0.5}, {0.5, 0.5}, {0.5, -0.5}, {-0.5, -0.5}};
        double[][] out = new double[4][2];
        for (int i = 0; i < 4; i++) {
            double along = corners[i][1]; // away from the camera
            double depth = distance + along * Math.cos(tilt);
            out[i][0] = WIDTH / 2.0 + focal * corners[i][0] / depth;
            out[i][1] = HEIGHT / 2.0 - focal * along * Math.sin(tilt) / depth;
        }
        return out;
    }

    @Test
    public void tellsTheAngleTheCameraLooksFrom() {
        for (double degrees : new double[] {35, 50, 65, 80}) {
            PhotoGuide guide = PhotoGuide.of(board(degrees, 2.6), WIDTH, HEIGHT);
            assertEquals(degrees, guide.elevation, 0.5);
        }
    }

    @Test
    public void aBoardSeenWellIsGood() {
        assertEquals(PhotoGuide.Advice.GOOD, PhotoGuide.of(board(60, 1.6), WIDTH, HEIGHT).advice);
    }

    @Test
    public void aFlatViewAsksForASteeperOne() {
        assertEquals(
                PhotoGuide.Advice.STEEPER, PhotoGuide.of(board(40, 1.6), WIDTH, HEIGHT).advice);
    }

    @Test
    public void aViewFromStraightAboveAsksForAFlatterOne() {
        assertEquals(
                PhotoGuide.Advice.FLATTER, PhotoGuide.of(board(85, 1.6), WIDTH, HEIGHT).advice);
    }

    @Test
    public void aViewAtALimitStaysGoodButDoesNotBecomeGood() {
        for (double degrees : new double[] {51, 69}) {
            double[][] corners = board(degrees, 1.45);
            assertEquals(
                    PhotoGuide.Advice.GOOD, PhotoGuide.of(corners, WIDTH, HEIGHT, true).advice);
            assertNotEquals(
                    PhotoGuide.Advice.GOOD, PhotoGuide.of(corners, WIDTH, HEIGHT, false).advice);
        }
    }

    @Test
    public void aSmallBoardAsksToComeCloser() {
        assertEquals(PhotoGuide.Advice.CLOSER, PhotoGuide.of(board(60, 3.5), WIDTH, HEIGHT).advice);
    }

    @Test
    public void aBoardThatTouchesTheEdgeAsksToStepBack() {
        assertEquals(
                PhotoGuide.Advice.FARTHER, PhotoGuide.of(board(60, 0.9), WIDTH, HEIGHT).advice);
    }

    @Test
    public void piecesThatLeaveThePictureAskToStepBack() {
        // The board itself is inside, but a king on the far rank would stick out at the top.
        double[][] corners = board(60, 1.6);
        double shift = corners[0][1] - 30;
        for (double[] p : corners) {
            p[1] -= shift;
        }
        assertEquals(PhotoGuide.Advice.FARTHER, PhotoGuide.of(corners, WIDTH, HEIGHT).advice);
    }
}
