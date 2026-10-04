/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.board;

/**
 * The platform's main (UI) thread as the board bridges need it: they run entirely on that thread
 * and post every transport callback and every timer onto it. Android backs this with a {@code
 * Handler}, desktop with the JavaFX Application Thread.
 */
public interface BoardScheduler {

    /** A delayed action that has not run yet and can still be called off. */
    interface Task {
        /** Keeps the action from running; no effect once it has run. Main thread only. */
        void cancel();
    }

    /** Runs {@code action} on the main thread; callable from any thread. */
    void post(Runnable action);

    /** Runs {@code action} on the main thread after {@code delayMs}. */
    Task postDelayed(Runnable action, long delayMs);

    /** Drops every pending delayed action. Nothing may be scheduled afterwards. */
    void shutdown();
}
