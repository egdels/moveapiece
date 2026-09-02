/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.desktop;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Resolves the host-built Stockfish binary and its NNUE net files, whose install directory is
 * recorded at build time by {@code desktop/stockfish.gradle}'s {@code writeStockfishHomeProperties}
 * task into a generated {@code stockfishHome.properties} classpath resource.
 */
final class StockfishLocator {

    record Location(File exe, File homeDir) {}

    private StockfishLocator() {}

    static Location locate() throws IOException {
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
