/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.logic;

/** The physical boards MoveAPiece can talk to. Product names are not translated. */
public enum BoardType {
    PEGASUS("DGT Pegasus", "pegasus"),
    CHESSNUT("Chessnut Air", "chessnut");

    private final String displayName;
    private final String key;

    BoardType(String displayName, String key) {
        this.displayName = displayName;
        this.key = key;
    }

    public String displayName() {
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
}
