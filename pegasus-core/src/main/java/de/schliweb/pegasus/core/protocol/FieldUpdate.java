/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.pegasus.core.protocol;

/**
 * Decoded DGT_MSG_FIELD_UPDATE (0x8E): a single square changed.
 *
 * <p>Payload format `[squareIndex, pieceCode]` is CONFIRMED_BY_REFERENCE_IMPLEMENTATION; whether
 * real hardware sends true piece codes or only occupied/empty is INFERRED
 * (docs/PEGASUS_PROTOCOL.md) — consumers should rely on {@link #isOccupied()}.
 */
public final class FieldUpdate {

    private final int squareIndex;
    private final int pieceCode;

    public FieldUpdate(int squareIndex, int pieceCode) {
        if (squareIndex < 0 || squareIndex >= BoardState.SQUARE_COUNT) {
            throw new IllegalArgumentException("Square index out of range: " + squareIndex);
        }
        this.squareIndex = squareIndex;
        this.pieceCode = pieceCode & 0xFF;
    }

    /** Decodes a 2-byte field update payload. */
    public static FieldUpdate fromPayload(byte[] payload) {
        if (payload == null || payload.length != 2) {
            throw new IllegalArgumentException(
                    "Field update payload must be 2 bytes, got "
                            + (payload == null ? "null" : payload.length));
        }
        return new FieldUpdate(payload[0] & 0xFF, payload[1] & 0xFF);
    }

    public int squareIndex() {
        return squareIndex;
    }

    public int pieceCode() {
        return pieceCode;
    }

    /** True if the square is now occupied (piece placed), false if lifted. */
    public boolean isOccupied() {
        return PieceCodes.isOccupied(pieceCode);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FieldUpdate)) {
            return false;
        }
        FieldUpdate other = (FieldUpdate) o;
        return squareIndex == other.squareIndex && pieceCode == other.pieceCode;
    }

    @Override
    public int hashCode() {
        return 31 * squareIndex + pieceCode;
    }

    @Override
    public String toString() {
        return String.format(
                "FieldUpdate[%s=0x%02X]", BoardState.squareName(squareIndex), pieceCode);
    }
}
