/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.logic;

import com.github.bhlangonijr.chesslib.Side;
import de.schliweb.moveapiece.training.OpeningLine;
import de.schliweb.moveapiece.training.OpeningRepository;
import java.util.Objects;

/**
 * Everything the "New Game" dialog asks for: the opponent, which colour the human plays (for the
 * trainer: the side being drilled), and - for the opening trainer only - the line and whether the
 * trainee's next move is highlighted. {@code opening} is non-null exactly when the opponent is
 * {@link Opponent#TRAINER}.
 *
 * <p>Both front ends remember the last setup across restarts so the dialog opens pre-filled with
 * it, and so a physical board's NEW GAME button can repeat it even right after an app start. It is
 * deliberately <em>not</em> auto-started on launch: the app still comes up in its plain default
 * (Human vs Stockfish as White) - loading a Maia model or a training line unasked would be a
 * surprise, pre-selecting it in a dialog is not.
 *
 * @param opponent who the human plays against
 * @param side the human's colour; for the trainer, the side being trained
 * @param opening the line to drill; null unless {@code opponent} is {@link Opponent#TRAINER}
 * @param hintsEnabled trainer only: whether the expected next move is highlighted
 * @param chess960 whether each game starts from a random Chess960 starting position; always false
 *     for the trainer, whose lines start from the standard position
 */
public record GameSetup(
        Opponent opponent, Side side, OpeningLine opening, boolean hintsEnabled, boolean chess960) {

    /** What both front ends start in: Human vs Stockfish, human as White. */
    public static final GameSetup DEFAULT =
            new GameSetup(Opponent.STOCKFISH, Side.WHITE, null, true);

    public GameSetup {
        Objects.requireNonNull(opponent, "opponent");
        Objects.requireNonNull(side, "side");
        if (opponent == Opponent.TRAINER) {
            Objects.requireNonNull(opening, "opening is required for the trainer");
        } else {
            opening = null;
        }
        if (!supportsChess960(opponent)) {
            chess960 = false;
        }
    }

    /** A standard-chess setup. */
    public GameSetup(Opponent opponent, Side side, OpeningLine opening, boolean hintsEnabled) {
        this(opponent, side, opening, hintsEnabled, false);
    }

    /**
     * Whether games against {@code opponent} can be played as Chess960. Maia learnt from standard
     * games only, but measured on human Chess960 games it still predicts the player's move almost
     * half the time (48 %, against 54 % in standard chess), lower mainly in the first moves.
     */
    public static boolean supportsChess960(Opponent opponent) {
        return opponent != Opponent.TRAINER;
    }

    /** A non-trainer setup; {@code side} is the human's colour. */
    public static GameSetup of(Opponent opponent, Side side) {
        return new GameSetup(opponent, side, null, true);
    }

    /** A trainer setup drilling {@code opening} as {@code side}. */
    public static GameSetup training(OpeningLine opening, Side side, boolean hintsEnabled) {
        return new GameSetup(Opponent.TRAINER, side, opening, hintsEnabled);
    }

    /**
     * Rebuilds a setup from persisted keys, forgiving anything that no longer parses: an unknown
     * opponent key falls back to Stockfish, an unknown side to White, and a trainer setup whose
     * opening id has since been removed or renamed from {@link OpeningRepository} drills the first
     * line in the repository instead of failing forever - the same self-healing idea as {@code
     * MaiaRatings.nearest} for a persisted rating.
     */
    public static GameSetup fromKeys(
            String opponentKey, String sideKey, String openingId, boolean hintsEnabled) {
        return fromKeys(opponentKey, sideKey, openingId, hintsEnabled, false);
    }

    /** {@link #fromKeys(String, String, String, boolean)} with the persisted Chess960 choice. */
    public static GameSetup fromKeys(
            String opponentKey,
            String sideKey,
            String openingId,
            boolean hintsEnabled,
            boolean chess960) {
        Opponent opponent = Opponent.fromKey(opponentKey);
        Side side = Side.BLACK.name().equals(sideKey) ? Side.BLACK : Side.WHITE;
        if (opponent != Opponent.TRAINER) {
            return new GameSetup(opponent, side, null, hintsEnabled, chess960);
        }
        OpeningLine opening = openingId == null ? null : OpeningRepository.byId(openingId);
        if (opening == null) {
            opening = OpeningRepository.ALL.get(0);
        }
        return new GameSetup(opponent, side, opening, hintsEnabled);
    }

    /** The persisted form of {@link #opening()}: its id, or null when there is none. */
    public String openingId() {
        return opening == null ? null : opening.id();
    }
}
