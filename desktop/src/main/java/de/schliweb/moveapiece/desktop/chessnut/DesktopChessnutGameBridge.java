/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.desktop.chessnut;

import de.schliweb.chessnut.core.game.ChessnutGameFlow;
import de.schliweb.chessnut.core.game.InvalidPositionException;
import de.schliweb.chessnut.core.protocol.ChessnutBatteryStatus;
import de.schliweb.chessnut.core.protocol.ChessnutDevice;
import de.schliweb.chessnut.core.protocol.ChessnutDeviceListener;
import de.schliweb.chessnut.core.protocol.ChessnutFrame;
import de.schliweb.chessnut.core.protocol.ChessnutLedController;
import de.schliweb.pegasus.core.chess.PieceColor;
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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;

/**
 * Desktop counterpart of the Android {@code ChessnutGameBridge}: bridges a Chessnut Air (via a
 * {@link PegasusTransport} built for {@link
 * de.schliweb.chessnut.core.protocol.ChessnutUuids#PROFILE}) to the desktop game. The game logic
 * lives in {@link ChessnutGameFlow}; this class marshals every transport callback onto the JavaFX
 * Application Thread, runs the connect/init sequence, polls the battery once a minute and records
 * raw traffic on request.
 *
 * <p>Runs entirely on the JavaFX Application Thread.
 */
public class DesktopChessnutGameBridge {

    private static final Logger LOG = Logger.getLogger(DesktopChessnutGameBridge.class.getName());

    /** Battery is not pushed by the board; poll it now and then. */
    private static final long BATTERY_POLL_INTERVAL_MS = 60_000;

    /** Physical-board events relevant to the desktop game flow. */
    public interface Listener extends ChessnutGameFlow.Listener {
        void onConnectionStateChanged(ConnectionState state);

        void onTransportError(TransportError error, String detail);

        /** Once per connect and on every transition into a low battery. */
        void onBatteryStatus(int percent, boolean low);

        /** NEW GAME was pressed on the board (debounced). The host decides what that means. */
        void onNewGameButton();
    }

    private final PegasusTransport transport;
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(
                    r -> {
                        Thread t = new Thread(r, "chessnut-bridge-timer");
                        t.setDaemon(true);
                        return t;
                    });
    private final ChessnutDevice device;
    private final ChessnutLedController ledController;
    private final ChessnutGameFlow flow;

    private ScheduledFuture<?> batteryPollTask;
    private boolean batteryReportPending;
    private boolean lastBatteryLow;
    private volatile Listener listener;
    private SessionRecorder sessionRecorder;

    public DesktopChessnutGameBridge(PegasusTransport transport, Listener listener) {
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
                                LOG.log(
                                        Level.INFO,
                                        "board (uptime {0}s):\n{1}",
                                        new Object[] {uptimeSeconds, state});
                                flow.onPhysicalBoard(state);
                            }

                            @Override
                            public void onBatteryStatus(ChessnutBatteryStatus status) {
                                onBattery(status);
                            }

                            @Override
                            public void onNewGameButton() {
                                LOG.info("NEW GAME button");
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
                                LOG.log(
                                        Level.INFO,
                                        "unknown frame on {0}: {1}",
                                        new Object[] {characteristicUuid, frame});
                            }
                        });
        transport.setListener(
                new TransportListener() {
                    @Override
                    public void onConnectionStateChanged(ConnectionState state) {
                        Platform.runLater(() -> onTransportState(state));
                    }

                    @Override
                    public void onDataReceived(String characteristicUuid, byte[] data) {
                        byte[] copy = data.clone();
                        recordIfActive(SessionRecorder.Direction.RX, characteristicUuid, copy);
                        Platform.runLater(() -> device.onDataReceived(characteristicUuid, copy));
                    }

                    @Override
                    public void onDataSent(String characteristicUuid, byte[] data) {
                        recordIfActive(SessionRecorder.Direction.TX, characteristicUuid, data);
                    }

                    @Override
                    public void onError(TransportError error, String detail) {
                        Platform.runLater(
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
        cancelBatteryPoll();
        if (state == ConnectionState.CONNECTED) {
            device.reset();
            flow.onConnected();
            batteryReportPending = true;
            lastBatteryLow = false;
            device.initialize();
            scheduleBatteryPoll();
        }
        Listener l = listener();
        if (l != null) {
            l.onConnectionStateChanged(state);
        }
    }

    private void scheduleBatteryPoll() {
        batteryPollTask =
                scheduler.schedule(
                        () -> Platform.runLater(this::pollBattery),
                        BATTERY_POLL_INTERVAL_MS,
                        TimeUnit.MILLISECONDS);
    }

    private void cancelBatteryPoll() {
        if (batteryPollTask != null) {
            batteryPollTask.cancel(false);
            batteryPollTask = null;
        }
    }

    private void pollBattery() {
        if (transport.getConnectionState() != ConnectionState.CONNECTED) {
            return;
        }
        device.requestBattery();
        scheduleBatteryPoll();
    }

    private void onBattery(ChessnutBatteryStatus status) {
        LOG.log(Level.INFO, "battery: {0}", status);
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
        cancelBatteryPoll();
        scheduler.shutdownNow();
        transport.disconnect();
        stopRecording();
    }

    public ConnectionState getConnectionState() {
        return transport.getConnectionState();
    }

    /** Plays a tone on the board's speaker; ignored while not connected. */
    public void beep(int frequencyHz, int durationMs) {
        device.beep(frequencyHz, durationMs);
    }

    // ------------------------------------------------------------ recording

    public void startRecording(File file) throws IOException {
        stopRecording();
        sessionRecorder = new SessionRecorder(new FileWriter(file, false));
    }

    public void stopRecording() {
        SessionRecorder recorder = sessionRecorder;
        sessionRecorder = null;
        if (recorder != null) {
            try {
                recorder.close();
            } catch (IOException e) {
                LOG.log(Level.WARNING, "stopRecording: close failed", e);
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
            LOG.log(Level.WARNING, "recording: write failed, stopping", e);
            stopRecording();
        }
    }

    // ------------------------------------------------------------ game flow

    public void guideEngineMove(String uciMove) {
        flow.guideEngineMove(uciMove);
    }

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

    public List<Integer> mismatchSquares() {
        return flow.mismatchSquares();
    }

    public int promotionSquareAwaitingPiece() {
        return flow.promotionSquareAwaitingPiece();
    }

    public String physicalPositionFen(PieceColor sideToMove) throws InvalidPositionException {
        return flow.physicalPositionFen(sideToMove);
    }

    public void resetForNewGame() {
        flow.resetForNewGame();
    }

    public void syncBoardToPosition(String fen) {
        flow.syncBoardToPosition(fen);
    }
}
