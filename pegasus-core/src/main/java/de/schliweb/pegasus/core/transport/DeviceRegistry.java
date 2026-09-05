/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.pegasus.core.transport;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deduplicates scan results by device address and keeps the latest name/RSSI/advertisement data.
 * Insertion order is preserved.
 */
public final class DeviceRegistry {

    private final Map<String, DiscoveredDevice> devices = new LinkedHashMap<>();

    /**
     * @return true if this device is new or its data changed.
     */
    public synchronized boolean update(DiscoveredDevice device) {
        DiscoveredDevice previous = devices.get(device.getAddress());
        if (previous != null
                && previous.getRssi() == device.getRssi()
                && equalsNullable(previous.getName(), device.getName())) {
            return false;
        }
        devices.put(device.getAddress(), device);
        return true;
    }

    public synchronized List<DiscoveredDevice> getDevices() {
        return new ArrayList<>(devices.values());
    }

    public synchronized DiscoveredDevice getByAddress(String address) {
        return devices.get(address);
    }

    public synchronized void clear() {
        devices.clear();
    }

    private static boolean equalsNullable(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }
}
