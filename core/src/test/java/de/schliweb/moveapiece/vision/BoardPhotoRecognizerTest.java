/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.vision;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Test;

/**
 * The whole chain against photos of known positions, with the recogniser the app ships. The photos
 * are not in the repository; the test runs where {@code training/samples} holds them (see {@code
 * training/README.md}). The photos used here were part of the training of that recogniser, so this
 * shows that the Java chain works, not how good the recogniser is - for that see {@code
 * training/EVALUATION.md}.
 */
public class BoardPhotoRecognizerTest {

    private static final File TOOLS = new File("../training");
    private static final File MODEL = TestBoards.SHIPPED_RECOGNISER;
    private static final File MANIFEST = new File(TOOLS, "samples/boards.jsonl");

    @Test
    public void recognisesThePositionsOfTheTestPhotos() throws Exception {
        assumeTrue("no trained recogniser here", MODEL.isFile() && MANIFEST.isFile());

        Pattern field = Pattern.compile("\"(image|fen|series)\":\\s*\"([^\"]+)\"");
        int photos = 0;
        int pieces = 0;
        int right = 0;
        int boardsRight = 0;
        int flaggedWrong = 0;
        List<String> wrong = new ArrayList<>();
        try (BoardPhotoRecognizer recognizer =
                new BoardPhotoRecognizer(Files.readAllBytes(MODEL.toPath()))) {
            for (String line : Files.readAllLines(MANIFEST.toPath())) {
                String image = null;
                String fen = null;
                String series = null;
                Matcher m = field.matcher(line);
                while (m.find()) {
                    switch (m.group(1)) {
                        case "image":
                            image = m.group(2);
                            break;
                        case "fen":
                            fen = m.group(2);
                            break;
                        default:
                            series = m.group(2);
                    }
                }
                if (series == null || !series.startsWith("desk_")) {
                    continue; // a handful of photos is enough to see that the chain works
                }
                int[][] truth = PositionDecoderTest.board(fen);
                BoardPhotoRecognizer.Result result =
                        recognizer.recognize(
                                TestPhotos.load(new File(MANIFEST.getParentFile(), image)),
                                PositionDecoderTest.occupancy(truth));
                assertNotNull("no board found in " + image, result);
                int[][] found = PositionDecoderTest.board(result.placement);
                photos++;
                boolean allRight = true;
                for (int rank = 0; rank < 8; rank++) {
                    for (int file = 0; file < 8; file++) {
                        if (truth[rank][file] == 0) {
                            continue;
                        }
                        pieces++;
                        if (found[rank][file] == truth[rank][file]) {
                            right++;
                        } else {
                            allRight = false;
                            String square = "" + (char) ('a' + file) + (char) ('1' + rank);
                            wrong.add(
                                    image.substring(image.length() - 14, image.length() - 8)
                                            + ":"
                                            + square);
                            if (result.uncertainSquares.contains(square)) {
                                flaggedWrong++;
                            }
                        }
                    }
                }
                if (allRight) {
                    boardsRight++;
                }
            }
        }
        System.out.printf(
                "test photos: %d, pieces right %d of %d, boards completely right %d, wrong pieces"
                        + " flagged as uncertain %d of %d; wrong: %s%n",
                photos, right, pieces, boardsRight, flaggedWrong, pieces - right, wrong);
        assumeTrue("no test photos in the manifest", photos > 0);
        assertTrue("pieces right: " + right + " of " + pieces, right >= pieces * 0.96);
    }
}
