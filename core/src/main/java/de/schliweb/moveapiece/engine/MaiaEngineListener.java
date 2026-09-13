/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.engine;

/**
 * Callbacks delivered by {@link MaiaEngine}. All callbacks are posted to the main thread, so
 * implementations may touch views directly.
 *
 * <p>Deliberately not {@link EngineListener}: Maia has no UCI handshake ({@code onUciOk}/{@code
 * onReadyOk} would be meaningless) and no iterative search log ({@code onInfo} raw UCI lines don't
 * exist for a single forward pass) - reusing that interface would mean either faking those
 * semantics or silently no-op-ing half of it, both worse than a small interface that only claims
 * what Maia actually does.
 */
public interface MaiaEngineListener {

    /** The ONNX session finished loading and the engine is ready for {@link MaiaEngine#go()}. */
    void onReady();

    /**
     * @param bestMoveUci the chosen move in real board UCI notation (e.g. "e2e4", "e1g1" for
     *     castling - already translated back from Maia's internal "king captures rook" convention),
     *     or {@code null} if the position had no legal moves
     * @param winProbability Maia's own win/draw/loss estimate for the position it was asked about,
     *     from the side-to-move's perspective - informational only, not used to choose the move
     *     (see the value-head discussion in MAIA_PROVENANCE_TEMPLATE.md for why)
     */
    void onBestMove(String bestMoveUci, float winProbability, float drawProbability, float lossProbability);

    void onEngineError(Exception error);
}
