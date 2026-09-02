/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.training;

import com.github.bhlangonijr.chesslib.Side;

/**
 * Tracks progress through a fixed {@link OpeningLine} for one side. Assumes the line starts from
 * the standard starting position (White to move first), true for every line in {@link
 * OpeningRepository}.
 */
public class TrainingSession {

    private final OpeningLine line;
    private final Side humanSide;
    private final boolean hintsEnabled;
    private int plyIndex = 0;

    public TrainingSession(OpeningLine line, Side humanSide, boolean hintsEnabled) {
        this.line = line;
        this.humanSide = humanSide;
        this.hintsEnabled = hintsEnabled;
    }

    public OpeningLine line() {
        return line;
    }

    public Side humanSide() {
        return humanSide;
    }

    /** Whether the trainee's own moves get a visible hint (LEDs / on-screen highlight). */
    public boolean hintsEnabled() {
        return hintsEnabled;
    }

    public int plyIndex() {
        return plyIndex;
    }

    public int totalPlies() {
        return line.uciMoves().size();
    }

    public boolean isComplete() {
        return plyIndex >= totalPlies();
    }

    public String currentExpectedUci() {
        return isComplete() ? null : line.uciMoves().get(plyIndex);
    }

    public boolean isHumanTurnNow() {
        Side sideToMove = plyIndex % 2 == 0 ? Side.WHITE : Side.BLACK;
        return sideToMove == humanSide;
    }

    public void advance() {
        plyIndex++;
    }

    /** Steps back one ply, for undo. No-op if already at the start of the line. */
    public void retreat() {
        if (plyIndex > 0) {
            plyIndex--;
        }
    }
}
