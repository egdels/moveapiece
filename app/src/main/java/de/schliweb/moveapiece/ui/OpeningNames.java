/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.ui;

import android.content.Context;
import de.schliweb.moveapiece.R;
import de.schliweb.moveapiece.training.OpeningLine;
import java.util.HashMap;
import java.util.Map;

/**
 * Resolves an {@link OpeningLine}'s platform-agnostic {@link OpeningLine#id()} to a localized
 * display name via this app's Android string resources - the localization side of {@code :core}'s
 * deliberately UI-free opening library.
 */
public final class OpeningNames {

    private static final Map<String, Integer> RES_BY_ID = new HashMap<>();

    static {
        RES_BY_ID.put("ruy_lopez", R.string.opening_ruy_lopez);
        RES_BY_ID.put("italian", R.string.opening_italian);
        RES_BY_ID.put("sicilian_najdorf", R.string.opening_sicilian_najdorf);
        RES_BY_ID.put("queens_gambit_declined", R.string.opening_queens_gambit_declined);
        RES_BY_ID.put("kings_indian", R.string.opening_kings_indian);
        RES_BY_ID.put("french", R.string.opening_french);
        RES_BY_ID.put("caro_kann", R.string.opening_caro_kann);
        RES_BY_ID.put("english", R.string.opening_english);
        RES_BY_ID.put("scandinavian", R.string.opening_scandinavian);
        RES_BY_ID.put("slav", R.string.opening_slav);
        RES_BY_ID.put("nimzo_indian", R.string.opening_nimzo_indian);
        RES_BY_ID.put("gruenfeld", R.string.opening_gruenfeld);
        RES_BY_ID.put("scotch", R.string.opening_scotch);
        RES_BY_ID.put("pirc", R.string.opening_pirc);
        RES_BY_ID.put("london", R.string.opening_london);
        RES_BY_ID.put("vienna", R.string.opening_vienna);
        RES_BY_ID.put("kings_gambit", R.string.opening_kings_gambit);
        RES_BY_ID.put("benoni", R.string.opening_benoni);
        RES_BY_ID.put("catalan", R.string.opening_catalan);
        RES_BY_ID.put("trompowsky", R.string.opening_trompowsky);
    }

    private OpeningNames() {}

    public static String displayName(Context context, OpeningLine line) {
        Integer resId = RES_BY_ID.get(line.id());
        if (resId == null) {
            throw new IllegalStateException("no display name mapped for opening id: " + line.id());
        }
        return context.getString(resId);
    }
}
