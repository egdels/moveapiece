/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.puzzle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.github.bhlangonijr.chesslib.Side;
import de.schliweb.moveapiece.logic.ChessGame;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Guards the bundled puzzle set (puzzles/puzzles.csv, wired in as a test resource by
 * core/build.gradle) the way {@code OpeningRepositoryTest} guards the opening lines: every puzzle
 * must load and play through legally in the app's own chess model before it can ship.
 */
public class PuzzleRepositoryTest {

    private static PuzzleRepository bundled;

    @BeforeClass
    public static void loadBundledSet() throws IOException {
        try (InputStream in = PuzzleRepositoryTest.class.getResourceAsStream("/puzzles.csv")) {
            if (in == null) {
                fail("puzzles.csv not on the test classpath");
            }
            bundled = PuzzleRepository.load(in);
        }
    }

    @Test
    public void bundledSet_coversAllStrengths() {
        assertTrue("only " + bundled.size() + " puzzles", bundled.size() >= 4000);
        List<Puzzle> all = bundled.all();
        assertTrue("easiest: " + all.get(0).rating(), all.get(0).rating() < 700);
        assertTrue(
                "hardest: " + all.get(all.size() - 1).rating(),
                all.get(all.size() - 1).rating() >= 2600);
    }

    @Test
    public void bundledSet_isSortedByRating() {
        List<Puzzle> all = bundled.all();
        for (int i = 1; i < all.size(); i++) {
            assertTrue(
                    "out of order at " + all.get(i).id(),
                    all.get(i - 1).rating() <= all.get(i).rating());
        }
    }

    /**
     * Every line plays through legally from its FEN, the trainee is the side to move after the
     * opening move, and a line tagged as mate really ends in checkmate.
     */
    @Test
    public void everyPuzzle_playsThroughLegally() {
        for (Puzzle puzzle : bundled.all()) {
            ChessGame game = new ChessGame();
            try {
                game.loadFen(puzzle.fen());
            } catch (RuntimeException e) {
                fail("puzzle " + puzzle.id() + ": bad FEN " + puzzle.fen() + ": " + e);
            }
            List<String> moves = puzzle.moves();
            for (int i = 0; i < moves.size(); i++) {
                if (i == 1) {
                    assertEquals(
                            "puzzle " + puzzle.id() + ": trainee's side",
                            puzzle.humanSide(),
                            game.sideToMove());
                }
                if (game.isGameOver()) {
                    fail("puzzle " + puzzle.id() + ": game over before move " + (i + 1));
                }
                if (!game.applyUciMove(moves.get(i))) {
                    fail("puzzle " + puzzle.id() + ": illegal move " + moves.get(i));
                }
            }
            if (puzzle.themes().contains("mate")) {
                assertTrue("puzzle " + puzzle.id() + ": no checkmate", game.isCheckmate());
            }
        }
    }

    @Test
    public void byId_findsBundledPuzzleAndReturnsNullForUnknown() {
        Puzzle first = bundled.all().get(0);
        assertSame(first, bundled.byId(first.id()));
        assertNull(bundled.byId("no-such-id"));
        assertNull(bundled.byId(null));
    }

    @Test
    public void load_parsesFields() throws IOException {
        PuzzleRepository repo =
                load(
                        PuzzleRepository.HEADER
                                + "\n"
                                + "abc12,6k1/5ppp/8/8/8/8/5PPP/R5K1 b - - 0 1,g8f8 a1a8,600,"
                                + "mate mateIn1\n");
        Puzzle puzzle = repo.byId("abc12");
        assertEquals(List.of("g8f8", "a1a8"), puzzle.moves());
        assertEquals(600, puzzle.rating());
        assertEquals(Side.WHITE, puzzle.humanSide());
        assertEquals(List.of("mate", "mateIn1"), puzzle.themes());
    }

    @Test
    public void load_rejectsMalformedInput() {
        assertThrows(IllegalArgumentException.class, () -> load("PuzzleId,FEN,Moves\n"));
        assertThrows(
                IllegalArgumentException.class,
                () -> load(PuzzleRepository.HEADER + "\nabc,fen,e2e4,600\n"));
        assertThrows(
                IllegalArgumentException.class,
                () -> load(PuzzleRepository.HEADER + "\nabc,fen,e2e4 e7e5,high,\n"));
        // Odd number of moves: the line would end on the app's move.
        assertThrows(
                IllegalArgumentException.class,
                () -> load(PuzzleRepository.HEADER + "\nabc,fen,e2e4 e7e5 g1f3,600,\n"));
        assertThrows(
                IllegalArgumentException.class,
                () -> load(PuzzleRepository.HEADER + "\na,f,e2e4 e7e5,600,\na,f,d2d4 d7d5,700,\n"));
    }

    private static PuzzleRepository load(String content) throws IOException {
        return PuzzleRepository.load(
                new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
    }
}
