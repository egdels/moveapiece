/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.puzzle;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The bundled puzzle set, parsed from {@code puzzles/puzzles.csv} (built by {@code
 * tools/puzzle-export/export_puzzles.py}). Like the Maia model, the file itself is supplied by the
 * platform - a classpath resource on the desktop, an asset on Android - so this class only reads
 * from a stream.
 *
 * <p>The format is a header line {@code id,fen,moves,rating,themes} followed by one puzzle per
 * line. None of the fields can contain a comma (FEN, UCI moves and theme tags are separated by
 * spaces), so no CSV quoting is involved.
 */
public final class PuzzleRepository {

    static final String HEADER = "id,fen,moves,rating,themes";

    private final List<Puzzle> puzzles;
    private final Map<String, Puzzle> byId;

    private PuzzleRepository(List<Puzzle> puzzles) {
        this.puzzles = Collections.unmodifiableList(puzzles);
        Map<String, Puzzle> index = new HashMap<>();
        for (Puzzle puzzle : puzzles) {
            if (index.put(puzzle.id(), puzzle) != null) {
                throw new IllegalArgumentException("duplicate puzzle id " + puzzle.id());
            }
        }
        this.byId = index;
    }

    /**
     * Reads a puzzle file; the stream is read to its end but not closed.
     *
     * @throws IOException if the stream cannot be read
     * @throws IllegalArgumentException if the content is not a valid puzzle file
     */
    public static PuzzleRepository load(InputStream in) throws IOException {
        BufferedReader reader =
                new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        String header = reader.readLine();
        if (!HEADER.equals(header)) {
            throw new IllegalArgumentException("not a puzzle file, header: " + header);
        }
        List<Puzzle> puzzles = new ArrayList<>();
        String line;
        int lineNumber = 1;
        while ((line = reader.readLine()) != null) {
            lineNumber++;
            if (line.isEmpty()) {
                continue;
            }
            puzzles.add(parseLine(line, lineNumber));
        }
        return new PuzzleRepository(puzzles);
    }

    private static Puzzle parseLine(String line, int lineNumber) {
        String[] fields = line.split(",", -1);
        if (fields.length != 5) {
            throw new IllegalArgumentException(
                    "line " + lineNumber + ": expected 5 fields, got " + fields.length);
        }
        int rating;
        try {
            rating = Integer.parseInt(fields[3]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("line " + lineNumber + ": bad rating " + fields[3]);
        }
        List<String> themes = fields[4].isEmpty() ? List.of() : Arrays.asList(fields[4].split(" "));
        try {
            return new Puzzle(
                    fields[0], fields[1], Arrays.asList(fields[2].split(" ")), rating, themes);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("line " + lineNumber + ": " + e.getMessage(), e);
        }
    }

    /** Every puzzle, sorted by rating. */
    public List<Puzzle> all() {
        return puzzles;
    }

    public int size() {
        return puzzles.size();
    }

    /** The puzzle with {@code id}, or {@code null} if the set has none (e.g. after an update). */
    public Puzzle byId(String id) {
        return id == null ? null : byId.get(id);
    }
}
