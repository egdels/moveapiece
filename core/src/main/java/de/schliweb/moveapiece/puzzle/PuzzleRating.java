/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.puzzle;

/**
 * The trainee's own puzzle rating: a plain Elo rating played against each puzzle's Lichess rating.
 * It starts at {@link #INITIAL} and moves fast at first, so that it finds the trainee's level
 * without a question about it, then settles: the K factor starts at {@link #K_START} and falls with
 * every puzzle, {@code K_START / (1 + played / K_HALF_LIFE)}, down to {@link #K_MIN} (reached after
 * 35 puzzles).
 *
 * <p>Measured by simulating trainees of known strength on the bundled set (see the puzzle commit
 * that introduced this schedule): players between 900 and 1500 are rated within 100 points after
 * about 6 puzzles (a flat K of 40 needed about 20), players at 600 or 1800 after about 23 (40 to
 * 46); after 100 puzzles the rating scatters as little as with a flat K of 20 (about ±40). Since
 * every puzzle is picked close to the current rating, a solve can only bring about K/2, so very
 * strong players still need 50 or more puzzles - {@link PuzzleDifficulty} lets them skip ahead.
 *
 * <p>A puzzle counts as solved only without a wrong move, a hint or the solution shown; anything
 * else counts as lost. Immutable; {@link #toKey()} and {@link #fromKey} are the persisted form.
 *
 * @param rating the current rating, between {@link #MIN} and {@link #MAX}
 * @param played how many puzzles have been rated so far
 */
public record PuzzleRating(int rating, int played) {

    public static final int INITIAL = 1200;
    public static final int MIN = 400;
    public static final int MAX = 3000;

    static final double K_START = 160;
    static final double K_HALF_LIFE = 5;
    static final double K_MIN = 20;

    /** Where a new trainee starts. */
    public static final PuzzleRating START = new PuzzleRating(INITIAL, 0);

    public PuzzleRating {
        rating = clamp(rating);
        played = Math.max(0, played);
    }

    /** The rating after a puzzle of {@code puzzleRating} was solved or not. */
    public PuzzleRating after(int puzzleRating, boolean solved) {
        return new PuzzleRating(rating + delta(puzzleRating, solved), played + 1);
    }

    /** How many points {@link #after} adds (or, negative, takes away); within the rating range. */
    public int delta(int puzzleRating, boolean solved) {
        double expected = 1.0 / (1.0 + Math.pow(10.0, (puzzleRating - rating) / 400.0));
        double k = kFactor(played);
        int change = (int) Math.round(k * ((solved ? 1.0 : 0.0) - expected));
        return clamp(rating + change) - rating;
    }

    /** The K factor for the puzzle after {@code played} rated ones. */
    static double kFactor(int played) {
        return Math.max(K_MIN, K_START / (1.0 + played / K_HALF_LIFE));
    }

    /** The persisted form, {@code "rating:played"}. */
    public String toKey() {
        return rating + ":" + played;
    }

    /** Reads {@link #toKey()}; anything that does not parse is a fresh {@link #START}. */
    public static PuzzleRating fromKey(String key) {
        if (key == null) {
            return START;
        }
        String[] parts = key.split(":");
        if (parts.length != 2) {
            return START;
        }
        try {
            return new PuzzleRating(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
        } catch (NumberFormatException e) {
            return START;
        }
    }

    private static int clamp(int value) {
        return Math.max(MIN, Math.min(MAX, value));
    }
}
