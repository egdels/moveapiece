/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.pegasus.core.protocol;

import de.schliweb.pegasus.core.util.HexUtil;
import java.util.Arrays;

/** Immutable reassembled Pegasus message frame (type byte + payload). */
public final class PegasusFrame {

    private final int type;
    private final byte[] payload;

    public PegasusFrame(int type, byte[] payload) {
        this.type = type & 0xFF;
        this.payload = payload == null ? new byte[0] : payload.clone();
    }

    /** Message type byte (bit 7 set), e.g. 0x86 for BOARD_DUMP. */
    public int type() {
        return type;
    }

    /** Payload without the 3-byte header. Defensive copy. */
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
        if (!(o instanceof PegasusFrame)) {
            return false;
        }
        PegasusFrame other = (PegasusFrame) o;
        return type == other.type && Arrays.equals(payload, other.payload);
    }

    @Override
    public int hashCode() {
        return 31 * type + Arrays.hashCode(payload);
    }

    @Override
    public String toString() {
        return PegasusMessageType.name(type) + "[" + HexUtil.toSpacedHex(payload) + "]";
    }
}
