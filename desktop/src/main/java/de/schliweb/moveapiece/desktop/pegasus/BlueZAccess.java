/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.desktop.pegasus;

import com.github.hypfvieh.bluetooth.DeviceManager;
import com.github.hypfvieh.bluetooth.wrapper.BluetoothAdapter;
import com.github.hypfvieh.bluetooth.wrapper.BluetoothDevice;
import java.util.List;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.handlers.AbstractPropertiesChangedHandler;

/**
 * The slice of {@code bluez-dbus}'s {@link DeviceManager} that {@link LinuxPegasusBleTransport}
 * actually uses. {@code DeviceManager} has no public constructor and always opens the system D-Bus,
 * so this seam is what lets the transport's connect/reconnect state machine run against a fake
 * BlueZ in a unit test (on any OS, no D-Bus needed); {@link #systemBus()} is the real thing.
 */
interface BlueZAccess {

    BluetoothAdapter getAdapter();

    void findBtDevicesByIntrospection(BluetoothAdapter adapter);

    List<BluetoothDevice> getDevices(String adapterAddress, boolean refresh);

    void registerPropertyHandler(AbstractPropertiesChangedHandler handler) throws DBusException;

    void unRegisterPropertyHandler(AbstractPropertiesChangedHandler handler) throws DBusException;

    void closeConnection();

    /** BlueZ over the system D-Bus, exactly what the transport used before this seam existed. */
    static BlueZAccess systemBus() throws DBusException {
        DeviceManager deviceManager = DeviceManager.createInstance(false);
        return new BlueZAccess() {
            @Override
            public BluetoothAdapter getAdapter() {
                return deviceManager.getAdapter();
            }

            @Override
            public void findBtDevicesByIntrospection(BluetoothAdapter adapter) {
                deviceManager.findBtDevicesByIntrospection(adapter);
            }

            @Override
            public List<BluetoothDevice> getDevices(String adapterAddress, boolean refresh) {
                return deviceManager.getDevices(adapterAddress, refresh);
            }

            @Override
            public void registerPropertyHandler(AbstractPropertiesChangedHandler handler)
                    throws DBusException {
                deviceManager.registerPropertyHandler(handler);
            }

            @Override
            public void unRegisterPropertyHandler(AbstractPropertiesChangedHandler handler)
                    throws DBusException {
                deviceManager.unRegisterPropertyHandler(handler);
            }

            @Override
            public void closeConnection() {
                deviceManager.closeConnection();
            }
        };
    }
}
