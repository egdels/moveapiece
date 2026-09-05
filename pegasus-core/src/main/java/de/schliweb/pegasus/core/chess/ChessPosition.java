/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.pegasus.core.chess;

import de.schliweb.pegasus.core.protocol.BoardState;
import java.util.ArrayList;
import java.util.List;

/**
 * Immutable logical chess position: 64 squares with pieces, side to move, castling rights, en
 * passant target square, halfmove clock and fullmove number. Supports FEN import/export, legal move
 * generation (including castling, en passant, promotion/underpromotion) and {@link #apply(Move)}.
 *
 * <p>Square numbering matches the DGT scheme shared with {@link BoardState}: index 0 = a8 … 63 = h1
 * (row = index / 8, 0 = rank 8; column = index % 8, 0 = file a).
 *
 * <p>This is a deliberately small, dependency-free rules layer (see docs/CHESS_RULES.md);
 * correctness is verified by perft tests.
 */
public final class ChessPosition {

    public static final String STARTING_FEN =
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    private static final int NO_SQUARE = -1;

    // Castling right bits.
    private static final int WHITE_KINGSIDE = 1;
    private static final int WHITE_QUEENSIDE = 2;
    private static final int BLACK_KINGSIDE = 4;
    private static final int BLACK_QUEENSIDE = 8;

    // Well-known square indices (0 = a8 … 63 = h1).
    private static final int A8 = 0, E8 = 4, H8 = 7;
    private static final int A1 = 56, E1 = 60, H1 = 63;

    private static final int[][] KNIGHT_OFFSETS = {
        {-2, -1}, {-2, 1}, {-1, -2}, {-1, 2},
        {1, -2}, {1, 2}, {2, -1}, {2, 1}
    };
    private static final int[][] KING_OFFSETS = {
        {-1, -1}, {-1, 0}, {-1, 1}, {0, -1},
        {0, 1}, {1, -1}, {1, 0}, {1, 1}
    };
    private static final int[][] ROOK_DIRECTIONS = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
    private static final int[][] BISHOP_DIRECTIONS = {{-1, -1}, {-1, 1}, {1, -1}, {1, 1}};

    private final Piece[] board;
    private final PieceColor sideToMove;
    private final int castlingRights;
    private final int enPassantSquare;
    private final int halfmoveClock;
    private final int fullmoveNumber;

    private ChessPosition(
            Piece[] board,
            PieceColor sideToMove,
            int castlingRights,
            int enPassantSquare,
            int halfmoveClock,
            int fullmoveNumber) {
        this.board = board;
        this.sideToMove = sideToMove;
        this.castlingRights = castlingRights;
        this.enPassantSquare = enPassantSquare;
        this.halfmoveClock = halfmoveClock;
        this.fullmoveNumber = fullmoveNumber;
    }

    /** Standard chess starting position. */
    public static ChessPosition starting() {
        return fromFen(STARTING_FEN);
    }

    /** Parses a FEN string into a position. */
    public static ChessPosition fromFen(String fen) {
        if (fen == null) {
            throw new IllegalArgumentException("FEN must not be null");
        }
        String[] parts = fen.trim().split("\\s+");
        if (parts.length < 4) {
            throw new IllegalArgumentException("FEN needs at least 4 fields: " + fen);
        }
        Piece[] board = new Piece[BoardState.SQUARE_COUNT];
        String[] ranks = parts[0].split("/");
        if (ranks.length != 8) {
            throw new IllegalArgumentException("FEN board must have 8 ranks: " + fen);
        }
        for (int row = 0; row < 8; row++) {
            int col = 0;
            for (char c : ranks[row].toCharArray()) {
                if (c >= '1' && c <= '8') {
                    col += c - '0';
                } else {
                    if (col > 7) {
                        throw new IllegalArgumentException("FEN rank overflow: " + fen);
                    }
                    board[row * 8 + col] = Piece.fromFenChar(c);
                    col++;
                }
            }
            if (col != 8) {
                throw new IllegalArgumentException("FEN rank has wrong width: " + fen);
            }
        }
        PieceColor side;
        if ("w".equals(parts[1])) {
            side = PieceColor.WHITE;
        } else if ("b".equals(parts[1])) {
            side = PieceColor.BLACK;
        } else {
            throw new IllegalArgumentException("Invalid side to move: " + fen);
        }
        int rights = 0;
        if (!"-".equals(parts[2])) {
            for (char c : parts[2].toCharArray()) {
                switch (c) {
                    case 'K':
                        rights |= WHITE_KINGSIDE;
                        break;
                    case 'Q':
                        rights |= WHITE_QUEENSIDE;
                        break;
                    case 'k':
                        rights |= BLACK_KINGSIDE;
                        break;
                    case 'q':
                        rights |= BLACK_QUEENSIDE;
                        break;
                    default:
                        throw new IllegalArgumentException("Invalid castling field: " + fen);
                }
            }
        }
        int ep = "-".equals(parts[3]) ? NO_SQUARE : BoardState.squareIndex(parts[3]);
        int halfmove = parts.length > 4 ? Integer.parseInt(parts[4]) : 0;
        int fullmove = parts.length > 5 ? Integer.parseInt(parts[5]) : 1;
        return new ChessPosition(board, side, rights, ep, halfmove, fullmove);
    }

    /** Exports this position as a FEN string. */
    public String toFen() {
        StringBuilder sb = new StringBuilder(64);
        for (int row = 0; row < 8; row++) {
            int empty = 0;
            for (int col = 0; col < 8; col++) {
                Piece piece = board[row * 8 + col];
                if (piece == null) {
                    empty++;
                } else {
                    if (empty > 0) {
                        sb.append(empty);
                        empty = 0;
                    }
                    sb.append(piece.fenChar());
                }
            }
            if (empty > 0) {
                sb.append(empty);
            }
            if (row != 7) {
                sb.append('/');
            }
        }
        sb.append(sideToMove == PieceColor.WHITE ? " w " : " b ");
        if (castlingRights == 0) {
            sb.append('-');
        } else {
            if ((castlingRights & WHITE_KINGSIDE) != 0) sb.append('K');
            if ((castlingRights & WHITE_QUEENSIDE) != 0) sb.append('Q');
            if ((castlingRights & BLACK_KINGSIDE) != 0) sb.append('k');
            if ((castlingRights & BLACK_QUEENSIDE) != 0) sb.append('q');
        }
        sb.append(' ');
        sb.append(enPassantSquare == NO_SQUARE ? "-" : BoardState.squareName(enPassantSquare));
        sb.append(' ').append(halfmoveClock).append(' ').append(fullmoveNumber);
        return sb.toString();
    }

    /** Piece on the given square (0 = a8 … 63 = h1) or {@code null}. */
    public Piece pieceAt(int square) {
        return board[square];
    }

    public PieceColor sideToMove() {
        return sideToMove;
    }

    /** En passant target square index or -1 if none. */
    public int enPassantSquare() {
        return enPassantSquare;
    }

    public int halfmoveClock() {
        return halfmoveClock;
    }

    public int fullmoveNumber() {
        return fullmoveNumber;
    }

    /** True if the side to move is currently in check. */
    public boolean inCheck() {
        return isAttacked(kingSquare(board, sideToMove), sideToMove.opposite(), board);
    }

    /** All strictly legal moves for the side to move. */
    public List<Move> legalMoves() {
        List<Move> pseudo = pseudoLegalMoves();
        List<Move> legal = new ArrayList<>(pseudo.size());
        for (Move move : pseudo) {
            Piece[] next = makeMoveOnBoard(move);
            if (!isAttacked(kingSquare(next, sideToMove), sideToMove.opposite(), next)) {
                legal.add(move);
            }
        }
        return legal;
    }

    /**
     * Applies a legal move and returns the resulting position. Throws {@link
     * IllegalArgumentException} if the move is not legal here.
     */
    public ChessPosition apply(Move move) {
        if (!legalMoves().contains(move)) {
            throw new IllegalArgumentException(
                    "Illegal move " + move.uci() + " in position " + toFen());
        }
        Piece moving = board[move.from()];
        boolean isPawnMove = moving.type() == PieceType.PAWN;
        boolean isCapture =
                board[move.to()] != null || (isPawnMove && move.to() == enPassantSquare);
        Piece[] next = makeMoveOnBoard(move);

        int rights = castlingRights;
        if (moving.type() == PieceType.KING) {
            rights &=
                    moving.color() == PieceColor.WHITE
                            ? ~(WHITE_KINGSIDE | WHITE_QUEENSIDE)
                            : ~(BLACK_KINGSIDE | BLACK_QUEENSIDE);
        }
        rights = clearRookRights(rights, move.from());
        rights = clearRookRights(rights, move.to());

        int ep = NO_SQUARE;
        if (isPawnMove && Math.abs(move.from() - move.to()) == 16) {
            ep = (move.from() + move.to()) / 2;
        }
        int halfmove = (isPawnMove || isCapture) ? 0 : halfmoveClock + 1;
        int fullmove = sideToMove == PieceColor.BLACK ? fullmoveNumber + 1 : fullmoveNumber;
        return new ChessPosition(next, sideToMove.opposite(), rights, ep, halfmove, fullmove);
    }

    // ---------------------------------------------------------------- rules

    private static int clearRookRights(int rights, int square) {
        switch (square) {
            case H1:
                return rights & ~WHITE_KINGSIDE;
            case A1:
                return rights & ~WHITE_QUEENSIDE;
            case H8:
                return rights & ~BLACK_KINGSIDE;
            case A8:
                return rights & ~BLACK_QUEENSIDE;
            default:
                return rights;
        }
    }

    /** Executes the move on a board copy (castling rook + ep capture included). */
    private Piece[] makeMoveOnBoard(Move move) {
        Piece[] next = board.clone();
        Piece moving = next[move.from()];
        next[move.from()] = null;
        if (moving.type() == PieceType.PAWN
                && move.to() == enPassantSquare
                && next[move.to()] == null) {
            // En passant: captured pawn sits "behind" the target square.
            int captured = moving.color() == PieceColor.WHITE ? move.to() + 8 : move.to() - 8;
            next[captured] = null;
        }
        next[move.to()] =
                move.promotion() == null ? moving : Piece.of(moving.color(), move.promotion());
        if (moving.type() == PieceType.KING && Math.abs(move.from() - move.to()) == 2) {
            // Castling: also move the rook.
            if (move.to() == move.from() + 2) { // kingside
                next[move.from() + 1] = next[move.from() + 3];
                next[move.from() + 3] = null;
            } else { // queenside
                next[move.from() - 1] = next[move.from() - 4];
                next[move.from() - 4] = null;
            }
        }
        return next;
    }

    private List<Move> pseudoLegalMoves() {
        List<Move> moves = new ArrayList<>(48);
        for (int square = 0; square < BoardState.SQUARE_COUNT; square++) {
            Piece piece = board[square];
            if (piece == null || piece.color() != sideToMove) {
                continue;
            }
            switch (piece.type()) {
                case PAWN:
                    addPawnMoves(moves, square, piece.color());
                    break;
                case KNIGHT:
                    addStepMoves(moves, square, KNIGHT_OFFSETS);
                    break;
                case BISHOP:
                    addSlidingMoves(moves, square, BISHOP_DIRECTIONS);
                    break;
                case ROOK:
                    addSlidingMoves(moves, square, ROOK_DIRECTIONS);
                    break;
                case QUEEN:
                    addSlidingMoves(moves, square, ROOK_DIRECTIONS);
                    addSlidingMoves(moves, square, BISHOP_DIRECTIONS);
                    break;
                case KING:
                    addStepMoves(moves, square, KING_OFFSETS);
                    addCastlingMoves(moves, piece.color());
                    break;
                default:
                    break;
            }
        }
        return moves;
    }

    private void addPawnMoves(List<Move> moves, int from, PieceColor color) {
        int direction = color == PieceColor.WHITE ? -8 : 8;
        int startRow = color == PieceColor.WHITE ? 6 : 1;
        int promotionRow = color == PieceColor.WHITE ? 0 : 7;
        int row = from / 8;
        int col = from % 8;
        int oneAhead = from + direction;
        if (board[oneAhead] == null) {
            addPawnMove(moves, from, oneAhead, oneAhead / 8 == promotionRow);
            int twoAhead = from + 2 * direction;
            if (row == startRow && board[twoAhead] == null) {
                moves.add(new Move(from, twoAhead));
            }
        }
        for (int dc = -1; dc <= 1; dc += 2) {
            int targetCol = col + dc;
            if (targetCol < 0 || targetCol > 7) {
                continue;
            }
            int target = oneAhead + dc;
            Piece victim = board[target];
            if (victim != null && victim.color() != color) {
                addPawnMove(moves, from, target, target / 8 == promotionRow);
            } else if (victim == null && target == enPassantSquare) {
                moves.add(new Move(from, target));
            }
        }
    }

    private static void addPawnMove(List<Move> moves, int from, int to, boolean promotion) {
        if (promotion) {
            moves.add(new Move(from, to, PieceType.QUEEN));
            moves.add(new Move(from, to, PieceType.ROOK));
            moves.add(new Move(from, to, PieceType.BISHOP));
            moves.add(new Move(from, to, PieceType.KNIGHT));
        } else {
            moves.add(new Move(from, to));
        }
    }

    private void addStepMoves(List<Move> moves, int from, int[][] offsets) {
        int row = from / 8;
        int col = from % 8;
        for (int[] offset : offsets) {
            int r = row + offset[0];
            int c = col + offset[1];
            if (r < 0 || r > 7 || c < 0 || c > 7) {
                continue;
            }
            int to = r * 8 + c;
            Piece target = board[to];
            if (target == null || target.color() != sideToMove) {
                moves.add(new Move(from, to));
            }
        }
    }

    private void addSlidingMoves(List<Move> moves, int from, int[][] directions) {
        int row = from / 8;
        int col = from % 8;
        for (int[] direction : directions) {
            int r = row + direction[0];
            int c = col + direction[1];
            while (r >= 0 && r <= 7 && c >= 0 && c <= 7) {
                int to = r * 8 + c;
                Piece target = board[to];
                if (target == null) {
                    moves.add(new Move(from, to));
                } else {
                    if (target.color() != sideToMove) {
                        moves.add(new Move(from, to));
                    }
                    break;
                }
                r += direction[0];
                c += direction[1];
            }
        }
    }

    private void addCastlingMoves(List<Move> moves, PieceColor color) {
        PieceColor enemy = color.opposite();
        int king = color == PieceColor.WHITE ? E1 : E8;
        if (board[king] == null
                || board[king].type() != PieceType.KING
                || isAttacked(king, enemy, board)) {
            return;
        }
        int kingside = color == PieceColor.WHITE ? WHITE_KINGSIDE : BLACK_KINGSIDE;
        int queenside = color == PieceColor.WHITE ? WHITE_QUEENSIDE : BLACK_QUEENSIDE;
        Piece rook = Piece.of(color, PieceType.ROOK);
        if ((castlingRights & kingside) != 0
                && rook == board[king + 3]
                && board[king + 1] == null
                && board[king + 2] == null
                && !isAttacked(king + 1, enemy, board)
                && !isAttacked(king + 2, enemy, board)) {
            moves.add(new Move(king, king + 2));
        }
        if ((castlingRights & queenside) != 0
                && rook == board[king - 4]
                && board[king - 1] == null
                && board[king - 2] == null
                && board[king - 3] == null
                && !isAttacked(king - 1, enemy, board)
                && !isAttacked(king - 2, enemy, board)) {
            moves.add(new Move(king, king - 2));
        }
    }

    private static int kingSquare(Piece[] board, PieceColor color) {
        Piece king = Piece.of(color, PieceType.KING);
        for (int square = 0; square < board.length; square++) {
            if (board[square] == king) {
                return square;
            }
        }
        throw new IllegalStateException("No " + color + " king on board");
    }

    /** True if {@code square} is attacked by any piece of {@code by}. */
    private static boolean isAttacked(int square, PieceColor by, Piece[] board) {
        int row = square / 8;
        int col = square % 8;
        // Pawns: a white pawn attacks "upwards" (towards row 0).
        int pawnRow = by == PieceColor.WHITE ? row + 1 : row - 1;
        if (pawnRow >= 0 && pawnRow <= 7) {
            for (int dc = -1; dc <= 1; dc += 2) {
                int c = col + dc;
                if (c >= 0 && c <= 7 && board[pawnRow * 8 + c] == Piece.of(by, PieceType.PAWN)) {
                    return true;
                }
            }
        }
        for (int[] offset : KNIGHT_OFFSETS) {
            int r = row + offset[0];
            int c = col + offset[1];
            if (r >= 0
                    && r <= 7
                    && c >= 0
                    && c <= 7
                    && board[r * 8 + c] == Piece.of(by, PieceType.KNIGHT)) {
                return true;
            }
        }
        for (int[] offset : KING_OFFSETS) {
            int r = row + offset[0];
            int c = col + offset[1];
            if (r >= 0
                    && r <= 7
                    && c >= 0
                    && c <= 7
                    && board[r * 8 + c] == Piece.of(by, PieceType.KING)) {
                return true;
            }
        }
        if (attackedBySlider(board, row, col, by, ROOK_DIRECTIONS, PieceType.ROOK)) {
            return true;
        }
        return attackedBySlider(board, row, col, by, BISHOP_DIRECTIONS, PieceType.BISHOP);
    }

    private static boolean attackedBySlider(
            Piece[] board,
            int row,
            int col,
            PieceColor by,
            int[][] directions,
            PieceType sliderType) {
        for (int[] direction : directions) {
            int r = row + direction[0];
            int c = col + direction[1];
            while (r >= 0 && r <= 7 && c >= 0 && c <= 7) {
                Piece piece = board[r * 8 + c];
                if (piece != null) {
                    if (piece.color() == by
                            && (piece.type() == sliderType || piece.type() == PieceType.QUEEN)) {
                        return true;
                    }
                    break;
                }
                r += direction[0];
                c += direction[1];
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return toFen();
    }
}
