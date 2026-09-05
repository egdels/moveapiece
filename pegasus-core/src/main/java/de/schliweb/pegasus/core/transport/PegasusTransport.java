/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.pegasus.core.transport;

/**
 * Raw BLE transport to a DGT Pegasus.
 *
 * <p>Responsibilities: scan, connect, disconnect, service discovery, notification setup, raw
 * receive, raw send, connection state.
 *
 * <p>NOT responsible for: DGT message parsing, BoardState, move detection, chess rules or Lichess
 * (phase 2+, see docs/ARCHITECTURE.md).
 */
public interface PegasusTransport {

    void setListener(TransportListener listener);

    /** Starts a BLE scan; stops automatically after {@code timeoutMs}. */
    void startScan(ScanListener listener, long timeoutMs);

    void stopScan();

    /** Connects to a device found during the current session's scan. */
    void connect(String deviceAddress);

    /** Manual disconnect. Must NOT trigger an automatic reconnect. */
    void disconnect();

    /** Writes raw bytes to the UART write characteristic. */
    void write(byte[] data);

    ConnectionState getConnectionState();
}
