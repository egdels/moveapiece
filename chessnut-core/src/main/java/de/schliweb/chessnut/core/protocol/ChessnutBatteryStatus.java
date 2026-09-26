/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.chessnut.core.protocol;

/**
 * Decoded battery reply ({@link ChessnutMessageType#BATTERY_STATUS}): {@code <level> <flag>}. Level
 * is a percentage, VERIFIED (100 fresh, 95 after a morning of sniffing). The flag has only ever
 * been observed as 0 while unplugged; it is kept raw until confirmed while charging.
 */
public final class ChessnutBatteryStatus {

    public static final int PAYLOAD_LENGTH = 2;

    private final int percent;
    private final int flag;

    private ChessnutBatteryStatus(int percent, int flag) {
        this.percent = percent;
        this.flag = flag;
    }

    public static ChessnutBatteryStatus fromPayload(byte[] payload) {
        if (payload == null || payload.length < PAYLOAD_LENGTH) {
            throw new IllegalArgumentException(
                    "Battery payload must be at least "
                            + PAYLOAD_LENGTH
                            + " bytes, got "
                            + (payload == null ? "null" : payload.length));
        }
        return new ChessnutBatteryStatus(Math.min(100, payload[0] & 0xFF), payload[1] & 0xFF);
    }

    /** Charge level 0–100. */
    public int percent() {
        return percent;
    }

    /** Raw second byte; 0 so far, meaning while charging not yet verified. */
    public int flag() {
        return flag;
    }

    public boolean isLow() {
        return percent <= 20;
    }

    @Override
    public String toString() {
        return "ChessnutBatteryStatus{" + percent + "%, flag=" + flag + "}";
    }
}
