/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.vision;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Test;

public class BoardFrameFinderTest {

    /** The frame of the drawn board is this fraction of the board wide, as on the Pegasus. */
    private static final double FRAME = 0.03;

    @Test
    public void findsThePlayingAreaOfADrawnBoardSeenFromAbove() {
        double[][] board = {{250, 150}, {950, 170}, {930, 870}, {230, 850}};
        Photo photo = TestBoards.draw(1200, 1000, board, FRAME, 0);

        BoardFrameFinder.Result result = BoardFrameFinder.find(photo);

        assertNotNull(result);
        assertCornersNear(TestBoards.playingArea(board, FRAME), result.corners, 0.1);
    }

    @Test
    public void findsThePlayingAreaOfADrawnBoardSeenAtAnAngle() {
        // A square seen from in front and above, with the focal length the finder assumes.
        double[][] board = TestBoards.squareSeenAtAnAngle(1200, 900, 50);
        Photo photo = TestBoards.draw(1200, 900, board, FRAME, 0);

        BoardFrameFinder.Result result = BoardFrameFinder.find(photo);

        assertNotNull(result);
        assertCornersNear(TestBoards.playingArea(board, FRAME), result.corners, 0.1);
    }

    @Test
    public void findsThePlayingAreaWhenPiecesHideTheFarEdge() {
        double[][] board = TestBoards.squareSeenAtAnAngle(1200, 900, 50);
        Photo photo = TestBoards.draw(1200, 900, board, FRAME, 8);

        BoardFrameFinder.Result result = BoardFrameFinder.find(photo);

        assertNotNull(result);
        assertCornersNear(TestBoards.playingArea(board, FRAME), result.corners, 0.15);
    }

    @Test
    public void findsNothingWhereThereIsNoBoard() {
        int[] rgb = new int[800 * 600];
        for (int i = 0; i < rgb.length; i++) {
            int v = 90 + (i * 31 % 60);
            rgb[i] = (v << 16) | (v << 8) | v;
        }
        assertNull(BoardFrameFinder.find(new Photo(800, 600, rgb)));
    }

    @Test
    public void aQuadThatIsNoSquareIsToldApart() {
        double focal = BoardFrameFinder.FOCAL * Math.hypot(1200, 900);
        double[][] square = TestBoards.squareSeenAtAnAngle(1200, 900, 50);
        assertEquals(0.0, Quads.offSquare(square, 1200, 900, focal), 0.01);
        // The same view of a board that is a quarter too long.
        double[][] oblong = TestBoards.rectangleSeenAtAnAngle(1200, 900, 50, 1.25);
        assertTrue(Quads.offSquare(oblong, 1200, 900, focal) > 0.15);
    }

    /**
     * The photos the method was developed on, with hand-labelled playing areas. They are not in the
     * repository; the test runs where {@code training/samples/frame_test} exists.
     */
    @Test
    public void findsThePlayingAreaInTheLabelledPhotos() throws Exception {
        File folder = new File("../training/samples/frame_test");
        File labels = new File(folder, "labels.jsonl");
        assumeTrue("no labelled photos here", labels.isFile());

        Pattern image = Pattern.compile("\"image\":\\s*\"([^\"]+)\"");
        Pattern number = Pattern.compile("-?\\d+(?:\\.\\d+)?");
        int photos = 0;
        int found = 0;
        int withinQuarter = 0;
        int withinTenth = 0;
        List<String> off = new ArrayList<>();
        for (String line : Files.readAllLines(labels.toPath())) {
            Matcher name = image.matcher(line);
            if (!name.find()) {
                continue;
            }
            Matcher numbers = number.matcher(line.substring(line.indexOf("\"corners\"")));
            double[][] truth = new double[4][2];
            for (int i = 0; i < 8; i++) {
                assertTrue(numbers.find());
                truth[i / 2][i % 2] = Double.parseDouble(numbers.group());
            }
            truth = Quads.canonical(truth);
            photos++;
            BoardFrameFinder.Result result =
                    BoardFrameFinder.find(TestPhotos.load(new File(folder, name.group(1))));
            if (result == null) {
                off.add(name.group(1) + " not found");
                continue;
            }
            found++;
            double worst = worstCornerError(truth, result.corners);
            if (worst <= 0.25) {
                withinQuarter++;
            } else {
                off.add(String.format(Locale.ROOT, "%s %.2f", name.group(1), worst));
            }
            if (worst <= 0.10) {
                withinTenth++;
            }
        }
        System.out.printf(
                "labelled photos: %d, found %d, all corners within 0.25 squares %d, within 0.10"
                        + " %d; off: %s%n",
                photos, found, withinQuarter, withinTenth, off);
        // The Python original: 55 of 55 within a quarter, 54 within a tenth.
        assertTrue("within a quarter of a square: " + withinQuarter, withinQuarter >= photos - 1);
        assertTrue("within a tenth of a square: " + withinTenth, withinTenth >= photos - 5);
    }

    /** Largest corner distance in squares of the labelled playing area, any cyclic order. */
    static double worstCornerError(double[][] truth, double[][] found) {
        double square = 0;
        for (int i = 0; i < 4; i++) {
            square += Quads.distance(truth[i], truth[(i + 1) % 4]) / 32;
        }
        double best = Double.MAX_VALUE;
        for (int shift = 0; shift < 4; shift++) {
            double worst = 0;
            for (int i = 0; i < 4; i++) {
                worst = Math.max(worst, Quads.distance(truth[i], found[(i + shift) % 4]));
            }
            best = Math.min(best, worst);
        }
        return best / square;
    }

    private static void assertCornersNear(double[][] expected, double[][] actual, double squares) {
        double error = worstCornerError(Quads.canonical(expected), actual);
        assertTrue("corners off by " + error + " squares", error <= squares);
    }

    /**
     * Photos of the dev app in which the board stands in the sun on a light wooden table and the
     * far side of the frame glares: found only on a smaller picture. Runs where the photos are;
     * which records are meant is listed in {@code glare.txt} beside them, one name a line.
     */
    @Test
    public void findsABoardWhoseFrameGlares() throws Exception {
        java.io.File records = new java.io.File("../training/samples/device/board-photos");
        java.io.File list = new java.io.File(records, "glare.txt");
        org.junit.Assume.assumeTrue("no such photos here", list.isFile());
        for (String line : java.nio.file.Files.readAllLines(list.toPath())) {
            String name = line.trim();
            if (name.isEmpty()) {
                continue;
            }
            BoardFrameFinder.Result found =
                    BoardFrameFinder.find(
                            TestPhotos.load(
                                    new java.io.File(
                                            new java.io.File(records, name), "photo.jpg")));
            org.junit.Assert.assertNotNull("no board found in " + name, found);
        }
    }
}
