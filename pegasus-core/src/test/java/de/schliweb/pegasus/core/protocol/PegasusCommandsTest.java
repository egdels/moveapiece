/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.pegasus.core.protocol;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

public class PegasusCommandsTest {

    @Test
    public void encodesSingleByteRequests() {
        assertArrayEquals(new byte[] {0x42}, PegasusCommands.encodeBoardStateRequest());
        assertArrayEquals(new byte[] {0x40}, PegasusCommands.encodeReset());
        assertArrayEquals(new byte[] {0x44}, PegasusCommands.encodeUpdateMode());
        assertArrayEquals(new byte[] {0x47}, PegasusCommands.encodeTrademarkRequest());
        assertArrayEquals(new byte[] {0x4D}, PegasusCommands.encodeVersionRequest());
        assertArrayEquals(new byte[] {0x48}, PegasusCommands.encodeHardwareVersionRequest());
        assertArrayEquals(new byte[] {0x45}, PegasusCommands.encodeSerialNumberRequest());
        assertArrayEquals(new byte[] {0x55}, PegasusCommands.encodeLongSerialNumberRequest());
        assertArrayEquals(new byte[] {0x4C}, PegasusCommands.encodeBatteryRequest());
    }

    @Test
    public void encodesDevKey() {
        assertArrayEquals(
                new byte[] {
                    0x63,
                    0x07,
                    (byte) 0xBE,
                    (byte) 0xF5,
                    (byte) 0xAE,
                    (byte) 0xDD,
                    (byte) 0xA9,
                    0x5F,
                    0x00
                },
                PegasusCommands.encodeDevKey());
    }

    @Test
    public void encodesLedsOff() {
        assertArrayEquals(new byte[] {0x60, 0x02, 0x00, 0x00}, PegasusCommands.encodeLedsOff());
    }

    @Test
    public void encodesLedsForSingleSquare() {
        // e2 = index 52: 0x60 len 0x05 speed mode intensity square 0x00
        byte[] cmd = PegasusCommands.encodeLeds(3, 0, 5, 52);
        assertArrayEquals(new byte[] {0x60, 0x06, 0x05, 0x03, 0x00, 0x05, 0x34, 0x00}, cmd);
    }

    @Test
    public void encodesLedsForMoveFromTo() {
        // e2 (52) + e4 (36), move mode 1.
        byte[] cmd = PegasusCommands.encodeLeds(2, 1, 4, 52, 36);
        assertArrayEquals(new byte[] {0x60, 0x07, 0x05, 0x02, 0x01, 0x04, 0x34, 0x24, 0x00}, cmd);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsLedsWithoutSquares() {
        PegasusCommands.encodeLeds(1, 0, 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsLedsWithInvalidMode() {
        PegasusCommands.encodeLeds(1, 2, 1, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsLedsWithInvalidSquare() {
        PegasusCommands.encodeLeds(1, 0, 1, 64);
    }
}
