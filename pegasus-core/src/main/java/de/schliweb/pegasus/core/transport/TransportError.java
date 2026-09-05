/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.pegasus.core.transport;

/** Explicit, non-silent error conditions of the BLE transport. */
public enum TransportError {
    BLUETOOTH_DISABLED,
    PERMISSION_DENIED,
    SCAN_FAILED,
    CONNECT_FAILED,
    CONNECT_TIMEOUT,
    SERVICE_NOT_FOUND,
    CHARACTERISTIC_NOT_FOUND,
    NOTIFICATION_SETUP_FAILED,
    WRITE_FAILED,
    DISCONNECTED_UNEXPECTEDLY,
    RECONNECT_GIVEN_UP
}
