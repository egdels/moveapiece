/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.desktop;

import de.schliweb.moveapiece.training.OpeningLine;

/**
 * Resolves an {@link OpeningLine}'s platform-agnostic {@link OpeningLine#id()} to a localized
 * display name via {@link Messages} (key {@code "opening_" + id}) - the desktop equivalent of the
 * Android app's {@code ui.OpeningNames}, which does the same lookup against Android string
 * resources instead.
 */
final class OpeningNames {

    private OpeningNames() {}

    static String displayName(OpeningLine line) {
        return Messages.get("opening_" + line.id());
    }
}
