/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.chessnut.core.protocol;

import de.schliweb.pegasus.core.protocol.BoardState;

/**
 * Encoders for the commands the app writes to {@link ChessnutUuids#COMMAND_WRITE_CHARACTERISTIC}.
 */
public final class ChessnutCommands {

    private static final int LED_MASK_BYTES = 8;

    private ChessnutCommands() {}

    /** {@code 21 01 00}: start streaming board reports. Must be sent after every connect. */
    public static byte[] encodeEnableReports() {
        return new byte[] {(byte) ChessnutMessageType.ENABLE_REPORTS, 0x01, 0x00};
    }

    /** {@code 29 01 00}: ask for a {@link ChessnutBatteryStatus}. */
    public static byte[] encodeBatteryRequest() {
        return new byte[] {(byte) ChessnutMessageType.BATTERY_REQUEST, 0x01, 0x00};
    }

    /**
     * {@code 0a 08 <8 bytes>}: light exactly the given squares (in {@link BoardState} numbering, a8
     * = 0), all others off. Duplicates are fine; an empty list switches every LED off.
     */
    public static byte[] encodeLeds(int... boardSquareIndices) {
        byte[] out = new byte[2 + LED_MASK_BYTES];
        out[0] = (byte) ChessnutMessageType.SET_LEDS;
        out[1] = (byte) LED_MASK_BYTES;
        if (boardSquareIndices != null) {
            for (int square : boardSquareIndices) {
                int bit = ChessnutSquares.toChessnutIndex(square);
                out[2 + bit / 8] |= (byte) (1 << (bit % 8));
            }
        }
        return out;
    }

    /** All LEDs off. */
    public static byte[] encodeLedsOff() {
        return encodeLeds();
    }
}
