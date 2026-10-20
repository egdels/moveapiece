/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece;

import android.content.Context;
import android.content.SharedPreferences;
import de.schliweb.moveapiece.engine.MaiaRatings;
import de.schliweb.moveapiece.logic.BoardType;
import de.schliweb.moveapiece.logic.ChessGame;
import de.schliweb.moveapiece.logic.GameSetup;

/**
 * Persists user-adjustable settings (engine strength, Maia rating, evaluation display, physical
 * board type, last game setup) and the game in progress across app restarts.
 */
final class Settings {

    private static final String PREFS_NAME = "settings";
    private static final String KEY_ENGINE_ELO = "engineElo";
    private static final String KEY_MAIA_RATING = "maiaRating";
    private static final String KEY_EVALUATION_ENABLED = "evaluationEnabled";
    private static final String KEY_BOARD_TYPE = "boardType";
    private static final String KEY_LAST_OPPONENT = "lastOpponent";
    private static final String KEY_LAST_SIDE = "lastSide";
    private static final String KEY_LAST_OPENING = "lastOpening";
    private static final String KEY_LAST_TRAINING_HINTS = "lastTrainingHints";
    private static final String KEY_LAST_CHESS960 = "lastChess960";
    private static final int DEFAULT_MAIA_RATING = 1500;

    /** The game in progress lives in a file of its own, so forgetting it is a plain clear(). */
    private static final String GAME_PREFS_NAME = "savedGame";

    private final SharedPreferences prefs;
    private final SharedPreferences gamePrefs;

    Settings(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        gamePrefs = context.getSharedPreferences(GAME_PREFS_NAME, Context.MODE_PRIVATE);
    }

    int getEngineElo(int defaultValue) {
        return prefs.getInt(KEY_ENGINE_ELO, defaultValue);
    }

    void setEngineElo(int elo) {
        prefs.edit().putInt(KEY_ENGINE_ELO, elo).apply();
    }

    /**
     * Last-chosen Maia rating (one of {@link MaiaRatings#ALL}), remembered across app restarts and
     * pre-selected the next time the "New Game" dialog's Maia option is picked. Passed through
     * {@link MaiaRatings#nearest} so a value written by some earlier, buggier build self-heals to a
     * loadable rating instead of failing forever - see that method's Javadoc.
     */
    int getMaiaRating() {
        return MaiaRatings.nearest(prefs.getInt(KEY_MAIA_RATING, DEFAULT_MAIA_RATING));
    }

    void setMaiaRating(int rating) {
        prefs.edit().putInt(KEY_MAIA_RATING, rating).apply();
    }

    boolean isEvaluationDisplayEnabled(boolean defaultValue) {
        return prefs.getBoolean(KEY_EVALUATION_ENABLED, defaultValue);
    }

    void setEvaluationDisplayEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_EVALUATION_ENABLED, enabled).apply();
    }

    /** Which physical board the connect button talks to; {@link BoardType#PEGASUS} until chosen. */
    BoardType getBoardType() {
        return BoardType.fromKey(prefs.getString(KEY_BOARD_TYPE, null));
    }

    void setBoardType(BoardType type) {
        prefs.edit().putString(KEY_BOARD_TYPE, type.key()).apply();
    }

    /**
     * What the "New Game" dialog last started (see {@link GameSetup}): pre-fills that dialog and is
     * what a physical board's NEW GAME button repeats right after launch. Read defensively via
     * {@link GameSetup#fromKeys}; {@link GameSetup#DEFAULT} until anything was chosen.
     */
    GameSetup getLastGameSetup() {
        return GameSetup.fromKeys(
                prefs.getString(KEY_LAST_OPPONENT, null),
                prefs.getString(KEY_LAST_SIDE, null),
                prefs.getString(KEY_LAST_OPENING, null),
                prefs.getBoolean(KEY_LAST_TRAINING_HINTS, true),
                prefs.getBoolean(KEY_LAST_CHESS960, false));
    }

    void setLastGameSetup(GameSetup setup) {
        prefs.edit()
                .putString(KEY_LAST_OPPONENT, setup.opponent().key())
                .putString(KEY_LAST_SIDE, setup.side().name())
                .putString(KEY_LAST_OPENING, setup.openingId())
                .putBoolean(KEY_LAST_TRAINING_HINTS, setup.hintsEnabled())
                .putBoolean(KEY_LAST_CHESS960, setup.chess960())
                .apply();
    }

    // ---- The game in progress ---------------------------------------------------------------

    /**
     * The game in progress, kept across a restart of the app (see {@code MainActivity#saveGame}).
     * {@code side} is the engine's side outside training and the trainee's in training. In training
     * only the training fields are set and {@code snapshot} is null; otherwise the game is the
     * snapshot and the training fields are unused.
     */
    record SavedGame(
            String mode,
            String side,
            boolean flipped,
            int maiaRating,
            ChessGame.Snapshot snapshot,
            String trainingLine,
            boolean trainingHints,
            int trainingPly) {}

    private static final String KEY_GAME_MODE = "mode";
    private static final String KEY_GAME_SIDE = "side";
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
    SavedGame getSavedGame() {
        String mode = gamePrefs.getString(KEY_GAME_MODE, null);
        if (mode == null) {
            return null;
        }
        String startFen = gamePrefs.getString(KEY_GAME_START_FEN, null);
        String uciMoves = gamePrefs.getString(KEY_GAME_UCI_MOVES, null);
        ChessGame.Snapshot snapshot =
                startFen == null || uciMoves == null
                        ? null
                        : new ChessGame.Snapshot(
                                startFen,
                                gamePrefs.getBoolean(KEY_GAME_CHESS960, false),
                                uciMoves,
                                gamePrefs.getInt(KEY_GAME_PLY, 0));
        return new SavedGame(
                mode,
                gamePrefs.getString(KEY_GAME_SIDE, null),
                gamePrefs.getBoolean(KEY_GAME_FLIPPED, false),
                gamePrefs.getInt(KEY_GAME_MAIA_RATING, DEFAULT_MAIA_RATING),
                snapshot,
                gamePrefs.getString(KEY_GAME_TRAINING_LINE, null),
                gamePrefs.getBoolean(KEY_GAME_TRAINING_HINTS, true),
                gamePrefs.getInt(KEY_GAME_TRAINING_PLY, 0));
    }

    /** Keeps {@code game} for the next launch, replacing what was kept before. */
    void setSavedGame(SavedGame game) {
        SharedPreferences.Editor editor =
                gamePrefs
                        .edit()
                        .clear()
                        .putString(KEY_GAME_MODE, game.mode())
                        .putString(KEY_GAME_SIDE, game.side())
                        .putBoolean(KEY_GAME_FLIPPED, game.flipped())
                        .putInt(KEY_GAME_MAIA_RATING, game.maiaRating())
                        .putBoolean(KEY_GAME_TRAINING_HINTS, game.trainingHints())
                        .putInt(KEY_GAME_TRAINING_PLY, game.trainingPly());
        ChessGame.Snapshot snapshot = game.snapshot();
        if (snapshot != null) {
            editor.putString(KEY_GAME_START_FEN, snapshot.startFen())
                    .putBoolean(KEY_GAME_CHESS960, snapshot.chess960())
                    .putString(KEY_GAME_UCI_MOVES, snapshot.uciMoves())
                    .putInt(KEY_GAME_PLY, snapshot.ply());
        }
        if (game.trainingLine() != null) {
            editor.putString(KEY_GAME_TRAINING_LINE, game.trainingLine());
        }
        editor.apply();
    }

    /** Forgets the kept game: {@link #getSavedGame} returns null until the next save. */
    void clearSavedGame() {
        gamePrefs.edit().clear().apply();
    }
}
