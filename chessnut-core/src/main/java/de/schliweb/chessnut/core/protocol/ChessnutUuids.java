/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.chessnut.core.protocol;

/**
 * BLE UUIDs of a Chessnut Air, VERIFIED on real hardware 2026-09-26 (see
 * tools/chessnut-sniffer/CHESSNUT_PROTOCOL.md).
 *
 * <p>Unlike the Pegasus, commands and board reports live in two different services: the app writes
 * to {@link #COMMAND_WRITE_CHARACTERISTIC}, replies (acks, battery, button events) arrive on {@link
 * #COMMAND_NOTIFY_CHARACTERISTIC}, and the board reports stream on {@link
 * #BOARD_NOTIFY_CHARACTERISTIC}. A transport must subscribe to both notify characteristics.
 */
public final class ChessnutUuids {

    /** Service carrying the streamed board reports. */
    public static final String BOARD_SERVICE = "1b7e8261-2877-41c3-b46e-cf057c562023";

    /** Notify characteristic: board reports (board → app). */
    public static final String BOARD_NOTIFY_CHARACTERISTIC = "1b7e8262-2877-41c3-b46e-cf057c562023";

    /** Service carrying commands and their replies. */
    public static final String COMMAND_SERVICE = "1b7e8271-2877-41c3-b46e-cf057c562023";

    /** Write characteristic (app → board). */
    public static final String COMMAND_WRITE_CHARACTERISTIC =
            "1b7e8272-2877-41c3-b46e-cf057c562023";

    /** Notify characteristic: command replies, battery, button events (board → app). */
    public static final String COMMAND_NOTIFY_CHARACTERISTIC =
            "1b7e8273-2877-41c3-b46e-cf057c562023";

    /** Client Characteristic Configuration Descriptor (Bluetooth standard). */
    public static final String CCCD = "00002902-0000-1000-8000-00805f9b34fb";

    /** Advertised local name prefix (the board advertises no service UUIDs). */
    public static final String NAME_PREFIX = "Chessnut";

    private ChessnutUuids() {}
}
