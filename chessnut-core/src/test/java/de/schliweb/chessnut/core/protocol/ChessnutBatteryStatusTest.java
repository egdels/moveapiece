/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.chessnut.core.protocol;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ChessnutBatteryStatusTest {

    @Test
    public void decodesObservedReplies() {
        ChessnutBatteryStatus full = ChessnutBatteryStatus.fromPayload(new byte[] {0x64, 0});
        ChessnutBatteryStatus later = ChessnutBatteryStatus.fromPayload(new byte[] {0x5f, 0});

        assertEquals(100, full.percent());
        assertEquals(95, later.percent());
        assertEquals(0, later.flag());
        assertFalse(later.isLow());
    }

    @Test
    public void clampsAboveHundredAndFlagsLow() {
        assertEquals(100, ChessnutBatteryStatus.fromPayload(new byte[] {(byte) 0xff, 1}).percent());
        assertTrue(ChessnutBatteryStatus.fromPayload(new byte[] {20, 0}).isLow());
        assertEquals(1, ChessnutBatteryStatus.fromPayload(new byte[] {50, 1}).flag());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsShortPayload() {
        ChessnutBatteryStatus.fromPayload(new byte[] {0x64});
    }
}
