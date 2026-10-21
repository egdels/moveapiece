/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.maia960eval;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.TreeMap;

/**
 * Appends every notable event (slot collision, wrong castle, illegal move, engine trouble) as one
 * tab-separated line with its FEN to events.tsv, and counts them by kind. Of the raw top slot not
 * being a legal move, which is common and harmless (see REPORT.md), only the first {@link
 * #SAMPLED_MAX} cases are written, as examples.
 */
final class EvalLog {

    private static final String SAMPLED_KIND = "top-slot-illegal";
    private static final int SAMPLED_MAX = 50;

    private final Path file;
    private final Map<String, Integer> counts = new TreeMap<>();

    EvalLog(Path file) {
        this.file = file;
    }

    synchronized void event(String kind, String gameId, int ply, String fen, String detail) {
        int count = counts.merge(kind, 1, Integer::sum);
        if (kind.equals(SAMPLED_KIND) && count > SAMPLED_MAX) {
            return;
        }
        String line = String.join("\t", kind, gameId, "" + ply, fen, detail) + "\n";
        try {
            Files.writeString(
                    file,
                    line,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (!kind.equals(SAMPLED_KIND) && !kind.equals("slot-collision")) {
            System.err.println("[" + kind + "] " + gameId + " ply " + ply + ": " + detail);
        }
    }

    synchronized Map<String, Integer> counts() {
        return new TreeMap<>(counts);
    }
}
