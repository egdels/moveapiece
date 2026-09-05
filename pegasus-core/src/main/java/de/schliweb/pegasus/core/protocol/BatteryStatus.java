/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.pegasus.core.protocol;

import de.schliweb.pegasus.core.util.HexUtil;

/**
 * Decoded DGT_MSG_BATTERY_STATUS (0xA0), 9-byte payload.
 *
 * <p>Byte 0 is presumed to be the charge percentage (0x58 ≈ 88 observed as "about 100 %" in the
 * reference emulation — INFERRED); the remaining bytes are UNKNOWN and kept raw for hardware
 * verification (docs/PEGASUS_PROTOCOL.md).
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

    /** Presumed charge percentage (byte 0, semantics INFERRED). */
    public int percent() {
        return raw[0] & 0xFF;
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
