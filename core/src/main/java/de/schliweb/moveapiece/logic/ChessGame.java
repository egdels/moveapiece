/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.logic;

import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.Square;
import com.github.bhlangonijr.chesslib.game.Game;
import com.github.bhlangonijr.chesslib.move.Move;
import com.github.bhlangonijr.chesslib.move.MoveConversionException;
import com.github.bhlangonijr.chesslib.move.MoveList;
import com.github.bhlangonijr.chesslib.pgn.PgnHolder;
import com.github.bhlangonijr.chesslib.pgn.PgnIterator;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * Wraps a chesslib {@link Board} with the subset of operations the UI and the engine bridge need:
 * applying moves by square (with promotion choice), applying engine moves given as raw UCI strings,
 * undo, and game-end state.
 */
public class ChessGame {

    private static final String START_FEN =
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    private final Board board = new Board();
    private final List<Move> moveHistory = new ArrayList<>();
    private String startFen = START_FEN;

    public Piece pieceAt(Square square) {
        return board.getPiece(square);
    }

    public Side sideToMove() {
        return board.getSideToMove();
    }

    public List<Square> legalDestinationsFrom(Square from) {
        List<Square> result = new ArrayList<>();
        for (Move m : board.legalMoves()) {
            if (m.getFrom() == from && !result.contains(m.getTo())) {
                result.add(m.getTo());
            }
        }
        return result;
    }

    public boolean isPromotion(Square from, Square to) {
        for (Move m : board.legalMoves()) {
            if (m.getFrom() == from && m.getTo() == to && m.getPromotion() != Piece.NONE) {
                return true;
            }
        }
        return false;
    }

    /**
     * Applies a move chosen by square, resolving to the matching legal move.
     *
     * @param promotion desired promotion piece, or {@code null} if the move is not a promotion
     * @return true if a matching legal move was found and applied
     */
    public boolean applyMove(Square from, Square to, Piece promotion) {
        Piece wantedPromotion = promotion == null ? Piece.NONE : promotion;
        for (Move m : board.legalMoves()) {
            if (m.getFrom() == from && m.getTo() == to && m.getPromotion() == wantedPromotion) {
                if (board.doMove(m)) {
                    moveHistory.add(m);
                    return true;
                }
                return false;
            }
        }
        return false;
    }

    /** Applies a move given in UCI notation (e.g. "e2e4", "e7e8q"), as returned by the engine. */
    public boolean applyUciMove(String uciMove) {
        if (uciMove == null || uciMove.length() < 4) {
            return false;
        }
        Move move = new Move(uciMove, board.getSideToMove());
        // Full validation, not the default doMove(move): the origin square may
        // not hold a piece at all (e.g. a physical-board move confirmed against
        // a position the board has since drifted from) - chesslib's own
        // isMoveLegal() only null-checks that under full validation, otherwise
        // NPEs instead of returning false.
        if (board.doMove(move, true)) {
            moveHistory.add(move);
            return true;
        }
        return false;
    }

    public boolean undoLastMove() {
        if (moveHistory.isEmpty()) {
            return false;
        }
        board.undoMove();
        moveHistory.remove(moveHistory.size() - 1);
        return true;
    }

    public boolean isCheckmate() {
        return board.isMated();
    }

    public boolean isStalemate() {
        return board.isStaleMate();
    }

    public boolean isDraw() {
        return board.isDraw();
    }

    public boolean isCheck() {
        return board.isKingAttacked();
    }

    public boolean isGameOver() {
        return isCheckmate() || isStalemate() || isDraw();
    }

    /** Space-separated UCI moves from the start position, for "position startpos moves ...". */
    public String toUciMoveList() {
        StringBuilder sb = new StringBuilder();
        for (Move m : moveHistory) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(m.toString());
        }
        return sb.toString();
    }

    public void reset() {
        loadFen(START_FEN);
    }

    /** Sets up an arbitrary position, clearing move history. Mainly for tests. */
    public void loadFen(String fen) {
        board.loadFromFen(fen);
        startFen = fen;
        moveHistory.clear();
    }

    public int moveCount() {
        return moveHistory.size();
    }

    /** Current position as a FEN string (full 6-field form). */
    public String toFen() {
        return board.getFen();
    }

    /** Move history in Short Algebraic Notation with move numbers, e.g. "1. e4 e5 2. Nf3". */
    public String toSan() {
        if (moveHistory.isEmpty()) {
            return "";
        }
        MoveList moveList = new MoveList(startFen);
        moveList.addAll(moveHistory);
        try {
            return moveList.toSanWithMoveNumbers().trim();
        } catch (MoveConversionException e) {
            return "";
        }
    }

    /**
     * Full PGN text (Seven Tag Roster header + movetext) for the game so far. {@code
     * whiteName}/{@code blackName} are supplied by the caller, since this class has no notion of
     * game mode or opponent strength.
     */
    public String toPgn(String whiteName, String blackName) {
        String date = new SimpleDateFormat("yyyy.MM.dd", Locale.ROOT).format(new Date());
        String result = pgnResult();
        StringBuilder sb = new StringBuilder();
        sb.append("[Event \"MoveAPiece-Partie\"]\n");
        sb.append("[Site \"?\"]\n");
        sb.append("[Date \"").append(date).append("\"]\n");
        sb.append("[Round \"?\"]\n");
        sb.append("[White \"").append(whiteName).append("\"]\n");
        sb.append("[Black \"").append(blackName).append("\"]\n");
        sb.append("[Result \"").append(result).append("\"]\n");
        sb.append('\n');
        String movetext = toSan();
        sb.append(movetext.isEmpty() ? result : movetext + " " + result);
        sb.append('\n');
        return sb.toString();
    }

    private String pgnResult() {
        if (isCheckmate()) {
            return sideToMove() == Side.BLACK ? "1-0" : "0-1";
        }
        if (isStalemate() || isDraw()) {
            return "1/2-1/2";
        }
        return "*";
    }

    /**
     * Replaces the current game with the first game found in {@code pgnText}. On any failure
     * (unparsable PGN, no games found, no half-moves parsed - chesslib's PGN parser is lenient
     * enough to "succeed" with zero moves on arbitrary non-PGN text, which would otherwise silently
     * reset the game with no error - or a half-move that doesn't apply) this is left at the start
     * position, as if {@link #reset()} had been called, and {@code false} is returned - never a
     * half-imported game.
     *
     * <p>Uses {@link PgnIterator} directly rather than {@link PgnHolder}: {@code PgnHolder.loadPgn}
     * eagerly parses every game in the text into memory even though only the first is ever used
     * here, which turns importing a large multi-game PGN (a tournament or opening database) into a
     * multi-second UI freeze. {@link PgnIterator} parses lazily, so taking just the first game
     * leaves the rest of the file untouched.
     */
    public boolean loadPgn(String pgnText) {
        Game pgnGame;
        try {
            // PgnIterator's constructor itself eagerly parses ahead to the
            // first game (see PgnIterator#loadNextGame), so malformed leading
            // content (e.g. a stray byte before the first "[Event") throws
            // from here, not from hasNext()/next() below - must be inside
            // this try too, or it escapes uncaught as a PgnException.
            Iterator<Game> games =
                    new PgnIterator(Arrays.asList(pgnText.split("\n")).iterator()).iterator();
            if (!games.hasNext()) {
                reset();
                return false;
            }
            pgnGame = games.next();
        } catch (RuntimeException e) {
            reset();
            return false;
        }
        MoveList halfMoves;
        try {
            halfMoves = pgnGame.getHalfMoves();
        } catch (RuntimeException e) {
            reset();
            return false;
        }
        if (halfMoves.isEmpty()) {
            reset();
            return false;
        }
        reset();
        for (Move move : halfMoves) {
            if (!applyUciMove(move.toString())) {
                reset();
                return false;
            }
        }
        return true;
    }
}
