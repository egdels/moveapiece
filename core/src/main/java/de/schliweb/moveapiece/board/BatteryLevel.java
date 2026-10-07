/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.board;

/** How a board rates its own battery; the host decides how loudly to say so. */
public enum BatteryLevel {
    /** Nothing to worry about. */
    OK,
    /** The board flags its battery as low (the Pegasus does so at about 10 %). */
    LOW,
    /** The board is about to shut itself down (Pegasus: "low" and "empty" both set, see DGT). */
    CRITICAL;

    /** Whether this level is worse than {@code other}. */
    public boolean worseThan(BatteryLevel other) {
        return ordinal() > other.ordinal();
    }
}
