/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.pegasus.core.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class HexUtilTest {

    @Test
    public void spacedHex() {
        assertEquals("86 00 43", HexUtil.toSpacedHex(new byte[] {(byte) 0x86, 0x00, 0x43}));
        assertEquals("", HexUtil.toSpacedHex(new byte[0]));
        assertEquals("", HexUtil.toSpacedHex(null));
        assertEquals("FF", HexUtil.toSpacedHex(new byte[] {(byte) 0xFF}));
    }

    @Test
    public void compactHexRoundTrip() {
        byte[] data = {(byte) 0x86, 0x00, 0x43, (byte) 0xFF, 0x01};
        String hex = HexUtil.toCompactHex(data);
        assertEquals("860043FF01", hex);
        assertArrayEquals(data, HexUtil.fromCompactHex(hex));
        assertArrayEquals(data, HexUtil.fromCompactHex(hex.toLowerCase()));
    }

    @Test(expected = IllegalArgumentException.class)
    public void oddLengthRejected() {
        HexUtil.fromCompactHex("ABC");
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidCharacterRejected() {
        HexUtil.fromCompactHex("ZZ");
    }

    @Test
    public void asciiRendering() {
        assertEquals("DGT.", HexUtil.toAscii(new byte[] {0x44, 0x47, 0x54, 0x01}));
    }
}
