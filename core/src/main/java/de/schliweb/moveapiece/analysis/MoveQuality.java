/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.analysis;

/** How bad a move was, graded by the centipawns it lost against the engine's evaluation. */
public enum MoveQuality {
    INACCURACY(50),
    MISTAKE(150),
    BLUNDER(300);

    private final int minCpLoss;

    MoveQuality(int minCpLoss) {
        this.minCpLoss = minCpLoss;
    }

    /** The grade for a centipawn loss, or {@code null} if it is not notable enough to flag. */
    public static MoveQuality of(int cpLoss) {
        if (cpLoss >= BLUNDER.minCpLoss) {
            return BLUNDER;
        }
        if (cpLoss >= MISTAKE.minCpLoss) {
            return MISTAKE;
        }
        if (cpLoss >= INACCURACY.minCpLoss) {
            return INACCURACY;
        }
        return null;
    }

    /**
     * Converts a "mate in N" score to a centipawn-scale value that still dominates normal evals.
     */
    public static int mateToCp(int mateIn) {
        int magnitude = 100000 - Math.min(Math.abs(mateIn), 100) * 100;
        return mateIn >= 0 ? magnitude : -magnitude;
    }
}
