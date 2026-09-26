/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.chessnut;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import de.schliweb.chessnut.core.game.ChessnutGameFlow;
import de.schliweb.chessnut.core.protocol.ChessnutBatteryStatus;
import de.schliweb.chessnut.core.protocol.ChessnutDevice;
import de.schliweb.chessnut.core.protocol.ChessnutDeviceListener;
import de.schliweb.chessnut.core.protocol.ChessnutFrame;
import de.schliweb.chessnut.core.protocol.ChessnutLedController;
import de.schliweb.pegasus.core.protocol.BoardState;
import de.schliweb.pegasus.core.record.SessionRecorder;
import de.schliweb.pegasus.core.transport.ConnectionState;
import de.schliweb.pegasus.core.transport.PegasusTransport;
import de.schliweb.pegasus.core.transport.ScanListener;
import de.schliweb.pegasus.core.transport.TransportError;
import de.schliweb.pegasus.core.transport.TransportListener;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

/**
 * Bridges a physical Chessnut Air (via a {@link PegasusTransport} built for {@link
 * de.schliweb.chessnut.core.protocol.ChessnutUuids#PROFILE}) to MoveAPiece's own game state. The
 * game logic itself lives in {@link ChessnutGameFlow}; this class adds what is Android-specific:
 * marshalling every transport callback onto the main thread, the connect/init sequence, periodic
 * battery polling and optional raw-traffic recording.
 *
 * <p>Same public surface as the Pegasus bridge where the concepts overlap, minus promotion and
 * ambiguity resolution: the Chessnut reports piece identity, so neither can arise.
 *
 * <p>Runs entirely on the main thread; all transport callbacks are posted onto it.
 */
public class ChessnutGameBridge {

    private static final String TAG = "ChessnutGameBridge";

    /** Battery is not pushed by the board; poll it now and then. */
    private static final long BATTERY_POLL_INTERVAL_MS = 60_000;

    /** Physical-board events relevant to MoveAPiece's game flow. */
    public interface Listener extends ChessnutGameFlow.Listener {
        void onConnectionStateChanged(ConnectionState state);

        void onTransportError(TransportError error, String detail);

        /**
         * Reported once per connect and again on every transition into a low battery; routine
         * readings in between are logged only.
         */
        void onBatteryStatus(int percent, boolean low);

        /** NEW GAME was pressed on the board (debounced). The host decides what that means. */
        void onNewGameButton();
    }

    private final PegasusTransport transport;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ChessnutDevice device;
    private final ChessnutLedController ledController;
    private final ChessnutGameFlow flow;
    private final Runnable batteryPollRunnable = this::pollBattery;

    private boolean batteryReportPending;
    private boolean lastBatteryLow;
    private volatile Listener listener;
    private SessionRecorder sessionRecorder;

    public ChessnutGameBridge(PegasusTransport transport, Listener listener) {
        this.transport = transport;
        this.listener = listener;
        ChessnutDevice.CommandSink sink =
                command -> {
                    if (transport.getConnectionState() == ConnectionState.CONNECTED) {
                        transport.write(command);
                    }
                };
        this.ledController = new ChessnutLedController(sink);
        this.flow =
                new ChessnutGameFlow(
                        ledController,
                        new ChessnutGameFlow.Listener() {
                            @Override
                            public void onPhysicalMoveConfirmed(String uci) {
                                Listener l = listener();
                                if (l != null) {
                                    l.onPhysicalMoveConfirmed(uci);
                                }
                            }

                            @Override
                            public void onBoardMismatch(boolean mismatched) {
                                Listener l = listener();
                                if (l != null) {
                                    l.onBoardMismatch(mismatched);
                                }
                            }

                            @Override
                            public void onEngineMoveGuidanceComplete() {
                                Listener l = listener();
                                if (l != null) {
                                    l.onEngineMoveGuidanceComplete();
                                }
                            }

                            @Override
                            public void onGuideDeviation(boolean deviating) {
                                Listener l = listener();
                                if (l != null) {
                                    l.onGuideDeviation(deviating);
                                }
                            }
                        });
        this.device =
                new ChessnutDevice(
                        sink,
                        new ChessnutDeviceListener() {
                            @Override
                            public void onBoardState(BoardState state, long uptimeSeconds) {
                                Log.i(TAG, "board (uptime " + uptimeSeconds + "s):\n" + state);
                                flow.onPhysicalBoard(state);
                            }

                            @Override
                            public void onBatteryStatus(ChessnutBatteryStatus status) {
                                onBattery(status);
                            }

                            @Override
                            public void onNewGameButton() {
                                Log.i(TAG, "NEW GAME button");
                                Listener l = listener();
                                if (l != null) {
                                    l.onNewGameButton();
                                }
                            }

                            @Override
                            public void onAck() {}

                            @Override
                            public void onUnknownFrame(
                                    String characteristicUuid, ChessnutFrame frame) {
                                Log.i(TAG, "unknown frame on " + characteristicUuid + ": " + frame);
                            }
                        });
        transport.setListener(
                new TransportListener() {
                    @Override
                    public void onConnectionStateChanged(ConnectionState state) {
                        mainHandler.post(() -> onTransportState(state));
                    }

                    @Override
                    public void onDataReceived(String characteristicUuid, byte[] data) {
                        byte[] copy = data.clone();
                        recordIfActive(SessionRecorder.Direction.RX, characteristicUuid, copy);
                        mainHandler.post(() -> device.onDataReceived(characteristicUuid, copy));
                    }

                    @Override
                    public void onDataSent(String characteristicUuid, byte[] data) {
                        recordIfActive(SessionRecorder.Direction.TX, characteristicUuid, data);
                    }

                    @Override
                    public void onError(TransportError error, String detail) {
                        mainHandler.post(
                                () -> {
                                    Listener l = listener();
                                    if (l != null) {
                                        l.onTransportError(error, detail);
                                    }
                                });
                    }
                });
    }

    private void onTransportState(ConnectionState state) {
        mainHandler.removeCallbacks(batteryPollRunnable);
        if (state == ConnectionState.CONNECTED) {
            // Reconnect-safe: drop partial frames, the dedupe memory and transient game
            // state, keep the logical position; the first report re-syncs it.
            device.reset();
            flow.onConnected();
            batteryReportPending = true;
            lastBatteryLow = false;
            device.initialize();
            mainHandler.postDelayed(batteryPollRunnable, BATTERY_POLL_INTERVAL_MS);
        }
        Listener l = listener();
        if (l != null) {
            l.onConnectionStateChanged(state);
        }
    }

    private void pollBattery() {
        if (transport.getConnectionState() != ConnectionState.CONNECTED) {
            return;
        }
        device.requestBattery();
        mainHandler.postDelayed(batteryPollRunnable, BATTERY_POLL_INTERVAL_MS);
    }

    private void onBattery(ChessnutBatteryStatus status) {
        Log.i(TAG, "battery: " + status);
        boolean newlyLow = status.isLow() && !lastBatteryLow;
        boolean shouldNotify = batteryReportPending || newlyLow;
        batteryReportPending = false;
        lastBatteryLow = status.isLow();
        if (shouldNotify) {
            Listener l = listener();
            if (l != null) {
                l.onBatteryStatus(status.percent(), status.isLow());
            }
        }
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    private Listener listener() {
        return listener;
    }

    // ------------------------------------------------------------ transport

    public void startScan(ScanListener scanListener, long timeoutMs) {
        transport.startScan(scanListener, timeoutMs);
    }

    public void stopScan() {
        transport.stopScan();
    }

    public void connect(String deviceAddress) {
        transport.connect(deviceAddress);
    }

    public void disconnect() {
        transport.disconnect();
    }

    public void shutdown() {
        mainHandler.removeCallbacksAndMessages(null);
        transport.disconnect();
        stopRecording();
    }

    public ConnectionState getConnectionState() {
        return transport.getConnectionState();
    }

    /** Asks the board for its battery level; answered via {@link Listener#onBatteryStatus}. */
    public void requestBattery() {
        batteryReportPending = true;
        device.requestBattery();
    }

    /** Plays a tone on the board's speaker; ignored while not connected. */
    public void beep(int frequencyHz, int durationMs) {
        device.beep(frequencyHz, durationMs);
    }

    // ------------------------------------------------------------ recording

    /**
     * Starts recording raw BLE traffic as NDJSON to {@code file} for hardware-verification
     * sessions. Replaces any recording already in progress.
     */
    public void startRecording(File file) throws IOException {
        stopRecording();
        sessionRecorder = new SessionRecorder(new FileWriter(file, false));
    }

    /** Stops and flushes the current recording, if any. Safe to call repeatedly. */
    public void stopRecording() {
        SessionRecorder recorder = sessionRecorder;
        sessionRecorder = null;
        if (recorder != null) {
            try {
                recorder.close();
            } catch (IOException e) {
                Log.w(TAG, "stopRecording: close failed", e);
            }
        }
    }

    private void recordIfActive(
            SessionRecorder.Direction dir, String characteristicUuid, byte[] data) {
        SessionRecorder recorder = sessionRecorder;
        if (recorder == null) {
            return;
        }
        try {
            recorder.record(dir, characteristicUuid, data);
        } catch (IOException e) {
            Log.w(TAG, "recording: write failed, stopping", e);
            stopRecording();
        }
    }

    // ------------------------------------------------------------ game flow

    /** See {@link ChessnutGameFlow#guideEngineMove(String)}. */
    public void guideEngineMove(String uciMove) {
        flow.guideEngineMove(uciMove);
    }

    /** See {@link ChessnutGameFlow#guideEngineMove(String, boolean)}. */
    public void guideEngineMove(String uciMove, boolean showLed) {
        flow.guideEngineMove(uciMove, showLed);
    }

    public boolean isGuideActive() {
        return flow.isGuideActive();
    }

    public String trackedFen() {
        return flow.trackedFen();
    }

    public boolean isBoardInSync() {
        return flow.isBoardInSync();
    }

    public boolean isBoardMismatched() {
        return flow.isBoardMismatched();
    }

    /** Squares the board currently disagrees on, as {@link BoardState} indices. */
    public List<Integer> mismatchSquares() {
        return flow.mismatchSquares();
    }

    public void resetForNewGame() {
        flow.resetForNewGame();
    }

    public void syncBoardToPosition(String fen) {
        flow.syncBoardToPosition(fen);
    }
}
