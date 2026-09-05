/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.pegasus.core.chess;

/** Color of a logical chess piece / side to move. */
public enum PieceColor {
    WHITE,
    BLACK;

    public PieceColor opposite() {
        return this == WHITE ? BLACK : WHITE;
    }
}
