/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.pegasus.core.transport;

/**
 * BLE UUIDs expected on a DGT Pegasus.
 *
 * <p>Source: DGTCentaurMods Pegasus emulation (protocol knowledge only, no code copied; see
 * docs/PEGASUS_PROTOCOL.md). Status: CONFIRMED_BY_REFERENCE_IMPLEMENTATION until verified on real
 * hardware.
 */
public final class PegasusUuids {

    /** Nordic UART Service (NUS). */
    public static final String UART_SERVICE = "6e400001-b5a3-f393-e0a9-e50e24dcca9e";

    /** Write characteristic (app → board). */
    public static final String UART_WRITE_CHARACTERISTIC = "6e400002-b5a3-f393-e0a9-e50e24dcca9e";

    /** Notify characteristic (board → app). */
    public static final String UART_NOTIFY_CHARACTERISTIC = "6e400003-b5a3-f393-e0a9-e50e24dcca9e";

    /** Client Characteristic Configuration Descriptor (Bluetooth standard). */
    public static final String CCCD = "00002902-0000-1000-8000-00805f9b34fb";

    private PegasusUuids() {}
}
