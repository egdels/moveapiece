/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.pegasus.core.protocol;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class PegasusLedControllerTest {

    private final List<byte[]> written = new ArrayList<>();
    private final PegasusLedController controller = new PegasusLedController(written::add);

    @Test
    public void showMoveLightsFromAndToSquares() {
        controller.showMove(52, 36); // e2, e4
        assertEquals(1, written.size());
        assertArrayEquals(
                PegasusCommands.encodeLeds(
                        PegasusLedController.DEFAULT_SPEED,
                        PegasusLedController.MODE_STEADY,
                        PegasusLedController.DEFAULT_INTENSITY,
                        52,
                        36),
                written.get(0));
    }

    @Test
    public void showSquaresPulsingUsesPulseSpeed() {
        controller.showSquaresPulsing(4); // e1
        assertEquals(1, written.size());
        assertArrayEquals(
                PegasusCommands.encodeLeds(
                        PegasusLedController.SPEED_PULSE,
                        PegasusLedController.MODE_STEADY,
                        PegasusLedController.DEFAULT_INTENSITY,
                        4),
                written.get(0));
    }

    @Test
    public void showSquaresAcceptsCollection() {
        controller.showSquares(Arrays.asList(0, 63));
        assertEquals(1, written.size());
        assertArrayEquals(
                PegasusCommands.encodeLeds(
                        PegasusLedController.DEFAULT_SPEED,
                        PegasusLedController.MODE_STEADY,
                        PegasusLedController.DEFAULT_INTENSITY,
                        0,
                        63),
                written.get(0));
    }

    @Test
    public void emptyCollectionTurnsLedsOff() {
        controller.showSquares(12);
        controller.showSquares(new ArrayList<>());
        assertEquals(2, written.size());
        assertArrayEquals(PegasusCommands.encodeLedsOff(), written.get(1));
    }

    @Test
    public void identicalConsecutivePatternsAreDeduplicated() {
        controller.showSquares(52, 36);
        controller.showSquares(52, 36);
        assertEquals(1, written.size());
        controller.showSquares(36, 52); // different order = different command
        assertEquals(2, written.size());
    }

    @Test
    public void offIsSkippedWhenNothingWasLit() {
        controller.off();
        assertTrue(written.isEmpty());
    }

    @Test
    public void offAfterShowSendsAllOffOnce() {
        controller.showSquares(1);
        controller.off();
        controller.off();
        assertEquals(2, written.size());
        assertArrayEquals(PegasusCommands.encodeLedsOff(), written.get(1));
    }

    @Test
    public void forceOffAlwaysSends() {
        controller.forceOff();
        assertEquals(1, written.size());
        assertArrayEquals(PegasusCommands.encodeLedsOff(), written.get(0));
    }

    @Test
    public void samePatternAfterOffIsSentAgain() {
        controller.showSquares(5);
        controller.off();
        controller.showSquares(5);
        assertEquals(3, written.size());
    }

    @Test
    public void resendRepeatsTheCurrentPatternBypassingDedupe() {
        controller.showSquares(52, 36);
        controller.resend();
        controller.resend();
        assertEquals(3, written.size());
        assertArrayEquals(written.get(0), written.get(1));
        assertArrayEquals(written.get(0), written.get(2));
    }

    @Test
    public void resendIsNoOpWhenNothingIsLit() {
        controller.resend();
        assertTrue(written.isEmpty());
    }

    @Test
    public void resendIsNoOpAfterOff() {
        controller.showSquares(52, 36);
        controller.off();
        controller.resend();
        assertEquals(2, written.size()); // show + off, no third write
    }

    @Test
    public void customSpeedAndIntensityAreUsed() {
        PegasusLedController custom = new PegasusLedController(written::add, 7, 3);
        custom.showSquares(10);
        assertArrayEquals(
                PegasusCommands.encodeLeds(7, PegasusLedController.MODE_STEADY, 3, 10),
                written.get(0));
    }
}
