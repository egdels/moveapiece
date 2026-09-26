/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.chessnut.core.protocol;

import java.util.Arrays;

/** One decoded Chessnut message: type byte plus payload (without the length byte). */
public final class ChessnutFrame {

    private final int type;
    private final byte[] payload;

    public ChessnutFrame(int type, byte[] payload) {
        this.type = type & 0xFF;
        this.payload = payload == null ? new byte[0] : payload.clone();
    }

    public int type() {
        return type;
    }

    /** Defensive copy. */
    public byte[] payload() {
        return payload.clone();
    }

    public int payloadLength() {
        return payload.length;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ChessnutFrame)) {
            return false;
        }
        ChessnutFrame other = (ChessnutFrame) o;
        return type == other.type && Arrays.equals(payload, other.payload);
    }

    @Override
    public int hashCode() {
        return 31 * type + Arrays.hashCode(payload);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%02x %02x", type, payload.length));
        for (byte b : payload) {
            sb.append(String.format(" %02x", b & 0xFF));
        }
        return sb.toString();
    }
}
