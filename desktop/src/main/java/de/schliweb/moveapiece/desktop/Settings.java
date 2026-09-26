/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.desktop;

import de.schliweb.moveapiece.engine.MaiaRatings;
import de.schliweb.moveapiece.logic.BoardType;
import de.schliweb.moveapiece.logic.GameSetup;
import java.util.prefs.Preferences;

/**
 * Persists user-adjustable settings (engine strength, Maia rating, evaluation display, physical
 * board type, last game setup) across app restarts.
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
    private static final int DEFAULT_ENGINE_ELO = 2200;
    private static final boolean DEFAULT_EVALUATION_ENABLED = true;
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
                PREFS.getBoolean(KEY_LAST_TRAINING_HINTS, true));
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
    }
}
