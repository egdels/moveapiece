/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.desktop.pegasus;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.security.CodeSource;
import java.util.Properties;

/**
 * Resolves and loads the native Windows Runtime/JNI library ({@code PegasusBleWin.dll}) used by
 * {@link WindowsPegasusBleTransport}.
 *
 * <p>Two possible locations, tried in order - mirrors {@code StockfishLocator}/{@link
 * PegasusBleMacLibrary}'s own lookup exactly, for the same reasons:
 *
 * <ol>
 *   <li>Bundled next to this class's own jar, in a {@code native/} sibling directory - how a
 *       packaged app (jpackage app-image/MSI) ships it, see {@code desktop/packaging.gradle}.
 *   <li>The build-time output directory recorded by {@code desktop/pegasus-ble-windows.gradle}'s
 *       {@code writePegasusBleWinHomeProperties} task into a generated {@code
 *       pegasusBleWinHome.properties} classpath resource - a fallback for running unpackaged, e.g.
 *       {@code :desktop:run}.
 * </ol>
 */
final class PegasusBleWinLibrary {

    private static volatile boolean loaded;

    private PegasusBleWinLibrary() {}

    static synchronized void loadIfNeeded() throws IOException {
        if (loaded) {
            return;
        }
        File dll = bundledLocation();
        if (dll == null) {
            dll = propertiesLocation();
        }
        System.load(dll.getAbsolutePath());
        loaded = true;
    }

    private static File bundledLocation() {
        File appDir = ownJarDirectory();
        if (appDir == null) {
            return null;
        }
        File dll = new File(new File(appDir, "native"), "PegasusBleWin.dll");
        return dll.exists() ? dll : null;
    }

    /** The directory containing this class's own jar, or {@code null} when not run from a jar. */
    private static File ownJarDirectory() {
        CodeSource codeSource = PegasusBleWinLibrary.class.getProtectionDomain().getCodeSource();
        if (codeSource == null) {
            return null;
        }
        File location;
        try {
            location = new File(codeSource.getLocation().toURI());
        } catch (URISyntaxException e) {
            return null;
        }
        return location.isFile() ? location.getParentFile() : null;
    }

    private static File propertiesLocation() throws IOException {
        Properties props = new Properties();
        try (InputStream in =
                PegasusBleWinLibrary.class.getResourceAsStream("/pegasusBleWinHome.properties")) {
            if (in == null) {
                throw new FileNotFoundException(
                        "pegasusBleWinHome.properties not found on the classpath - "
                                + "run the :desktop:compilePegasusBleWin Gradle task first");
            }
            props.load(in);
        }
        File homeDir = new File(props.getProperty("pegasusBleWin.home"));
        File dll = new File(homeDir, props.getProperty("pegasusBleWin.lib"));
        if (!dll.exists()) {
            throw new FileNotFoundException(
                    "PegasusBleWin.dll not found at "
                            + dll
                            + " - run the :desktop:compilePegasusBleWin Gradle task first");
        }
        return dll;
    }
}
