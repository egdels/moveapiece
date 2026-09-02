/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.engine;

/**
 * Callbacks delivered by {@link StockfishEngine}. All callbacks are posted to the main thread, so
 * implementations may touch views directly.
 */
public interface EngineListener {

    void onUciOk();

    void onReadyOk();

    /**
     * @param bestMoveUci move in UCI notation (e.g. "e2e4"), or null if the engine had no move to
     *     offer
     * @param ponderUci the engine's predicted reply, or null
     */
    void onBestMove(String bestMoveUci, String ponderUci);

    /** Raw "info ..." line from the engine, e.g. for a "thinking" indicator. */
    void onInfo(String infoLine);

    void onEngineError(Exception error);
}
