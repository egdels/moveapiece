/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.desktop;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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

    /**
     * ONNX Runtime's native core reads this at {@code OrtEnvironment} creation time, before
     * {@code MaiaEngine}'s {@code environment.setTelemetry(false)} call can have any effect (see
     * THIRD-PARTY-NOTICES.md's "onnxruntime-android's bundled telemetry" section - the same
     * native telemetry system backs the desktop {@code onnxruntime} JVM artifact too). It's a
     * real OS environment variable, not a JVM system property, so it has to be set before this
     * process's JVM - and with it the bundled onnxruntime native library - exists at all; Java
     * has no supported way to mutate its own process's already-running environment block. Hence
     * the self-relaunch below, scoped to this jpackage entry point only: {@code :desktop:run}
     * (dev mode) goes through {@link DesktopApp} directly and is unaffected.
     */
    private static final String TELEMETRY_ENV_VAR = "ORT_DISABLE_TELEMETRY";

    public static void main(String[] args) throws IOException, InterruptedException {
        if ("1".equals(System.getenv(TELEMETRY_ENV_VAR))) {
            DesktopApp.main(args);
            return;
        }
        System.exit(relaunchWithTelemetryDisabled());
    }

    /**
     * Re-execs the current jpackage-built launcher binary (not a bare {@code java} invocation -
     * see {@link ProcessHandle.Info#command()}) with {@link #TELEMETRY_ENV_VAR} added to its
     * environment, then waits for it and forwards its exit code. {@code ProcessHandle.Info
     * #arguments()} already carries this same process's own argv - jpackage's native launcher
     * passes module-path/main-class etc. to the embedded JVM internally, straight from its
     * {@code .cfg} file, so those never appear in argv for this method to reconstruct by hand.
     */
    private static int relaunchWithTelemetryDisabled() throws IOException, InterruptedException {
        ProcessHandle.Info info = ProcessHandle.current().info();
        String command =
                info.command()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Could not determine this process's own executable path"
                                                        + " to relaunch it with "
                                                        + TELEMETRY_ENV_VAR
                                                        + " set"));
        List<String> commandLine = new ArrayList<>();
        commandLine.add(command);
        info.arguments().ifPresent(a -> commandLine.addAll(List.of(a)));

        ProcessBuilder builder = new ProcessBuilder(commandLine);
        builder.environment().put(TELEMETRY_ENV_VAR, "1");
        builder.inheritIO();
        Process child = builder.start();
        return child.waitFor();
    }
}
