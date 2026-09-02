/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight, chesslib-free scan of a PGN text's game boundaries and header tags - used only to
 * offer a "pick which game" list for a multi-game file without paying chesslib's full per-game
 * SAN-decode cost just to show a preview (see {@link ChessGame#loadPgn}, which does the real,
 * authoritative parse of whichever single block is ultimately picked).
 */
public final class PgnGames {

    private static final Pattern TAG = Pattern.compile("\\[(\\w+)\\s+\"([^\"]*)\"]");

    private PgnGames() {}

    /** Splits {@code pgnText} into one block per game, each starting at its "[Event ...]" tag. */
    public static List<String> splitGames(String pgnText) {
        List<String> blocks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean started = false;
        for (String line : pgnText.split("\n", -1)) {
            if (line.trim().startsWith("[Event ") && started && current.length() > 0) {
                blocks.add(current.toString());
                current.setLength(0);
            }
            current.append(line).append('\n');
            started = true;
        }
        if (current.length() > 0) {
            blocks.add(current.toString());
        }
        return blocks;
    }

    /** "White – Black (Date)" summary from a single game block's header tags, for a picker list. */
    public static String summarize(String gameBlock) {
        String white = "?";
        String black = "?";
        String date = null;
        Matcher m = TAG.matcher(gameBlock);
        while (m.find()) {
            switch (m.group(1)) {
                case "White":
                    white = m.group(2);
                    break;
                case "Black":
                    black = m.group(2);
                    break;
                case "Date":
                    date = m.group(2);
                    break;
                default:
                    break;
            }
        }
        String summary = white + " – " + black;
        return date != null && !date.isEmpty() && !date.equals("????.??.??")
                ? summary + " (" + date + ")"
                : summary;
    }
}
