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
 * Resolves and loads the native macOS CoreBluetooth/JNI library ({@code libPegasusBleMac.dylib})
 * used by {@link MacosPegasusBleTransport}.
 *
 * <p>Two possible locations, tried in order - mirrors {@code StockfishLocator}'s own lookup
 * exactly, for the same reasons:
 *
 * <ol>
 *   <li>Bundled next to this class's own jar, in a {@code native/} sibling directory - how a
 *       packaged app (jpackage app-image/DMG) ships it, see {@code desktop/packaging.gradle}.
 *   <li>The build-time output directory recorded by {@code desktop/pegasus-ble-macos.gradle}'s
 *       {@code writePegasusBleMacHomeProperties} task into a generated {@code
 *       pegasusBleMacHome.properties} classpath resource - a fallback for running unpackaged, e.g.
 *       {@code :desktop:run}.
 * </ol>
 */
final class PegasusBleMacLibrary {

    private static volatile boolean loaded;

    private PegasusBleMacLibrary() {}

    static synchronized void loadIfNeeded() throws IOException {
        if (loaded) {
            return;
        }
        File dylib = bundledLocation();
        if (dylib == null) {
            dylib = propertiesLocation();
        }
        System.load(dylib.getAbsolutePath());
        loaded = true;
    }

    private static File bundledLocation() {
        File appDir = ownJarDirectory();
        if (appDir == null) {
            return null;
        }
        File dylib = new File(new File(appDir, "native"), "libPegasusBleMac.dylib");
        return dylib.exists() ? dylib : null;
    }

    /** The directory containing this class's own jar, or {@code null} when not run from a jar. */
    private static File ownJarDirectory() {
        CodeSource codeSource = PegasusBleMacLibrary.class.getProtectionDomain().getCodeSource();
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
                PegasusBleMacLibrary.class.getResourceAsStream("/pegasusBleMacHome.properties")) {
            if (in == null) {
                throw new FileNotFoundException(
                        "pegasusBleMacHome.properties not found on the classpath - "
                                + "run the :desktop:compilePegasusBleMac Gradle task first");
            }
            props.load(in);
        }
        File homeDir = new File(props.getProperty("pegasusBleMac.home"));
        File dylib = new File(homeDir, props.getProperty("pegasusBleMac.lib"));
        if (!dylib.exists()) {
            throw new FileNotFoundException(
                    "libPegasusBleMac.dylib not found at "
                            + dylib
                            + " - run the :desktop:compilePegasusBleMac Gradle task first");
        }
        return dylib;
    }
}
