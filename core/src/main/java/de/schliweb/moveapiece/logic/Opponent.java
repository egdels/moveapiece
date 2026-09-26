/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.logic;

/**
 * Who (or what) sits across the board in a new game, as chosen in the "New Game" dialog on both
 * front ends. The stable {@link #key()} is what gets persisted (see {@link GameSetup}), so renaming
 * an enum constant later does not silently reset anyone's saved choice.
 */
public enum Opponent {
    HUMAN("human"),
    STOCKFISH("stockfish"),
    MAIA("maia"),
    TRAINER("trainer");

    private final String key;

    Opponent(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    /** The opponent persisted under {@code key}; {@link #STOCKFISH} for an unknown or null key. */
    public static Opponent fromKey(String key) {
        for (Opponent opponent : values()) {
            if (opponent.key.equals(key)) {
                return opponent;
            }
        }
        return STOCKFISH;
    }
}
