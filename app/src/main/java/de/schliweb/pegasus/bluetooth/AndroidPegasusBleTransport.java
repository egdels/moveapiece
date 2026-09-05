/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.pegasus.bluetooth;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothStatusCodes;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.util.Log;
import de.schliweb.pegasus.core.transport.ConnectionState;
import de.schliweb.pegasus.core.transport.DeviceRegistry;
import de.schliweb.pegasus.core.transport.DiscoveredDevice;
import de.schliweb.pegasus.core.transport.GattOperationQueue;
import de.schliweb.pegasus.core.transport.PegasusTransport;
import de.schliweb.pegasus.core.transport.PegasusUuids;
import de.schliweb.pegasus.core.transport.ReconnectPolicy;
import de.schliweb.pegasus.core.transport.ScanListener;
import de.schliweb.pegasus.core.transport.TransportError;
import de.schliweb.pegasus.core.transport.TransportListener;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Android implementation of {@link PegasusTransport} on top of the official BLE APIs. Raw bytes
 * only — no DGT message interpretation (phase 1 boundary).
 *
 * <p>Uses the application context only; no Activity references are kept (lifecycle notes:
 * docs/ANDROID_BLE.md).
 */
@SuppressLint("MissingPermission") // permissions are checked via BlePermissions before use
public final class AndroidPegasusBleTransport implements PegasusTransport {

    private static final String TAG = "PegasusBle";
    private static final long CONNECT_TIMEOUT_MS = 15000;
    private static final int REQUESTED_MTU = 247;

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final GattOperationQueue operationQueue = new GattOperationQueue(scheduler);
    private final ReconnectPolicy reconnectPolicy = new ReconnectPolicy();
    private final DeviceRegistry deviceRegistry = new DeviceRegistry();

    private volatile TransportListener listener;
    private volatile ScanListener scanListener;
    private volatile ConnectionState state = ConnectionState.DISCONNECTED;

    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic writeCharacteristic;
    private BluetoothGattCharacteristic notifyCharacteristic;
    private String currentAddress;
    private ScanCallback activeScanCallback;
    private Runnable scanTimeoutRunnable;
    private Runnable connectTimeoutRunnable;
    private final List<String> gattDiagnostics = new ArrayList<>();

    public AndroidPegasusBleTransport(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public void setListener(TransportListener listener) {
        this.listener = listener;
    }

    public DeviceRegistry getDeviceRegistry() {
        return deviceRegistry;
    }

    /** GATT structure observed after service discovery (for the debug UI). */
    public synchronized List<String> getGattDiagnostics() {
        return new ArrayList<>(gattDiagnostics);
    }

    // ------------------------------------------------------------------ scan

    @Override
    public void startScan(ScanListener listener, long timeoutMs) {
        this.scanListener = listener;
        if (!BlePermissions.allGranted(context)) {
            listener.onScanFailed(TransportError.PERMISSION_DENIED, "BLE permissions missing");
            return;
        }
        BluetoothAdapter adapter = getAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            listener.onScanFailed(TransportError.BLUETOOTH_DISABLED, "Bluetooth is disabled");
            return;
        }
        BluetoothLeScanner scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) {
            listener.onScanFailed(TransportError.SCAN_FAILED, "No BLE scanner available");
            return;
        }
        stopScanInternal(false);
        deviceRegistry.clear();

        activeScanCallback =
                new ScanCallback() {
                    @Override
                    public void onScanResult(int callbackType, ScanResult result) {
                        DiscoveredDevice device = toDiscoveredDevice(result);
                        if (deviceRegistry.update(device)) {
                            ScanListener l = scanListener;
                            if (l != null) {
                                l.onDeviceFound(device);
                            }
                        }
                    }

                    @Override
                    public void onScanFailed(int errorCode) {
                        Log.w(TAG, "Scan failed, code=" + errorCode);
                        stopScanInternal(false);
                        ScanListener l = scanListener;
                        if (l != null) {
                            l.onScanFailed(TransportError.SCAN_FAILED, "errorCode=" + errorCode);
                        }
                    }
                };
        ScanSettings settings =
                new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
        // No filter: unknown Pegasus units may advertise different names, and
        // some devices omit the service UUID in the advertisement. The user
        // selects the device; identification is verified via GATT after connect.
        scanner.startScan(null, settings, activeScanCallback);

        scanTimeoutRunnable = () -> stopScanInternal(true);
        mainHandler.postDelayed(scanTimeoutRunnable, timeoutMs);
    }

    @Override
    public void stopScan() {
        stopScanInternal(true);
    }

    private void stopScanInternal(boolean notifyFinished) {
        if (scanTimeoutRunnable != null) {
            mainHandler.removeCallbacks(scanTimeoutRunnable);
            scanTimeoutRunnable = null;
        }
        if (activeScanCallback != null) {
            BluetoothAdapter adapter = getAdapter();
            if (adapter != null && adapter.isEnabled()) {
                BluetoothLeScanner scanner = adapter.getBluetoothLeScanner();
                if (scanner != null) {
                    try {
                        scanner.stopScan(activeScanCallback);
                    } catch (IllegalStateException ignored) {
                        // Bluetooth turned off in the meantime
                    }
                }
            }
            activeScanCallback = null;
            if (notifyFinished) {
                ScanListener l = scanListener;
                if (l != null) {
                    l.onScanFinished();
                }
            }
        }
    }

    private static DiscoveredDevice toDiscoveredDevice(ScanResult result) {
        List<String> serviceUuids = new ArrayList<>();
        if (result.getScanRecord() != null && result.getScanRecord().getServiceUuids() != null) {
            for (ParcelUuid uuid : result.getScanRecord().getServiceUuids()) {
                serviceUuids.add(uuid.getUuid().toString());
            }
        }
        String name =
                result.getScanRecord() == null ? null : result.getScanRecord().getDeviceName();
        return new DiscoveredDevice(
                name, result.getDevice().getAddress(), result.getRssi(), serviceUuids);
    }

    // --------------------------------------------------------------- connect

    @Override
    public void connect(String deviceAddress) {
        reconnectPolicy.onConnectRequested();
        connectInternal(deviceAddress);
    }

    private void connectInternal(String deviceAddress) {
        if (!BlePermissions.allGranted(context)) {
            emitError(TransportError.PERMISSION_DENIED, "BLE permissions missing");
            return;
        }
        BluetoothAdapter adapter = getAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            emitError(TransportError.BLUETOOTH_DISABLED, "Bluetooth is disabled");
            return;
        }
        stopScanInternal(true);
        closeGattQuietly();

        currentAddress = deviceAddress;
        setState(ConnectionState.CONNECTING);
        BluetoothDevice device = adapter.getRemoteDevice(deviceAddress);
        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
        if (gatt == null) {
            setState(ConnectionState.DISCONNECTED);
            emitError(TransportError.CONNECT_FAILED, "connectGatt returned null");
            return;
        }
        connectTimeoutRunnable =
                () -> {
                    if (state == ConnectionState.CONNECTING
                            || state == ConnectionState.DISCOVERING_SERVICES
                            || state == ConnectionState.SUBSCRIBING) {
                        Log.w(TAG, "Connect timeout");
                        closeGattQuietly();
                        emitError(
                                TransportError.CONNECT_TIMEOUT,
                                "No connection within " + CONNECT_TIMEOUT_MS + " ms");
                        handleUnexpectedDisconnect();
                    }
                };
        mainHandler.postDelayed(connectTimeoutRunnable, CONNECT_TIMEOUT_MS);
    }

    @Override
    public void disconnect() {
        reconnectPolicy.onManualDisconnect();
        cancelConnectTimeout();
        operationQueue.clear();
        if (gatt != null) {
            try {
                gatt.disconnect();
            } catch (Exception ignored) {
            }
        }
        closeGattQuietly();
        setState(ConnectionState.DISCONNECTED);
    }

    /** Call when the owning component is destroyed for good. */
    public void shutdown() {
        disconnect();
        stopScanInternal(false);
        scheduler.shutdownNow();
    }

    @Override
    public ConnectionState getConnectionState() {
        return state;
    }

    // ----------------------------------------------------------------- write

    /**
     * Payload of the write currently in flight. Writes are serialized by the operation queue, so at
     * most one is pending. Needed because on API 33+ {@code characteristic.getValue()} is empty in
     * onCharacteristicWrite.
     */
    private volatile byte[] pendingWritePayload;

    @Override
    public void write(byte[] data) {
        final BluetoothGatt g = gatt;
        final BluetoothGattCharacteristic wc = writeCharacteristic;
        if (g == null || wc == null || state != ConnectionState.CONNECTED) {
            emitError(TransportError.WRITE_FAILED, "Not connected");
            return;
        }
        operationQueue.enqueue(
                new GattOperationQueue.Operation() {
                    @Override
                    public String name() {
                        return "write(" + data.length + " bytes)";
                    }

                    @Override
                    public boolean start() {
                        pendingWritePayload = data;
                        boolean ok;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            // WRITE_NO_RESPONSE: captured from the official DGT app via
                            // HCI snoop on real hardware (ATT opcode 0x52), 2026-08-16.
                            ok =
                                    g.writeCharacteristic(
                                                    wc,
                                                    data,
                                                    BluetoothGattCharacteristic
                                                            .WRITE_TYPE_NO_RESPONSE)
                                            == BluetoothStatusCodes.SUCCESS;
                        } else {
                            wc.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
                            wc.setValue(data);
                            ok = g.writeCharacteristic(wc);
                        }
                        if (!ok) {
                            emitError(TransportError.WRITE_FAILED, "writeCharacteristic rejected");
                        }
                        return ok;
                    }

                    @Override
                    public void onTimeout() {
                        emitError(TransportError.WRITE_FAILED, "Write timeout");
                    }
                });
        // onDataSent is emitted from onCharacteristicWrite when the stack confirms.
    }

    // ------------------------------------------------------------------ gatt

    private final BluetoothGattCallback gattCallback =
            new BluetoothGattCallback() {

                @Override
                public void onConnectionStateChange(BluetoothGatt g, int status, int newState) {
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        Log.i(TAG, "GATT connected, requesting MTU " + REQUESTED_MTU);
                        setState(ConnectionState.DISCOVERING_SERVICES);
                        if (!g.requestMtu(REQUESTED_MTU)) {
                            // MTU request refused synchronously → continue with discovery
                            g.discoverServices();
                        }
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        Log.i(TAG, "GATT disconnected, status=" + status);
                        boolean wasManual = state == ConnectionState.DISCONNECTED;
                        closeGattQuietly();
                        if (!wasManual) {
                            emitError(TransportError.DISCONNECTED_UNEXPECTEDLY, "status=" + status);
                            handleUnexpectedDisconnect();
                        }
                    }
                }

                @Override
                public void onMtuChanged(BluetoothGatt g, int mtu, int status) {
                    Log.i(TAG, "MTU changed to " + mtu + " (status=" + status + ")");
                    addDiagnostic("MTU: " + mtu);
                    g.discoverServices();
                }

                @Override
                public void onServicesDiscovered(BluetoothGatt g, int status) {
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        emitError(
                                TransportError.SERVICE_NOT_FOUND,
                                "discoverServices failed, status=" + status);
                        handleUnexpectedDisconnect();
                        return;
                    }
                    logGattStructure(g);
                    BluetoothGattService uart =
                            g.getService(UUID.fromString(PegasusUuids.UART_SERVICE));
                    if (uart == null) {
                        emitError(
                                TransportError.SERVICE_NOT_FOUND,
                                "Nordic UART service not present on this device");
                        handleUnexpectedDisconnect();
                        return;
                    }
                    writeCharacteristic =
                            uart.getCharacteristic(
                                    UUID.fromString(PegasusUuids.UART_WRITE_CHARACTERISTIC));
                    notifyCharacteristic =
                            uart.getCharacteristic(
                                    UUID.fromString(PegasusUuids.UART_NOTIFY_CHARACTERISTIC));
                    if (writeCharacteristic == null || notifyCharacteristic == null) {
                        emitError(
                                TransportError.CHARACTERISTIC_NOT_FOUND,
                                "write="
                                        + (writeCharacteristic != null)
                                        + " notify="
                                        + (notifyCharacteristic != null));
                        handleUnexpectedDisconnect();
                        return;
                    }
                    setState(ConnectionState.SUBSCRIBING);
                    enableNotifications(g, notifyCharacteristic);
                }

                @Override
                public void onDescriptorWrite(
                        BluetoothGatt g, BluetoothGattDescriptor descriptor, int status) {
                    operationQueue.operationCompleted();
                    if (PegasusUuids.CCCD.equalsIgnoreCase(descriptor.getUuid().toString())) {
                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            Log.i(TAG, "Notifications enabled");
                            cancelConnectTimeout();
                            reconnectPolicy.onConnected();
                            setState(ConnectionState.CONNECTED);
                        } else {
                            emitError(TransportError.NOTIFICATION_SETUP_FAILED, "status=" + status);
                            handleUnexpectedDisconnect();
                        }
                    }
                }

                @Override
                public void onCharacteristicWrite(
                        BluetoothGatt g, BluetoothGattCharacteristic characteristic, int status) {
                    // Capture the payload BEFORE operationCompleted(): completing the
                    // operation may synchronously start the next queued write, which
                    // would overwrite pendingWritePayload (off-by-one in TX logging).
                    byte[] sent = pendingWritePayload;
                    pendingWritePayload = null;
                    operationQueue.operationCompleted();
                    TransportListener l = listener;
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        if (l != null) {
                            l.onDataSent(
                                    characteristic.getUuid().toString(),
                                    sent == null ? new byte[0] : sent);
                        }
                    } else {
                        emitError(TransportError.WRITE_FAILED, "status=" + status);
                    }
                }

                // API < 33 callback
                @Override
                @SuppressWarnings("deprecation")
                public void onCharacteristicChanged(
                        BluetoothGatt g, BluetoothGattCharacteristic characteristic) {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                        byte[] value = characteristic.getValue();
                        dispatchRx(
                                characteristic.getUuid().toString(),
                                value == null ? new byte[0] : value);
                    }
                }

                // API 33+ callback
                @Override
                public void onCharacteristicChanged(
                        BluetoothGatt g, BluetoothGattCharacteristic characteristic, byte[] value) {
                    dispatchRx(characteristic.getUuid().toString(), value);
                }
            };

    private void dispatchRx(String characteristicUuid, byte[] value) {
        TransportListener l = listener;
        if (l != null) {
            l.onDataReceived(characteristicUuid, value);
        }
    }

    private void enableNotifications(BluetoothGatt g, BluetoothGattCharacteristic ch) {
        operationQueue.enqueue(
                new GattOperationQueue.Operation() {
                    @Override
                    public String name() {
                        return "enableNotifications";
                    }

                    @Override
                    public boolean start() {
                        if (!g.setCharacteristicNotification(ch, true)) {
                            emitError(
                                    TransportError.NOTIFICATION_SETUP_FAILED,
                                    "setCharacteristicNotification failed");
                            handleUnexpectedDisconnect();
                            return false;
                        }
                        BluetoothGattDescriptor cccd =
                                ch.getDescriptor(UUID.fromString(PegasusUuids.CCCD));
                        if (cccd == null) {
                            emitError(TransportError.NOTIFICATION_SETUP_FAILED, "CCCD missing");
                            handleUnexpectedDisconnect();
                            return false;
                        }
                        boolean indicate =
                                (ch.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY)
                                                == 0
                                        && (ch.getProperties()
                                                        & BluetoothGattCharacteristic
                                                                .PROPERTY_INDICATE)
                                                != 0;
                        byte[] value =
                                indicate
                                        ? BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                                        : BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE;
                        addDiagnostic("Subscription mode: " + (indicate ? "INDICATE" : "NOTIFY"));
                        boolean ok;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            ok = g.writeDescriptor(cccd, value) == BluetoothStatusCodes.SUCCESS;
                        } else {
                            cccd.setValue(value);
                            ok = g.writeDescriptor(cccd);
                        }
                        if (!ok) {
                            emitError(
                                    TransportError.NOTIFICATION_SETUP_FAILED,
                                    "writeDescriptor rejected");
                            handleUnexpectedDisconnect();
                        }
                        return ok;
                    }

                    @Override
                    public void onTimeout() {
                        emitError(TransportError.NOTIFICATION_SETUP_FAILED, "CCCD write timeout");
                        handleUnexpectedDisconnect();
                    }
                });
    }

    private synchronized void logGattStructure(BluetoothGatt g) {
        gattDiagnostics.clear();
        for (BluetoothGattService service : g.getServices()) {
            addDiagnostic("Service " + service.getUuid());
            for (BluetoothGattCharacteristic ch : service.getCharacteristics()) {
                addDiagnostic(
                        "  Char "
                                + ch.getUuid()
                                + " props="
                                + describeProperties(ch.getProperties()));
                for (BluetoothGattDescriptor d : ch.getDescriptors()) {
                    addDiagnostic("    Desc " + d.getUuid());
                }
            }
        }
    }

    static String describeProperties(int properties) {
        StringBuilder sb = new StringBuilder();
        if ((properties & BluetoothGattCharacteristic.PROPERTY_READ) != 0) sb.append("READ ");
        if ((properties & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0) sb.append("WRITE ");
        if ((properties & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
            sb.append("WRITE_NR ");
        }
        if ((properties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) sb.append("NOTIFY ");
        if ((properties & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) {
            sb.append("INDICATE ");
        }
        return sb.toString().trim();
    }

    private void addDiagnostic(String line) {
        synchronized (this) {
            gattDiagnostics.add(line);
        }
        Log.i(TAG, "GATT: " + line);
    }

    // ------------------------------------------------------------- reconnect

    private void handleUnexpectedDisconnect() {
        operationQueue.clear();
        cancelConnectTimeout();
        if (currentAddress != null && reconnectPolicy.shouldReconnect()) {
            setState(ConnectionState.RECONNECTING);
            Log.i(
                    TAG,
                    "Reconnect attempt "
                            + reconnectPolicy.getAttemptsMade()
                            + " in "
                            + reconnectPolicy.getDelayMs()
                            + " ms");
            mainHandler.postDelayed(
                    () -> {
                        if (state == ConnectionState.RECONNECTING) {
                            connectInternal(currentAddress);
                        }
                    },
                    reconnectPolicy.getDelayMs());
        } else {
            if (state != ConnectionState.DISCONNECTED && reconnectPolicy.getAttemptsMade() > 0) {
                emitError(
                        TransportError.RECONNECT_GIVEN_UP,
                        "after " + reconnectPolicy.getAttemptsMade() + " attempts");
            }
            setState(ConnectionState.DISCONNECTED);
        }
    }

    // ------------------------------------------------------------------ misc

    private void cancelConnectTimeout() {
        if (connectTimeoutRunnable != null) {
            mainHandler.removeCallbacks(connectTimeoutRunnable);
            connectTimeoutRunnable = null;
        }
    }

    private void closeGattQuietly() {
        writeCharacteristic = null;
        notifyCharacteristic = null;
        if (gatt != null) {
            try {
                gatt.close();
            } catch (Exception ignored) {
            }
            gatt = null;
        }
    }

    private BluetoothAdapter getAdapter() {
        BluetoothManager manager =
                (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        return manager == null ? null : manager.getAdapter();
    }

    private void setState(ConnectionState newState) {
        if (state != newState) {
            state = newState;
            Log.i(TAG, "Connection state: " + newState);
            TransportListener l = listener;
            if (l != null) {
                l.onConnectionStateChanged(newState);
            }
        }
    }

    private void emitError(TransportError error, String detail) {
        Log.w(TAG, "Error " + error + ": " + detail);
        TransportListener l = listener;
        if (l != null) {
            l.onError(error, detail);
        }
    }
}
