/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.logic;

/**
 * The physical boards MoveAPiece can talk to. Product names are not translated.
 *
 * <p>{@link #displayName()} is the short name used inside messages ("Chessnut connected", "Chessnut
 * battery: 95 %"); {@link #chooserLabel()} is the longer label for the board-type chooser, where
 * there is room to list the supported models.
 */
public enum BoardType {
    PEGASUS("DGT Pegasus", "DGT Pegasus", "pegasus"),
    CHESSNUT("Chessnut", "Chessnut (Air, Air+, Pro, Go)", "chessnut");

    private final String displayName;
    private final String chooserLabel;
    private final String key;

    BoardType(String displayName, String chooserLabel, String key) {
        this.displayName = displayName;
        this.chooserLabel = chooserLabel;
        this.key = key;
    }

    /** Short product name for status, error and battery messages. */
    public String displayName() {
        return displayName;
    }

    /** Label for the board-type chooser; lists the supported models where applicable. */
    public String chooserLabel() {
        return chooserLabel;
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
