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
 * <p>Castling follows the Chess960 rules, of which standard chess is the special case with the king
 * on e1/e8 and the rooks in the corners: a castling right names its rook's square, and the king
 * ends on the g- or c-file with the rook next to it on the inside. In a Chess960 position ({@link
 * #isChess960()}) a castle move is written as the king moving onto its own rook ("b1a1", the UCI
 * Chess960 convention also used by chesslib and Stockfish) and FEN castling rights name the rook
 * files (Shredder-FEN, "HAha"); otherwise castling is the king's two-square move ("e1g1") and the
 * rights are "KQkq".
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

    // Indices into castlingRooks.
    private static final int WHITE_KINGSIDE = 0;
    private static final int WHITE_QUEENSIDE = 1;
    private static final int BLACK_KINGSIDE = 2;
    private static final int BLACK_QUEENSIDE = 3;

    private static final int E_FILE = 4;

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
    // Square of the rook each castling right belongs to, or NO_SQUARE without that right; indexed
    // by WHITE_KINGSIDE ... BLACK_QUEENSIDE.
    private final int[] castlingRooks;
    private final boolean chess960;
    private final int enPassantSquare;
    private final int halfmoveClock;
    private final int fullmoveNumber;

    private ChessPosition(
            Piece[] board,
            PieceColor sideToMove,
            int[] castlingRooks,
            boolean chess960,
            int enPassantSquare,
            int halfmoveClock,
            int fullmoveNumber) {
        this.board = board;
        this.sideToMove = sideToMove;
        this.castlingRooks = castlingRooks;
        this.chess960 = chess960;
        this.enPassantSquare = enPassantSquare;
        this.halfmoveClock = halfmoveClock;
        this.fullmoveNumber = fullmoveNumber;
    }

    /** Standard chess starting position. */
    public static ChessPosition starting() {
        return fromFen(STARTING_FEN);
    }

    /**
     * Parses a FEN string into a position. It is a Chess960 position ({@link #isChess960()}) if the
     * castling rights say so: rook files (Shredder-FEN), a king off the e-file, or an X-FEN "K"/"Q"
     * whose rook is not in the corner.
     */
    public static ChessPosition fromFen(String fen) {
        return fromFen(fen, false);
    }

    /**
     * Like {@link #fromFen(String)}; {@code chess960} makes it a Chess960 position whatever the
     * castling rights look like (e.g. Chess960 starting position 518, the standard array).
     */
    public static ChessPosition fromFen(String fen, boolean chess960) {
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
        int[] rooks = {NO_SQUARE, NO_SQUARE, NO_SQUARE, NO_SQUARE};
        boolean nonStandard = chess960;
        if (!"-".equals(parts[2])) {
            for (char c : parts[2].toCharArray()) {
                PieceColor color = Character.isUpperCase(c) ? PieceColor.WHITE : PieceColor.BLACK;
                int row = color == PieceColor.WHITE ? 7 : 0;
                int kingFile = backRankKingFile(board, color);
                char letter = Character.toUpperCase(c);
                int rookFile;
                if (letter == 'K') {
                    rookFile = outermostRookFile(board, color, kingFile, true);
                } else if (letter == 'Q') {
                    rookFile = outermostRookFile(board, color, kingFile, false);
                } else if (letter >= 'A' && letter <= 'H') {
                    rookFile = letter - 'A';
                    nonStandard = true;
                } else {
                    throw new IllegalArgumentException("Invalid castling field: " + fen);
                }
                if (kingFile < 0
                        || rookFile < 0
                        || rookFile == kingFile
                        || board[row * 8 + rookFile] != Piece.of(color, PieceType.ROOK)) {
                    continue; // a right without its king and rook in place cannot be used
                }
                boolean kingside = rookFile > kingFile;
                rooks[rightIndex(color, kingside)] = row * 8 + rookFile;
                if (kingFile != E_FILE || rookFile != (kingside ? 7 : 0)) {
                    nonStandard = true;
                }
            }
        }
        int ep = "-".equals(parts[3]) ? NO_SQUARE : BoardState.squareIndex(parts[3]);
        int halfmove = parts.length > 4 ? Integer.parseInt(parts[4]) : 0;
        int fullmove = parts.length > 5 ? Integer.parseInt(parts[5]) : 1;
        return new ChessPosition(board, side, rooks, nonStandard, ep, halfmove, fullmove);
    }

    private static int rightIndex(PieceColor color, boolean kingside) {
        if (color == PieceColor.WHITE) {
            return kingside ? WHITE_KINGSIDE : WHITE_QUEENSIDE;
        }
        return kingside ? BLACK_KINGSIDE : BLACK_QUEENSIDE;
    }

    /** File of {@code color}'s king if it stands on its back rank, else -1. */
    private static int backRankKingFile(Piece[] board, PieceColor color) {
        int row = color == PieceColor.WHITE ? 7 : 0;
        for (int file = 0; file < 8; file++) {
            if (board[row * 8 + file] == Piece.of(color, PieceType.KING)) {
                return file;
            }
        }
        return -1;
    }

    /** X-FEN "K"/"Q": the outermost rook on that side of the king, or -1. */
    private static int outermostRookFile(
            Piece[] board, PieceColor color, int kingFile, boolean kingside) {
        if (kingFile < 0) {
            return -1;
        }
        int row = color == PieceColor.WHITE ? 7 : 0;
        Piece rook = Piece.of(color, PieceType.ROOK);
        if (kingside) {
            for (int file = 7; file > kingFile; file--) {
                if (board[row * 8 + file] == rook) {
                    return file;
                }
            }
        } else {
            for (int file = 0; file < kingFile; file++) {
                if (board[row * 8 + file] == rook) {
                    return file;
                }
            }
        }
        return -1;
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
        int rightsStart = sb.length();
        String standardLetters = "KQkq";
        for (int i = 0; i < castlingRooks.length; i++) {
            if (castlingRooks[i] != NO_SQUARE) {
                char file = (char) ('A' + castlingRooks[i] % 8);
                char letter = chess960 ? file : standardLetters.charAt(i);
                sb.append(i < BLACK_KINGSIDE ? letter : Character.toLowerCase(letter));
            }
        }
        if (sb.length() == rightsStart) {
            sb.append('-');
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

    /**
     * Whether castling is written the Chess960 way: the king moving onto its own rook, and rook
     * files as FEN castling rights. See the class comment.
     */
    public boolean isChess960() {
        return chess960;
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
     * The squares a player may put the piece on {@code from} down on, from {@link #legalMoves()}. A
     * castle counts with the square the king ends up on (g- or c-file), not with the rook's square
     * a Chess960 castle is written with; a castle that leaves the king where it stands adds no
     * square, since only the rook moves.
     */
    public List<Integer> destinationsFrom(int from) {
        List<Integer> destinations = new ArrayList<>();
        for (Move move : legalMoves()) {
            if (move.from() != from) {
                continue;
            }
            int right = castlingRightOf(move);
            int to = right >= 0 ? kingDestination(right, from / 8) : move.to();
            if (to != from && !destinations.contains(to)) {
                destinations.add(to);
            }
        }
        return destinations;
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
        // A Chess960 castle moves the king onto its own rook, which captures nothing.
        boolean isCapture =
                (board[move.to()] != null && castlingRightOf(move) < 0)
                        || (isPawnMove && move.to() == enPassantSquare);
        Piece[] next = makeMoveOnBoard(move);

        int[] rooks = castlingRooks.clone();
        for (int i = 0; i < rooks.length; i++) {
            boolean ownKing =
                    moving.type() == PieceType.KING
                            && (i < BLACK_KINGSIDE) == (moving.color() == PieceColor.WHITE);
            // A moved king, or a rook leaving or captured on its square, ends that right.
            if (ownKing || rooks[i] == move.from() || rooks[i] == move.to()) {
                rooks[i] = NO_SQUARE;
            }
        }

        int ep = NO_SQUARE;
        if (isPawnMove && Math.abs(move.from() - move.to()) == 16) {
            ep = (move.from() + move.to()) / 2;
        }
        int halfmove = (isPawnMove || isCapture) ? 0 : halfmoveClock + 1;
        int fullmove = sideToMove == PieceColor.BLACK ? fullmoveNumber + 1 : fullmoveNumber;
        return new ChessPosition(
                next, sideToMove.opposite(), rooks, chess960, ep, halfmove, fullmove);
    }

    // ---------------------------------------------------------------- rules

    /**
     * The castling right {@code move} uses, or -1 if it is no castle move: in Chess960 the king
     * moving onto its own castling rook, otherwise the king's two-square move along its rank.
     */
    private int castlingRightOf(Move move) {
        Piece moving = board[move.from()];
        if (moving == null || moving.type() != PieceType.KING) {
            return -1;
        }
        boolean kingside = move.to() > move.from();
        int right = rightIndex(moving.color(), kingside);
        if (chess960) {
            return castlingRooks[right] == move.to() ? right : -1;
        }
        boolean twoSquares =
                move.from() / 8 == move.to() / 8 && Math.abs(move.from() - move.to()) == 2;
        return twoSquares && castlingRooks[right] != NO_SQUARE ? right : -1;
    }

    private static boolean isKingside(int right) {
        return right == WHITE_KINGSIDE || right == BLACK_KINGSIDE;
    }

    /** Where the king ends up when castling with {@code right}: the g- or c-file. */
    private static int kingDestination(int right, int row) {
        return row * 8 + (isKingside(right) ? 6 : 2);
    }

    /** Where the rook ends up when castling with {@code right}: the f- or d-file. */
    private static int rookDestination(int right, int row) {
        return row * 8 + (isKingside(right) ? 5 : 3);
    }

    /** Executes the move on a board copy (castling rook + ep capture included). */
    private Piece[] makeMoveOnBoard(Move move) {
        Piece[] next = board.clone();
        Piece moving = next[move.from()];
        int castlingRight = castlingRightOf(move);
        if (castlingRight >= 0) {
            int row = move.from() / 8;
            int rookFrom = castlingRooks[castlingRight];
            Piece rook = next[rookFrom];
            next[move.from()] = null;
            next[rookFrom] = null;
            next[kingDestination(castlingRight, row)] = moving;
            next[rookDestination(castlingRight, row)] = rook;
            return next;
        }
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

    /**
     * Castling, by the Chess960 rules: the king is not in check; every square between king and its
     * destination and between rook and its destination is empty apart from king and rook
     * themselves; and no square the king crosses or lands on is attacked once king and rook have
     * left their squares (the castling rook may otherwise shield the king's destination from a rook
     * or queen behind it).
     */
    private void addCastlingMoves(List<Move> moves, PieceColor color) {
        PieceColor enemy = color.opposite();
        int row = color == PieceColor.WHITE ? 7 : 0;
        int kingFile = backRankKingFile(board, color);
        if (kingFile < 0) {
            return;
        }
        int king = row * 8 + kingFile;
        if (isAttacked(king, enemy, board)) {
            return;
        }
        Piece rook = Piece.of(color, PieceType.ROOK);
        for (boolean kingside : new boolean[] {true, false}) {
            int right = rightIndex(color, kingside);
            int rookFrom = castlingRooks[right];
            if (rookFrom == NO_SQUARE || board[rookFrom] != rook) {
                continue;
            }
            int kingTo = kingDestination(right, row);
            int rookTo = rookDestination(right, row);
            if (!emptyBetween(row, king, kingTo, king, rookFrom)
                    || !emptyBetween(row, rookFrom, rookTo, king, rookFrom)) {
                continue;
            }
            Piece[] without = board.clone();
            without[king] = null;
            without[rookFrom] = null;
            without[rookTo] = rook;
            // Every square from the one next to the king up to its destination; none if the king
            // already stands there (its own square was checked above).
            boolean safe = true;
            for (int square = king; safe && square != kingTo; ) {
                square += Integer.signum(kingTo - square);
                safe = !isAttacked(square, enemy, without);
            }
            if (safe) {
                moves.add(new Move(king, chess960 ? rookFrom : kingTo));
            }
        }
    }

    /** Whether all squares from {@code a} to {@code b} (inclusive) are empty or king or rook. */
    private boolean emptyBetween(int row, int a, int b, int king, int rookFrom) {
        for (int square = Math.min(a, b); square <= Math.max(a, b); square++) {
            if (square / 8 == row
                    && square != king
                    && square != rookFrom
                    && board[square] != null) {
                return false;
            }
        }
        return true;
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
