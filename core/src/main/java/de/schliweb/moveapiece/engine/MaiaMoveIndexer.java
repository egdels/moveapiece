/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.engine;

import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.File;
import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.PieceType;
import com.github.bhlangonijr.chesslib.Rank;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.Square;
import com.github.bhlangonijr.chesslib.game.VariationType;
import com.github.bhlangonijr.chesslib.move.Move;

/**
 * Maps a move to its slot in Maia-3's policy output. Mirrors {@code get_all_possible_moves} of the
 * reference implementation (see MAIA_PROVENANCE.md): 64 x 64 from/to pairs, then the 256 promotions
 * from the seventh to the eighth rank with one slot each for queen, rook, bishop and knight.
 *
 * <p>Like the input (see {@link MaiaPositionEncoder}) the output is from the side to move's point
 * of view, so a move of Black is flipped top to bottom first. Castling is the plain king move
 * ({@code e1g1}), a promotion always uses its promotion slot. A Chess960 castle takes the slot of
 * the king's move to its castling square, see {@link #indexOf(Board, Move)}.
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

    /**
     * {@link #indexOf(Move, boolean)} for a legal move in {@code board}. Maia learnt castling from
     * standard games only, as the king's move to the g- or c-file; a Chess960 castle (the king onto
     * its own rook, a pair of squares Maia never saw as castling) takes that slot too, or - when
     * the king already stands on its castling square - the slot of the rook's move. Measured on
     * human Chess960 games, this lets Maia predict castling about as often as one in five times the
     * player castled, against hardly ever with the king-onto-rook slot.
     */
    static int indexOf(Board board, Move move) {
        boolean blackToMove = board.getSideToMove() == Side.BLACK;
        Piece king = board.getPiece(move.getFrom());
        Piece target = board.getPiece(move.getTo());
        if (board.getContext().getVariationType() == VariationType.CHESS960
                && king.getPieceType() == PieceType.KING
                && target.getPieceType() == PieceType.ROOK
                && target.getPieceSide() == king.getPieceSide()) {
            boolean kingSide = file(move.getTo()) > file(move.getFrom());
            Rank rank = move.getFrom().getRank();
            Square kingTo = Square.encode(rank, kingSide ? File.FILE_G : File.FILE_C);
            if (kingTo != move.getFrom()) {
                return indexOf(new Move(move.getFrom(), kingTo), blackToMove);
            }
            Square rookTo = Square.encode(rank, kingSide ? File.FILE_F : File.FILE_D);
            return indexOf(new Move(move.getTo(), rookTo), blackToMove);
        }
        return indexOf(move, blackToMove);
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
