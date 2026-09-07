/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.desktop.pegasus;

import com.github.hypfvieh.bluetooth.DeviceManager;
import com.github.hypfvieh.bluetooth.wrapper.BluetoothAdapter;
import com.github.hypfvieh.bluetooth.wrapper.BluetoothDevice;
import com.github.hypfvieh.bluetooth.wrapper.BluetoothGattCharacteristic;
import com.github.hypfvieh.bluetooth.wrapper.BluetoothGattService;
import de.schliweb.pegasus.core.transport.ConnectionState;
import de.schliweb.pegasus.core.transport.DiscoveredDevice;
import de.schliweb.pegasus.core.transport.PegasusTransport;
import de.schliweb.pegasus.core.transport.PegasusUuids;
import de.schliweb.pegasus.core.transport.ReconnectPolicy;
import de.schliweb.pegasus.core.transport.ScanListener;
import de.schliweb.pegasus.core.transport.TransportError;
import de.schliweb.pegasus.core.transport.TransportListener;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.handlers.AbstractPropertiesChangedHandler;
import org.freedesktop.dbus.interfaces.Properties.PropertiesChanged;
import org.freedesktop.dbus.types.Variant;

/**
 * Linux implementation of {@link PegasusTransport} on top of BlueZ, via its D-Bus API ({@code
 * com.github.hypfvieh:bluez-dbus}). No native code at all - unlike macOS ({@link
 * MacosPegasusBleTransport}, a JNI bridge to CoreBluetooth) and Windows ({@link
 * WindowsPegasusBleTransport}, a JNI bridge to WinRT), BlueZ's whole GATT client API is reachable
 * from pure Java over D-Bus; the one runtime dependency ({@code
 * dbus-java-transport-native-unixsocket}) is itself pure Java (JDK 16+'s own {@code
 * java.net.UnixDomainSocketAddress}), no JNI/JNR involved either. Both {@code bluez-dbus} and
 * {@code dbus-java} are MIT-licensed (see the physical board section of README.md for why that
 * matters here, unlike SimpleBLE's BUSL-1.1).
 *
 * <p>Every {@code bluez-dbus} call that talks to BlueZ over D-Bus (adapter/device discovery, {@code
 * Connect()}, GATT discovery, {@code WriteValue()}, {@code StartNotify()}) is a <em>blocking</em>
 * D-Bus method call, unlike CoreBluetooth/WinRT's async-callback APIs - so unlike the other two
 * transports, this one drives them from its own single-thread {@link #worker} executor rather than
 * relying on a platform-provided async model, hopping back to the JavaFX Application Thread via
 * {@link Platform#runLater} for every state/listener update, exactly like the others do for their
 * own (differently-sourced) background-thread callbacks.
 *
 * <p>Reuses {@code pegasus-core}'s {@link ReconnectPolicy} for connect-timeout and
 * reconnect-on-unexpected-disconnect, matching {@code AndroidPegasusBleTransport}/{@link
 * MacosPegasusBleTransport}/{@link WindowsPegasusBleTransport}. Deliberately not shared as a common
 * base class with those two (same reasoning as between them): different failure/threading
 * characteristics, and this transport is not yet hardware-verified.
 */
public final class LinuxPegasusBleTransport implements PegasusTransport {

    private static final Logger LOG = Logger.getLogger(LinuxPegasusBleTransport.class.getName());
    private static final long CONNECT_TIMEOUT_MS = 15000;
    private static final long SERVICES_RESOLVED_TIMEOUT_MS = 10000;
    private static final long SCAN_POLL_INTERVAL_MS = 1000;

    private final DeviceManager deviceManager;
    private final AbstractPropertiesChangedHandler propertiesHandler =
            new AbstractPropertiesChangedHandler() {
                @Override
                public void handle(PropertiesChanged signal) {
                    onPropertiesChanged(signal);
                }
            };

    private final ExecutorService worker =
            Executors.newSingleThreadExecutor(
                    r -> {
                        Thread t = new Thread(r, "pegasus-ble-linux-worker");
                        t.setDaemon(true);
                        return t;
                    });
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(
                    r -> {
                        Thread t = new Thread(r, "pegasus-ble-linux-timer");
                        t.setDaemon(true);
                        return t;
                    });
    private final ReconnectPolicy reconnectPolicy = new ReconnectPolicy();

    private volatile TransportListener listener;
    private volatile ScanListener scanListener;
    private volatile ConnectionState state = ConnectionState.DISCONNECTED;
    private volatile boolean scanning;
    private String currentAddress;
    private volatile BluetoothDevice connectedDevice;
    private volatile BluetoothGattCharacteristic writeChar;
    private volatile BluetoothGattCharacteristic notifyChar;
    private ScheduledFuture<?> connectTimeoutTask;
    private ScheduledFuture<?> reconnectTask;

    public LinuxPegasusBleTransport() {
        try {
            deviceManager = DeviceManager.createInstance(false);
            deviceManager.registerPropertyHandler(propertiesHandler);
        } catch (DBusException e) {
            throw new IllegalStateException("Could not connect to the system D-Bus / BlueZ", e);
        }
    }

    @Override
    public void setListener(TransportListener listener) {
        this.listener = listener;
    }

    @Override
    public void startScan(ScanListener listener, long timeoutMs) {
        this.scanListener = listener;
        scanning = true;
        worker.execute(() -> doScan(timeoutMs));
    }

    private void doScan(long timeoutMs) {
        BluetoothAdapter adapter;
        try {
            adapter = deviceManager.getAdapter();
        } catch (RuntimeException e) {
            reportScanFailed(TransportError.SCAN_FAILED, e.getMessage());
            return;
        }
        if (adapter == null || !Boolean.TRUE.equals(adapter.isPowered())) {
            reportScanFailed(
                    TransportError.BLUETOOTH_DISABLED, "No powered Bluetooth adapter found");
            scanning = false;
            return;
        }
        if (!adapter.startDiscovery()) {
            reportScanFailed(TransportError.SCAN_FAILED, "BlueZ StartDiscovery failed");
            scanning = false;
            return;
        }
        // No service filter: unknown Pegasus units may not advertise the
        // UART service UUID directly - same rationale as
        // AndroidPegasusBleTransport/PegasusBleMac.m/PegasusBleWin.cpp's
        // unfiltered scans; the user picks the device, GATT verifies it
        // after connect. Polled rather than event-driven (no convenient
        // per-device "discovered" event on this library's high-level API):
        // re-introspects the adapter's known devices once per second,
        // reporting only newly-seen addresses, until timeoutMs elapses or
        // stopScan() clears the flag.
        Set<String> reported = new HashSet<>();
        long deadline = System.currentTimeMillis() + timeoutMs;
        try {
            while (scanning && System.currentTimeMillis() < deadline) {
                deviceManager.findBtDevicesByIntrospection(adapter);
                for (BluetoothDevice device :
                        deviceManager.getDevices(adapter.getAddress(), true)) {
                    if (reported.add(device.getAddress())) {
                        DiscoveredDevice found =
                                new DiscoveredDevice(
                                        device.getName(),
                                        device.getAddress(),
                                        device.getRssi() == null ? 0 : device.getRssi(),
                                        List.of());
                        Platform.runLater(
                                () -> {
                                    ScanListener l = scanListener;
                                    if (l != null) {
                                        l.onDeviceFound(found);
                                    }
                                });
                    }
                }
                Thread.sleep(SCAN_POLL_INTERVAL_MS);
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } finally {
            scanning = false;
            try {
                adapter.stopDiscovery();
            } catch (RuntimeException ignored) {
            }
            Platform.runLater(
                    () -> {
                        ScanListener l = scanListener;
                        if (l != null) {
                            l.onScanFinished();
                        }
                    });
        }
    }

    private void reportScanFailed(TransportError error, String detail) {
        Platform.runLater(
                () -> {
                    ScanListener l = scanListener;
                    if (l != null) {
                        l.onScanFailed(error, detail);
                    }
                });
    }

    @Override
    public void stopScan() {
        scanning = false;
    }

    @Override
    public void connect(String deviceAddress) {
        reconnectPolicy.onConnectRequested();
        connectInternal(deviceAddress);
    }

    private void connectInternal(String deviceAddress) {
        scanning = false;
        cancelConnectTimeout();
        currentAddress = deviceAddress;
        setState(ConnectionState.CONNECTING);
        connectTimeoutTask =
                scheduler.schedule(
                        () -> Platform.runLater(this::onConnectTimeout),
                        CONNECT_TIMEOUT_MS,
                        TimeUnit.MILLISECONDS);
        worker.execute(() -> doConnect(deviceAddress));
    }

    private void onConnectTimeout() {
        if (state == ConnectionState.CONNECTING
                || state == ConnectionState.DISCOVERING_SERVICES
                || state == ConnectionState.SUBSCRIBING) {
            LOG.log(Level.WARNING, "Connect timeout");
            emitError(
                    TransportError.CONNECT_TIMEOUT,
                    "No connection within " + CONNECT_TIMEOUT_MS + " ms");
            worker.execute(() -> doDisconnectQuiet(connectedDevice));
            Platform.runLater(this::handleDisconnected);
        }
    }

    private void doConnect(String deviceAddress) {
        BluetoothDevice device;
        try {
            BluetoothAdapter adapter = deviceManager.getAdapter();
            device = findDeviceByAddress(adapter, deviceAddress);
            if (device == null) {
                Platform.runLater(
                        () ->
                                emitError(
                                        TransportError.CONNECT_FAILED,
                                        "Device not found - rescan required"));
                Platform.runLater(this::handleDisconnected);
                return;
            }
            if (!device.connect()) {
                Platform.runLater(
                        () -> emitError(TransportError.CONNECT_FAILED, "BlueZ Connect failed"));
                Platform.runLater(this::handleDisconnected);
                return;
            }
        } catch (RuntimeException e) {
            Platform.runLater(
                    () -> emitError(TransportError.CONNECT_FAILED, String.valueOf(e.getMessage())));
            Platform.runLater(this::handleDisconnected);
            return;
        }
        connectedDevice = device;
        Platform.runLater(() -> setState(ConnectionState.DISCOVERING_SERVICES));
        try {
            // BlueZ resolves GATT services asynchronously after Connect() returns;
            // ServicesResolved flips true once ready.
            long deadline = System.currentTimeMillis() + SERVICES_RESOLVED_TIMEOUT_MS;
            while (!Boolean.TRUE.equals(device.isServicesResolved())
                    && System.currentTimeMillis() < deadline) {
                Thread.sleep(200);
            }
            BluetoothGattService service = device.getGattServiceByUuid(PegasusUuids.UART_SERVICE);
            if (service == null) {
                Platform.runLater(
                        () ->
                                emitError(
                                        TransportError.SERVICE_NOT_FOUND,
                                        "Nordic UART service not present on this device"));
                doDisconnectQuiet(device);
                Platform.runLater(this::handleDisconnected);
                return;
            }
            BluetoothGattCharacteristic write =
                    service.getGattCharacteristicByUuid(PegasusUuids.UART_WRITE_CHARACTERISTIC);
            BluetoothGattCharacteristic notify =
                    service.getGattCharacteristicByUuid(PegasusUuids.UART_NOTIFY_CHARACTERISTIC);
            if (write == null || notify == null) {
                boolean haveWrite = write != null;
                boolean haveNotify = notify != null;
                Platform.runLater(
                        () ->
                                emitError(
                                        TransportError.CHARACTERISTIC_NOT_FOUND,
                                        "write=" + haveWrite + " notify=" + haveNotify));
                doDisconnectQuiet(device);
                Platform.runLater(this::handleDisconnected);
                return;
            }
            Platform.runLater(() -> setState(ConnectionState.SUBSCRIBING));
            notify.startNotify();
            writeChar = write;
            notifyChar = notify;
            Platform.runLater(() -> setState(ConnectionState.CONNECTED));
        } catch (Exception e) {
            Platform.runLater(
                    () ->
                            emitError(
                                    TransportError.NOTIFICATION_SETUP_FAILED,
                                    String.valueOf(e.getMessage())));
            doDisconnectQuiet(device);
            Platform.runLater(this::handleDisconnected);
        }
    }

    private BluetoothDevice findDeviceByAddress(BluetoothAdapter adapter, String address) {
        if (adapter == null) {
            return null;
        }
        for (BluetoothDevice device : deviceManager.getDevices(adapter.getAddress(), true)) {
            if (address.equalsIgnoreCase(device.getAddress())) {
                return device;
            }
        }
        return null;
    }

    @Override
    public void disconnect() {
        reconnectPolicy.onManualDisconnect();
        cancelConnectTimeout();
        cancelReconnectTask();
        BluetoothDevice device = connectedDevice;
        worker.execute(() -> doDisconnectQuiet(device));
        setState(ConnectionState.DISCONNECTED);
    }

    private void doDisconnectQuiet(BluetoothDevice device) {
        writeChar = null;
        notifyChar = null;
        connectedDevice = null;
        if (device != null) {
            try {
                device.disconnect();
            } catch (RuntimeException ignored) {
            }
        }
    }

    /** Call when the owning component is destroyed for good. */
    public void shutdown() {
        disconnect();
        scanning = false;
        scheduler.shutdownNow();
        worker.shutdownNow();
        try {
            deviceManager.unRegisterPropertyHandler(propertiesHandler);
        } catch (DBusException ignored) {
        }
        deviceManager.closeConnection();
    }

    @Override
    public void write(byte[] data) {
        if (state != ConnectionState.CONNECTED) {
            emitError(TransportError.WRITE_FAILED, "Not connected");
            return;
        }
        BluetoothGattCharacteristic ch = writeChar;
        worker.execute(
                () -> {
                    try {
                        // "command" = write-without-response (BlueZ gatt-api): same
                        // choice as AndroidPegasusBleTransport/PegasusBleMac.m/
                        // PegasusBleWin.cpp (captured from the official DGT app via
                        // HCI snoop on real hardware).
                        ch.writeValue(data, Map.of("type", "command"));
                        Platform.runLater(
                                () -> {
                                    TransportListener l = listener;
                                    if (l != null) {
                                        l.onDataSent(PegasusUuids.UART_WRITE_CHARACTERISTIC, data);
                                    }
                                });
                    } catch (Exception e) {
                        Platform.runLater(
                                () ->
                                        emitError(
                                                TransportError.WRITE_FAILED,
                                                String.valueOf(e.getMessage())));
                    }
                });
    }

    @Override
    public ConnectionState getConnectionState() {
        return state;
    }

    // ---- BlueZ property-change signals (arrive on dbus-java's own reader thread) ------

    private void onPropertiesChanged(PropertiesChanged signal) {
        BluetoothDevice device = connectedDevice;
        BluetoothGattCharacteristic notify = notifyChar;
        if (device != null
                && signal.getPath().equals(device.getDbusPath())
                && "org.bluez.Device1".equals(signal.getInterfaceName())) {
            Variant<?> connected = signal.getPropertiesChanged().get("Connected");
            if (connected != null && Boolean.FALSE.equals(connected.getValue())) {
                Platform.runLater(this::handleDisconnected);
            }
        } else if (notify != null
                && signal.getPath().equals(notify.getDbusPath())
                && "org.bluez.GattCharacteristic1".equals(signal.getInterfaceName())) {
            Variant<?> value = signal.getPropertiesChanged().get("Value");
            if (value != null) {
                byte[] data = toByteArray(value.getValue());
                Platform.runLater(
                        () -> {
                            TransportListener l = listener;
                            if (l != null) {
                                l.onDataReceived(PegasusUuids.UART_NOTIFY_CHARACTERISTIC, data);
                            }
                        });
            }
        }
    }

    private static byte[] toByteArray(Object value) {
        if (value instanceof byte[] array) {
            return array;
        }
        if (value instanceof List<?> list) {
            byte[] result = new byte[list.size()];
            for (int i = 0; i < list.size(); i++) {
                result[i] = ((Number) list.get(i)).byteValue();
            }
            return result;
        }
        return new byte[0];
    }

    /**
     * BlueZ reports every disconnect (manual or unexpected) the same way via the {@code Connected}
     * property flipping to {@code false} - distinguishing the two requires checking whether {@link
     * #state} was already set to {@code DISCONNECTED} by our own {@link #disconnect()} before this
     * arrived. Mirrors {@code MacosPegasusBleTransport}/{@code WindowsPegasusBleTransport}'s
     * identically-named method exactly.
     */
    private void handleDisconnected() {
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
}
