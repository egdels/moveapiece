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
 * undo/redo, and game-end state.
 *
 * <p>Undo/redo is a plain two-stack model: {@link #undoLastMove()} moves the last played move onto
 * a redo stack, {@link #redoMove()} moves it back. Playing any new move (by either {@link
 * #applyMove} or {@link #applyUciMove}) discards the redo stack, same as in any editor - once a
 * different move branches off from an earlier point, the old "future" is no longer reachable.
 */
public class ChessGame {

    private static final String START_FEN =
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    private final Board board = new Board();
    private final List<Move> moveHistory = new ArrayList<>();
    private final List<Move> redoHistory = new ArrayList<>();
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
                    redoHistory.clear();
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
            redoHistory.clear();
            return true;
        }
        return false;
    }

    /** Undoes the last move, e.g. moving it to the redo stack; false if there is nothing to undo. */
    public boolean undoLastMove() {
        if (moveHistory.isEmpty()) {
            return false;
        }
        board.undoMove();
        redoHistory.add(moveHistory.remove(moveHistory.size() - 1));
        return true;
    }

    /** Reapplies the most recently undone move; false if there is nothing to redo. */
    public boolean redoMove() {
        if (redoHistory.isEmpty()) {
            return false;
        }
        Move move = redoHistory.remove(redoHistory.size() - 1);
        board.doMove(move);
        moveHistory.add(move);
        return true;
    }

    /** Whether {@link #redoMove()} has a move to reapply. */
    public boolean canRedo() {
        return !redoHistory.isEmpty();
    }

    /**
     * Jumps to the position right after {@code targetPly} moves from the start (0 = starting
     * position), via repeated {@link #undoLastMove()}/{@link #redoMove()} - same two stacks, no
     * rebuild from scratch, so this is exactly what clicking a move in the history and landing on
     * it does. Clamped to what undo/redo can actually reach (negative or beyond the redo stack);
     * callers compare the returned ply against {@code targetPly} to detect that.
     *
     * @return the ply actually reached, i.e. the new {@link #moveCount()}
     */
    public int jumpToPly(int targetPly) {
        while (moveCount() > targetPly && undoLastMove()) {
            // continue
        }
        while (moveCount() < targetPly && redoMove()) {
            // continue
        }
        return moveCount();
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
        return uciOf(moveHistory);
    }

    /**
     * Like {@link #toUciMoveList()}, but includes still-redoable future moves too - see {@link
     * #toFullSan()}.
     */
    public String toFullUciMoveList() {
        return uciOf(fullMoveList());
    }

    private String uciOf(List<Move> moves) {
        StringBuilder sb = new StringBuilder();
        for (Move m : moves) {
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
        redoHistory.clear();
    }

    public int moveCount() {
        return moveHistory.size();
    }

    /**
     * Total plies in the currently known line: played ({@link #moveCount()}) plus still-redoable.
     * Unlike {@link #moveCount()}, this doesn't shrink when {@link #undoLastMove()} is called - see
     * {@link #toFullSan()}.
     */
    public int totalPlyCount() {
        return moveHistory.size() + redoHistory.size();
    }

    /**
     * Every move in the currently known line, played plus still-redoable, in chronological order -
     * independent of the undo/redo cursor position. {@code redoHistory} itself is LIFO (most
     * recently undone move last), so it's walked back-to-front to restore chronological order.
     */
    private List<Move> fullMoveList() {
        List<Move> all = new ArrayList<>(moveHistory);
        for (int i = redoHistory.size() - 1; i >= 0; i--) {
            all.add(redoHistory.get(i));
        }
        return all;
    }

    /** Current position as a FEN string (full 6-field form). */
    public String toFen() {
        return board.getFen();
    }

    /** Move history in Short Algebraic Notation with move numbers, e.g. "1. e4 e5 2. Nf3". */
    public String toSan() {
        return sanOf(moveHistory);
    }

    /**
     * Like {@link #toSan()}, but includes still-redoable future moves too (see {@link
     * #fullMoveList()}) - for a move-list UI that lets the user navigate the whole known line
     * (including plies that {@link #undoLastMove()} stepped back past) without moves disappearing
     * as soon as they step back once.
     */
    public String toFullSan() {
        return sanOf(fullMoveList());
    }

    private String sanOf(List<Move> moves) {
        if (moves.isEmpty()) {
            return "";
        }
        MoveList moveList = new MoveList(startFen);
        moveList.addAll(moves);
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
