/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.puzzle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.Test;

/** {@link PuzzleSelector} and {@link RecentPuzzles}. */
public class PuzzleSelectorTest {

    /** One puzzle every 10 points from 500 to 2790 (230 puzzles). */
    private static List<Puzzle> evenSet() {
        List<Puzzle> puzzles = new ArrayList<>();
        for (int rating = 500; rating < 2800; rating += 10) {
            puzzles.add(puzzle("p" + rating, rating));
        }
        return puzzles;
    }

    private static Puzzle puzzle(String id, int rating) {
        return new Puzzle(
                id, "8/8/8/8/8/8/8/8 w - - 0 1", List.of("a1a2", "a2a3"), rating, List.of());
    }

    @Test
    public void next_staysCloseToRating() {
        List<Puzzle> puzzles = evenSet();
        Random random = new Random(1);
        for (int i = 0; i < 200; i++) {
            Puzzle next = PuzzleSelector.next(puzzles, 1500, new RecentPuzzles(), random);
            assertTrue(next.rating() + " too far", Math.abs(next.rating() - 1500) <= 100);
        }
    }

    @Test
    public void next_leavesOutRecentPuzzles() {
        List<Puzzle> puzzles = evenSet();
        RecentPuzzles recent = new RecentPuzzles();
        Random random = new Random(2);
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 21; i++) { // exactly the puzzles within 100 points of 1500
            Puzzle next = PuzzleSelector.next(puzzles, 1500, recent, random);
            assertTrue("repeated " + next.id(), seen.add(next.id()));
            recent.add(next.id());
        }
    }

    @Test
    public void next_widensWindowWhereSetIsThin() {
        List<Puzzle> puzzles = evenSet();

        Puzzle next = PuzzleSelector.next(puzzles, 3000, new RecentPuzzles(), new Random(3));

        // Within 100 points of 3000 there is nothing; the window grows until 20 qualify.
        assertTrue(next.rating() >= 2600);
    }

    @Test
    public void next_repeatsRecentPuzzleOnlyWhenNothingElseIsLeft() {
        List<Puzzle> puzzles = List.of(puzzle("a", 1000), puzzle("b", 1000));
        RecentPuzzles recent = new RecentPuzzles();
        recent.add("a");

        assertEquals("b", PuzzleSelector.next(puzzles, 1000, recent, new Random(4)).id());
        recent.add("b");
        assertTrue(
                Set.of("a", "b")
                        .contains(PuzzleSelector.next(puzzles, 1000, recent, new Random(5)).id()));
    }

    @Test
    public void next_ofEmptySet_isNull() {
        assertNull(PuzzleSelector.next(List.of(), 1200, new RecentPuzzles(), new Random()));
    }

    @Test
    public void recent_dropsOldestBeyondCapacity() {
        RecentPuzzles recent = new RecentPuzzles(3);
        recent.add("a");
        recent.add("b");
        recent.add("c");
        recent.add("a"); // moves to the newest place
        recent.add("d"); // drops b

        assertEquals("c a d", recent.toKey());
        assertFalse(recent.contains("b"));
        assertTrue(recent.contains("a"));
        assertEquals(3, recent.size());
    }

    @Test
    public void recent_keyRoundTrips() {
        RecentPuzzles recent = RecentPuzzles.fromKey("x1 y2  z3 ");

        assertEquals("x1 y2 z3", recent.toKey());
        assertEquals(0, RecentPuzzles.fromKey(null).size());
        assertEquals(0, RecentPuzzles.fromKey("").size());
    }
}
