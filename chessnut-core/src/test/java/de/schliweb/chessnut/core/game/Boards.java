/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.chessnut.core.game;

import de.schliweb.pegasus.core.chess.ChessPosition;
import de.schliweb.pegasus.core.protocol.BoardState;
import de.schliweb.pegasus.core.protocol.PieceCodes;

/** Test helpers: physical boards built from FENs and edited square by square. */
final class Boards {

    private Boards() {}

    static BoardState of(String fen) {
        return IdentityProjection.of(ChessPosition.fromFen(fen));
    }

    static BoardState start() {
        return IdentityProjection.of(ChessPosition.starting());
    }

    static int sq(String name) {
        return BoardState.squareIndex(name);
    }

    static BoardState lift(BoardState board, String square) {
        return board.withSquare(sq(square), PieceCodes.EMPTY);
    }

    static BoardState put(BoardState board, String square, int pieceCode) {
        return board.withSquare(sq(square), pieceCode);
    }

    /** Moves whatever stands on {@code from} to {@code to} (overwriting). */
    static BoardState move(BoardState board, String from, String to) {
        int code = board.pieceCodeAt(sq(from));
        return lift(put(board, to, code), from);
    }
}
