/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.chessnut.core.protocol;

/** Frames copied verbatim from tools/chessnut-sniffer captures of 2026-09-26. */
final class Fixtures {

    private Fixtures() {}

    static byte[] hex(String s) {
        String[] parts = s.trim().split("\\s+");
        byte[] out = new byte[parts.length];
        for (int i = 0; i < parts.length; i++) {
            out[i] = (byte) Integer.parseInt(parts[i], 16);
        }
        return out;
    }

    /** Empty board, uptime 0x43 = 67 s (first session). */
    static final byte[] EMPTY_BOARD =
            hex(
                    "01 24 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00"
                            + " 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 43 00 00 00");

    /** Start position, uptime 0x01cb = 459 s. */
    static final byte[] START_POSITION =
            hex(
                    "01 24 58 23 31 85 44 44 44 44 00 00 00 00 00 00 00 00"
                            + " 00 00 00 00 00 00 00 00 77 77 77 77 a6 c9 9b 6a cb 01 00 00");

    /** Start position with the e2 pawn lifted. */
    static final byte[] E2_LIFTED =
            hex(
                    "01 24 58 23 31 85 44 44 44 44 00 00 00 00 00 00 00 00"
                            + " 00 00 00 00 00 00 00 00 77 07 77 77 a6 c9 9b 6a ec 01 00 00");

    /** Position after e2-e4, 0.36 s later. */
    static final byte[] AFTER_E4 =
            hex(
                    "01 24 58 23 31 85 44 44 44 44 00 00 00 00 00 00 00 00"
                            + " 00 70 00 00 00 00 00 00 77 07 77 77 a6 c9 9b 6a ec 01 00 00");

    static final byte[] ACK = hex("23 01 00");
    static final byte[] BATTERY_100 = hex("2a 02 64 00");
    static final byte[] BATTERY_95 = hex("2a 02 5f 00");
    static final byte[] NEW_GAME = hex("0f 01 02");

    static final String START_POSITION_TEXT =
            "rnbqkbnr\n"
                    + "pppppppp\n"
                    + "........\n"
                    + "........\n"
                    + "........\n"
                    + "........\n"
                    + "PPPPPPPP\n"
                    + "RNBQKBNR";
}
