/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.pegasus.core.protocol;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BatteryStatusTest {

    private static BatteryStatus withStatusBits(int statusBits) {
        return BatteryStatus.fromPayload(new byte[] {0x58, 0, 0, 0, 0, 0, 0, 0, (byte) statusBits});
    }

    @Test
    public void neitherBitSetIsNotCriticallyLow() {
        BatteryStatus status = withStatusBits(0x00);

        assertFalse(status.isLow());
        assertFalse(status.isEmpty());
        assertFalse(status.isCriticallyLow());
    }

    @Test
    public void lowBitAloneIsNotCriticallyLow() {
        BatteryStatus status = withStatusBits(0x04);

        assertTrue(status.isLow());
        assertFalse(status.isEmpty());
        assertFalse(status.isCriticallyLow());
    }

    @Test
    public void emptyBitAloneIsNotCriticallyLow() {
        BatteryStatus status = withStatusBits(0x08);

        assertFalse(status.isLow());
        assertTrue(status.isEmpty());
        assertFalse(status.isCriticallyLow());
    }

    @Test
    public void bothLowAndEmptyBitsAreCriticallyLow() {
        BatteryStatus status = withStatusBits(0x04 | 0x08);

        assertTrue(status.isLow());
        assertTrue(status.isEmpty());
        assertTrue(status.isCriticallyLow());
    }
}
