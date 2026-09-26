/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.chessnut.core.game;

/** Why a physical board cannot be taken over as a game position; see {@link PhysicalPosition}. */
public final class InvalidPositionException extends Exception {

    public enum Reason {
        /** No board report received yet. */
        NO_BOARD,
        /** Not exactly one king per side. */
        KINGS,
        /** A pawn on the first or eighth rank. */
        PAWN_ON_BACK_RANK,
        /**
         * The side that is not to move is in check, i.e. the side to move could capture the king.
         */
        OPPONENT_IN_CHECK,
        /** The position is otherwise unplayable (FEN rejected). */
        UNPLAYABLE
    }

    private final Reason reason;

    public InvalidPositionException(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
