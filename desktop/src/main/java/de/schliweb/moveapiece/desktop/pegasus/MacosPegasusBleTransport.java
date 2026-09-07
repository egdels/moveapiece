/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.desktop.pegasus;

import de.schliweb.pegasus.core.transport.ConnectionState;
import de.schliweb.pegasus.core.transport.DiscoveredDevice;
import de.schliweb.pegasus.core.transport.PegasusTransport;
import de.schliweb.pegasus.core.transport.PegasusUuids;
import de.schliweb.pegasus.core.transport.ReconnectPolicy;
import de.schliweb.pegasus.core.transport.ScanListener;
import de.schliweb.pegasus.core.transport.TransportError;
import de.schliweb.pegasus.core.transport.TransportListener;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;

/**
 * macOS implementation of {@link PegasusTransport} on top of CoreBluetooth, via a small JNI bridge
 * ({@code desktop/src/main/native/macos/PegasusBleMac.m}). Raw bytes only - no DGT message
 * interpretation (phase 1 boundary), matching {@code AndroidPegasusBleTransport}.
 *
 * <p>The native layer only reports raw CoreBluetooth state transitions and errors; this class owns
 * all policy - connect-timeout and reconnect-on-unexpected-disconnect - reusing {@code
 * pegasus-core}'s {@link ReconnectPolicy} exactly as {@code AndroidPegasusBleTransport} does,
 * keeping the platform-specific native code as thin as possible.
 *
 * <p>Every native callback is expected to already arrive on the JavaFX Application Thread (see
 * {@code PegasusBleMac.m}'s header comment: CoreBluetooth's delegate queue is {@code
 * dispatch_get_main_queue()}, which on macOS is the same thread JavaFX pins its Application Thread
 * to). Callback bodies are still wrapped in {@link Platform#runLater} defensively and for
 * consistency with {@code DesktopPegasusGameBridge}'s single-thread contract - a cheap no-op hop
 * when already on that thread.
 */
public final class MacosPegasusBleTransport implements PegasusTransport {

    private static final Logger LOG = Logger.getLogger(MacosPegasusBleTransport.class.getName());
    private static final long CONNECT_TIMEOUT_MS = 15000;

    static {
        try {
            PegasusBleMacLibrary.loadIfNeeded();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private final long handle;
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(
                    r -> {
                        Thread t = new Thread(r, "pegasus-ble-mac-timer");
                        t.setDaemon(true);
                        return t;
                    });
    private final ReconnectPolicy reconnectPolicy = new ReconnectPolicy();

    private volatile TransportListener listener;
    private volatile ScanListener scanListener;
    private volatile ConnectionState state = ConnectionState.DISCONNECTED;
    private String currentAddress;
    private ScheduledFuture<?> scanTimeoutTask;
    private ScheduledFuture<?> connectTimeoutTask;
    private ScheduledFuture<?> reconnectTask;

    public MacosPegasusBleTransport() {
        handle =
                nativeCreate(
                        PegasusUuids.UART_SERVICE,
                        PegasusUuids.UART_WRITE_CHARACTERISTIC,
                        PegasusUuids.UART_NOTIFY_CHARACTERISTIC);
    }

    @Override
    public void setListener(TransportListener listener) {
        this.listener = listener;
    }

    @Override
    public void startScan(ScanListener listener, long timeoutMs) {
        this.scanListener = listener;
        cancelScanTimeout();
        nativeStartScan(handle);
        scanTimeoutTask =
                scheduler.schedule(
                        () -> Platform.runLater(() -> stopScanInternal(true)),
                        timeoutMs,
                        TimeUnit.MILLISECONDS);
    }

    @Override
    public void stopScan() {
        stopScanInternal(true);
    }

    private void stopScanInternal(boolean notifyFinished) {
        cancelScanTimeout();
        nativeStopScan(handle);
        if (notifyFinished) {
            ScanListener l = scanListener;
            if (l != null) {
                l.onScanFinished();
            }
        }
    }

    private void cancelScanTimeout() {
        if (scanTimeoutTask != null) {
            scanTimeoutTask.cancel(false);
            scanTimeoutTask = null;
        }
    }

    @Override
    public void connect(String deviceAddress) {
        reconnectPolicy.onConnectRequested();
        connectInternal(deviceAddress);
    }

    private void connectInternal(String deviceAddress) {
        cancelScanTimeout();
        cancelConnectTimeout();
        currentAddress = deviceAddress;
        nativeConnect(handle, deviceAddress);
        connectTimeoutTask =
                scheduler.schedule(
                        () -> Platform.runLater(this::onConnectTimeout),
                        CONNECT_TIMEOUT_MS,
                        TimeUnit.MILLISECONDS);
    }

    private void onConnectTimeout() {
        if (state == ConnectionState.CONNECTING
                || state == ConnectionState.DISCOVERING_SERVICES
                || state == ConnectionState.SUBSCRIBING) {
            LOG.log(Level.WARNING, "Connect timeout");
            emitError(
                    TransportError.CONNECT_TIMEOUT,
                    "No connection within " + CONNECT_TIMEOUT_MS + " ms");
            // Quiet cleanup, not a manual disconnect: the resulting native
            // "DISCONNECTED" callback (see nativeOnConnectionStateChanged)
            // finds state != DISCONNECTED and runs handleUnexpectedDisconnect()
            // itself, exactly like AndroidPegasusBleTransport's own
            // connectTimeoutRunnable.
            nativeDisconnect(handle);
        }
    }

    @Override
    public void disconnect() {
        reconnectPolicy.onManualDisconnect();
        cancelConnectTimeout();
        cancelReconnectTask();
        nativeDisconnect(handle);
        setState(ConnectionState.DISCONNECTED);
    }

    /** Call when the owning component is destroyed for good. */
    public void shutdown() {
        disconnect();
        stopScanInternal(false);
        scheduler.shutdownNow();
        nativeDestroy(handle);
    }

    @Override
    public void write(byte[] data) {
        if (state != ConnectionState.CONNECTED) {
            emitError(TransportError.WRITE_FAILED, "Not connected");
            return;
        }
        nativeWrite(handle, data);
    }

    @Override
    public ConnectionState getConnectionState() {
        return state;
    }

    // ---- native callbacks, invoked from PegasusBleMac.m --------------------

    private void onNativeDeviceFound(String address, String name, int rssi) {
        Platform.runLater(
                () -> {
                    ScanListener l = scanListener;
                    if (l != null) {
                        l.onDeviceFound(new DiscoveredDevice(name, address, rssi, List.of()));
                    }
                });
    }

    private void onNativeScanFailed(String errorName, String detail) {
        Platform.runLater(
                () -> {
                    ScanListener l = scanListener;
                    if (l != null) {
                        l.onScanFailed(mapError(errorName), detail);
                    }
                });
    }

    private void onNativeConnectionStateChanged(String stateName) {
        Platform.runLater(
                () -> {
                    ConnectionState newState = mapState(stateName);
                    if (newState == null) {
                        return;
                    }
                    if (newState == ConnectionState.DISCONNECTED) {
                        handleNativeDisconnected();
                        return;
                    }
                    setState(newState);
                });
    }

    /**
     * CoreBluetooth reports every disconnect (manual or unexpected) the same way - like Android's
     * {@code onConnectionStateChange(STATE_DISCONNECTED)}, distinguishing the two requires checking
     * whether {@link #state} was already set to {@code DISCONNECTED} by our own {@link
     * #disconnect()} before this callback arrived.
     */
    private void handleNativeDisconnected() {
        cancelConnectTimeout();
        boolean wasManual = state == ConnectionState.DISCONNECTED;
        if (wasManual) {
            return;
        }
        emitError(TransportError.DISCONNECTED_UNEXPECTEDLY, "state=" + state);
        if (currentAddress != null && reconnectPolicy.shouldReconnect()) {
            setState(ConnectionState.RECONNECTING);
            reconnectTask =
                    scheduler.schedule(
                            () ->
                                    Platform.runLater(
                                            () -> {
                                                if (state == ConnectionState.RECONNECTING) {
                                                    connectInternal(currentAddress);
                                                }
                                            }),
                            reconnectPolicy.getDelayMs(),
                            TimeUnit.MILLISECONDS);
        } else {
            if (reconnectPolicy.getAttemptsMade() > 0) {
                emitError(
                        TransportError.RECONNECT_GIVEN_UP,
                        "after " + reconnectPolicy.getAttemptsMade() + " attempts");
            }
            setState(ConnectionState.DISCONNECTED);
        }
    }

    private void onNativeDataReceived(byte[] data) {
        Platform.runLater(
                () -> {
                    TransportListener l = listener;
                    if (l != null) {
                        l.onDataReceived(PegasusUuids.UART_NOTIFY_CHARACTERISTIC, data);
                    }
                });
    }

    private void onNativeDataSent(byte[] data) {
        Platform.runLater(
                () -> {
                    TransportListener l = listener;
                    if (l != null) {
                        l.onDataSent(PegasusUuids.UART_WRITE_CHARACTERISTIC, data);
                    }
                });
    }

    private void onNativeError(String errorName, String detail) {
        Platform.runLater(() -> emitError(mapError(errorName), detail));
    }

    private static ConnectionState mapState(String name) {
        try {
            return ConnectionState.valueOf(name);
        } catch (IllegalArgumentException e) {
            LOG.log(Level.WARNING, "Unknown native connection state: {0}", name);
            return null;
        }
    }

    private static TransportError mapError(String name) {
        try {
            return TransportError.valueOf(name);
        } catch (IllegalArgumentException e) {
            return TransportError.CONNECT_FAILED;
        }
    }

    private void setState(ConnectionState newState) {
        if (state != newState) {
            state = newState;
            LOG.log(Level.INFO, "Connection state: {0}", newState);
            if (newState == ConnectionState.CONNECTED) {
                cancelConnectTimeout();
                reconnectPolicy.onConnected();
            }
            TransportListener l = listener;
            if (l != null) {
                l.onConnectionStateChanged(newState);
            }
        }
    }

    private void emitError(TransportError error, String detail) {
        LOG.log(Level.WARNING, "Error {0}: {1}", new Object[] {error, detail});
        TransportListener l = listener;
        if (l != null) {
            l.onError(error, detail);
        }
    }

    private void cancelConnectTimeout() {
        if (connectTimeoutTask != null) {
            connectTimeoutTask.cancel(false);
            connectTimeoutTask = null;
        }
    }

    private void cancelReconnectTask() {
        if (reconnectTask != null) {
            reconnectTask.cancel(false);
            reconnectTask = null;
        }
    }

    // ---- native methods, implemented in PegasusBleMac.m --------------------

    private native long nativeCreate(
            String uartServiceUuid, String writeCharUuid, String notifyCharUuid);

    private static native void nativeDestroy(long handle);

    private static native void nativeStartScan(long handle);

    private static native void nativeStopScan(long handle);

    private static native void nativeConnect(long handle, String deviceId);

    private static native void nativeDisconnect(long handle);

    private static native void nativeWrite(long handle, byte[] data);
}
