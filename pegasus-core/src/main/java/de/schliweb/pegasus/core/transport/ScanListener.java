/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.pegasus.core.transport;

/** Callback interface for BLE scan results. */
public interface ScanListener {

    /** Called for new devices and for updates (e.g. changed RSSI) of known ones. */
    void onDeviceFound(DiscoveredDevice device);

    /** Called when the scan stops (timeout, manual stop or failure). */
    void onScanFinished();

    void onScanFailed(TransportError error, String detail);
}
