/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.logic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

/**
 * Covers the header-only PGN scan used to offer a "pick which game" list for multi-game files
 * without invoking chesslib's full per-game parser.
 */
public class PgnGamesTest {

    private static final String GAME_1 =
            "[Event \"Test Open\"]\n[White \"Alice\"]\n[Black \"Bob\"]\n[Date \"2024.01.02\"]\n\n1. e4 e5 *\n";
    private static final String GAME_2 =
            "[Event \"Test Open\"]\n[White \"Carol\"]\n[Black \"Dave\"]\n[Date \"2024.01.03\"]\n\n1. d4 d5 *\n";

    @Test
    public void splitGames_singleGame_returnsOneBlock() {
        List<String> games = PgnGames.splitGames(GAME_1);
        assertEquals(1, games.size());
        assertTrue(games.get(0).contains("Alice"));
    }

    @Test
    public void splitGames_multipleGames_splitsAtEventTag() {
        List<String> games = PgnGames.splitGames(GAME_1 + GAME_2);
        assertEquals(2, games.size());
        assertTrue(games.get(0).contains("Alice"));
        assertTrue(games.get(0).contains("Bob"));
        assertTrue(games.get(1).contains("Carol"));
        assertTrue(games.get(1).contains("Dave"));
    }

    @Test
    public void summarize_includesPlayersAndDate() {
        assertEquals("Alice – Bob (2024.01.02)", PgnGames.summarize(GAME_1));
    }

    @Test
    public void summarize_missingTags_fallsBackToPlaceholders() {
        assertEquals("? – ?", PgnGames.summarize("1. e4 e5 *\n"));
    }

    @Test
    public void summarize_unknownDate_omitsDateSuffix() {
        String withUnknownDate =
                "[Event \"Test\"]\n[White \"Alice\"]\n[Black \"Bob\"]\n[Date \"????.??.??\"]\n\n1. e4 *\n";
        assertEquals("Alice – Bob", PgnGames.summarize(withUnknownDate));
    }
}
