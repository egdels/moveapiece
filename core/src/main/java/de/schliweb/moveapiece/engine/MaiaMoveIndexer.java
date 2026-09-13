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
import com.github.bhlangonijr.chesslib.move.Move;

/**
 * Translates between a real chesslib {@link Move} (real board coordinates, normal UCI castling
 * notation like "e1g1") and the "network space" move string a Maia/lc0 policy output is addressed
 * by via {@link MaiaPolicyIndex} (always as if White is to move, castling written as "king captures
 * own rook") - see MAIA_PROVENANCE_TEMPLATE.md for why both quirks exist.
 *
 * <p>Translation only ever runs forward, from a real legal {@link Move} to its network string,
 * never the other way around: {@link MaiaEngine} keeps the original {@link Move} object alongside
 * its network string while scoring candidates, so the winning move is reported in real board
 * coordinates directly, with no need to invert this transform.
 */
final class MaiaMoveIndexer {

    private MaiaMoveIndexer() {}

    /**
     * @param board the position {@code move} is legal in - only used to tell a castling king move
     *     apart from an ordinary two-square move, and to resolve the promotion piece letter
     */
    static String toNetworkMove(Board board, Move move) {
        Square from = move.getFrom();
        Square to = move.getTo();
        int fromFile = from.getFile().ordinal();
        int fromRank = from.getRank().ordinal();
        int toFile = to.getFile().ordinal();
        int toRank = to.getRank().ordinal();

        if (isCastling(board, move, fromFile, fromRank, toFile, toRank)) {
            // "King captures own rook": in normal (non-Chess960) chess the rook is always on the
            // a-file (queenside) or h-file (kingside) of the same rank as the king.
            toFile = toFile > fromFile ? 7 : 0;
        }

        boolean blackToMove = board.getSideToMove() == Side.BLACK;
        if (blackToMove) {
            fromRank = 7 - fromRank;
            toRank = 7 - toRank;
        }

        StringBuilder sb = new StringBuilder(5);
        appendSquare(sb, fromFile, fromRank);
        appendSquare(sb, toFile, toRank);
        char promo = promotionLetter(move.getPromotion());
        if (promo != 0) {
            sb.append(promo);
        }
        return sb.toString();
    }

    /**
     * A king move is castling iff the moving piece is a king and it travels two files - no other
     * king move (or any other piece's move) can do that in standard chess, so this needs no
     * explicit castling-rights lookup.
     */
    private static boolean isCastling(
            Board board, Move move, int fromFile, int fromRank, int toFile, int toRank) {
        if (fromRank != toRank) {
            return false;
        }
        Piece moving = board.getPiece(move.getFrom());
        return moving.getPieceType() == PieceType.KING && Math.abs(toFile - fromFile) == 2;
    }

    private static void appendSquare(StringBuilder sb, int file, int rank) {
        sb.append((char) ('a' + file));
        sb.append((char) ('1' + rank));
    }

    private static char promotionLetter(Piece promotion) {
        if (promotion == null || promotion == Piece.NONE) {
            return 0;
        }
        switch (promotion.getPieceType()) {
            case KNIGHT:
                return 'n';
            case BISHOP:
                return 'b';
            case ROOK:
                return 'r';
            case QUEEN:
                return 'q';
            default:
                return 0;
        }
    }
}
