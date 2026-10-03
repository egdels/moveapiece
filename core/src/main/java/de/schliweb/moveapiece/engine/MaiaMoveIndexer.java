/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.engine;

import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.PieceType;
import com.github.bhlangonijr.chesslib.Square;
import com.github.bhlangonijr.chesslib.move.Move;

/**
 * Maps a move to its slot in Maia-3's policy output. Mirrors {@code get_all_possible_moves} of the
 * reference implementation (see MAIA_PROVENANCE.md): 64 x 64 from/to pairs, then the 256 promotions
 * from the seventh to the eighth rank with one slot each for queen, rook, bishop and knight.
 *
 * <p>Like the input (see {@link MaiaPositionEncoder}) the output is from the side to move's point
 * of view, so a move of Black is flipped top to bottom first. Castling is the plain king move
 * ({@code e1g1}), a promotion always uses its promotion slot.
 */
final class MaiaMoveIndexer {

    private static final int SQUARES = 64;

    static final int POLICY_SIZE = SQUARES * SQUARES + 8 * 8 * 4;

    private MaiaMoveIndexer() {}

    static int indexOf(Move move, boolean blackToMove) {
        int fromFile = file(move.getFrom());
        int toFile = file(move.getTo());
        int fromRank = rank(move.getFrom(), blackToMove);
        int toRank = rank(move.getTo(), blackToMove);
        Piece promotion = move.getPromotion();
        if (promotion != null && promotion != Piece.NONE) {
            return SQUARES * SQUARES
                    + fromFile * 32
                    + toFile * 4
                    + promotionIndex(promotion.getPieceType());
        }
        return (fromRank * 8 + fromFile) * SQUARES + toRank * 8 + toFile;
    }

    private static int file(Square square) {
        return square.getFile().ordinal();
    }

    private static int rank(Square square, boolean blackToMove) {
        int rank = square.getRank().ordinal();
        return blackToMove ? 7 - rank : rank;
    }

    private static int promotionIndex(PieceType type) {
        switch (type) {
            case QUEEN:
                return 0;
            case ROOK:
                return 1;
            case BISHOP:
                return 2;
            case KNIGHT:
                return 3;
            default:
                throw new IllegalArgumentException("Not a promotion piece: " + type);
        }
    }
}
