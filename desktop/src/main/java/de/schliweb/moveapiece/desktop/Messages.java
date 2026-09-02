/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.desktop;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 * Localized UI strings, driven by the JVM's default locale (i.e. the host OS locale) exactly like
 * the Android app follows the device locale - no in-app language picker on either platform. Backed
 * by {@code i18n/Messages.properties} (English default) plus {@code _de}, {@code _fr}, {@code _es},
 * {@code _it}, {@code _nl} variants, matching the Android app's six locales (see the {@code
 * strings.xml} files under {@code app/src/main/res}, which several keys here are copied from
 * verbatim for parity).
 */
final class Messages {

    private static final ResourceBundle BUNDLE =
            ResourceBundle.getBundle(
                    "de.schliweb.moveapiece.desktop.i18n.Messages", Locale.getDefault());

    private Messages() {}

    /**
     * Plain lookup, no placeholder substitution - safe for strings containing literal
     * apostrophes/braces.
     */
    static String get(String key) {
        return BUNDLE.getString(key);
    }

    /**
     * Lookup with {0}/{1}/... placeholder substitution via {@link MessageFormat}. Arguments are
     * stringified first so a plain {0} placeholder never triggers MessageFormat's locale-sensitive
     * default Number formatting (which would e.g. turn an Elo rating of 2200 into "2.200"/"2,200"
     * depending on locale) - every argument here is meant to be substituted verbatim, never
     * grouped/localized.
     */
    static String get(String key, Object... args) {
        Object[] stringArgs = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            stringArgs[i] = String.valueOf(args[i]);
        }
        return MessageFormat.format(BUNDLE.getString(key), stringArgs);
    }
}
