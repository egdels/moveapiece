/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.chessnut.core.protocol;

/**
 * Message type bytes of the Chessnut Air protocol. Every message is {@code <type> <length>
 * <payload[length]>} in both directions; all values VERIFIED on hardware.
 */
public final class ChessnutMessageType {

    /** Board report, payload 36 bytes: 32 board bytes + 4-byte uptime counter (board → app). */
    public static final int BOARD_REPORT = 0x01;

    /** Set LEDs, payload 8 bytes, one bit per square (app → board). */
    public static final int SET_LEDS = 0x0A;

    /**
     * Beep, payload 4 bytes: frequency Hz and duration ms, each 16-bit big-endian (app → board).
     */
    public static final int BEEP = 0x0B;

    /** Button event, payload 1 byte (board → app). */
    public static final int BUTTON = 0x0F;

    /** Enable real-time board reports, payload {@code 00} (app → board). */
    public static final int ENABLE_REPORTS = 0x21;

    /** Generic acknowledgement, payload {@code 00}; follows every command (board → app). */
    public static final int ACK = 0x23;

    /** Battery request, payload {@code 00} (app → board). */
    public static final int BATTERY_REQUEST = 0x29;

    /** Battery reply, payload {@code <level> <flag>} (board → app). */
    public static final int BATTERY_STATUS = 0x2A;

    /** Payload of a {@link #BUTTON} message when NEW GAME was pressed. */
    public static final int BUTTON_NEW_GAME = 0x02;

    private ChessnutMessageType() {}
}
