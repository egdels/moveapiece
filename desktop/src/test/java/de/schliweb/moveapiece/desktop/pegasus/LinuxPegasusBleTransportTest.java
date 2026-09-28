/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.desktop.pegasus;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.github.hypfvieh.bluetooth.wrapper.BluetoothAdapter;
import com.github.hypfvieh.bluetooth.wrapper.BluetoothDevice;
import com.github.hypfvieh.bluetooth.wrapper.BluetoothGattCharacteristic;
import com.github.hypfvieh.bluetooth.wrapper.BluetoothGattService;
import de.schliweb.pegasus.core.transport.BleProfile;
import de.schliweb.pegasus.core.transport.ConnectionState;
import de.schliweb.pegasus.core.transport.ReconnectPolicy;
import de.schliweb.pegasus.core.transport.TransportError;
import de.schliweb.pegasus.core.transport.TransportListener;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.handlers.AbstractPropertiesChangedHandler;
import org.freedesktop.dbus.interfaces.Properties.PropertiesChanged;
import org.freedesktop.dbus.types.Variant;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Drives {@link LinuxPegasusBleTransport}'s connect/reconnect state machine against a fake BlueZ
 * (no D-Bus, runs on any OS). The scenario under test is a board that drops the link while its GATT
 * services are still being resolved - the same situation that produced a cascade of bogus
 * SERVICE_NOT_FOUND / CONNECT_FAILED reports on Windows before {@code PegasusBleWin.cpp} got its
 * generation guard. Timings are scaled down (reconnect delay, services-resolved timeout) so the
 * whole thing plays out in well under a second.
 */
public class LinuxPegasusBleTransportTest {

    private static final String ADDRESS = "AA:BB:CC:DD:EE:FF";
    private static final long RECONNECT_DELAY_MS = 100;
    private static final long CONNECT_TIMEOUT_MS = 5000;
    private static final long SERVICES_RESOLVED_TIMEOUT_MS = 500;

    private final BleProfile profile = BleProfile.PEGASUS;
    private FakeBlueZ blueZ;
    private FakeDevice device;
    private ExecutorService uiThread;
    private RecordingListener listener;
    private ReconnectPolicy reconnectPolicy;
    private LinuxPegasusBleTransport transport;

    @Before
    public void setUp() {
        device = new FakeDevice(ADDRESS, profile);
        blueZ = new FakeBlueZ(device);
        // Like the JavaFX Application Thread: one thread, tasks in order.
        uiThread = Executors.newSingleThreadExecutor();
        listener = new RecordingListener();
        reconnectPolicy = new ReconnectPolicy(3, RECONNECT_DELAY_MS);
        transport =
                new LinuxPegasusBleTransport(
                        profile,
                        blueZ,
                        uiThread,
                        reconnectPolicy,
                        CONNECT_TIMEOUT_MS,
                        SERVICES_RESOLVED_TIMEOUT_MS);
        transport.setListener(listener);
    }

    @After
    public void tearDown() {
        transport.shutdown();
        uiThread.shutdownNow();
    }

    @Test
    public void linkDropDuringDiscoveryIsRetriedOnceWithoutStaleReports() throws Exception {
        // Services never resolve on the first link; the board drops it mid-discovery.
        device.resolveServicesOnConnect = false;
        transport.connect(ADDRESS);
        await("DISCOVERING_SERVICES", () -> transport.getConnectionState()
                == ConnectionState.DISCOVERING_SERVICES);
        assertEquals(1, device.connectCalls.get());

        device.resolveServicesOnConnect = true; // the next link will be fine
        device.dropLink(blueZ);

        await("CONNECTED", () -> transport.getConnectionState() == ConnectionState.CONNECTED);
        // Let the superseded first attempt run into where it *would* have
        // reported SERVICE_NOT_FOUND (its services-resolved deadline).
        Thread.sleep(SERVICES_RESOLVED_TIMEOUT_MS + 200);

        assertEquals("connect calls (initial + one reconnect)", 2, device.connectCalls.get());
        assertEquals(
                "exactly one unexpected disconnect",
                1,
                listener.count(TransportError.DISCONNECTED_UNEXPECTEDLY));
        assertEquals(
                "no bogus report from the superseded attempt: " + listener.errors,
                0,
                listener.count(TransportError.SERVICE_NOT_FOUND)
                        + listener.count(TransportError.CONNECT_FAILED)
                        + listener.count(TransportError.RECONNECT_GIVEN_UP));
        assertEquals(ConnectionState.CONNECTED, transport.getConnectionState());
        assertTrue("the reconnected link is the one still up", device.connected.get());
    }

    @Test
    public void manualDisconnectDuringDiscoveryEndsTheAttemptSilently() throws Exception {
        device.resolveServicesOnConnect = false;
        transport.connect(ADDRESS);
        await("DISCOVERING_SERVICES", () -> transport.getConnectionState()
                == ConnectionState.DISCOVERING_SERVICES);

        transport.disconnect();
        assertEquals(ConnectionState.DISCONNECTED, transport.getConnectionState());

        Thread.sleep(SERVICES_RESOLVED_TIMEOUT_MS + 200);
        assertTrue("no error at all, got " + listener.errors, listener.errors.isEmpty());
        assertEquals(ConnectionState.DISCONNECTED, transport.getConnectionState());
        assertFalse("nothing reported CONNECTED", listener.states.contains(ConnectionState.CONNECTED));
        assertFalse("link released", device.connected.get());
        assertEquals(1, device.connectCalls.get());
    }

    @Test
    public void happyPathConnects() throws Exception {
        device.resolveServicesOnConnect = true;
        transport.connect(ADDRESS);
        await("CONNECTED", () -> transport.getConnectionState() == ConnectionState.CONNECTED);
        assertTrue(listener.errors.isEmpty());
        assertEquals(profile.subscriptions().size(), device.notifyStarted.get());
    }

    private static void await(String what, BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 3000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("timed out waiting for " + what);
            }
            Thread.sleep(10);
        }
    }

    // ---- fakes ---------------------------------------------------------------------------

    private static final class RecordingListener implements TransportListener {
        final List<ConnectionState> states = new CopyOnWriteArrayList<>();
        final List<TransportError> errors = new CopyOnWriteArrayList<>();

        @Override
        public void onConnectionStateChanged(ConnectionState state) {
            states.add(state);
        }

        @Override
        public void onDataReceived(String characteristicUuid, byte[] data) {}

        @Override
        public void onDataSent(String characteristicUuid, byte[] data) {}

        @Override
        public void onError(TransportError error, String detail) {
            errors.add(error);
        }

        int count(TransportError error) {
            return (int) errors.stream().filter(e -> e == error).count();
        }
    }

    /** The adapter/device registry plus the property-change signal path back into the transport. */
    private static final class FakeBlueZ implements BlueZAccess {
        final FakeAdapter adapter = new FakeAdapter();
        final List<BluetoothDevice> devices;
        volatile AbstractPropertiesChangedHandler handler;

        FakeBlueZ(BluetoothDevice... devices) {
            this.devices = List.of(devices);
        }

        @Override
        public BluetoothAdapter getAdapter() {
            return adapter;
        }

        @Override
        public void findBtDevicesByIntrospection(BluetoothAdapter adapter) {}

        @Override
        public List<BluetoothDevice> getDevices(String adapterAddress, boolean refresh) {
            return devices;
        }

        @Override
        public void registerPropertyHandler(AbstractPropertiesChangedHandler handler) {
            this.handler = handler;
        }

        @Override
        public void unRegisterPropertyHandler(AbstractPropertiesChangedHandler handler) {
            this.handler = null;
        }

        @Override
        public void closeConnection() {}

        /** What BlueZ emits on a device's D-Bus path when its link goes away. */
        void signalConnected(String dbusPath, boolean connected) throws DBusException {
            Map<String, Variant<?>> changed = new HashMap<>();
            changed.put("Connected", new Variant<>(connected));
            PropertiesChanged signal =
                    new PropertiesChanged(dbusPath, "org.bluez.Device1", changed, List.of());
            AbstractPropertiesChangedHandler h = handler;
            assertNotNull("transport registered a property handler", h);
            h.handle(signal);
        }
    }

    private static final class FakeAdapter extends BluetoothAdapter {
        FakeAdapter() {
            super(null, "/org/bluez/hci0", null);
        }

        @Override
        public String getAddress() {
            return "00:11:22:33:44:55";
        }

        @Override
        public boolean isPowered() {
            return true;
        }

        @Override
        public boolean startDiscovery() {
            return true;
        }

        @Override
        public boolean stopDiscovery() {
            return true;
        }
    }

    /**
     * A board whose GATT database only becomes visible once {@code ServicesResolved} is true, and
     * whose services vanish (like BlueZ's do) as soon as the link is gone.
     */
    private static final class FakeDevice extends BluetoothDevice {
        final String address;
        final AtomicBoolean connected = new AtomicBoolean();
        final AtomicBoolean servicesResolved = new AtomicBoolean();
        final AtomicInteger connectCalls = new AtomicInteger();
        final AtomicInteger notifyStarted = new AtomicInteger();
        volatile boolean resolveServicesOnConnect = true;
        private final Map<String, FakeService> services = new HashMap<>();

        FakeDevice(String address, BleProfile profile) {
            super(null, null, "/org/bluez/hci0/dev_" + address.replace(':', '_'), null);
            this.address = address;
            List<BleProfile.Subscription> subs = profile.subscriptions();
            serviceFor(profile.writeServiceUuid()).addCharacteristic(profile.writeCharacteristicUuid());
            for (BleProfile.Subscription sub : subs) {
                serviceFor(sub.serviceUuid()).addCharacteristic(sub.characteristicUuid());
            }
        }

        private FakeService serviceFor(String uuid) {
            return services.computeIfAbsent(
                    uuid.toLowerCase(), u -> new FakeService(this, u, notifyStarted));
        }

        @Override
        public String getAddress() {
            return address;
        }

        @Override
        public String getName() {
            return "Fake Pegasus";
        }

        @Override
        public Short getRssi() {
            return (short) -50;
        }

        @Override
        public boolean connect() {
            connectCalls.incrementAndGet();
            connected.set(true);
            servicesResolved.set(resolveServicesOnConnect);
            return true;
        }

        @Override
        public boolean disconnect() {
            connected.set(false);
            servicesResolved.set(false);
            return true;
        }

        @Override
        public Boolean isServicesResolved() {
            return connected.get() && servicesResolved.get();
        }

        @Override
        public BluetoothGattService getGattServiceByUuid(String uuid) {
            if (!connected.get()) {
                return null;
            }
            return services.get(uuid.toLowerCase());
        }

        /** The peripheral goes away: link down, and BlueZ tells the transport so. */
        void dropLink(FakeBlueZ blueZ) throws DBusException {
            connected.set(false);
            servicesResolved.set(false);
            blueZ.signalConnected(getDbusPath(), false);
        }
    }

    private static final class FakeService extends BluetoothGattService {
        private final Map<String, BluetoothGattCharacteristic> characteristics = new HashMap<>();
        private final AtomicInteger notifyStarted;
        private final String uuid;

        FakeService(FakeDevice device, String uuid, AtomicInteger notifyStarted) {
            super(null, device, device.getDbusPath() + "/service_" + uuid.substring(0, 8), null);
            this.uuid = uuid;
            this.notifyStarted = notifyStarted;
        }

        void addCharacteristic(String charUuid) {
            String key = charUuid.toLowerCase();
            characteristics.put(key, new FakeCharacteristic(this, key, notifyStarted));
        }

        @Override
        public String getUuid() {
            return uuid;
        }

        @Override
        public BluetoothGattCharacteristic getGattCharacteristicByUuid(String charUuid) {
            return characteristics.get(charUuid.toLowerCase());
        }
    }

    private static final class FakeCharacteristic extends BluetoothGattCharacteristic {
        private final String uuid;
        private final AtomicInteger notifyStarted;

        FakeCharacteristic(FakeService service, String uuid, AtomicInteger notifyStarted) {
            super(null, service, service.getDbusPath() + "/char_" + uuid.substring(0, 8), null);
            this.uuid = uuid;
            this.notifyStarted = notifyStarted;
        }

        @Override
        public String getUuid() {
            return uuid;
        }

        @Override
        public void startNotify() {
            notifyStarted.incrementAndGet();
        }

        @Override
        public void writeValue(byte[] value, Map<String, Object> options) {}
    }
}
