/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.pegasus.core.transport;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable description of a BLE device found during a scan. The address is only kept in memory for
 * the current session and is never persisted (see docs/ANDROID_BLE.md).
 */
public final class DiscoveredDevice {

    private final String name;
    private final String address;
    private final int rssi;
    private final List<String> advertisedServiceUuids;

    public DiscoveredDevice(
            String name, String address, int rssi, List<String> advertisedServiceUuids) {
        this.name = name;
        this.address = Objects.requireNonNull(address, "address");
        this.rssi = rssi;
        this.advertisedServiceUuids =
                advertisedServiceUuids == null
                        ? Collections.emptyList()
                        : Collections.unmodifiableList(advertisedServiceUuids);
    }

    /** May be null if the device does not advertise a local name. */
    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }

    public int getRssi() {
        return rssi;
    }

    public List<String> getAdvertisedServiceUuids() {
        return advertisedServiceUuids;
    }

    /** True if the advertisement contains the Nordic UART service UUID. */
    public boolean advertisesUartService() {
        for (String uuid : advertisedServiceUuids) {
            if (PegasusUuids.UART_SERVICE.equalsIgnoreCase(uuid)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Heuristic only: name matches the pattern observed on the reference implementation ({@code
     * PCS-REVII-######}). Never the sole identification criterion; final verification happens after
     * connect via GATT services.
     */
    public boolean nameLooksLikePegasus() {
        return name != null && name.toUpperCase().startsWith("PCS-REVII");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DiscoveredDevice)) return false;
        DiscoveredDevice that = (DiscoveredDevice) o;
        return address.equals(that.address);
    }

    @Override
    public int hashCode() {
        return address.hashCode();
    }

    @Override
    public String toString() {
        return (name == null ? "(unnamed)" : name) + " [" + address + "] " + rssi + " dBm";
    }
}
