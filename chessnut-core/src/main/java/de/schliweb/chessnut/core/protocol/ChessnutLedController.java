/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.chessnut.core.protocol;

import java.util.Arrays;
import java.util.Collection;

/**
 * Stateful LED helper on top of {@link ChessnutCommands#encodeLeds(int...)}: dedupes identical
 * consecutive patterns, skips a redundant off, and can re-send the current pattern after a
 * reconnect. Same shape as the Pegasus controller so a game bridge can treat both alike.
 *
 * <p>The Chessnut has no speed or intensity settings; a pattern is steady until overwritten.
 */
public final class ChessnutLedController {

    private final ChessnutDevice.CommandSink sink;

    private byte[] lastCommand;
    private boolean anyLit;

    public ChessnutLedController(ChessnutDevice.CommandSink sink) {
        if (sink == null) {
            throw new IllegalArgumentException("sink must not be null");
        }
        this.sink = sink;
    }

    /** Lights the from and to squares of a move (steady until {@link #off()}). */
    public void showMove(int fromSquare, int toSquare) {
        showSquares(fromSquare, toSquare);
    }

    /** Lights exactly the given {@code BoardState} square indices (steady until {@link #off()}). */
    public void showSquares(int... squares) {
        if (squares == null || squares.length == 0) {
            off();
            return;
        }
        send(ChessnutCommands.encodeLeds(squares));
    }

    /** Collection variant of {@link #showSquares(int...)}; empty switches off. */
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

    /** Whether this controller currently believes a pattern is lit. */
    public boolean isAnyLit() {
        return anyLit;
    }

    /** Forgets the tracked LED state without sending (e.g. after reconnect). */
    public void resetTracking() {
        anyLit = false;
        lastCommand = null;
    }

    /** Re-sends the current pattern unconditionally; no-op if nothing is lit. */
    public void resend() {
        if (!anyLit || lastCommand == null) {
            return;
        }
        sink.write(lastCommand);
    }

    /** Always sends the all-off command (e.g. UI button, reconnect cleanup). */
    public void forceOff() {
        sink.write(ChessnutCommands.encodeLedsOff());
        anyLit = false;
        lastCommand = null;
    }

    private void send(byte[] command) {
        if (anyLit && Arrays.equals(command, lastCommand)) {
            return;
        }
        sink.write(command);
        lastCommand = command;
        anyLit = true;
    }
}
