/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.pegasus;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Instrumentation;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import de.schliweb.pegasus.core.chess.ChessPosition;
import de.schliweb.pegasus.core.chess.OccupancyProjection;
import de.schliweb.pegasus.core.chess.PieceType;
import de.schliweb.pegasus.core.protocol.BoardState;
import de.schliweb.pegasus.core.protocol.PegasusCommands;
import de.schliweb.pegasus.core.protocol.PegasusLedController;
import de.schliweb.pegasus.core.protocol.PegasusMessageType;
import de.schliweb.pegasus.core.transport.ConnectionState;
import de.schliweb.pegasus.core.transport.PegasusTransport;
import de.schliweb.pegasus.core.transport.ScanListener;
import de.schliweb.pegasus.core.transport.TransportError;
import de.schliweb.pegasus.core.transport.TransportListener;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Drives {@link PegasusGameBridge} through a hand-written fake {@link PegasusTransport} (no real
 * BLE needed) using real protocol-level frame bytes built with core's own {@code
 * PegasusCommands}/frame encoding. Covers the connect/init sequence (incl. DevKey), a confirmed
 * physical move, and engine-move LED guidance to completion.
 *
 * <p>{@link PegasusGameBridge} documents itself as main-thread-confined (like the real BLE
 * transport's callback consumer would be in MainActivity), so every call to its public API here
 * goes through {@link Instrumentation#runOnMainSync}; only simulated incoming transport data is fed
 * from the test thread, mirroring a real BLE callback thread.
 */
@RunWith(AndroidJUnit4.class)
public class PegasusGameBridgeTest {

    private static final long TIMEOUT_SECONDS = 5;
    private static final String DEVICE_ADDRESS = "AA:BB:CC:DD:EE:FF";

    /** Same encoding as core's own PegasusFrameParserTest. */
    private static byte[] frame(int type, byte[] payload) {
        int totalLen = payload.length + 3;
        byte[] out = new byte[totalLen];
        out[0] = (byte) type;
        out[1] = (byte) ((totalLen >> 7) & 0x7F);
        out[2] = (byte) (totalLen & 0x7F);
        System.arraycopy(payload, 0, out, 3, payload.length);
        return out;
    }

    private static byte[] startingBoardDumpFrame() {
        BoardState occupancy = OccupancyProjection.occupancyOf(ChessPosition.starting());
        byte[] payload = new byte[BoardState.SQUARE_COUNT];
        for (int i = 0; i < BoardState.SQUARE_COUNT; i++) {
            payload[i] = (byte) occupancy.pieceCodeAt(i);
        }
        return frame(PegasusMessageType.BOARD_DUMP, payload);
    }

    private static byte[] fieldUpdateFrame(String square, int code) {
        return frame(
                PegasusMessageType.FIELD_UPDATE,
                new byte[] {(byte) BoardState.squareIndex(square), (byte) code});
    }

    /** Fake BLE transport: records writes, lets the test inject "received" bytes. */
    private static class FakeTransport implements PegasusTransport {
        final LinkedBlockingQueue<byte[]> written = new LinkedBlockingQueue<>();
        private volatile TransportListener listener;
        private volatile ConnectionState state = ConnectionState.DISCONNECTED;

        @Override
        public void setListener(TransportListener listener) {
            this.listener = listener;
        }

        @Override
        public void startScan(ScanListener listener, long timeoutMs) {}

        @Override
        public void stopScan() {}

        @Override
        public void connect(String deviceAddress) {
            state = ConnectionState.CONNECTED;
            if (listener != null) {
                listener.onConnectionStateChanged(ConnectionState.CONNECTED);
            }
        }

        @Override
        public void disconnect() {
            state = ConnectionState.DISCONNECTED;
            if (listener != null) {
                listener.onConnectionStateChanged(ConnectionState.DISCONNECTED);
            }
        }

        @Override
        public void write(byte[] data) {
            written.add(data.clone());
        }

        @Override
        public ConnectionState getConnectionState() {
            return state;
        }

        /** Simulates a BLE notification arriving (real hardware: a non-main thread). */
        void feed(byte[] data) {
            TransportListener l = listener;
            if (l != null) {
                l.onDataReceived("uart-rx", data);
            }
        }
    }

    private static class RecordingListener implements PegasusGameBridge.Listener {
        final CountDownLatch connected = new CountDownLatch(1);
        final CountDownLatch synced = new CountDownLatch(1);
        final CountDownLatch moveConfirmed = new CountDownLatch(1);
        final CountDownLatch guidanceComplete = new CountDownLatch(1);
        final CountDownLatch mismatchDetected = new CountDownLatch(1);
        final CountDownLatch mismatchResolved = new CountDownLatch(1);
        final CountDownLatch promotionRequired = new CountDownLatch(1);
        final AtomicReference<String> confirmedUci = new AtomicReference<>();

        @Override
        public void onConnectionStateChanged(ConnectionState state) {
            if (state == ConnectionState.CONNECTED) {
                connected.countDown();
            }
        }

        @Override
        public void onPhysicalMoveConfirmed(String uci) {
            confirmedUci.set(uci);
            moveConfirmed.countDown();
        }

        @Override
        public void onBoardMismatch(boolean mismatched) {
            if (mismatched) {
                mismatchDetected.countDown();
            } else {
                synced.countDown();
                mismatchResolved.countDown();
            }
        }

        @Override
        public void onEngineMoveGuidanceComplete() {
            guidanceComplete.countDown();
        }

        @Override
        public void onPromotionRequired() {
            promotionRequired.countDown();
        }

        @Override
        public void onAmbiguousMove(java.util.List<String> candidateUcis) {
            // Not exercised by these tests (see class javadoc on the fix).
        }

        @Override
        public void onTransportError(TransportError error, String detail) {
            // Not exercised by these tests.
        }

        @Override
        public void onBatteryStatus(int percent) {
            // Not exercised by these tests.
        }
    }

    private PegasusGameBridge newBridgeOnMainThread(
            FakeTransport transport, RecordingListener listener) {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        AtomicReference<PegasusGameBridge> ref = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> ref.set(new PegasusGameBridge(transport, listener)));
        return ref.get();
    }

    /** Connects and feeds the starting-position board dump; waits for both to settle. */
    private void connectAndSyncStartingPosition(
            PegasusGameBridge bridge, FakeTransport transport, RecordingListener listener)
            throws InterruptedException {
        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(() -> bridge.connect(DEVICE_ADDRESS));
        assertTrue(
                "expected CONNECTED within " + TIMEOUT_SECONDS + "s",
                listener.connected.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));

        transport.feed(startingBoardDumpFrame());
        assertTrue(
                "expected the initial board dump to synchronize within " + TIMEOUT_SECONDS + "s",
                listener.synced.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
    }

    @Test
    public void connect_sendsDevKeyDuringInitSequence() throws InterruptedException {
        FakeTransport transport = new FakeTransport();
        RecordingListener listener = new RecordingListener();
        PegasusGameBridge bridge = newBridgeOnMainThread(transport, listener);

        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(() -> bridge.connect(DEVICE_ADDRESS));
        assertTrue(listener.connected.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));

        byte[] devKey = PegasusCommands.encodeDevKey();
        boolean found = false;
        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(2 * TIMEOUT_SECONDS);
        while (!found && System.currentTimeMillis() < deadline) {
            byte[] sent = transport.written.poll(500, TimeUnit.MILLISECONDS);
            if (sent != null && Arrays.equals(sent, devKey)) {
                found = true;
            }
        }
        assertTrue("expected the DevKey command among the init sequence writes", found);
    }

    @Test
    public void physicalPawnPush_isConfirmedWithCorrectUci() throws InterruptedException {
        FakeTransport transport = new FakeTransport();
        RecordingListener listener = new RecordingListener();
        PegasusGameBridge bridge = newBridgeOnMainThread(transport, listener);
        connectAndSyncStartingPosition(bridge, transport, listener);

        byte[] combined = new byte[0];
        for (byte[] frame : new byte[][] {fieldUpdateFrame("e2", 0), fieldUpdateFrame("e4", 1)}) {
            byte[] next = new byte[combined.length + frame.length];
            System.arraycopy(combined, 0, next, 0, combined.length);
            System.arraycopy(frame, 0, next, combined.length, frame.length);
            combined = next;
        }
        transport.feed(combined);

        assertTrue(
                "expected a confirmed physical move within " + TIMEOUT_SECONDS + "s",
                listener.moveConfirmed.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertEquals("e2e4", listener.confirmedUci.get());
    }

    @Test
    public void guideEngineMove_completesOnceThePhysicalMoveIsReproduced()
            throws InterruptedException {
        FakeTransport transport = new FakeTransport();
        RecordingListener listener = new RecordingListener();
        PegasusGameBridge bridge = newBridgeOnMainThread(transport, listener);
        connectAndSyncStartingPosition(bridge, transport, listener);
        transport.written.clear();

        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(() -> bridge.guideEngineMove("e2e4"));
        byte[] ledCommand = transport.written.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertTrue("expected an LED command as soon as guidance starts", ledCommand != null);

        transport.feed(fieldUpdateFrame("e2", 0));
        transport.feed(fieldUpdateFrame("e4", 1));

        assertTrue(
                "expected guidance to complete once the physical board matches the target",
                listener.guidanceComplete.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
    }

    /**
     * Simulates resuming after a reconnect where on-screen play (which the bridge never observes)
     * has left the physical board behind: pushing a FEN that the untouched physical board no longer
     * matches must surface a mismatch, and reproducing it physically must resolve it - this is the
     * mechanism MainActivity relies on to let the player continue on the physical board after a
     * connection drop.
     */
    @Test
    public void syncBoardToPosition_lightsMismatchThenResolvesOnceBoardMatches()
            throws InterruptedException {
        FakeTransport transport = new FakeTransport();
        RecordingListener listener = new RecordingListener();
        PegasusGameBridge bridge = newBridgeOnMainThread(transport, listener);
        connectAndSyncStartingPosition(bridge, transport, listener);

        String fenAfterE4 = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1";
        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(() -> bridge.syncBoardToPosition(fenAfterE4));

        assertTrue(
                "expected a mismatch as soon as the target diverges from the untouched board",
                listener.mismatchDetected.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));

        transport.feed(fieldUpdateFrame("e2", 0));
        transport.feed(fieldUpdateFrame("e4", 1));

        assertTrue(
                "expected the mismatch to resolve once the physical board matches the target",
                listener.mismatchResolved.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
    }

    /**
     * The board is occupancy-only and can never tell which piece a promoted pawn should become, so
     * it must ask the UI - and must not silently drop the move (regression coverage for a real bug:
     * routing PROMOTION_REQUIRED through the generic mismatch path turned the LEDs off and reported
     * "back in sync" for a move that was never applied).
     */
    @Test
    public void physicalPromotion_requiresUiChoiceThenAppliesConfirmedMove()
            throws InterruptedException {
        FakeTransport transport = new FakeTransport();
        RecordingListener listener = new RecordingListener();
        PegasusGameBridge bridge = newBridgeOnMainThread(transport, listener);

        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(() -> bridge.connect(DEVICE_ADDRESS));
        assertTrue(listener.connected.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));

        // Minimal legal position with a white pawn one push away from
        // promoting: white king e1, white pawn e7, black king a8.
        String promotionFen = "k7/4P3/8/8/8/8/8/4K3 w - - 0 1";
        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(() -> bridge.syncBoardToPosition(promotionFen));

        byte[] payload = new byte[BoardState.SQUARE_COUNT];
        payload[BoardState.squareIndex("a8")] = 1;
        payload[BoardState.squareIndex("e7")] = 1;
        payload[BoardState.squareIndex("e1")] = 1;
        transport.feed(frame(PegasusMessageType.BOARD_DUMP, payload));
        assertTrue(
                "expected the synthetic position to synchronize first",
                listener.synced.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));

        // Push the pawn to e8; occupancy alone can't reveal which piece it
        // became.
        transport.feed(fieldUpdateFrame("e7", 0));
        transport.feed(fieldUpdateFrame("e8", 1));

        assertTrue(
                "expected a promotion prompt instead of a silently stuck detector",
                listener.promotionRequired.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));

        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(() -> bridge.selectPromotion(PieceType.QUEEN));

        assertTrue(
                "expected the move to confirm once the promotion is resolved",
                listener.moveConfirmed.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertEquals("e7e8q", listener.confirmedUci.get());
    }

    /**
     * Regression coverage for a fixed design bug: a capture only ever changes its origin square
     * (the destination was already occupied by the captured piece, and stays occupied by the
     * capturing one), so merely lifting a piece with a legal capture available produces occupancy
     * identical to "the capture already happened". The system must never auto-apply that guess -
     * and, since a UI prompt asking the player to confirm turned out to have an unreliably narrow
     * window in practice (see git history: essentially any further physical event moves the
     * detector on before a person reliably notices and taps a prompt in time, and once that happens
     * the prompt is a no-op anyway), there no longer is one. It just stays pending: resolved later
     * either by positive proof (the destination independently observed vacated, see {@link
     * #executingARealCapture_confirmsImmediatelyWithoutAsking}) or by a subsequent,
     * otherwise-unexplained physical event proving it by itself (the opponent's reply only making
     * sense once this capture is treated as finished).
     */
    @Test
    public void liftingACapturingPieceAlone_staysPendingWithoutAutoApplying()
            throws InterruptedException {
        FakeTransport transport = new FakeTransport();
        RecordingListener listener = new RecordingListener();
        PegasusGameBridge bridge = newBridgeOnMainThread(transport, listener);

        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(() -> bridge.connect(DEVICE_ADDRESS));
        assertTrue(listener.connected.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));

        // White pawn e4 can capture the black pawn on d5; kings elsewhere,
        // out of the way.
        String captureFen = "k7/8/8/3p4/4P3/8/8/4K3 w - - 0 1";
        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(() -> bridge.syncBoardToPosition(captureFen));

        byte[] payload = new byte[BoardState.SQUARE_COUNT];
        payload[BoardState.squareIndex("a8")] = 1;
        payload[BoardState.squareIndex("d5")] = 1;
        payload[BoardState.squareIndex("e4")] = 1;
        payload[BoardState.squareIndex("e1")] = 1;
        transport.feed(frame(PegasusMessageType.BOARD_DUMP, payload));
        assertTrue(
                "expected the synthetic position to synchronize first",
                listener.synced.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));

        // Lift the e4 pawn only; d5 (the piece it could capture) is
        // untouched, so occupancy alone already matches "exd5 completed".
        transport.feed(fieldUpdateFrame("e4", 0));

        assertFalse(
                "must not auto-apply a capture just because it was lifted",
                listener.moveConfirmed.await(1500, TimeUnit.MILLISECONDS));
    }

    /**
     * The normal, hands-on way to execute a capture: lift the attacker, explicitly remove the
     * captured piece (briefly vacating its square too), then place the attacker down. That sequence
     * is positive proof of a deliberate capture and must apply immediately - unlike the ambiguous
     * case covered by {@link #liftingACapturingPieceAlone_staysPendingWithoutAutoApplying} where
     * the destination is never touched at all.
     */
    @Test
    public void executingARealCapture_confirmsImmediatelyWithoutAsking()
            throws InterruptedException {
        FakeTransport transport = new FakeTransport();
        RecordingListener listener = new RecordingListener();
        PegasusGameBridge bridge = newBridgeOnMainThread(transport, listener);

        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(() -> bridge.connect(DEVICE_ADDRESS));
        assertTrue(listener.connected.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));

        String captureFen = "k7/8/8/3p4/4P3/8/8/4K3 w - - 0 1";
        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(() -> bridge.syncBoardToPosition(captureFen));

        byte[] payload = new byte[BoardState.SQUARE_COUNT];
        payload[BoardState.squareIndex("a8")] = 1;
        payload[BoardState.squareIndex("d5")] = 1;
        payload[BoardState.squareIndex("e4")] = 1;
        payload[BoardState.squareIndex("e1")] = 1;
        transport.feed(frame(PegasusMessageType.BOARD_DUMP, payload));
        assertTrue(listener.synced.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));

        transport.feed(fieldUpdateFrame("e4", 0)); // lift the attacker
        transport.feed(fieldUpdateFrame("d5", 0)); // remove the captured piece
        transport.feed(fieldUpdateFrame("d5", 1)); // place the attacker down

        assertTrue(
                "expected the capture to confirm right away",
                listener.moveConfirmed.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertEquals("e4d5", listener.confirmedUci.get());
    }

    /**
     * Regression coverage for a fixed bug in engine-move LED guidance ({@link
     * PegasusGameBridge#guideEngineMove}): same root ambiguity as {@link
     * #liftingACapturingPiece_asksForConfirmationInsteadOfAutoApplying}, but on the guidance path
     * instead of move detection. A capture's destination square is occupied both before and after
     * the move (by the captured piece, then the attacker), so merely lifting the attacker off its
     * origin already matches the guide's target occupancy - the LEDs must keep indicating the
     * destination instead of declaring guidance complete before the captured piece was actually
     * removed and replaced.
     */
    @Test
    public void guideEngineCaptureMove_completesOnlyAfterTheCapturedPieceIsActuallyReplaced()
            throws InterruptedException {
        FakeTransport transport = new FakeTransport();
        RecordingListener listener = new RecordingListener();
        PegasusGameBridge bridge = newBridgeOnMainThread(transport, listener);

        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(() -> bridge.connect(DEVICE_ADDRESS));
        assertTrue(listener.connected.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));

        // Black queen a5 can capture the white knight on a4; kings elsewhere.
        String captureFen = "7k/8/8/q7/N7/8/8/7K b - - 0 1";
        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(() -> bridge.syncBoardToPosition(captureFen));

        byte[] payload = new byte[BoardState.SQUARE_COUNT];
        payload[BoardState.squareIndex("h8")] = 1;
        payload[BoardState.squareIndex("a5")] = 1;
        payload[BoardState.squareIndex("a4")] = 1;
        payload[BoardState.squareIndex("h1")] = 1;
        transport.feed(frame(PegasusMessageType.BOARD_DUMP, payload));
        assertTrue(listener.synced.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));

        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(() -> bridge.guideEngineMove("a5a4"));

        // Lift the queen only; a4 (the knight it captures) is untouched, so
        // occupancy alone already matches "Qxa4 completed".
        transport.feed(fieldUpdateFrame("a5", 0));
        assertFalse(
                "must not declare guidance complete just because the attacker was lifted",
                listener.guidanceComplete.await(300, TimeUnit.MILLISECONDS));

        transport.feed(fieldUpdateFrame("a4", 0)); // remove the captured knight
        transport.feed(fieldUpdateFrame("a4", 1)); // place the queen down

        assertTrue(
                "expected guidance to complete once the capture was actually reproduced",
                listener.guidanceComplete.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
    }

    private static void drain(FakeTransport transport) throws InterruptedException {
        while (transport.written.poll(300, TimeUnit.MILLISECONDS) != null) {
            // discard
        }
    }

    /**
     * Regression coverage for a hardware-verified UX gap (real DGT Pegasus): occupancy-only diffing
     * sees a capture's destination as already "correct" (still occupied by the piece about to be
     * captured), so the guide's own automatic diff only lit the origin square when a capture guide
     * started - the player had no visual cue a piece also had to be removed from the destination
     * until they'd already lifted the attacker. {@link PegasusGameBridge#guideEngineMove} must show
     * both squares immediately.
     */
    @Test
    public void guideEngineCaptureMove_lightsBothSquaresImmediately() throws InterruptedException {
        FakeTransport transport = new FakeTransport();
        RecordingListener listener = new RecordingListener();
        PegasusGameBridge bridge = newBridgeOnMainThread(transport, listener);

        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(() -> bridge.connect(DEVICE_ADDRESS));
        assertTrue(listener.connected.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));

        // Black queen a5 can capture the white knight on a4; kings elsewhere.
        String captureFen = "7k/8/8/q7/N7/8/8/7K b - - 0 1";
        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(() -> bridge.syncBoardToPosition(captureFen));

        byte[] payload = new byte[BoardState.SQUARE_COUNT];
        payload[BoardState.squareIndex("h8")] = 1;
        payload[BoardState.squareIndex("a5")] = 1;
        payload[BoardState.squareIndex("a4")] = 1;
        payload[BoardState.squareIndex("h1")] = 1;
        transport.feed(frame(PegasusMessageType.BOARD_DUMP, payload));
        assertTrue(listener.synced.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        transport.written.clear();

        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(() -> bridge.guideEngineMove("a5a4"));

        byte[] expected =
                PegasusCommands.encodeLeds(
                        PegasusLedController.DEFAULT_SPEED,
                        PegasusLedController.MODE_STEADY,
                        PegasusLedController.DEFAULT_INTENSITY,
                        BoardState.squareIndex("a5"),
                        BoardState.squareIndex("a4"));
        byte[] lastCommand = transport.written.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertTrue(
                "expected at least one LED command as soon as guidance starts",
                lastCommand != null);
        byte[] next;
        while ((next = transport.written.poll(300, TimeUnit.MILLISECONDS)) != null) {
            lastCommand = next;
        }
        assertTrue(
                "expected the final LED command at guide start to light both a5 (origin) "
                        + "and a4 (capture destination), not just the origin",
                Arrays.equals(expected, lastCommand));
    }

    /**
     * Regression coverage for a hardware-verified gap (real DGT Pegasus): real hardware clears an
     * already-lit LED pattern on its own whenever a physical square's occupancy changes, even when
     * the app never sent an off/overwrite command and even when the newly computed indication
     * happens to be identical to what was already shown (both {@link
     * de.schliweb.pegasus.core.movedetect.BoardSyncGuide}'s own and {@link PegasusLedController}'s
     * dedupe would otherwise silently skip re-sending). Every physical event during an active
     * capture guide must force a fresh send - purely event-driven, not on a timer, so it holds no
     * matter how fast or slowly the player acts.
     */
    @Test
    public void guideEngineCaptureMove_resendsLedsOnEveryPhysicalEventDuringAmbiguity()
            throws InterruptedException {
        FakeTransport transport = new FakeTransport();
        RecordingListener listener = new RecordingListener();
        PegasusGameBridge bridge = newBridgeOnMainThread(transport, listener);

        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(() -> bridge.connect(DEVICE_ADDRESS));
        assertTrue(listener.connected.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));

        String captureFen = "7k/8/8/q7/N7/8/8/7K b - - 0 1";
        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(() -> bridge.syncBoardToPosition(captureFen));

        byte[] payload = new byte[BoardState.SQUARE_COUNT];
        payload[BoardState.squareIndex("h8")] = 1;
        payload[BoardState.squareIndex("a5")] = 1;
        payload[BoardState.squareIndex("a4")] = 1;
        payload[BoardState.squareIndex("h1")] = 1;
        transport.feed(frame(PegasusMessageType.BOARD_DUMP, payload));
        assertTrue(listener.synced.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));

        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(() -> bridge.guideEngineMove("a5a4"));
        drain(transport); // discard the initial guide-start commands

        transport.feed(fieldUpdateFrame("a5", 0)); // lift the attacker
        assertTrue(
                "expected an LED re-assertion right after lifting the attacker",
                transport.written.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS) != null);
        drain(transport);

        transport.feed(fieldUpdateFrame("a4", 0)); // remove the captured knight
        assertTrue(
                "expected an LED re-assertion after removing the captured piece, even "
                        + "though the indicated square set is unchanged (the hardware-observed gap)",
                transport.written.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS) != null);

        transport.feed(fieldUpdateFrame("a4", 1)); // place the queen down
        assertTrue(
                "expected guidance to complete once the capture was actually reproduced",
                listener.guidanceComplete.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
    }
}
