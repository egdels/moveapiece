/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.puzzle;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Picks the next puzzle: one at random from those rated close to the trainee, leaving out the ones
 * played recently. "Close" starts at {@link #INITIAL_WINDOW} points either side and widens in steps
 * until at least {@link #MIN_CANDIDATES} puzzles qualify, so that a trainee at either end of the
 * range, where the set is thin, still gets puzzles near their level.
 */
public final class PuzzleSelector {

    static final int INITIAL_WINDOW = 100;
    static final int WINDOW_STEP = 100;
    static final int MIN_CANDIDATES = 20;

    private PuzzleSelector() {}

    /**
     * The next puzzle for a trainee rated {@code rating}, or {@code null} if {@code puzzles} is
     * empty. Only if every puzzle was played recently is a recent one handed out again.
     */
    public static Puzzle next(
            List<Puzzle> puzzles, int rating, RecentPuzzles recent, Random random) {
        if (puzzles.isEmpty()) {
            return null;
        }
        List<Puzzle> candidates = candidates(puzzles, rating, recent);
        if (candidates.isEmpty()) {
            candidates = candidates(puzzles, rating, new RecentPuzzles());
        }
        return candidates.get(random.nextInt(candidates.size()));
    }

    private static List<Puzzle> candidates(List<Puzzle> puzzles, int rating, RecentPuzzles recent) {
        List<Puzzle> candidates = new ArrayList<>();
        for (int window = INITIAL_WINDOW; ; window += WINDOW_STEP) {
            candidates.clear();
            for (Puzzle puzzle : puzzles) {
                if (Math.abs(puzzle.rating() - rating) <= window && !recent.contains(puzzle.id())) {
                    candidates.add(puzzle);
                }
            }
            if (candidates.size() >= MIN_CANDIDATES || window > PuzzleRating.MAX) {
                return candidates;
            }
        }
    }
}
