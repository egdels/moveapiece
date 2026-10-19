/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.logic;

import com.github.bhlangonijr.chesslib.Square;
import java.util.Locale;

/** The squares of a move in UCI notation ("e2e4", "e7e8q"). */
public final class UciMoves {

    private UciMoves() {}

    /** The square the move starts on. */
    public static Square from(String uci) {
        return Square.valueOf(uci.substring(0, 2).toUpperCase(Locale.ROOT));
    }

    /** The square the move ends on. */
    public static Square to(String uci) {
        return Square.valueOf(uci.substring(2, 4).toUpperCase(Locale.ROOT));
    }

    /** The move as plain "from-to" squares, e.g. "e2-e4". */
    public static String squares(String uci) {
        return uci.substring(0, 2).toLowerCase(Locale.ROOT)
                + "-"
                + uci.substring(2, 4).toLowerCase(Locale.ROOT);
    }
}
