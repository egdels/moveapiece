/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.chessnut.core.protocol;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import de.schliweb.pegasus.core.protocol.BoardState;
import org.junit.Test;

public class ChessnutCommandsTest {

    @Test
    public void encodesVerifiedCommandBytes() {
        assertArrayEquals(Fixtures.hex("21 01 00"), ChessnutCommands.encodeEnableReports());
        assertArrayEquals(Fixtures.hex("29 01 00"), ChessnutCommands.encodeBatteryRequest());
    }

    @Test
    public void ledBitZeroIsH8AsObserved() {
        assertArrayEquals(
                Fixtures.hex("0a 08 01 00 00 00 00 00 00 00"),
                ChessnutCommands.encodeLeds(BoardState.squareIndex("h8")));
    }

    @Test
    public void encodesMoveSquaresInDifferentRanks() {
        // e2 = chessnut index 51 → byte 6 bit 3; e4 = index 35 → byte 4 bit 3.
        assertArrayEquals(
                Fixtures.hex("0a 08 00 00 00 00 08 00 08 00"),
                ChessnutCommands.encodeLeds(
                        BoardState.squareIndex("e2"), BoardState.squareIndex("e4")));
    }

    @Test
    public void duplicatesAndCornersAreIdempotent() {
        // a1 = index 63 → byte 7 bit 7.
        assertArrayEquals(
                Fixtures.hex("0a 08 00 00 00 00 00 00 00 80"),
                ChessnutCommands.encodeLeds(
                        BoardState.squareIndex("a1"), BoardState.squareIndex("a1")));
    }

    @Test
    public void profileWritesToCommandServiceAndSubscribesToBothNotifies() {
        assertEquals(ChessnutUuids.COMMAND_SERVICE, ChessnutUuids.PROFILE.writeServiceUuid());
        assertEquals(
                ChessnutUuids.COMMAND_WRITE_CHARACTERISTIC,
                ChessnutUuids.PROFILE.writeCharacteristicUuid());
        assertEquals(2, ChessnutUuids.PROFILE.subscriptions().size());
        assertEquals(
                ChessnutUuids.COMMAND_NOTIFY_CHARACTERISTIC,
                ChessnutUuids.PROFILE.subscriptions().get(0).characteristicUuid());
        assertEquals(
                ChessnutUuids.BOARD_SERVICE,
                ChessnutUuids.PROFILE.subscriptions().get(1).serviceUuid());
        assertEquals(
                ChessnutUuids.BOARD_NOTIFY_CHARACTERISTIC,
                ChessnutUuids.PROFILE.subscriptions().get(1).characteristicUuid());
    }

    @Test
    public void offIsAllZeros() {
        assertArrayEquals(
                Fixtures.hex("0a 08 00 00 00 00 00 00 00 00"), ChessnutCommands.encodeLedsOff());
        assertArrayEquals(ChessnutCommands.encodeLedsOff(), ChessnutCommands.encodeLeds());
        assertArrayEquals(
                ChessnutCommands.encodeLedsOff(), ChessnutCommands.encodeLeds((int[]) null));
    }
}
