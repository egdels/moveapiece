/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.training;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Filters {@link OpeningLine}s by a case-insensitive substring match against their
 * (already-localized) display name. Takes display names as a parallel list rather than resolving
 * them itself, so this stays plain-JUnit testable without an Android {@code Context}.
 */
public final class OpeningSearch {

    private OpeningSearch() {}

    public static List<OpeningLine> filter(
            List<OpeningLine> lines, List<String> displayNames, String query) {
        if (lines.size() != displayNames.size()) {
            throw new IllegalArgumentException("lines and displayNames must be parallel lists");
        }
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) {
            return new ArrayList<>(lines);
        }
        List<OpeningLine> result = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            if (displayNames.get(i).toLowerCase(Locale.ROOT).contains(needle)) {
                result.add(lines.get(i));
            }
        }
        return result;
    }
}
