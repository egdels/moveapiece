/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.chessnut.core.protocol;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import de.schliweb.pegasus.core.protocol.BoardState;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class ChessnutLedControllerTest {

    private final List<byte[]> written = new ArrayList<>();
    private final ChessnutLedController leds = new ChessnutLedController(d -> written.add(d));

    private static int sq(String name) {
        return BoardState.squareIndex(name);
    }

    @Test
    public void showMoveLightsBothSquares() {
        leds.showMove(sq("e2"), sq("e4"));

        assertEquals(1, written.size());
        assertArrayEquals(ChessnutCommands.encodeLeds(sq("e2"), sq("e4")), written.get(0));
        assertTrue(leds.isAnyLit());
    }

    @Test
    public void dedupesIdenticalConsecutivePatterns() {
        leds.showSquares(sq("a1"));
        leds.showSquares(sq("a1"));
        leds.showSquares(Arrays.asList(sq("a1")));

        assertEquals(1, written.size());
    }

    @Test
    public void offIsSkippedWhenNothingLitButForceOffAlwaysSends() {
        leds.off();
        assertEquals(0, written.size());

        leds.forceOff();
        assertEquals(1, written.size());
        assertArrayEquals(ChessnutCommands.encodeLedsOff(), written.get(0));
        assertFalse(leds.isAnyLit());
    }

    @Test
    public void emptyPatternSwitchesOff() {
        leds.showSquares(sq("h8"));
        leds.showSquares();
        leds.showSquares(Arrays.asList());

        assertEquals(2, written.size());
        assertArrayEquals(ChessnutCommands.encodeLedsOff(), written.get(1));
        assertFalse(leds.isAnyLit());
    }

    @Test
    public void resendRepeatsCurrentPatternOnly() {
        leds.resend();
        assertEquals(0, written.size());

        leds.showSquares(sq("d4"));
        leds.resend();
        assertEquals(2, written.size());
        assertArrayEquals(written.get(0), written.get(1));

        leds.resetTracking();
        leds.resend();
        assertEquals(2, written.size());
        assertFalse(leds.isAnyLit());
    }
}
