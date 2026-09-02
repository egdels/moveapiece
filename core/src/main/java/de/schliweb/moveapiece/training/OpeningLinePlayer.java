/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.training;

import com.github.bhlangonijr.chesslib.Square;
import de.schliweb.moveapiece.logic.ChessGame;
import java.util.Locale;

/**
 * Steps forward/backward through a fixed {@link OpeningLine}, replaying moves on a fresh {@link
 * ChessGame} for the current ply so callers can render the resulting position. Read-only: never
 * mutates the line itself, and has no notion of a live game (unlike {@link TrainingSession}, this
 * is for the standalone opening library / reference viewer).
 */
public class OpeningLinePlayer {

    private final OpeningLine line;
    private int ply = 0;

    public OpeningLinePlayer(OpeningLine line) {
        this.line = line;
    }

    public OpeningLine line() {
        return line;
    }

    public int ply() {
        return ply;
    }

    public int totalPlies() {
        return line.uciMoves().size();
    }

    public boolean hasNext() {
        return ply < totalPlies();
    }

    public boolean hasPrevious() {
        return ply > 0;
    }

    public void next() {
        if (hasNext()) {
            ply++;
        }
    }

    public void previous() {
        if (hasPrevious()) {
            ply--;
        }
    }

    /** Replays the line from the start position up to the current ply on a fresh game. */
    public ChessGame gameAtCurrentPly() {
        ChessGame game = new ChessGame();
        for (int i = 0; i < ply; i++) {
            game.applyUciMove(line.uciMoves().get(i));
        }
        return game;
    }

    /** From/to squares of the move that led to the current ply, or {@code null} at the start. */
    public Square[] lastMoveSquares() {
        if (ply == 0) {
            return null;
        }
        String uci = line.uciMoves().get(ply - 1);
        Square from = Square.valueOf(uci.substring(0, 2).toUpperCase(Locale.ROOT));
        Square to = Square.valueOf(uci.substring(2, 4).toUpperCase(Locale.ROOT));
        return new Square[] {from, to};
    }
}
