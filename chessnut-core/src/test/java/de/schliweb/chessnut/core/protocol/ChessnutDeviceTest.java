/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.chessnut.core.protocol;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import de.schliweb.pegasus.core.protocol.BoardState;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class ChessnutDeviceTest {

    private static final String BOARD = ChessnutUuids.BOARD_NOTIFY_CHARACTERISTIC;
    private static final String CMD = ChessnutUuids.COMMAND_NOTIFY_CHARACTERISTIC;

    private static final class RecordingSink implements ChessnutDevice.CommandSink {
        final List<byte[]> written = new ArrayList<>();

        @Override
        public void write(byte[] data) {
            written.add(data.clone());
        }
    }

    private static final class RecordingListener implements ChessnutDeviceListener {
        final List<String> events = new ArrayList<>();
        BoardState lastState;

        @Override
        public void onBoardState(BoardState state, long uptimeSeconds) {
            lastState = state;
            events.add("board@" + uptimeSeconds);
        }

        @Override
        public void onBatteryStatus(ChessnutBatteryStatus status) {
            events.add("battery:" + status.percent());
        }

        @Override
        public void onNewGameButton() {
            events.add("newgame");
        }

        @Override
        public void onAck() {
            events.add("ack");
        }

        @Override
        public void onUnknownFrame(String characteristicUuid, ChessnutFrame frame) {
            events.add("unknown:" + frame);
        }
    }

    private final RecordingSink sink = new RecordingSink();
    private final RecordingListener listener = new RecordingListener();
    private long nowMs = 1_000_000;
    private final ChessnutDevice device = new ChessnutDevice(sink, listener, () -> nowMs);

    @Test
    public void initializeEnablesReportsThenAsksBattery() {
        device.initialize();

        assertEquals(2, sink.written.size());
        assertArrayEquals(ChessnutCommands.encodeEnableReports(), sink.written.get(0));
        assertArrayEquals(ChessnutCommands.encodeBatteryRequest(), sink.written.get(1));
    }

    @Test
    public void dispatchesFirstReportAndOnlyChangesAfterwards() {
        device.onDataReceived(BOARD, Fixtures.START_POSITION);
        device.onDataReceived(BOARD, Fixtures.START_POSITION);
        device.onDataReceived(BOARD, Fixtures.START_POSITION);
        device.onDataReceived(BOARD, Fixtures.E2_LIFTED);
        device.onDataReceived(BOARD, Fixtures.E2_LIFTED);
        device.onDataReceived(BOARD, Fixtures.AFTER_E4);

        assertEquals(Arrays.asList("board@459", "board@492", "board@492"), listener.events);
        assertEquals(
                de.schliweb.pegasus.core.protocol.PieceCodes.WPAWN,
                listener.lastState.pieceCodeAt(BoardState.squareIndex("e4")));
    }

    @Test
    public void counterOnlyChangesAreNotDispatched() {
        byte[] laterCounter = Fixtures.START_POSITION.clone();
        laterCounter[34] = 0x02;

        device.onDataReceived(BOARD, Fixtures.START_POSITION);
        device.onDataReceived(BOARD, laterCounter);

        assertEquals(1, listener.events.size());
    }

    @Test
    public void resetRedispatchesUnchangedBoardAfterReconnect() {
        device.onDataReceived(BOARD, Fixtures.START_POSITION);
        device.reset();
        device.onDataReceived(BOARD, Fixtures.START_POSITION);

        assertEquals(Arrays.asList("board@459", "board@459"), listener.events);
    }

    @Test
    public void reassemblesPerCharacteristicIndependently() {
        byte[] head = Arrays.copyOfRange(Fixtures.START_POSITION, 0, 20);
        byte[] tail = Arrays.copyOfRange(Fixtures.START_POSITION, 20, 38);

        device.onDataReceived(BOARD, head);
        device.onDataReceived(CMD, Fixtures.ACK); // must not be spliced into the board fragment
        device.onDataReceived(BOARD, tail);

        assertEquals(Arrays.asList("ack", "board@459"), listener.events);
    }

    @Test
    public void dispatchesBatteryAndAck() {
        device.onDataReceived(CMD, Fixtures.ACK);
        device.onDataReceived(CMD, Fixtures.BATTERY_95);

        assertEquals(Arrays.asList("ack", "battery:95"), listener.events);
    }

    @Test
    public void longPressYieldsOneNewGameEvent() {
        device.onDataReceived(CMD, Fixtures.NEW_GAME);
        nowMs += 3200; // release event of a long press, as observed
        device.onDataReceived(CMD, Fixtures.NEW_GAME);
        nowMs += ChessnutDevice.BUTTON_DEBOUNCE_MS;
        device.onDataReceived(CMD, Fixtures.NEW_GAME);

        assertEquals(Arrays.asList("newgame", "newgame"), listener.events);
    }

    @Test
    public void unknownMessagesAreReportedRaw() {
        device.onDataReceived(CMD, Fixtures.hex("0f 01 07"));
        device.onDataReceived(CMD, Fixtures.hex("2a 01 64"));
        device.onDataReceived(BOARD, Fixtures.hex("01 02 aa bb"));
        device.onDataReceived(CMD, Fixtures.hex("77 00"));

        assertEquals(
                Arrays.asList(
                        "unknown:0f 01 07",
                        "unknown:2a 01 64",
                        "unknown:01 02 aa bb",
                        "unknown:77 00"),
                listener.events);
    }

    @Test
    public void ledHelpersWriteEncodedCommands() {
        device.showLeds(BoardState.squareIndex("h8"));
        device.ledsOff();
        device.requestBattery();

        assertArrayEquals(Fixtures.hex("0a 08 01 00 00 00 00 00 00 00"), sink.written.get(0));
        assertArrayEquals(ChessnutCommands.encodeLedsOff(), sink.written.get(1));
        assertArrayEquals(ChessnutCommands.encodeBatteryRequest(), sink.written.get(2));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNullListener() {
        new ChessnutDevice(sink, null);
    }
}
