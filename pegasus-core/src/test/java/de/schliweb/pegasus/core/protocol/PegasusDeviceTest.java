/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.pegasus.core.protocol;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class PegasusDeviceTest {

    /** Records written commands. */
    private static final class RecordingSink implements PegasusDevice.CommandSink {
        final List<byte[]> written = new ArrayList<>();

        @Override
        public void write(byte[] data) {
            written.add(data.clone());
        }
    }

    /** Records dispatched events as readable strings. */
    private static final class RecordingListener implements PegasusDeviceListener {
        final List<String> events = new ArrayList<>();
        BoardState lastBoardState;

        @Override
        public void onBoardState(BoardState state) {
            lastBoardState = state;
            events.add("board");
        }

        @Override
        public void onFieldUpdate(FieldUpdate update) {
            events.add(
                    "field:"
                            + BoardState.squareName(update.squareIndex())
                            + ":"
                            + (update.isOccupied() ? "occupied" : "empty"));
        }

        @Override
        public void onBatteryStatus(BatteryStatus status) {
            events.add("battery:" + status.percent());
        }

        @Override
        public void onIdentity(int messageType, String text) {
            events.add("identity:" + PegasusMessageType.name(messageType) + ":" + text);
        }

        @Override
        public void onVersion(int messageType, int major, int minor) {
            events.add(
                    "version:" + PegasusMessageType.name(messageType) + ":" + major + "." + minor);
        }

        @Override
        public void onUnknownFrame(PegasusFrame frame) {
            events.add("unknown:" + String.format("0x%02X", frame.type()));
        }
    }

    private static byte[] frame(int type, byte[] payload) {
        int totalLen = payload.length + 3;
        byte[] out = new byte[totalLen];
        out[0] = (byte) type;
        out[1] = (byte) ((totalLen >> 7) & 0x7F);
        out[2] = (byte) (totalLen & 0x7F);
        System.arraycopy(payload, 0, out, 3, payload.length);
        return out;
    }

    private final RecordingSink sink = new RecordingSink();
    private final RecordingListener listener = new RecordingListener();
    private final PegasusDevice device = new PegasusDevice(sink, listener);

    @Test
    public void initializeSendsDocumentedSequence() {
        device.initialize();

        assertEquals(7, sink.written.size());
        assertArrayEquals(new byte[] {0x47}, sink.written.get(0)); // G trademark
        assertArrayEquals(new byte[] {0x4D}, sink.written.get(1)); // M version
        assertArrayEquals(new byte[] {0x48}, sink.written.get(2)); // H hw version
        assertArrayEquals(new byte[] {0x45}, sink.written.get(3)); // E serial
        assertArrayEquals(new byte[] {0x55}, sink.written.get(4)); // U long serial
        assertArrayEquals(new byte[] {0x4C}, sink.written.get(5)); // L battery
        assertArrayEquals(new byte[] {0x42}, sink.written.get(6)); // B board dump
    }

    @Test
    public void dispatchesBoardDump() {
        byte[] payload = new byte[64];
        payload[BoardState.squareIndex("e2")] = PieceCodes.WPAWN;

        device.onDataReceived(frame(PegasusMessageType.BOARD_DUMP, payload));

        assertEquals(1, listener.events.size());
        assertEquals("board", listener.events.get(0));
        assertTrue(listener.lastBoardState.isOccupied(BoardState.squareIndex("e2")));
    }

    @Test
    public void dispatchesFragmentedBoardDump() {
        byte[] full = frame(PegasusMessageType.BOARD_DUMP, new byte[64]);
        byte[] f1 = new byte[20];
        byte[] f2 = new byte[full.length - 20];
        System.arraycopy(full, 0, f1, 0, 20);
        System.arraycopy(full, 20, f2, 0, f2.length);

        device.onDataReceived(f1);
        assertTrue(listener.events.isEmpty());
        device.onDataReceived(f2);

        assertEquals(1, listener.events.size());
        assertEquals("board", listener.events.get(0));
    }

    @Test
    public void dispatchesFieldUpdateLiftAndPlace() {
        int e2 = BoardState.squareIndex("e2");
        int e4 = BoardState.squareIndex("e4");

        device.onDataReceived(frame(PegasusMessageType.FIELD_UPDATE, new byte[] {(byte) e2, 0}));
        device.onDataReceived(frame(PegasusMessageType.FIELD_UPDATE, new byte[] {(byte) e4, 1}));

        assertEquals("field:e2:empty", listener.events.get(0));
        assertEquals("field:e4:occupied", listener.events.get(1));
    }

    @Test
    public void dispatchesBatteryStatus() {
        device.onDataReceived(
                frame(
                        PegasusMessageType.BATTERY_STATUS,
                        new byte[] {0x58, 0, 0, 0, 0, 0, 0, 0, 2}));

        assertEquals("battery:88", listener.events.get(0));
    }

    @Test
    public void dispatchesIdentityAndVersionMessages() {
        device.onDataReceived(
                frame(
                        PegasusMessageType.TRADEMARK,
                        "Digital Game Technology".getBytes(StandardCharsets.US_ASCII)));
        device.onDataReceived(frame(PegasusMessageType.VERSION, new byte[] {1, 2}));
        device.onDataReceived(frame(PegasusMessageType.HARDWARE_VERSION, new byte[] {3, 4}));

        assertEquals("identity:TRADEMARK:Digital Game Technology", listener.events.get(0));
        assertEquals("version:VERSION:1.2", listener.events.get(1));
        assertEquals("version:HARDWARE_VERSION:3.4", listener.events.get(2));
    }

    @Test
    public void reportsUnknownTypeAndMalformedPayloadAsUnknownFrame() {
        device.onDataReceived(frame(0x8F, new byte[0]));
        // FIELD_UPDATE with wrong payload length must not crash the dispatcher.
        device.onDataReceived(frame(PegasusMessageType.FIELD_UPDATE, new byte[] {1, 2, 3}));

        assertEquals("unknown:0x8F", listener.events.get(0));
        assertEquals("unknown:0x8E", listener.events.get(1));
    }

    @Test
    public void ledHelpersWriteEncodedCommands() {
        device.showLeds(2, 1, 4, 52, 36);
        device.ledsOff();

        assertArrayEquals(PegasusCommands.encodeLeds(2, 1, 4, 52, 36), sink.written.get(0));
        assertArrayEquals(PegasusCommands.encodeLedsOff(), sink.written.get(1));
    }
}
