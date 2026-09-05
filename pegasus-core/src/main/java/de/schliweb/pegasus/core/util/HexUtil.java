/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.pegasus.core.util;

/** Deterministic hex formatting for raw BLE payload logging. */
public final class HexUtil {

    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    private HexUtil() {}

    /** e.g. {@code 86 00 43 00} — uppercase, space separated. */
    public static String toSpacedHex(byte[] data) {
        if (data == null || data.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder(data.length * 3 - 1);
        for (int i = 0; i < data.length; i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(HEX[(data[i] >> 4) & 0x0F]);
            sb.append(HEX[data[i] & 0x0F]);
        }
        return sb.toString();
    }

    /** e.g. {@code 860043} — uppercase, compact (recording format). */
    public static String toCompactHex(byte[] data) {
        if (data == null || data.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder(data.length * 2);
        for (byte b : data) {
            sb.append(HEX[(b >> 4) & 0x0F]);
            sb.append(HEX[b & 0x0F]);
        }
        return sb.toString();
    }

    /** Parses compact hex (case insensitive) back to bytes. */
    public static byte[] fromCompactHex(String hex) {
        if (hex == null || hex.isEmpty()) {
            return new byte[0];
        }
        if (hex.length() % 2 != 0) {
            throw new IllegalArgumentException("Odd hex length: " + hex.length());
        }
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int hi = Character.digit(hex.charAt(2 * i), 16);
            int lo = Character.digit(hex.charAt(2 * i + 1), 16);
            if (hi < 0 || lo < 0) {
                throw new IllegalArgumentException("Invalid hex character in: " + hex);
            }
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }

    /** Optional ASCII rendering; non-printable bytes become '.'. */
    public static String toAscii(byte[] data) {
        if (data == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(data.length);
        for (byte b : data) {
            int c = b & 0xFF;
            sb.append(c >= 0x20 && c < 0x7F ? (char) c : '.');
        }
        return sb.toString();
    }
}
