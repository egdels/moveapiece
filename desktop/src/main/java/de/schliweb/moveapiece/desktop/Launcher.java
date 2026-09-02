/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.desktop;

/**
 * Separate, non-{@link javafx.application.Application} entry point for the jpackage-built app
 * image. JavaFX refuses to start a classpath-mode jar (no {@code --module-path}, as jpackage's
 * default app-image runs it) whose manifest {@code Main-Class} itself extends {@code Application},
 * with "Error: JavaFX runtime components are missing" - even though the JavaFX jars are right there
 * on the classpath. Routing through a plain class sidesteps that check; {@code :desktop:run}
 * (module-path launch via the JavaFX Gradle plugin) never needed this indirection.
 */
public final class Launcher {

    private Launcher() {}

    public static void main(String[] args) {
        DesktopApp.main(args);
    }
}
