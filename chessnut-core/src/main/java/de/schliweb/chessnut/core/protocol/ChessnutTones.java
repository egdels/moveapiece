/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.chessnut.core.protocol;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * What the board's speaker says during a game. Ordinary moves and captures are silent, the LEDs
 * show them; a tone is kept for what the player must not miss: check, checkmate, a piece put where
 * it cannot go, and the first of the two NEW GAME presses that end a running game.
 */
public final class ChessnutTones {

    /** One tone of a sequence, see {@link ChessnutCommands#encodeBeep}. */
    public static final class Tone {
        public final int frequencyHz;
        public final int durationMs;

        Tone(int frequencyHz, int durationMs) {
            this.frequencyHz = frequencyHz;
            this.durationMs = durationMs;
        }
    }

    /** Silence between two tones of a sequence. */
    public static final int GAP_MS = 150;

    private static final Tone CHECK_TONE = new Tone(1800, 250);

    public static final List<Tone> CHECK = Collections.singletonList(CHECK_TONE);
    public static final List<Tone> CHECKMATE =
            Collections.unmodifiableList(Arrays.asList(CHECK_TONE, CHECK_TONE));
    public static final List<Tone> ILLEGAL_PLACEMENT =
            Collections.singletonList(new Tone(400, 300));
    public static final List<Tone> NEW_GAME_ARMED = Collections.singletonList(new Tone(1200, 70));

    private ChessnutTones() {}

    /** The tones for a move just made; empty unless it gives check or mate. */
    public static List<Tone> forMove(boolean check, boolean checkmate) {
        if (checkmate) {
            return CHECKMATE;
        }
        return check ? CHECK : Collections.emptyList();
    }
}
