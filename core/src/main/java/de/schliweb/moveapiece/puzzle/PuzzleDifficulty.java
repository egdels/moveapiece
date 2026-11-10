/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.puzzle;

/**
 * The "Easier / Normal / Harder" choice for puzzles in the New Game dialog: it shifts which puzzles
 * {@link PuzzleSelector} picks by a fixed number of points from the trainee's rating (see {@link
 * #target}). The rating itself is not touched - a harder puzzle solved simply counts for more - so
 * the choice only lets a trainee reach their level sooner than the rating alone would (see {@link
 * PuzzleRating}). {@link #key()} is the persisted form.
 */
public enum PuzzleDifficulty {
    EASIER("easier", -300),
    NORMAL("normal", 0),
    HARDER("harder", 300);

    private final String key;
    private final int offset;

    PuzzleDifficulty(String key, int offset) {
        this.key = key;
        this.offset = offset;
    }

    public String key() {
        return key;
    }

    /** The puzzle rating to aim at for a trainee rated {@code rating}. */
    public int target(int rating) {
        return rating + offset;
    }

    /** The difficulty persisted under {@code key}; {@link #NORMAL} for an unknown or null key. */
    public static PuzzleDifficulty fromKey(String key) {
        for (PuzzleDifficulty difficulty : values()) {
            if (difficulty.key.equals(key)) {
                return difficulty;
            }
        }
        return NORMAL;
    }
}
