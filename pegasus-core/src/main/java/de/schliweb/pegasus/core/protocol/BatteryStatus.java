/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.pegasus.core.protocol;

import de.schliweb.pegasus.core.util.HexUtil;

/**
 * Decoded DGT_MSG_BATTERY_STATUS (0xA0), 9-byte payload.
 *
 * <p>Byte 0 (percentage) and byte 8 (status bits) are CONFIRMED_BY_MANUFACTURER_SPEC (DGT
 * Chessboard Communication Protocol v1.2.1): byte 0 is the charge percentage, byte 8 has bit 2 =
 * "battery low" (yellow) and bit 3 = "battery empty" (red). Per that document, if both bits are set
 * the board shuts itself down within about 3 minutes. The remaining bytes (running/on/standby time
 * fields) are documented as "currently not used" and kept only in the raw payload.
 */
public final class BatteryStatus {

    public static final int PAYLOAD_LENGTH = 9;

    private final byte[] raw;

    private BatteryStatus(byte[] raw) {
        this.raw = raw;
    }

    /** Decodes a 9-byte battery status payload. */
    public static BatteryStatus fromPayload(byte[] payload) {
        if (payload == null || payload.length != PAYLOAD_LENGTH) {
            throw new IllegalArgumentException(
                    "Battery payload must be "
                            + PAYLOAD_LENGTH
                            + " bytes, got "
                            + (payload == null ? "null" : payload.length));
        }
        return new BatteryStatus(payload.clone());
    }

    /** Charge percentage (byte 0). */
    public int percent() {
        return raw[0] & 0xFF;
    }

    /** Status bits (byte 8 of the payload = byte 11 of the message, see class javadoc). */
    private int statusBits() {
        return raw[8] & 0xFF;
    }

    /** Bit 2: "battery low" (yellow) condition. */
    public boolean isLow() {
        return (statusBits() & 0x04) != 0;
    }

    /** Bit 3: "battery empty" (red) condition. */
    public boolean isEmpty() {
        return (statusBits() & 0x08) != 0;
    }

    /**
     * Both the "low" and "empty" bits are set: per the manufacturer's protocol document, the board
     * will shut itself down within about 3 minutes.
     */
    public boolean isCriticallyLow() {
        return isLow() && isEmpty();
    }

    /** Full raw payload for logging/verification. Defensive copy. */
    public byte[] rawPayload() {
        return raw.clone();
    }

    @Override
    public String toString() {
        return "BatteryStatus[percent=" + percent() + ", raw=" + HexUtil.toSpacedHex(raw) + "]";
    }
}
