/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.pegasus.core.transport;

/** Callback interface for {@link PegasusTransport} events. */
public interface TransportListener {

    void onConnectionStateChanged(ConnectionState state);

    /** Raw bytes received via notification. No DGT interpretation in phase 1. */
    void onDataReceived(String characteristicUuid, byte[] data);

    /** Raw bytes successfully written. */
    void onDataSent(String characteristicUuid, byte[] data);

    void onError(TransportError error, String detail);
}
