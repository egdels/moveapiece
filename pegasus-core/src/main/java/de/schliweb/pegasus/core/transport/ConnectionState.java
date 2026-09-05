/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.pegasus.core.transport;

/**
 * Connection state of the Pegasus BLE transport.
 *
 * <pre>
 * DISCONNECTED → CONNECTING → DISCOVERING_SERVICES → SUBSCRIBING → CONNECTED
 * </pre>
 *
 * RECONNECTING is entered after an unexpected disconnect while automatic reconnect attempts are
 * pending.
 */
public enum ConnectionState {
    DISCONNECTED,
    CONNECTING,
    DISCOVERING_SERVICES,
    SUBSCRIBING,
    CONNECTED,
    RECONNECTING
}
