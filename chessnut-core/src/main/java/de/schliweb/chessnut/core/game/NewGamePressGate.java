/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.chessnut.core.game;

import de.schliweb.chessnut.core.protocol.ChessnutDevice;
import java.util.function.LongSupplier;

/**
 * Decides what a press of the board's NEW GAME button means. With no game under way one press
 * starts the next one. A game in progress is only given up by two presses in quick succession, so a
 * bumped button cannot cost the game; the first press alone just arms the gate.
 */
public final class NewGamePressGate {

    /** What the host should do with a press. */
    public enum Decision {
        /** Start the new game. */
        RESTART,
        /** First press during a game: tell the player a second one is needed. */
        ARMED
    }

    /**
     * How long the first press stays armed. A little longer than the board layer's own double-press
     * window, since the two clocks are read at different moments.
     */
    public static final long CONFIRM_WINDOW_MS = ChessnutDevice.BUTTON_DOUBLE_PRESS_MS + 500;

    private final LongSupplier clockMs;
    private long armedAtMs = Long.MIN_VALUE / 2;

    public NewGamePressGate() {
        this(System::currentTimeMillis);
    }

    /** Test seam: injectable clock. */
    NewGamePressGate(LongSupplier clockMs) {
        this.clockMs = clockMs;
    }

    public Decision onPress(boolean gameInProgress) {
        long now = clockMs.getAsLong();
        boolean confirmed = now - armedAtMs <= CONFIRM_WINDOW_MS;
        if (!gameInProgress || confirmed) {
            reset();
            return Decision.RESTART;
        }
        armedAtMs = now;
        return Decision.ARMED;
    }

    /** Forgets a first press, e.g. once a new game was started some other way. */
    public void reset() {
        armedAtMs = Long.MIN_VALUE / 2;
    }
}
