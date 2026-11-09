/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.puzzle;

import com.github.bhlangonijr.chesslib.Side;
import java.util.List;
import java.util.Objects;

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
 */
public record Puzzle(String id, String fen, List<String> moves, int rating, List<String> themes) {

    public Puzzle {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(fen, "fen");
        moves = List.copyOf(moves);
        themes = List.copyOf(themes);
        if (moves.size() < 2 || moves.size() % 2 != 0) {
            throw new IllegalArgumentException(
                    "puzzle " + id + ": expected an even number of moves, got " + moves.size());
        }
    }

    /** The side the trainee plays: the one not to move in {@link #fen()}. */
    public Side humanSide() {
        String[] fields = fen.split(" ");
        boolean whiteToMove = fields.length < 2 || "w".equals(fields[1]);
        return whiteToMove ? Side.BLACK : Side.WHITE;
    }

    /** The app's move that sets the puzzle up. */
    public String openingMove() {
        return moves.get(0);
    }

    /** How many moves the trainee has to find. */
    public int solutionLength() {
        return moves.size() / 2;
    }

    /**
     * Whether the line ends in checkmate. Lichess then accepts any mating move as the last move,
     * not only the one stored, and so should the app.
     */
    public boolean endsInMate() {
        return themes.contains("mate");
    }
}
