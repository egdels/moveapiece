/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.desktop;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.security.CodeSource;
import java.util.Properties;

/**
 * Resolves the host-built Stockfish binary and its NNUE net files.
 *
 * <p>Two possible locations, tried in order:
 *
 * <ol>
 *   <li>Bundled next to this class's own jar, in a {@code stockfish/} sibling directory - how a
 *       packaged app (jpackage app-image/DMG) ships it, see {@code desktop/packaging.gradle}.
 *       Stable regardless of which machine built or installed the package.
 *   <li>The build-time install directory recorded by {@code desktop/stockfish.gradle}'s {@code
 *       writeStockfishHomeProperties} task into a generated {@code stockfishHome.properties}
 *       classpath resource. Only valid on the machine that ran the build (an absolute path), so
 *       this is a fallback for running unpackaged, e.g. {@code :desktop:run}.
 * </ol>
 */
final class StockfishLocator {

    record Location(File exe, File homeDir) {}

    private StockfishLocator() {}

    static Location locate() throws IOException {
        Location bundled = bundledLocation();
        if (bundled != null) {
            return bundled;
        }
        return propertiesLocation();
    }

    private static Location bundledLocation() {
        File appDir = ownJarDirectory();
        if (appDir == null) {
            return null;
        }
        File homeDir = new File(appDir, "stockfish");
        for (String exeName : new String[] {"stockfish", "stockfish.exe"}) {
            File exe = new File(homeDir, exeName);
            if (exe.exists()) {
                return new Location(exe, homeDir);
            }
        }
        return null;
    }

    /** The directory containing this class's own jar, or {@code null} when not run from a jar. */
    private static File ownJarDirectory() {
        CodeSource codeSource = StockfishLocator.class.getProtectionDomain().getCodeSource();
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

    private static Location propertiesLocation() throws IOException {
        Properties props = new Properties();
        try (InputStream in =
                StockfishLocator.class.getResourceAsStream("/stockfishHome.properties")) {
            if (in == null) {
                throw new FileNotFoundException(
                        "stockfishHome.properties not found on the classpath - "
                                + "run the :desktop:buildStockfishHost Gradle task first");
            }
            props.load(in);
        }
        File homeDir = new File(props.getProperty("stockfish.home"));
        File exe = new File(homeDir, props.getProperty("stockfish.exe"));
        if (!exe.exists()) {
            throw new FileNotFoundException(
                    "Stockfish binary not found at "
                            + exe
                            + " - run the :desktop:buildStockfishHost Gradle task first");
        }
        return new Location(exe, homeDir);
    }
}
