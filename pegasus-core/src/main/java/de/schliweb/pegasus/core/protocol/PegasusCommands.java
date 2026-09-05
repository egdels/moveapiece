/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.pegasus.core.protocol;

/**
 * Command encoder for the Pegasus protocol (app → board).
 *
 * <p>Source: DGTCentaurMods pegasus.py (GPL-3.0, protocol knowledge only, no code copied) plus the
 * classic DGT serial protocol; evidence levels per command are documented in
 * docs/PEGASUS_PROTOCOL.md and in the javadoc below. Single-byte request commands are written as
 * one byte to the UART write characteristic.
 */
public final class PegasusCommands {

    /** Semantic label used in developer logs before sending. */
    public static final String BOARD_STATE_REQUEST_LABEL = "REFERENCE_BOARD_STATE_REQUEST";

    private PegasusCommands() {}

    /** Semantic label for the reset command. */
    public static final String RESET_LABEL = "REFERENCE_RESET";

    /** Semantic label for the update-mode command. */
    public static final String UPDATE_MODE_LABEL = "REFERENCE_UPDATE_MODE";

    /** Encodes the reference board-state request: single byte 'B' (0x42). */
    public static byte[] encodeBoardStateRequest() {
        return new byte[] {0x42};
    }

    /**
     * Encodes the reset command '@' (0x40). Classic DGT serial protocol: DGT_SEND_RESET. The
     * official DGT app was observed sending '@' to the Pegasus (DGTCentaurMods pegasus.py comment).
     * Semantics on real Pegasus: INFERRED.
     */
    public static byte[] encodeReset() {
        return new byte[] {0x40};
    }

    /**
     * Encodes the update-mode command 'D' (0x44). Classic DGT serial protocol: DGT_SEND_UPDATE_BRD
     * (board sends field updates on piece changes). The official DGT app sends 'D' to the Pegasus;
     * the reference emulation ignores it. Semantics on real Pegasus: INFERRED.
     */
    public static byte[] encodeUpdateMode() {
        return new byte[] {0x44};
    }

    /**
     * Encodes the devkey command 0x63 0x07 0xBE 0xF5 0xAE 0xDD 0xA9 0x5F 0x00. Captured on real
     * hardware (full HCI snoop, 2026-08-16): the official DGT app sends exactly these 9 bytes right
     * after reset '@' during initialization. Without a valid devkey the board stays "locked": board
     * dumps report 0x7F for every square and no field updates (0x8E) are sent; with it, dumps
     * report real occupancy (0x01 occupied / 0x00 empty) and field updates flow. Payload semantics
     * (0x07 likely payload length, key possibly device-specific): UNKNOWN; byte sequence:
     * CONFIRMED_ON_HARDWARE (as sent by official app, verified to unlock).
     */
    public static byte[] encodeDevKey() {
        return new byte[] {
            0x63, 0x07, (byte) 0xBE, (byte) 0xF5, (byte) 0xAE, (byte) 0xDD, (byte) 0xA9, 0x5F, 0x00
        };
    }

    /** Encodes 'G' (0x47) → DGT_MSG_TRADEMARK (0x92). CONFIRMED_BY_REFERENCE_IMPLEMENTATION. */
    public static byte[] encodeTrademarkRequest() {
        return new byte[] {0x47};
    }

    /** Encodes 'M' (0x4D) → DGT_MSG_VERSION (0x93). CONFIRMED_BY_REFERENCE_IMPLEMENTATION. */
    public static byte[] encodeVersionRequest() {
        return new byte[] {0x4D};
    }

    /**
     * Encodes 'H' (0x48) → DGT_MSG_HARDWARE_VERSION (0x96). CONFIRMED_BY_REFERENCE_IMPLEMENTATION.
     */
    public static byte[] encodeHardwareVersionRequest() {
        return new byte[] {0x48};
    }

    /** Encodes 'E' (0x45) → DGT_MSG_SERIALNR (0x91). CONFIRMED_BY_REFERENCE_IMPLEMENTATION. */
    public static byte[] encodeSerialNumberRequest() {
        return new byte[] {0x45};
    }

    /** Encodes 'U' (0x55) → DGT_MSG_LONG_SERIALNR (0xA2). CONFIRMED_BY_REFERENCE_IMPLEMENTATION. */
    public static byte[] encodeLongSerialNumberRequest() {
        return new byte[] {0x55};
    }

    /**
     * Encodes 'L' (0x4C) → DGT_MSG_BATTERY_STATUS (0xA0). CONFIRMED_BY_REFERENCE_IMPLEMENTATION.
     */
    public static byte[] encodeBatteryRequest() {
        return new byte[] {0x4C};
    }

    /**
     * Encodes the all-LEDs-off command: {@code 0x60 0x02 0x00 0x00}.
     * CONFIRMED_BY_REFERENCE_IMPLEMENTATION (docs/PEGASUS_PROTOCOL.md).
     */
    public static byte[] encodeLedsOff() {
        return new byte[] {0x60, 0x02, 0x00, 0x00};
    }

    /**
     * Encodes the LED command lighting the given squares: {@code 0x60 <lenPayload> 0x05 <ledSpeed>
     * <mode> <intensity> <squareIndex…> 0x00}. Format CONFIRMED_BY_REFERENCE_IMPLEMENTATION; value
     * ranges of {@code ledSpeed}/{@code intensity} and mode semantics on real hardware are
     * UNKNOWN/INFERRED (docs/PEGASUS_PROTOCOL.md).
     *
     * @param ledSpeed speed byte (range unknown; emulation passes through)
     * @param mode 0 = normal, 1 = move/check LED (auto-off in emulation)
     * @param intensity brightness byte (range unknown)
     * @param squareIndices DGT square indices (0 = a8 … 63 = h1), at least one
     */
    public static byte[] encodeLeds(int ledSpeed, int mode, int intensity, int... squareIndices) {
        if (squareIndices == null || squareIndices.length == 0) {
            throw new IllegalArgumentException("At least one square index required");
        }
        if (mode != 0 && mode != 1) {
            throw new IllegalArgumentException("Mode must be 0 or 1: " + mode);
        }
        byte[] out = new byte[6 + squareIndices.length + 1];
        out[0] = 0x60;
        out[1] = (byte) (out.length - 2); // payload length after the length byte
        out[2] = 0x05;
        out[3] = (byte) ledSpeed;
        out[4] = (byte) mode;
        out[5] = (byte) intensity;
        for (int i = 0; i < squareIndices.length; i++) {
            int index = squareIndices[i];
            if (index < 0 || index > 63) {
                throw new IllegalArgumentException("Square index out of range: " + index);
            }
            out[6 + i] = (byte) index;
        }
        out[out.length - 1] = 0x00;
        return out;
    }
}
