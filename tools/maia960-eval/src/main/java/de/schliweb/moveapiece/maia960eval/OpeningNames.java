/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.maia960eval;

import com.github.bhlangonijr.chesslib.move.Move;
import com.github.bhlangonijr.chesslib.move.MoveList;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Opening names from lichess-org/chess-openings (CC0; the TSV files are downloaded by reproduce.sh
 * into data/, see README.md): a line of play is named after the longest listed opening it starts
 * with.
 */
final class OpeningNames {

    static final String UNNAMED = "(not in the list)";

    private final Map<String, String> byLine = new HashMap<>();

    private OpeningNames() {}

    /** Reads every *.tsv (columns eco, name, pgn) in {@code dir}. */
    static OpeningNames load(Path dir) throws IOException {
        OpeningNames names = new OpeningNames();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "*.tsv")) {
            for (Path file : files) {
                List<String> lines = Files.readAllLines(file);
                for (String line : lines.subList(1, lines.size())) {
                    String[] f = line.split("\t");
                    MoveList moves = new MoveList();
                    moves.loadFromSan(f[2]);
                    List<String> uci = new ArrayList<>();
                    for (Move m : moves) {
                        uci.add(m.toString());
                    }
                    names.byLine.put(String.join(" ", uci), f[1]);
                }
            }
        }
        return names;
    }

    /** The name of the longest listed opening {@code plies} (UCI, space-separated) starts with. */
    String name(String plies) {
        String[] p = plies.isBlank() ? new String[0] : plies.split(" ");
        for (int n = p.length; n > 0; n--) {
            String name = byLine.get(String.join(" ", java.util.Arrays.copyOf(p, n)));
            if (name != null) {
                return name;
            }
        }
        return UNNAMED;
    }

    /** The opening's family: its name up to the first colon, e.g. "Sicilian Defense". */
    String family(String plies) {
        String name = name(plies);
        int colon = name.indexOf(':');
        return colon < 0 ? name : name.substring(0, colon);
    }
}
