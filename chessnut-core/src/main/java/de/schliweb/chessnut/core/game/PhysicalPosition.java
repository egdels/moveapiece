/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.chessnut.core.game;

import de.schliweb.chessnut.core.game.InvalidPositionException.Reason;
import de.schliweb.pegasus.core.chess.ChessPosition;
import de.schliweb.pegasus.core.chess.PieceColor;
import de.schliweb.pegasus.core.protocol.BoardState;
import de.schliweb.pegasus.core.protocol.PieceCodes;

/**
 * Turns a physical board with piece identities into a FEN the host can load as a game position. The
 * board tells the pieces; the caller supplies the side to move. Castling rights are assumed
 * wherever king and rook still stand on their starting squares, en passant is never available, and
 * the clocks start fresh.
 */
public final class PhysicalPosition {

    private PhysicalPosition() {}

    /**
     * @throws InvalidPositionException if the board is not a playable position: not exactly one
     *     king each, a pawn on a back rank, or the side not to move standing in check.
     */
    public static String fenOf(BoardState board, PieceColor sideToMove)
            throws InvalidPositionException {
        if (board == null) {
            throw new InvalidPositionException(Reason.NO_BOARD);
        }
        int whiteKings = 0;
        int blackKings = 0;
        StringBuilder placement = new StringBuilder();
        for (int rank = 0; rank < 8; rank++) {
            int empty = 0;
            for (int file = 0; file < 8; file++) {
                int code = board.pieceCodeAt(rank * 8 + file);
                if (code == PieceCodes.EMPTY) {
                    empty++;
                    continue;
                }
                if (empty > 0) {
                    placement.append(empty);
                    empty = 0;
                }
                placement.append(PieceCodes.toChar(code));
                if (code == PieceCodes.WKING) {
                    whiteKings++;
                } else if (code == PieceCodes.BKING) {
                    blackKings++;
                } else if ((code == PieceCodes.WPAWN || code == PieceCodes.BPAWN)
                        && (rank == 0 || rank == 7)) {
                    throw new InvalidPositionException(Reason.PAWN_ON_BACK_RANK);
                }
            }
            if (empty > 0) {
                placement.append(empty);
            }
            if (rank < 7) {
                placement.append('/');
            }
        }
        if (whiteKings != 1 || blackKings != 1) {
            throw new InvalidPositionException(Reason.KINGS);
        }
        String castling = castlingRights(board);
        String fen =
                placement
                        + " "
                        + (sideToMove == PieceColor.WHITE ? "w" : "b")
                        + " "
                        + castling
                        + " - 0 1";
        String opponentFen =
                placement
                        + " "
                        + (sideToMove == PieceColor.WHITE ? "b" : "w")
                        + " "
                        + castling
                        + " - 0 1";
        try {
            if (ChessPosition.fromFen(opponentFen).inCheck()) {
                throw new InvalidPositionException(Reason.OPPONENT_IN_CHECK);
            }
            ChessPosition.fromFen(fen).legalMoves();
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new InvalidPositionException(Reason.UNPLAYABLE);
        }
        return fen;
    }

    private static String castlingRights(BoardState board) {
        StringBuilder sb = new StringBuilder();
        if (board.pieceCodeAt(BoardState.squareIndex("e1")) == PieceCodes.WKING) {
            if (board.pieceCodeAt(BoardState.squareIndex("h1")) == PieceCodes.WROOK) {
                sb.append('K');
            }
            if (board.pieceCodeAt(BoardState.squareIndex("a1")) == PieceCodes.WROOK) {
                sb.append('Q');
            }
        }
        if (board.pieceCodeAt(BoardState.squareIndex("e8")) == PieceCodes.BKING) {
            if (board.pieceCodeAt(BoardState.squareIndex("h8")) == PieceCodes.BROOK) {
                sb.append('k');
            }
            if (board.pieceCodeAt(BoardState.squareIndex("a8")) == PieceCodes.BROOK) {
                sb.append('q');
            }
        }
        return sb.length() == 0 ? "-" : sb.toString();
    }
}
