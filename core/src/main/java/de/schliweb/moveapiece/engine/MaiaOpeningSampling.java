/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.engine;

import de.schliweb.moveapiece.logic.ChessGame;
import java.util.Random;

/**
 * Some variety in Maia's openings: its highest-scoring move alone would open every standard game
 * the same way. For its first {@link #SAMPLED_MOVES} own moves of a new standard game Maia samples
 * from the network's own move distribution ({@link #TEMPERATURE}), then plays its top move again.
 * Measured in tools/maia960-eval/REPORT.md ("Study: more variety in Maia's standard openings"):
 * human-like first moves at no significant strength loss, 0.06 % of the sampled moves losing more
 * than 200 cp.
 */
public final class MaiaOpeningSampling {

    /** How many of Maia's own moves are sampled. */
    public static final int SAMPLED_MOVES = 2;

    /** The sampling temperature; 1.0 is the network's own distribution. */
    public static final double TEMPERATURE = 1.0;

    private MaiaOpeningSampling() {}

    /**
     * The temperature for Maia's next move in {@code game}: {@link #TEMPERATURE} for its first
     * {@link #SAMPLED_MOVES} moves of a standard game from the start position, 0 (its top move)
     * otherwise - always in Chess960 and in a game taken over from training, a board position or an
     * import.
     *
     * @param newGame whether the Maia game began with New Game from its first move
     */
    public static double temperature(ChessGame game, boolean newGame) {
        boolean standardStart = !game.isChess960() && game.startsFromStandardPosition();
        // Ply 0 and 1 are each side's first move, so Maia's n-th own move is played at ply
        // 2(n-1) or 2(n-1)+1 whichever colour it has.
        return newGame && standardStart && game.moveCount() < 2 * SAMPLED_MOVES ? TEMPERATURE : 0.0;
    }

    /**
     * Index of a move sampled with weights exp(logit / {@code temperature}).
     *
     * @param temperature greater than 0
     */
    static int sample(float[] logits, double temperature, Random random) {
        double max = Double.NEGATIVE_INFINITY;
        for (float logit : logits) {
            max = Math.max(max, logit);
        }
        double[] weights = new double[logits.length];
        double sum = 0;
        for (int i = 0; i < logits.length; i++) {
            weights[i] = Math.exp((logits[i] - max) / temperature);
            sum += weights[i];
        }
        double pick = random.nextDouble() * sum;
        double acc = 0;
        for (int i = 0; i < weights.length; i++) {
            acc += weights[i];
            if (pick <= acc) {
                return i;
            }
        }
        return weights.length - 1;
    }
}
