/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.engine;

import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.PieceType;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.Square;
import java.util.List;

/**
 * Builds Maia-3's input: one token per square, each holding the piece on that square for the
 * current position and the seven before it. Mirrors {@code tokenize_board} and {@code
 * get_historical_tokens} of the reference implementation (see MAIA_PROVENANCE.md).
 *
 * <p>Every position is seen from its own side to move: with Black to move the board is flipped top
 * to bottom and the colours are swapped, so "own pieces" always sit in the first six channels. In a
 * history the perspective therefore alternates from one position to the next. Castling rights, en
 * passant and move counters are no inputs of this model.
 */
final class MaiaPositionEncoder {

    static final int HISTORY_STEPS = 8;
    static final int SQUARES = 64;
    private static final int CHANNELS_PER_STEP = 12;

    /** Piece channels of all history steps plus one trailing clock channel, which stays zero. */
    static final int TOKEN_SIZE = HISTORY_STEPS * CHANNELS_PER_STEP + 1;

    private static final int EMPTY = -1;

    private MaiaPositionEncoder() {}

    /**
     * The piece channel (0-5 own pawn..king, 6-11 the opponent's) for each square of {@code board},
     * or -1 for an empty square, indexed a1 = 0 .. h8 = 63 from the side to move's point of view.
     */
    static int[] placement(Board board) {
        boolean blackToMove = board.getSideToMove() == Side.BLACK;
        int[] channels = new int[SQUARES];
        for (int i = 0; i < SQUARES; i++) {
            Square square = Square.squareAt(i);
            int file = square.getFile().ordinal();
            int rank = square.getRank().ordinal();
            int target = (blackToMove ? 7 - rank : rank) * 8 + file;
            Piece piece = board.getPiece(square);
            if (piece == Piece.NONE) {
                channels[target] = EMPTY;
                continue;
            }
            boolean own = (piece.getPieceSide() == Side.BLACK) == blackToMove;
            channels[target] = typeIndex(piece.getPieceType()) + (own ? 0 : 6);
        }
        return channels;
    }

    /**
     * @param history placements from {@link #placement}, oldest first, the current position last;
     *     only the last {@link #HISTORY_STEPS} count. A shorter history is filled up at the front
     *     with its oldest position, as the reference implementation does.
     * @return the {@code [64][TOKEN_SIZE]} input tensor, flattened row by row
     */
    static float[] encode(List<int[]> history) {
        int available = Math.min(history.size(), HISTORY_STEPS);
        int first = history.size() - available;
        float[] tokens = new float[SQUARES * TOKEN_SIZE];
        for (int step = 0; step < HISTORY_STEPS; step++) {
            int fromEnd = HISTORY_STEPS - 1 - step;
            int[] channels =
                    history.get(fromEnd < available ? history.size() - 1 - fromEnd : first);
            for (int square = 0; square < SQUARES; square++) {
                if (channels[square] != EMPTY) {
                    tokens[square * TOKEN_SIZE + step * CHANNELS_PER_STEP + channels[square]] = 1f;
                }
            }
        }
        return tokens;
    }

    private static int typeIndex(PieceType type) {
        switch (type) {
            case PAWN:
                return 0;
            case KNIGHT:
                return 1;
            case BISHOP:
                return 2;
            case ROOK:
                return 3;
            case QUEEN:
                return 4;
            case KING:
                return 5;
            default:
                throw new IllegalArgumentException("No channel for " + type);
        }
    }
}
