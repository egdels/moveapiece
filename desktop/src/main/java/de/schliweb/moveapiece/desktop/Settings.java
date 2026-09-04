/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.desktop;

import java.util.prefs.Preferences;

/** Persists user-adjustable settings (engine strength, evaluation display) across app restarts. */
final class Settings {

    private static final Preferences PREFS = Preferences.userNodeForPackage(Settings.class);
    private static final String KEY_ENGINE_ELO = "engineElo";
    private static final String KEY_EVALUATION_ENABLED = "evaluationEnabled";
    private static final int DEFAULT_ENGINE_ELO = 2200;
    private static final boolean DEFAULT_EVALUATION_ENABLED = true;

    private Settings() {}

    static int getEngineElo() {
        return PREFS.getInt(KEY_ENGINE_ELO, DEFAULT_ENGINE_ELO);
    }

    static void setEngineElo(int elo) {
        PREFS.putInt(KEY_ENGINE_ELO, elo);
    }

    static boolean isEvaluationDisplayEnabled() {
        return PREFS.getBoolean(KEY_EVALUATION_ENABLED, DEFAULT_EVALUATION_ENABLED);
    }

    static void setEvaluationDisplayEnabled(boolean enabled) {
        PREFS.putBoolean(KEY_EVALUATION_ENABLED, enabled);
    }
}
