/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.pegasus.core.transport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class DeviceRegistryTest {

    private static DiscoveredDevice device(String name, String address, int rssi) {
        return new DiscoveredDevice(name, address, rssi, Collections.emptyList());
    }

    @Test
    public void deduplicatesByAddress() {
        DeviceRegistry registry = new DeviceRegistry();
        assertTrue(registry.update(device("PCS-REVII-081500", "AA:BB", -50)));
        assertFalse(registry.update(device("PCS-REVII-081500", "AA:BB", -50)));
        assertEquals(1, registry.getDevices().size());
    }

    @Test
    public void updatesRssiAndName() {
        DeviceRegistry registry = new DeviceRegistry();
        registry.update(device(null, "AA:BB", -60));
        assertTrue(registry.update(device("PCS-REVII-081500", "AA:BB", -55)));
        List<DiscoveredDevice> devices = registry.getDevices();
        assertEquals(1, devices.size());
        assertEquals("PCS-REVII-081500", devices.get(0).getName());
        assertEquals(-55, devices.get(0).getRssi());
    }

    @Test
    public void keepsMultipleDevices() {
        DeviceRegistry registry = new DeviceRegistry();
        registry.update(device("A", "AA:BB", -50));
        registry.update(device("B", "CC:DD", -60));
        assertEquals(2, registry.getDevices().size());
        assertEquals("B", registry.getByAddress("CC:DD").getName());
    }

    @Test
    public void pegasusHeuristics() {
        DiscoveredDevice byName = device("PCS-REVII-123456", "AA:BB", -50);
        assertTrue(byName.nameLooksLikePegasus());
        assertFalse(byName.advertisesUartService());

        DiscoveredDevice byService =
                new DiscoveredDevice(
                        null,
                        "CC:DD",
                        -60,
                        Collections.singletonList("6E400001-B5A3-F393-E0A9-E50E24DCCA9E"));
        assertTrue(byService.advertisesUartService());
        assertFalse(byService.nameLooksLikePegasus());
    }
}
