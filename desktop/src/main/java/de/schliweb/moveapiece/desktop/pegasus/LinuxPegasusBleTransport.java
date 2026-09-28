/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.desktop.pegasus;

import com.github.hypfvieh.bluetooth.wrapper.BluetoothAdapter;
import com.github.hypfvieh.bluetooth.wrapper.BluetoothDevice;
import com.github.hypfvieh.bluetooth.wrapper.BluetoothGattCharacteristic;
import com.github.hypfvieh.bluetooth.wrapper.BluetoothGattService;
import de.schliweb.pegasus.core.transport.BleProfile;
import de.schliweb.pegasus.core.transport.ConnectionState;
import de.schliweb.pegasus.core.transport.DiscoveredDevice;
import de.schliweb.pegasus.core.transport.PegasusTransport;
import de.schliweb.pegasus.core.transport.ReconnectPolicy;
import de.schliweb.pegasus.core.transport.ScanListener;
import de.schliweb.pegasus.core.transport.TransportError;
import de.schliweb.pegasus.core.transport.TransportListener;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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
 *
 * <p>Connect attempts are numbered ({@link #connectGeneration}): {@link #doConnect} runs on the
 * worker long after Java may have given the attempt up (BlueZ's {@code Connected=false} signal,
 * the connect timeout, a manual disconnect, or the reconnect that follows any of those), and the
 * blocking {@code ServicesResolved} poll alone can keep it busy for up to {@link
 * #servicesResolvedTimeoutMs}. A superseded attempt must neither report anything - its
 * SERVICE_NOT_FOUND would be counted as yet another failed reconnect - nor hold the worker
 * longer than one poll interval, since the next attempt queues behind it. The equivalent guard in
 * {@code PegasusBleWin.cpp} covers the same race on Windows, where it additionally protected the
 * shared device handle; here the single-thread worker already prevents that part.
 */
public final class LinuxPegasusBleTransport implements PegasusTransport {

    private static final Logger LOG = Logger.getLogger(LinuxPegasusBleTransport.class.getName());
    static final long DEFAULT_CONNECT_TIMEOUT_MS = 15000;
    static final long DEFAULT_SERVICES_RESOLVED_TIMEOUT_MS = 10000;
    private static final long SERVICES_RESOLVED_POLL_MS = 200;
    private static final long SCAN_POLL_INTERVAL_MS = 1000;

    private final BlueZAccess blueZ;
    /** Where every state/listener update runs: the JavaFX Application Thread in production. */
    private final Executor uiThread;
    private final long connectTimeoutMs;
    private final long servicesResolvedTimeoutMs;
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
    private final ReconnectPolicy reconnectPolicy;

    private volatile TransportListener listener;
    private volatile ScanListener scanListener;
    private volatile ConnectionState state = ConnectionState.DISCONNECTED;
    private volatile boolean scanning;
    private String currentAddress;
    private volatile BluetoothDevice connectedDevice;
    private volatile BluetoothGattCharacteristic writeChar;
    private volatile List<BluetoothGattCharacteristic> notifyChars = List.of();
    private final BleProfile profile;
    private ScheduledFuture<?> connectTimeoutTask;
    private ScheduledFuture<?> reconnectTask;
    /** See the class Javadoc; bumped on every new attempt and whenever one is given up. */
    private final AtomicInteger connectGeneration = new AtomicInteger();

    /** Transport for a DGT Pegasus ({@link BleProfile#PEGASUS}). */
    public LinuxPegasusBleTransport() {
        this(BleProfile.PEGASUS);
    }

    /** Transport for whichever board {@code profile} describes; CONNECTED once all subscribed. */
    public LinuxPegasusBleTransport(BleProfile profile) {
        this(
                profile,
                openSystemBus(),
                Platform::runLater,
                new ReconnectPolicy(),
                DEFAULT_CONNECT_TIMEOUT_MS,
                DEFAULT_SERVICES_RESOLVED_TIMEOUT_MS);
    }

    private static BlueZAccess openSystemBus() {
        try {
            return BlueZAccess.systemBus();
        } catch (DBusException e) {
            throw new IllegalStateException("Could not connect to the system D-Bus / BlueZ", e);
        }
    }

    /**
     * Test seam: every collaborator the connect/reconnect state machine depends on, injectable.
     * {@code uiThread} stands in for the JavaFX Application Thread and must execute tasks one at a
     * time, in order, like it does.
     */
    LinuxPegasusBleTransport(
            BleProfile profile,
            BlueZAccess blueZ,
            Executor uiThread,
            ReconnectPolicy reconnectPolicy,
            long connectTimeoutMs,
            long servicesResolvedTimeoutMs) {
        if (profile == null) {
            throw new IllegalArgumentException("profile must not be null");
        }
        this.profile = profile;
        this.blueZ = blueZ;
        this.uiThread = uiThread;
        this.reconnectPolicy = reconnectPolicy;
        this.connectTimeoutMs = connectTimeoutMs;
        this.servicesResolvedTimeoutMs = servicesResolvedTimeoutMs;
        try {
            blueZ.registerPropertyHandler(propertiesHandler);
        } catch (DBusException e) {
            throw new IllegalStateException("Could not subscribe to BlueZ property changes", e);
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
            adapter = blueZ.getAdapter();
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
                blueZ.findBtDevicesByIntrospection(adapter);
                for (BluetoothDevice device :
                        blueZ.getDevices(adapter.getAddress(), true)) {
                    if (reported.add(device.getAddress())) {
                        DiscoveredDevice found =
                                new DiscoveredDevice(
                                        device.getName(),
                                        device.getAddress(),
                                        device.getRssi() == null ? 0 : device.getRssi(),
                                        List.of());
                        uiThread.execute(
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
            uiThread.execute(
                    () -> {
                        ScanListener l = scanListener;
                        if (l != null) {
                            l.onScanFinished();
                        }
                    });
        }
    }

    private void reportScanFailed(TransportError error, String detail) {
        uiThread.execute(
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
        int generation = connectGeneration.incrementAndGet();
        setState(ConnectionState.CONNECTING);
        connectTimeoutTask =
                scheduler.schedule(
                        () -> uiThread.execute(this::onConnectTimeout),
                        connectTimeoutMs,
                        TimeUnit.MILLISECONDS);
        worker.execute(() -> doConnect(deviceAddress, generation));
    }

    private void onConnectTimeout() {
        if (state == ConnectionState.CONNECTING
                || state == ConnectionState.DISCOVERING_SERVICES
                || state == ConnectionState.SUBSCRIBING) {
            LOG.log(Level.WARNING, "Connect timeout");
            emitError(
                    TransportError.CONNECT_TIMEOUT,
                    "No connection within " + connectTimeoutMs + " ms");
            worker.execute(() -> doDisconnectQuiet(connectedDevice));
            uiThread.execute(this::handleDisconnected);
        }
    }

    private boolean superseded(int generation) {
        return generation != connectGeneration.get();
    }

    private void doConnect(String deviceAddress, int generation) {
        BluetoothDevice device;
        try {
            if (superseded(generation)) {
                return;
            }
            BluetoothAdapter adapter = blueZ.getAdapter();
            device = findDeviceByAddress(adapter, deviceAddress);
            if (device == null) {
                if (superseded(generation)) {
                    return;
                }
                uiThread.execute(
                        () ->
                                emitError(
                                        TransportError.CONNECT_FAILED,
                                        "Device not found - rescan required"));
                uiThread.execute(this::handleDisconnected);
                return;
            }
            if (!device.connect()) {
                if (superseded(generation)) {
                    return;
                }
                uiThread.execute(
                        () -> emitError(TransportError.CONNECT_FAILED, "BlueZ Connect failed"));
                uiThread.execute(this::handleDisconnected);
                return;
            }
        } catch (RuntimeException e) {
            if (superseded(generation)) {
                return;
            }
            uiThread.execute(
                    () -> emitError(TransportError.CONNECT_FAILED, String.valueOf(e.getMessage())));
            uiThread.execute(this::handleDisconnected);
            return;
        }
        if (superseded(generation)) {
            // Given up while Connect() was blocking; nobody else knows about
            // the link we just opened, so release it ourselves.
            doDisconnectQuiet(device);
            return;
        }
        connectedDevice = device;
        uiThread.execute(() -> setState(ConnectionState.DISCOVERING_SERVICES));
        try {
            // BlueZ resolves GATT services asynchronously after Connect() returns;
            // ServicesResolved flips true once ready.
            long deadline = System.currentTimeMillis() + servicesResolvedTimeoutMs;
            while (!Boolean.TRUE.equals(device.isServicesResolved())
                    && System.currentTimeMillis() < deadline) {
                if (superseded(generation)) {
                    doDisconnectQuiet(device);
                    return;
                }
                Thread.sleep(SERVICES_RESOLVED_POLL_MS);
            }
            if (superseded(generation)) {
                doDisconnectQuiet(device);
                return;
            }
            BluetoothGattService writeService =
                    device.getGattServiceByUuid(profile.writeServiceUuid());
            if (writeService == null) {
                String detail =
                        profile.displayName()
                                + " service "
                                + profile.writeServiceUuid()
                                + " not present on this device";
                uiThread.execute(() -> emitError(TransportError.SERVICE_NOT_FOUND, detail));
                doDisconnectQuiet(device);
                uiThread.execute(this::handleDisconnected);
                return;
            }
            BluetoothGattCharacteristic write =
                    writeService.getGattCharacteristicByUuid(profile.writeCharacteristicUuid());
            List<BluetoothGattCharacteristic> notifies = new ArrayList<>();
            for (BleProfile.Subscription sub : profile.subscriptions()) {
                BluetoothGattService service = device.getGattServiceByUuid(sub.serviceUuid());
                BluetoothGattCharacteristic ch =
                        service == null
                                ? null
                                : service.getGattCharacteristicByUuid(sub.characteristicUuid());
                if (ch != null) {
                    notifies.add(ch);
                }
            }
            if (write == null || notifies.size() != profile.subscriptions().size()) {
                String detail =
                        "write="
                                + (write != null)
                                + " notify="
                                + notifies.size()
                                + "/"
                                + profile.subscriptions().size();
                uiThread.execute(() -> emitError(TransportError.CHARACTERISTIC_NOT_FOUND, detail));
                doDisconnectQuiet(device);
                uiThread.execute(this::handleDisconnected);
                return;
            }
            uiThread.execute(() -> setState(ConnectionState.SUBSCRIBING));
            for (BluetoothGattCharacteristic ch : notifies) {
                if (superseded(generation)) {
                    doDisconnectQuiet(device);
                    return;
                }
                ch.startNotify();
            }
            if (superseded(generation)) {
                doDisconnectQuiet(device);
                return;
            }
            writeChar = write;
            notifyChars = List.copyOf(notifies);
            uiThread.execute(() -> setState(ConnectionState.CONNECTED));
        } catch (Exception e) {
            if (superseded(generation)) {
                doDisconnectQuiet(device);
                return;
            }
            uiThread.execute(
                    () ->
                            emitError(
                                    TransportError.NOTIFICATION_SETUP_FAILED,
                                    String.valueOf(e.getMessage())));
            doDisconnectQuiet(device);
            uiThread.execute(this::handleDisconnected);
        }
    }

    private BluetoothDevice findDeviceByAddress(BluetoothAdapter adapter, String address) {
        if (adapter == null) {
            return null;
        }
        for (BluetoothDevice device : blueZ.getDevices(adapter.getAddress(), true)) {
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
        connectGeneration.incrementAndGet();
        BluetoothDevice device = connectedDevice;
        worker.execute(() -> doDisconnectQuiet(device));
        setState(ConnectionState.DISCONNECTED);
    }

    private void doDisconnectQuiet(BluetoothDevice device) {
        writeChar = null;
        notifyChars = List.of();
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
            blueZ.unRegisterPropertyHandler(propertiesHandler);
        } catch (DBusException ignored) {
        }
        blueZ.closeConnection();
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
                        uiThread.execute(
                                () -> {
                                    TransportListener l = listener;
                                    if (l != null) {
                                        l.onDataSent(profile.writeCharacteristicUuid(), data);
                                    }
                                });
                    } catch (Exception e) {
                        uiThread.execute(
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
        if (device != null
                && signal.getPath().equals(device.getDbusPath())
                && "org.bluez.Device1".equals(signal.getInterfaceName())) {
            Variant<?> connected = signal.getPropertiesChanged().get("Connected");
            if (connected != null && Boolean.FALSE.equals(connected.getValue())) {
                uiThread.execute(this::handleDisconnected);
            }
            return;
        }
        if (!"org.bluez.GattCharacteristic1".equals(signal.getInterfaceName())) {
            return;
        }
        for (BluetoothGattCharacteristic notify : notifyChars) {
            if (!signal.getPath().equals(notify.getDbusPath())) {
                continue;
            }
            Variant<?> value = signal.getPropertiesChanged().get("Value");
            if (value != null) {
                byte[] data = toByteArray(value.getValue());
                String uuid = notify.getUuid().toLowerCase(Locale.ROOT);
                uiThread.execute(
                        () -> {
                            TransportListener l = listener;
                            if (l != null) {
                                l.onDataReceived(uuid, data);
                            }
                        });
            }
            return;
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
        // Whatever doConnect() is still doing for this attempt is moot now
        // (see the class Javadoc): let it exit at its next check instead of
        // polling ServicesResolved to the deadline and then reporting a
        // SERVICE_NOT_FOUND that would count as one more failed reconnect.
        connectGeneration.incrementAndGet();
        if (currentAddress != null && reconnectPolicy.shouldReconnect()) {
            setState(ConnectionState.RECONNECTING);
            // Keep in step with the Windows/macOS transports: a second
            // DISCONNECTED while already RECONNECTING must not leave two
            // timers racing to reconnect the same address.
            cancelReconnectTask();
            reconnectTask =
                    scheduler.schedule(
                            () ->
                                    uiThread.execute(
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
