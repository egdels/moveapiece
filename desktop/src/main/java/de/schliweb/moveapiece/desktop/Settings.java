/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.desktop;

import de.schliweb.moveapiece.engine.MaiaRatings;
import de.schliweb.moveapiece.logic.BoardType;
import de.schliweb.moveapiece.logic.ChessGame;
import de.schliweb.moveapiece.logic.GameSetup;
import java.util.prefs.Preferences;

/**
 * Persists user-adjustable settings (engine strength, Maia rating, evaluation display, physical
 * board type, last game setup) and the game in progress across app restarts.
 */
final class Settings {

    private static final Preferences PREFS = Preferences.userNodeForPackage(Settings.class);
    private static final String KEY_ENGINE_ELO = "engineElo";
    private static final String KEY_EVALUATION_ENABLED = "evaluationEnabled";
    private static final String KEY_MAIA_RATING = "maiaRating";
    private static final String KEY_BOARD_TYPE = "boardType";
    private static final String KEY_LAST_OPPONENT = "lastOpponent";
    private static final String KEY_LAST_SIDE = "lastSide";
    private static final String KEY_LAST_OPENING = "lastOpening";
    private static final String KEY_LAST_TRAINING_HINTS = "lastTrainingHints";
    private static final String KEY_LAST_CHESS960 = "lastChess960";
    private static final int DEFAULT_ENGINE_ELO = 2200;
    private static final boolean DEFAULT_EVALUATION_ENABLED = false;
    private static final int DEFAULT_MAIA_RATING = 1500;

    private Settings() {}

    static int getEngineElo() {
        return PREFS.getInt(KEY_ENGINE_ELO, DEFAULT_ENGINE_ELO);
    }

    static void setEngineElo(int elo) {
        PREFS.putInt(KEY_ENGINE_ELO, elo);
    }

    /**
     * Last-chosen Maia rating (one of {@link MaiaRatings#ALL}), remembered across app restarts.
     * Passed through {@link MaiaRatings#nearest} so a value persisted by an earlier, buggier build
     * of the rating slider's snapping logic self-heals to a loadable rating instead of failing
     * forever.
     */
    static int getMaiaRating() {
        return MaiaRatings.nearest(PREFS.getInt(KEY_MAIA_RATING, DEFAULT_MAIA_RATING));
    }

    static void setMaiaRating(int rating) {
        PREFS.putInt(KEY_MAIA_RATING, rating);
    }

    static boolean isEvaluationDisplayEnabled() {
        return PREFS.getBoolean(KEY_EVALUATION_ENABLED, DEFAULT_EVALUATION_ENABLED);
    }

    static void setEvaluationDisplayEnabled(boolean enabled) {
        PREFS.putBoolean(KEY_EVALUATION_ENABLED, enabled);
    }

    /** Which physical board the connect button talks to; {@link BoardType#PEGASUS} until chosen. */
    static BoardType getBoardType() {
        return BoardType.fromKey(PREFS.get(KEY_BOARD_TYPE, null));
    }

    static void setBoardType(BoardType type) {
        PREFS.put(KEY_BOARD_TYPE, type.key());
    }

    /**
     * What the "New Game" dialog last started (see {@link GameSetup}): pre-fills that dialog and is
     * what a physical board's NEW GAME button repeats right after launch. Read defensively via
     * {@link GameSetup#fromKeys}; {@link GameSetup#DEFAULT} until anything was chosen.
     */
    static GameSetup getLastGameSetup() {
        return GameSetup.fromKeys(
                PREFS.get(KEY_LAST_OPPONENT, null),
                PREFS.get(KEY_LAST_SIDE, null),
                PREFS.get(KEY_LAST_OPENING, null),
                PREFS.getBoolean(KEY_LAST_TRAINING_HINTS, true),
                PREFS.getBoolean(KEY_LAST_CHESS960, false));
    }

    static void setLastGameSetup(GameSetup setup) {
        PREFS.put(KEY_LAST_OPPONENT, setup.opponent().key());
        PREFS.put(KEY_LAST_SIDE, setup.side().name());
        if (setup.openingId() == null) {
            PREFS.remove(KEY_LAST_OPENING);
        } else {
            PREFS.put(KEY_LAST_OPENING, setup.openingId());
        }
        PREFS.putBoolean(KEY_LAST_TRAINING_HINTS, setup.hintsEnabled());
        PREFS.putBoolean(KEY_LAST_CHESS960, setup.chess960());
    }

    // ---- The game in progress ---------------------------------------------------------------

    /**
     * The game in progress, kept across a restart of the app (see {@code GameController#saveGame}).
     * In training only the training fields are set and {@code snapshot} is null; otherwise the game
     * is the snapshot and the training fields are unused.
     */
    record SavedGame(
            String mode,
            String humanSide,
            boolean flipped,
            int maiaRating,
            ChessGame.Snapshot snapshot,
            String trainingLine,
            boolean trainingHints,
            int trainingPly) {}

    private static final String NODE_SAVED_GAME = "savedGame";
    private static final String KEY_GAME_MODE = "mode";
    private static final String KEY_GAME_HUMAN_SIDE = "humanSide";
    private static final String KEY_GAME_FLIPPED = "flipped";
    private static final String KEY_GAME_MAIA_RATING = "maiaRating";
    private static final String KEY_GAME_START_FEN = "startFen";
    private static final String KEY_GAME_CHESS960 = "chess960";
    private static final String KEY_GAME_UCI_MOVES = "uciMoves";
    private static final String KEY_GAME_PLY = "ply";
    private static final String KEY_GAME_TRAINING_LINE = "trainingLine";
    private static final String KEY_GAME_TRAINING_HINTS = "trainingHints";
    private static final String KEY_GAME_TRAINING_PLY = "trainingPly";

    /** Null when no game was kept, see {@link #setSavedGame}. */
    static SavedGame getSavedGame() {
        Preferences node = PREFS.node(NODE_SAVED_GAME);
        String mode = node.get(KEY_GAME_MODE, null);
        if (mode == null) {
            return null;
        }
        String startFen = node.get(KEY_GAME_START_FEN, null);
        String uciMoves = node.get(KEY_GAME_UCI_MOVES, null);
        ChessGame.Snapshot snapshot =
                startFen == null || uciMoves == null
                        ? null
                        : new ChessGame.Snapshot(
                                startFen,
                                node.getBoolean(KEY_GAME_CHESS960, false),
                                uciMoves,
                                node.getInt(KEY_GAME_PLY, 0));
        return new SavedGame(
                mode,
                node.get(KEY_GAME_HUMAN_SIDE, null),
                node.getBoolean(KEY_GAME_FLIPPED, false),
                node.getInt(KEY_GAME_MAIA_RATING, DEFAULT_MAIA_RATING),
                snapshot,
                node.get(KEY_GAME_TRAINING_LINE, null),
                node.getBoolean(KEY_GAME_TRAINING_HINTS, true),
                node.getInt(KEY_GAME_TRAINING_PLY, 0));
    }

    /**
     * Keeps {@code game} for the next launch, replacing what was kept before. A game too long for a
     * preference value (over about 1600 moves) is not kept.
     */
    static void setSavedGame(SavedGame game) {
        Preferences node = PREFS.node(NODE_SAVED_GAME);
        ChessGame.Snapshot snapshot = game.snapshot();
        if (snapshot != null
                && (snapshot.uciMoves().length() > Preferences.MAX_VALUE_LENGTH
                        || snapshot.startFen().length() > Preferences.MAX_VALUE_LENGTH)) {
            clearSavedGame();
            return;
        }
        node.put(KEY_GAME_MODE, game.mode());
        node.put(KEY_GAME_HUMAN_SIDE, game.humanSide());
        node.putBoolean(KEY_GAME_FLIPPED, game.flipped());
        node.putInt(KEY_GAME_MAIA_RATING, game.maiaRating());
        if (snapshot == null) {
            node.remove(KEY_GAME_START_FEN);
            node.remove(KEY_GAME_CHESS960);
            node.remove(KEY_GAME_UCI_MOVES);
            node.remove(KEY_GAME_PLY);
        } else {
            node.put(KEY_GAME_START_FEN, snapshot.startFen());
            node.putBoolean(KEY_GAME_CHESS960, snapshot.chess960());
            node.put(KEY_GAME_UCI_MOVES, snapshot.uciMoves());
            node.putInt(KEY_GAME_PLY, snapshot.ply());
        }
        if (game.trainingLine() == null) {
            node.remove(KEY_GAME_TRAINING_LINE);
        } else {
            node.put(KEY_GAME_TRAINING_LINE, game.trainingLine());
        }
        node.putBoolean(KEY_GAME_TRAINING_HINTS, game.trainingHints());
        node.putInt(KEY_GAME_TRAINING_PLY, game.trainingPly());
    }

    /** Forgets the kept game: {@link #getSavedGame} returns null until the next save. */
    static void clearSavedGame() {
        PREFS.node(NODE_SAVED_GAME).remove(KEY_GAME_MODE);
    }
}
