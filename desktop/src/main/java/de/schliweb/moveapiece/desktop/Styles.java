/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.desktop;

import javafx.scene.Scene;
import javafx.scene.control.DialogPane;

/** Applies the app's shared visual theme (app.css) to a Scene or DialogPane. */
final class Styles {

    private static final String STYLESHEET = Styles.class.getResource("app.css").toExternalForm();

    private Styles() {}

    static void apply(Scene scene) {
        scene.getStylesheets().add(STYLESHEET);
    }

    static void apply(DialogPane pane) {
        pane.getStylesheets().add(STYLESHEET);
    }
}
