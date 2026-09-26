/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece;

import android.content.Context;
import android.content.SharedPreferences;
import de.schliweb.moveapiece.board.BoardType;
import de.schliweb.moveapiece.engine.MaiaRatings;

/**
 * Persists user-adjustable settings (engine strength, Maia rating, evaluation display, physical
 * board type) across app restarts.
 */
final class Settings {

    private static final String PREFS_NAME = "settings";
    private static final String KEY_ENGINE_ELO = "engineElo";
    private static final String KEY_MAIA_RATING = "maiaRating";
    private static final String KEY_EVALUATION_ENABLED = "evaluationEnabled";
    private static final String KEY_BOARD_TYPE = "boardType";
    private static final int DEFAULT_MAIA_RATING = 1500;

    private final SharedPreferences prefs;

    Settings(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
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
}
