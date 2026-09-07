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
 * Windows implementation of {@link PegasusTransport} on top of the Windows Runtime's {@code
 * Windows.Devices.Bluetooth} APIs, via a small C++/WinRT JNI bridge ({@code
 * desktop/src/main/native/windows/PegasusBleWin.cpp}). Raw bytes only - no DGT message
 * interpretation (phase 1 boundary), matching {@code AndroidPegasusBleTransport} and {@link
 * MacosPegasusBleTransport}.
 *
 * <p>The device "address" exposed to Java/UI is the standard colon-separated hex MAC string (e.g.
 * {@code "AA:BB:CC:DD:EE:FF"}) parsed from the 48-bit {@code BluetoothAddress} WinRT reports -
 * unlike macOS's CoreBluetooth (which has no stable MAC-like address at all, only a per-session
 * peripheral UUID), Windows can reconnect by address alone without caching any discovered-device
 * object, so there is no device registry/cache here.
 *
 * <p>The native layer only reports raw state transitions and errors; this class owns all policy -
 * connect-timeout and reconnect-on-unexpected-disconnect - reusing {@code pegasus-core}'s {@link
 * ReconnectPolicy} exactly as {@code AndroidPegasusBleTransport}/{@link MacosPegasusBleTransport}
 * do, keeping the platform-specific native code as thin as possible. Deliberately duplicated rather
 * than shared with {@link MacosPegasusBleTransport} (same policy logic, ~150 lines): the two native
 * layers have different threading/callback-origination guarantees (see {@code PegasusBleWin.cpp}'s
 * header comment - WinRT callbacks arrive on arbitrary thread-pool threads, unlike CoreBluetooth's
 * single delegate queue), and the macOS transport is hardware-verified while this one is not yet -
 * a shared base class would risk that verified code for an untested one.
 *
 * <p>Every native callback wraps its body in {@link Platform#runLater} before touching any shared
 * state, matching {@code DesktopPegasusGameBridge}'s single-thread contract.
 */
public final class WindowsPegasusBleTransport implements PegasusTransport {

    private static final Logger LOG = Logger.getLogger(WindowsPegasusBleTransport.class.getName());
    private static final long CONNECT_TIMEOUT_MS = 15000;

    static {
        try {
            PegasusBleWinLibrary.loadIfNeeded();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private long handle;
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(
                    r -> {
                        Thread t = new Thread(r, "pegasus-ble-win-timer");
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

    public WindowsPegasusBleTransport() {
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
            // "DISCONNECTED" callback finds state != DISCONNECTED and runs
            // handleUnexpectedDisconnect() itself, exactly like
            // MacosPegasusBleTransport's own onConnectTimeout.
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

    // ---- native callbacks, invoked from PegasusBleWin.cpp --------------------

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
     * WinRT reports every disconnect (manual or unexpected) the same way - distinguishing the two
     * requires checking whether {@link #state} was already set to {@code DISCONNECTED} by our own
     * {@link #disconnect()} before this callback arrived. Mirrors {@link
     * MacosPegasusBleTransport#handleNativeDisconnected()} exactly.
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

    // ---- native methods, implemented in PegasusBleWin.cpp --------------------

    private native long nativeCreate(
            String uartServiceUuid, String writeCharUuid, String notifyCharUuid);

    private static native void nativeDestroy(long handle);

    private static native void nativeStartScan(long handle);

    private static native void nativeStopScan(long handle);

    private static native void nativeConnect(long handle, String deviceId);

    private static native void nativeDisconnect(long handle);

    private static native void nativeWrite(long handle, byte[] data);
}
