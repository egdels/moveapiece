/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.chessnut.core.protocol;

import de.schliweb.pegasus.core.protocol.BoardState;

/**
 * Decoded board report ({@link ChessnutMessageType#BOARD_REPORT}): the full 64-square state and the
 * board's uptime counter. The board streams these about ten times per second while reporting is
 * enabled, whether or not anything changed.
 */
public final class ChessnutBoardReport {

    /** 32 board bytes plus 4 counter bytes. */
    public static final int PAYLOAD_LENGTH = 36;

    static final int BOARD_BYTES = 32;

    private final BoardState state;
    private final long uptimeSeconds;

    private ChessnutBoardReport(BoardState state, long uptimeSeconds) {
        this.state = state;
        this.uptimeSeconds = uptimeSeconds;
    }

    /** Decodes a 36-byte payload; throws on any other length. */
    public static ChessnutBoardReport fromPayload(byte[] payload) {
        if (payload == null || payload.length != PAYLOAD_LENGTH) {
            throw new IllegalArgumentException(
                    "Board report payload must be "
                            + PAYLOAD_LENGTH
                            + " bytes, got "
                            + (payload == null ? "null" : payload.length));
        }
        byte[] codes = new byte[BoardState.SQUARE_COUNT];
        for (int i = 0; i < BOARD_BYTES; i++) {
            int b = payload[i] & 0xFF;
            codes[ChessnutSquares.toBoardIndex(2 * i)] =
                    (byte) ChessnutPieceCodes.toPieceCode(b & 0x0F);
            codes[ChessnutSquares.toBoardIndex(2 * i + 1)] =
                    (byte) ChessnutPieceCodes.toPieceCode(b >>> 4);
        }
        long counter =
                (payload[32] & 0xFFL)
                        | (payload[33] & 0xFFL) << 8
                        | (payload[34] & 0xFFL) << 16
                        | (payload[35] & 0xFFL) << 24;
        return new ChessnutBoardReport(BoardState.fromBoardDumpPayload(codes), counter);
    }

    /** Full board with real piece identities, in {@link BoardState} numbering (a8 = 0). */
    public BoardState state() {
        return state;
    }

    /** Seconds since the board was switched on (little-endian 32-bit counter). */
    public long uptimeSeconds() {
        return uptimeSeconds;
    }

    @Override
    public String toString() {
        return "ChessnutBoardReport{uptime=" + uptimeSeconds + "s,\n" + state + "}";
    }
}
