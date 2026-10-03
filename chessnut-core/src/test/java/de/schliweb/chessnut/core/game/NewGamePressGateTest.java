/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.chessnut.core.game;

import static org.junit.Assert.assertEquals;

import de.schliweb.chessnut.core.game.NewGamePressGate.Decision;
import org.junit.Test;

public class NewGamePressGateTest {

    private long nowMs = 1_000_000;
    private final NewGamePressGate gate = new NewGamePressGate(() -> nowMs);

    @Test
    public void withNoGameUnderWayOnePressRestarts() {
        assertEquals(Decision.RESTART, gate.onPress(false));
        assertEquals(Decision.RESTART, gate.onPress(false));
    }

    @Test
    public void duringAGameTheSecondQuickPressRestarts() {
        assertEquals(Decision.ARMED, gate.onPress(true));
        nowMs += 800;
        assertEquals(Decision.RESTART, gate.onPress(true));
        // The restart used the arming up; the next game is protected again.
        nowMs += 800;
        assertEquals(Decision.ARMED, gate.onPress(true));
    }

    @Test
    public void aLateSecondPressOnlyArmsAgain() {
        assertEquals(Decision.ARMED, gate.onPress(true));
        nowMs += NewGamePressGate.CONFIRM_WINDOW_MS + 1;
        assertEquals(Decision.ARMED, gate.onPress(true));
        nowMs += 500;
        assertEquals(Decision.RESTART, gate.onPress(true));
    }

    @Test
    public void resetForgetsTheFirstPress() {
        assertEquals(Decision.ARMED, gate.onPress(true));
        gate.reset();
        nowMs += 500;
        assertEquals(Decision.ARMED, gate.onPress(true));
    }
}
