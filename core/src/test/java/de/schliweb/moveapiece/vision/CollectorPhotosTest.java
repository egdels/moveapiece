/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.vision;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Test;

/**
 * The chain as the app runs it against photos of played positions taken with the collector app
 * (surroundings 6 of the Pegasus): each photo alone, and the photos of a position read together.
 * The photos are not in the repository; the test runs where {@code training/samples} holds them.
 * They were part of the training of the shipped recogniser, so this shows that the Java chain works
 * and agrees with the Python one, not how good the recogniser is ({@code training/EVALUATION.md}).
 */
public class CollectorPhotosTest {

    private static final File TOOLS = new File("../training");
    private static final File MODEL = TestBoards.SHIPPED_RECOGNISER;
    private static final File PHOTOS =
            new File(TOOLS, "samples/collector/collection/boards/Pegasus/surroundings_6");

    @Test
    public void readsPlayedPositionsAloneAndTogether() throws Exception {
        assumeTrue("no recogniser or photos here", MODEL.isFile() && PHOTOS.isDirectory());
        Pattern fenField = Pattern.compile("\"fen\":\\s*\"([^\"]+)\"");
        int photos = 0;
        int alone = 0;
        int positions = 0;
        int together = 0;
        try (BoardPhotoRecognizer recognizer =
                new BoardPhotoRecognizer(Files.readAllBytes(MODEL.toPath()))) {
            File[] folders = PHOTOS.listFiles(File::isDirectory);
            Arrays.sort(folders);
            for (File folder : folders) {
                File[] notes =
                        folder.listFiles(
                                (dir, name) ->
                                        name.endsWith(".json") && !name.endsWith("_differs.json"));
                Arrays.sort(notes);
                List<BoardPhotoRecognizer.Reading> readings = new ArrayList<>();
                int[][] truth = null;
                for (File note : notes) {
                    String text =
                            new String(Files.readAllBytes(note.toPath()), StandardCharsets.UTF_8);
                    Matcher m = fenField.matcher(text);
                    if (!m.find()) {
                        continue;
                    }
                    truth = PositionDecoderTest.board(m.group(1));
                    File jpg = new File(folder, note.getName().replace(".json", ".jpg"));
                    BoardPhotoRecognizer.Reading reading = recognizer.read(TestPhotos.load(jpg));
                    if (reading == null) {
                        continue;
                    }
                    readings.add(reading);
                    photos++;
                    String placement =
                            recognizer.recognize(
                                            java.util.Collections.singletonList(reading),
                                            PositionDecoderTest.occupancy(truth))
                                    .placement;
                    alone += Arrays.deepEquals(PositionDecoderTest.board(placement), truth) ? 1 : 0;
                }
                if (readings.size() > 1) {
                    positions++;
                    String placement =
                            recognizer.recognize(readings, PositionDecoderTest.occupancy(truth))
                                    .placement;
                    together +=
                            Arrays.deepEquals(PositionDecoderTest.board(placement), truth) ? 1 : 0;
                }
            }
        }
        System.out.printf(
                "collector photos: %d, boards right alone %d; positions %d, right with all their"
                        + " photos together %d%n",
                photos, alone, positions, together);
        assumeTrue("no photos found", photos > 0);
        assertTrue("boards right alone: " + alone + " of " + photos, alone >= photos * 0.55);
    }
}
