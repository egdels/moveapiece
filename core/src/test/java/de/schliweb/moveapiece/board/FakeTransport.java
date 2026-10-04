/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.board;

import de.schliweb.pegasus.core.transport.ConnectionState;
import de.schliweb.pegasus.core.transport.PegasusTransport;
import de.schliweb.pegasus.core.transport.ScanListener;
import de.schliweb.pegasus.core.transport.TransportListener;
import java.util.concurrent.LinkedBlockingQueue;

/** Fake BLE transport: records writes, lets the test inject "received" bytes. */
final class FakeTransport implements PegasusTransport {
    final LinkedBlockingQueue<byte[]> written = new LinkedBlockingQueue<>();
    private volatile TransportListener listener;
    private volatile ConnectionState state = ConnectionState.DISCONNECTED;

    @Override
    public void setListener(TransportListener listener) {
        this.listener = listener;
    }

    @Override
    public void startScan(ScanListener listener, long timeoutMs) {}

    @Override
    public void stopScan() {}

    @Override
    public void connect(String deviceAddress) {
        state = ConnectionState.CONNECTED;
        if (listener != null) {
            listener.onConnectionStateChanged(ConnectionState.CONNECTED);
        }
    }

    @Override
    public void disconnect() {
        state = ConnectionState.DISCONNECTED;
        if (listener != null) {
            listener.onConnectionStateChanged(ConnectionState.DISCONNECTED);
        }
    }

    @Override
    public void write(byte[] data) {
        written.add(data.clone());
    }

    @Override
    public ConnectionState getConnectionState() {
        return state;
    }

    /** Simulates a BLE notification arriving (real hardware: a non-main thread). */
    void feed(byte[] data) {
        feed("uart-rx", data);
    }

    void feed(String characteristicUuid, byte[] data) {
        TransportListener l = listener;
        if (l != null) {
            l.onDataReceived(characteristicUuid, data);
        }
    }
}
