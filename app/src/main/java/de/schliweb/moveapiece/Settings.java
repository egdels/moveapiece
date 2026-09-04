/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece;

import android.content.Context;
import android.content.SharedPreferences;

/** Persists user-adjustable settings (engine strength, evaluation display) across app restarts. */
final class Settings {

    private static final String PREFS_NAME = "settings";
    private static final String KEY_ENGINE_ELO = "engineElo";
    private static final String KEY_EVALUATION_ENABLED = "evaluationEnabled";

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

    boolean isEvaluationDisplayEnabled(boolean defaultValue) {
        return prefs.getBoolean(KEY_EVALUATION_ENABLED, defaultValue);
    }

    void setEvaluationDisplayEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_EVALUATION_ENABLED, enabled).apply();
    }
}
