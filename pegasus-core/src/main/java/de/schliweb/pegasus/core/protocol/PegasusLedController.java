/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.pegasus.core.protocol;

import java.util.Arrays;
import java.util.Collection;

/**
 * Semantic LED operations on top of the low-level {@link PegasusCommands} LED encoding (0x60…):
 * show a move (from+to), highlight arbitrary squares (e.g. a board mismatch) and switch all LEDs
 * off.
 *
 * <p>Speed/intensity semantics were calibrated on real hardware (2026-08-17,
 * docs/PEGASUS_PROTOCOL.md "Hardware-Kalibrierung LEDs & Keepalive"): small speed values
 * alternate-blink the squares (0x02 = normal), speed 0xFF pulses all squares simultaneously
 * ("breathing"); intensity is inverse (0x00 = dark, 0x01 = brightest, larger = dimmer); mode 1
 * flashes once for ~0.5 s and then auto-offs.
 *
 * <p>Consecutive identical commands are deduplicated; {@link #off()} is only sent when LEDs might
 * be lit. Not thread-safe; drive from one thread.
 */
public final class PegasusLedController {

    /** Default speed: normal alternate blinking (CONFIRMED_ON_HARDWARE). */
    public static final int DEFAULT_SPEED = 0x02;

    /** Speed 0xFF: all squares pulse simultaneously ("breathing"). */
    public static final int SPEED_PULSE = 0xFF;

    /** Default intensity; scale is inverse: 0x00 dark, 0x01 brightest. */
    public static final int DEFAULT_INTENSITY = 0x05;

    /** Brightest intensity value (inverse scale, CONFIRMED_ON_HARDWARE). */
    public static final int INTENSITY_BRIGHTEST = 0x01;

    /** LED mode 0 = pattern stays lit until overwritten or switched off. */
    public static final int MODE_STEADY = 0;

    /** LED mode 1 = single short flash (~0.5 s), then hardware auto-off. */
    public static final int MODE_MOVE = 1;

    private final PegasusDevice.CommandSink sink;
    private final int speed;
    private final int intensity;

    private byte[] lastCommand;
    private boolean anyLit;

    public PegasusLedController(PegasusDevice.CommandSink sink) {
        this(sink, DEFAULT_SPEED, DEFAULT_INTENSITY);
    }

    public PegasusLedController(PegasusDevice.CommandSink sink, int speed, int intensity) {
        if (sink == null) {
            throw new IllegalArgumentException("sink must not be null");
        }
        this.sink = sink;
        this.speed = speed;
        this.intensity = intensity;
    }

    /** Lights the from and to squares of a move (steady until {@link #off()}). */
    public void showMove(int fromSquare, int toSquare) {
        showSquares(fromSquare, toSquare);
    }

    /** Lights the given DGT square indices (steady until {@link #off()}). */
    public void showSquares(int... squares) {
        send(PegasusCommands.encodeLeds(speed, MODE_STEADY, intensity, squares));
    }

    /**
     * Lights the given DGT square indices in "breathing" pulse mode ({@link #SPEED_PULSE}) rather
     * than this controller's configured alternate-blink speed - a background status indicator (e.g.
     * king in check) meant to read differently from a move indication. Steady until {@link #off()}
     * or another {@code showSquares*} call overwrites it; shares this controller's dedupe/resend
     * tracking like any other pattern.
     */
    public void showSquaresPulsing(int... squares) {
        send(PegasusCommands.encodeLeds(SPEED_PULSE, MODE_STEADY, intensity, squares));
    }

    /** Lights the given DGT square indices (steady until {@link #off()}). */
    public void showSquares(Collection<Integer> squares) {
        if (squares == null || squares.isEmpty()) {
            off();
            return;
        }
        int[] indices = new int[squares.size()];
        int i = 0;
        for (int square : squares) {
            indices[i++] = square;
        }
        showSquares(indices);
    }

    /** Switches all LEDs off (skipped when this controller lit nothing). */
    public void off() {
        if (!anyLit) {
            return;
        }
        forceOff();
    }

    /** Forgets the tracked LED state without sending (e.g. after reconnect). */
    public void resetTracking() {
        anyLit = false;
        lastCommand = null;
    }

    /**
     * Re-sends the currently displayed pattern unconditionally, bypassing the dedupe in {@link
     * #send}. No-op if nothing is currently lit.
     *
     * <p>Real Pegasus hardware has been observed to clear an already-lit pattern on its own when
     * the physical occupancy of an indicated square changes (e.g. a captured piece being lifted
     * off), even though the app never sent an off/overwrite command (confirmed on real hardware).
     * Callers that need the indication to stay visible across such changes — regardless of how fast
     * or slowly the player acts, so purely event-driven and never on a timer — should call this
     * after every relevant physical event.
     */
    public void resend() {
        if (!anyLit || lastCommand == null) {
            return;
        }
        sink.write(lastCommand);
    }

    /** Always sends the all-off command (e.g. UI button, reconnect cleanup). */
    public void forceOff() {
        sink.write(PegasusCommands.encodeLedsOff());
        anyLit = false;
        lastCommand = null;
    }

    private void send(byte[] command) {
        if (anyLit && Arrays.equals(command, lastCommand)) {
            return; // dedupe identical consecutive LED patterns
        }
        sink.write(command);
        lastCommand = command;
        anyLit = true;
    }
}
