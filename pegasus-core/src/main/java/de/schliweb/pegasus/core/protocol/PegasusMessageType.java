/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.pegasus.core.protocol;

/**
 * Known Pegasus message type bytes (board → app). Evidence levels per docs/PEGASUS_PROTOCOL.md: all
 * values CONFIRMED_BY_REFERENCE_IMPLEMENTATION unless noted otherwise. Message type bytes always
 * have bit 7 set.
 */
public final class PegasusMessageType {

    public static final int BOARD_DUMP = 0x86;
    public static final int FIELD_UPDATE = 0x8E;
    public static final int SERIALNR = 0x91;
    public static final int TRADEMARK = 0x92;
    public static final int VERSION = 0x93;
    public static final int HARDWARE_VERSION = 0x96;
    public static final int BATTERY_STATUS = 0xA0;
    public static final int LONG_SERIALNR = 0xA2;

    private PegasusMessageType() {}

    /** Human-readable name for logging; unknown types render as UNKNOWN_0xNN. */
    public static String name(int type) {
        switch (type) {
            case BOARD_DUMP:
                return "BOARD_DUMP";
            case FIELD_UPDATE:
                return "FIELD_UPDATE";
            case SERIALNR:
                return "SERIALNR";
            case TRADEMARK:
                return "TRADEMARK";
            case VERSION:
                return "VERSION";
            case HARDWARE_VERSION:
                return "HARDWARE_VERSION";
            case BATTERY_STATUS:
                return "BATTERY_STATUS";
            case LONG_SERIALNR:
                return "LONG_SERIALNR";
            default:
                return String.format("UNKNOWN_0x%02X", type);
        }
    }
}
