/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.board;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import de.schliweb.chessnut.core.protocol.ChessnutCommands;
import de.schliweb.chessnut.core.protocol.ChessnutTones;
import de.schliweb.pegasus.core.transport.ConnectionState;
import de.schliweb.pegasus.core.transport.TransportError;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Test;

/**
 * Drives {@link ChessnutGameBridge} and its {@link ChessnutBoardAdapter} through a {@link
 * FakeTransport}: what the bridge itself adds around {@code ChessnutGameFlow} - the connect
 * sequence, battery reporting, the NEW GAME button and the tones.
 */
public class ChessnutGameBridgeTest {

    private static final long TIMEOUT_SECONDS = 5;
    private static final String CMD = "cmd-rx";
    private static final byte[] BATTERY_95 = {0x2a, 0x02, 0x5f, 0x00};
    private static final byte[] BATTERY_10 = {0x2a, 0x02, 0x0a, 0x00};
    private static final byte[] NEW_GAME = {0x0f, 0x01, 0x02};
    private static final String BOARD = "board-rx";

    /** Board reports captured from a real Chessnut Air (see chessnut-core's own Fixtures). */
    private static final byte[] START_POSITION =
            hex(
                    "01 24 58 23 31 85 44 44 44 44 00 00 00 00 00 00 00 00"
                            + " 00 00 00 00 00 00 00 00 77 77 77 77 a6 c9 9b 6a cb 01 00 00");

    private static final byte[] E2_LIFTED =
            hex(
                    "01 24 58 23 31 85 44 44 44 44 00 00 00 00 00 00 00 00"
                            + " 00 00 00 00 00 00 00 00 77 07 77 77 a6 c9 9b 6a ec 01 00 00");

    private static final byte[] AFTER_E4 =
            hex(
                    "01 24 58 23 31 85 44 44 44 44 00 00 00 00 00 00 00 00"
                            + " 00 70 00 00 00 00 00 00 77 07 77 77 a6 c9 9b 6a ec 01 00 00");

    private static byte[] hex(String s) {
        String[] parts = s.trim().split(" +");
        byte[] out = new byte[parts.length];
        for (int i = 0; i < parts.length; i++) {
            out[i] = (byte) Integer.parseInt(parts[i], 16);
        }
        return out;
    }

    private final TestMainThread main = new TestMainThread();
    private final FakeTransport transport = new FakeTransport();
    private final RecordingListener listener = new RecordingListener();

    @After
    public void stopMainThread() {
        main.close();
    }

    private static class RecordingListener implements ChessnutGameBridge.Listener {
        final CountDownLatch connected = new CountDownLatch(1);
        final LinkedBlockingQueue<String> events = new LinkedBlockingQueue<>();

        @Override
        public void onConnectionStateChanged(ConnectionState state) {
            if (state == ConnectionState.CONNECTED) {
                connected.countDown();
            }
        }

        @Override
        public void onTransportError(TransportError error, String detail) {}

        @Override
        public void onBatteryStatus(int percent, boolean low) {
            events.add("battery:" + percent + ":" + low);
        }

        @Override
        public void onNewGameButton() {
            events.add("newGame");
        }

        @Override
        public void onPhysicalMoveConfirmed(String uci) {
            events.add("move:" + uci);
        }

        @Override
        public void onBoardMismatch(boolean mismatched) {
            events.add("mismatch:" + mismatched);
        }

        @Override
        public void onEngineMoveGuidanceComplete() {
            events.add("guidanceComplete");
        }

        @Override
        public void onGuideDeviation(boolean deviating) {}

        @Override
        public void onIllegalPlacement() {}
    }

    private ChessnutGameBridge connectedBridge() throws InterruptedException {
        ChessnutGameBridge[] ref = new ChessnutGameBridge[1];
        main.runSync(
                () -> {
                    ref[0] = new ChessnutGameBridge(transport, main, listener);
                    ref[0].connect("AA:BB:CC:DD:EE:FF");
                });
        assertTrue(listener.connected.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        return ref[0];
    }

    private byte[] nextWrite() throws InterruptedException {
        return transport.written.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    public void connect_enablesReportsThenAsksForTheBattery() throws InterruptedException {
        connectedBridge();

        assertArrayEquals(ChessnutCommands.encodeEnableReports(), nextWrite());
        assertArrayEquals(ChessnutCommands.encodeBatteryRequest(), nextWrite());
    }

    @Test
    public void battery_isReportedOncePerConnectAndAgainWhenItTurnsLow()
            throws InterruptedException {
        connectedBridge();

        transport.feed(CMD, BATTERY_95);
        transport.feed(CMD, BATTERY_95);
        transport.feed(CMD, BATTERY_10);
        transport.feed(CMD, NEW_GAME);

        assertEquals("battery:95:false", listener.events.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertEquals("battery:10:true", listener.events.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        // The button event after them shows that the second 95 % reading was not reported.
        assertEquals("newGame", listener.events.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS));
    }

    private String nextEvent() throws InterruptedException {
        return listener.events.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    public void aMovePlayedOnTheBoard_isConfirmedWithItsUci() throws InterruptedException {
        ChessnutGameBridge bridge = connectedBridge();

        transport.feed(BOARD, START_POSITION);
        transport.feed(BOARD, E2_LIFTED);
        transport.feed(BOARD, AFTER_E4);

        String event = nextEvent();
        while (event != null && !event.startsWith("move:")) {
            event = nextEvent();
        }
        assertEquals("move:e2e4", event);
        String[] fen = new String[1];
        main.runSync(() -> fen[0] = bridge.trackedFen());
        assertTrue(fen[0], fen[0].startsWith("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b"));
    }

    @Test
    public void anEngineMove_isGuidedUntilItStandsOnTheBoard() throws InterruptedException {
        ChessnutGameBridge bridge = connectedBridge();
        transport.feed(BOARD, START_POSITION);
        boolean[] active = new boolean[1];
        main.runSync(
                () -> {
                    bridge.guideEngineMove("e2e4");
                    active[0] = bridge.isGuideActive();
                });
        assertTrue(active[0]);

        transport.feed(BOARD, E2_LIFTED);
        transport.feed(BOARD, AFTER_E4);

        String event = nextEvent();
        while (event != null && !event.equals("guidanceComplete")) {
            event = nextEvent();
        }
        assertEquals("guidanceComplete", event);
        main.runSync(() -> active[0] = bridge.isGuideActive());
        assertFalse(active[0]);
    }

    @Test
    public void playTones_sendsTheTonesOneAfterTheOther() throws InterruptedException {
        ChessnutGameBridge bridge = connectedBridge();
        nextWrite();
        nextWrite();
        List<ChessnutTones.Tone> tones = ChessnutTones.forMove(false, true);
        assertEquals(2, tones.size());

        main.runSync(() -> bridge.playTones(tones));

        ChessnutTones.Tone first = tones.get(0);
        ChessnutTones.Tone second = tones.get(1);
        assertArrayEquals(
                ChessnutCommands.encodeBeep(first.frequencyHz, first.durationMs), nextWrite());
        assertNull(
                "the second tone must wait for the first to end",
                transport.written.poll(first.durationMs / 2, TimeUnit.MILLISECONDS));
        assertArrayEquals(
                ChessnutCommands.encodeBeep(second.frequencyHz, second.durationMs), nextWrite());
    }

    @Test
    public void adapter_leavesTheMoveSoundToTheHostWhileDisconnected() {
        ChessnutGameBridge[] ref = new ChessnutGameBridge[1];
        main.runSync(() -> ref[0] = new ChessnutGameBridge(transport, main, listener));
        ChessnutBoardAdapter adapter = new ChessnutBoardAdapter(ref[0]);

        boolean[] handled = new boolean[1];
        main.runSync(() -> handled[0] = adapter.playMoveSound(false, true, false));

        assertFalse(handled[0]);
        assertTrue(transport.written.isEmpty());
    }

    @Test
    public void adapter_staysSilentOnAnOrdinaryMoveAndBeepsOnCheck() throws InterruptedException {
        ChessnutBoardAdapter adapter = new ChessnutBoardAdapter(connectedBridge());
        nextWrite();
        nextWrite();

        boolean[] handled = new boolean[2];
        main.runSync(
                () -> {
                    handled[0] = adapter.playMoveSound(true, false, false);
                    handled[1] = adapter.playMoveSound(false, true, false);
                });

        assertTrue(handled[0]);
        assertTrue(handled[1]);
        ChessnutTones.Tone check = ChessnutTones.forMove(true, false).get(0);
        assertArrayEquals(
                ChessnutCommands.encodeBeep(check.frequencyHz, check.durationMs), nextWrite());
        assertNull(transport.written.poll(200, TimeUnit.MILLISECONDS));
    }
}
