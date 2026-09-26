/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.chessnut.core.game;

import de.schliweb.pegasus.core.chess.ChessPosition;
import de.schliweb.pegasus.core.chess.Piece;
import de.schliweb.pegasus.core.protocol.BoardState;
import de.schliweb.pegasus.core.protocol.PieceCodes;

/**
 * Projects a logical {@link ChessPosition} to the {@link BoardState} a Chessnut Air reports for it:
 * every square carries its real {@link PieceCodes} value, not just occupancy. Equality of two such
 * states therefore means "same pieces on the same squares", which is what the Chessnut can actually
 * observe (unlike the occupancy-only Pegasus).
 */
public final class IdentityProjection {

    private IdentityProjection() {}

    public static BoardState of(ChessPosition position) {
        byte[] codes = new byte[BoardState.SQUARE_COUNT];
        for (int square = 0; square < BoardState.SQUARE_COUNT; square++) {
            codes[square] = (byte) codeOf(position.pieceAt(square));
        }
        return BoardState.fromBoardDumpPayload(codes);
    }

    /** {@link PieceCodes} value of a piece, {@link PieceCodes#EMPTY} for {@code null}. */
    public static int codeOf(Piece piece) {
        if (piece == null) {
            return PieceCodes.EMPTY;
        }
        switch (piece) {
            case WHITE_PAWN:
                return PieceCodes.WPAWN;
            case WHITE_ROOK:
                return PieceCodes.WROOK;
            case WHITE_KNIGHT:
                return PieceCodes.WKNIGHT;
            case WHITE_BISHOP:
                return PieceCodes.WBISHOP;
            case WHITE_KING:
                return PieceCodes.WKING;
            case WHITE_QUEEN:
                return PieceCodes.WQUEEN;
            case BLACK_PAWN:
                return PieceCodes.BPAWN;
            case BLACK_ROOK:
                return PieceCodes.BROOK;
            case BLACK_KNIGHT:
                return PieceCodes.BKNIGHT;
            case BLACK_BISHOP:
                return PieceCodes.BBISHOP;
            case BLACK_KING:
                return PieceCodes.BKING;
            case BLACK_QUEEN:
                return PieceCodes.BQUEEN;
            default:
                throw new IllegalArgumentException("Unknown piece " + piece);
        }
    }
}
