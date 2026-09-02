/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.engine;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Reads the UCI engine's stdout line by line on its own thread and forwards each line to a
 * callback. Never touches UI state directly.
 */
final class EngineOutputReader implements Runnable {

    interface Callback {
        void onLine(String line);

        void onStreamClosed();
    }

    private final InputStream inputStream;
    private final Callback callback;

    EngineOutputReader(InputStream inputStream, Callback callback) {
        this.inputStream = inputStream;
        this.callback = callback;
    }

    @Override
    public void run() {
        try (BufferedReader reader =
                new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.US_ASCII))) {
            String line;
            while ((line = reader.readLine()) != null) {
                callback.onLine(line);
            }
        } catch (IOException ignored) {
            // Stream closed because the engine process ended; nothing to do.
        } finally {
            callback.onStreamClosed();
        }
    }
}
