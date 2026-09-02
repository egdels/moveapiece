/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.training;

import java.util.Collections;
import java.util.List;

/**
 * A single named opening line as a fixed sequence of UCI moves from the start position. {@link
 * #id()} doubles as the lookup key each platform uses to resolve a localized display name from its
 * own resource system (Android string resources, a desktop {@code ResourceBundle}, ...) - this
 * module has no UI/localization dependency of its own.
 */
public class OpeningLine {

    private final String id;
    private final List<String> uciMoves;

    public OpeningLine(String id, List<String> uciMoves) {
        this.id = id;
        this.uciMoves = Collections.unmodifiableList(uciMoves);
    }

    public String id() {
        return id;
    }

    public List<String> uciMoves() {
        return uciMoves;
    }
}
