/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.puzzle;

import com.github.bhlangonijr.chesslib.Side;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * One tactics puzzle from the bundled Lichess set (see {@code PUZZLE_PROVENANCE.md}).
 *
 * <p>As in the Lichess database, {@code fen} is the position <em>before</em> the opponent's move:
 * the first of {@code moves} is played by the app, then the trainee and the app alternate, and the
 * line ends on the trainee's move. The trainee therefore plays the side that is <em>not</em> to
 * move in {@code fen}.
 *
 * @param id the Lichess puzzle id (lichess.org/training/{id})
 * @param fen the position before the opponent's first move
 * @param moves the full line in UCI notation, opponent's move first; always an even count
 * @param rating the puzzle's Lichess rating
 * @param themes the Lichess theme tags, e.g. "fork", "mateIn2"
 * @param chess960 whether {@code fen} is a Chess960 position (castling onto the own rook); false
 *     for every Lichess puzzle, possibly true for a mistake practised from one's own game
 * @param alsoAccepted moves that solve the puzzle's last move as well as the stored one - nearly as
 *     good, see {@link MistakePractice}; empty for a Lichess puzzle
 */
public record Puzzle(
        String id,
        String fen,
        List<String> moves,
        int rating,
        List<String> themes,
        boolean chess960,
        Set<String> alsoAccepted) {

    public Puzzle {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(fen, "fen");
        moves = List.copyOf(moves);
        themes = List.copyOf(themes);
        alsoAccepted = alsoAccepted == null ? Set.of() : Set.copyOf(alsoAccepted);
        if (moves.size() < 2 || moves.size() % 2 != 0) {
            throw new IllegalArgumentException(
                    "puzzle " + id + ": expected an even number of moves, got " + moves.size());
        }
    }

    /** A Lichess puzzle: standard chess, only the stored moves solve it. */
    public Puzzle(String id, String fen, List<String> moves, int rating, List<String> themes) {
        this(id, fen, moves, rating, themes, false, Set.of());
    }

    /**
     * Whether {@code uci} (in the engine's notation) is a right move at {@code ply}: the stored
     * one, or, for the last move, one of {@link #alsoAccepted()}. A mating move is right anyway;
     * {@link PuzzleFlow} checks that itself.
     */
    public boolean accepts(int ply, String uci) {
        return uci.equals(moves.get(ply))
                || (ply == moves.size() - 1 && alsoAccepted.contains(uci));
    }

    /** The side the trainee plays: the one not to move in {@link #fen()}. */
    public Side humanSide() {
        String[] fields = fen.split(" ");
        boolean whiteToMove = fields.length < 2 || "w".equals(fields[1]);
        return whiteToMove ? Side.BLACK : Side.WHITE;
    }
}
