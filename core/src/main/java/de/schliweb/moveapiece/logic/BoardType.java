/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.logic;

/**
 * The physical boards MoveAPiece can talk to. Product names are not translated. The user never
 * picks a type: {@link BoardTypeDetection} works it out from the device chosen in the scan list,
 * and the stored type only decides which candidate is tried first.
 *
 * <p>{@link #displayName()} is the short name used inside messages ("Chessnut connected", "Chessnut
 * battery: 95 %"); the Chessnut Air, Air+, Pro and Go all share the CHESSNUT type.
 */
public enum BoardType {
    PEGASUS("DGT Pegasus", "pegasus"),
    CHESSNUT("Chessnut", "chessnut");

    private final String displayName;
    private final String key;

    BoardType(String displayName, String key) {
        this.displayName = displayName;
        this.key = key;
    }

    /** Short product name for status, error and battery messages. */
    public String displayName() {
        return displayName;
    }

    /**
     * {@link #displayName()}, refined by the connected device's advertised name where that is
     * reliable: a Chessnut cannot be renamed and advertises its model ("Chessnut Air"), so messages
     * can say which one it is. The Pegasus name is user-changeable (see {@link
     * #guessFromDeviceName}), and a Chessnut-family board advertising something else ("Smart
     * Chess") is better called "Chessnut" than by that name; both keep the plain product name.
     */
    public String displayNameFor(String deviceName) {
        if (this == CHESSNUT && deviceName != null) {
            String trimmed = deviceName.trim();
            if (trimmed.toLowerCase(java.util.Locale.ROOT).startsWith("chessnut")) {
                return trimmed;
            }
        }
        return displayName;
    }

    /** Stable identifier for persistence. */
    public String key() {
        return key;
    }

    /** Inverse of {@link #key()}; unknown or null keys fall back to {@link #PEGASUS}. */
    public static BoardType fromKey(String key) {
        for (BoardType type : values()) {
            if (type.key.equals(key)) {
                return type;
            }
        }
        return PEGASUS;
    }

    /**
     * The Nordic UART service a DGT Pegasus advertises (seen on hardware: a Pegasus advertises
     * exactly this one UUID). A Chessnut advertises no service UUIDs at all.
     */
    public static final String PEGASUS_ADVERTISED_SERVICE_UUID =
            "6e400001-b5a3-f393-e0a9-e50e24dcca9e";

    /**
     * Best guess from what a device advertises: its name when that carries a known prefix, else the
     * Pegasus UART service UUID, which survives a rename (DGT_SET_BOARD_NAME lets any app with a
     * developer key change the name). Null when neither says anything; the GATT probe after
     * connecting ({@link BoardTypeDetection}) is what decides in the end.
     */
    public static BoardType guessFromAdvertisement(
            String deviceName, java.util.List<String> advertisedServiceUuids) {
        BoardType byName = guessFromDeviceName(deviceName);
        if (byName != null) {
            return byName;
        }
        if (advertisedServiceUuids != null) {
            for (String uuid : advertisedServiceUuids) {
                if (PEGASUS_ADVERTISED_SERVICE_UUID.equalsIgnoreCase(uuid)) {
                    return PEGASUS;
                }
            }
        }
        return null;
    }

    /**
     * Best guess from a device's advertised BLE name, or null when the name gives nothing away.
     * Only a hint to order the candidates of a {@link BoardTypeDetection}: the Chessnut boards
     * cannot be renamed, but the Pegasus can (DGT_SET_BOARD_NAME in DGT's protocol document, any
     * UTF-8 string), and the observed Pegasus names differ between units ("PCS-REVII-..." on the
     * reference unit, "DGT_PEGASUS_<serial>" as the documented default).
     */
    public static BoardType guessFromDeviceName(String deviceName) {
        if (deviceName == null) {
            return null;
        }
        String name = deviceName.trim().toLowerCase(java.util.Locale.ROOT);
        if (name.startsWith("chessnut") || name.startsWith("smart chess")) {
            return CHESSNUT;
        }
        if (name.startsWith("pcs-revii") || name.contains("pegasus")) {
            return PEGASUS;
        }
        return null;
    }
}
