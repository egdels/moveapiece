/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.engine;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import de.schliweb.moveapiece.logic.Chess960;
import de.schliweb.moveapiece.logic.ChessGame;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.junit.Test;

/**
 * Maia samples only its first {@link MaiaOpeningSampling#SAMPLED_MOVES} own moves of a new standard
 * game from the start position, and the sampling is reproducible with a fixed seed.
 */
public class MaiaOpeningSamplingTest {

    private static final double T = MaiaOpeningSampling.TEMPERATURE;

    @Test
    public void newStandardGame_samplesMaiasFirstTwoMovesWhicheverColour() {
        ChessGame game = new ChessGame();
        String[] moves = {"e2e4", "e7e5", "g1f3", "b8c6", "f1b5"};
        double[] expected = {T, T, T, T, 0};
        for (int ply = 0; ply < expected.length; ply++) {
            assertEquals(
                    "ply " + ply, expected[ply], MaiaOpeningSampling.temperature(game, true), 0);
            game.applyUciMove(moves[ply]);
        }
    }

    @Test
    public void afterUndo_samplesAgain() {
        ChessGame game = new ChessGame();
        for (String move : new String[] {"d2d4", "d7d5", "c2c4", "e7e6"}) {
            game.applyUciMove(move);
        }
        assertEquals(0, MaiaOpeningSampling.temperature(game, true), 0);
        game.undoLastMove();
        assertEquals(T, MaiaOpeningSampling.temperature(game, true), 0);
    }

    @Test
    public void chess960_neverSamples() {
        for (int number : new int[] {0, Chess960.STANDARD, 959}) {
            ChessGame game = new ChessGame();
            game.resetChess960(number);
            assertEquals("position " + number, 0, MaiaOpeningSampling.temperature(game, true), 0);
        }
    }

    @Test
    public void gameTakenOver_neverSamples() {
        // Free play after an opening drill, or an imported game: same start position, but Maia
        // did not start it.
        ChessGame game = new ChessGame();
        game.applyUciMove("e2e4");
        assertEquals(0, MaiaOpeningSampling.temperature(game, false), 0);
        game.reset();
        assertEquals(0, MaiaOpeningSampling.temperature(game, false), 0);
    }

    @Test
    public void positionFromBoardOrFen_neverSamples() {
        ChessGame game = new ChessGame();
        game.loadFen("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1");
        assertEquals(0, MaiaOpeningSampling.temperature(game, true), 0);
        assertEquals(0, MaiaOpeningSampling.temperature(game, false), 0);
    }

    @Test
    public void fixedSeed_samplesTheSameMoves() {
        float[] logits = {2.0f, 1.5f, 1.0f, 0.2f, -1.0f};
        int[] first = new int[50];
        int[] second = new int[50];
        Random a = new Random(4242);
        Random b = new Random(4242);
        for (int i = 0; i < first.length; i++) {
            first[i] = MaiaOpeningSampling.sample(logits, T, a);
            second[i] = MaiaOpeningSampling.sample(logits, T, b);
        }
        assertArrayEquals(first, second);
        Set<Integer> distinct = new HashSet<>();
        for (int i : first) {
            distinct.add(i);
        }
        assertTrue("sampling must vary, got " + distinct, distinct.size() > 1);
    }

    @Test
    public void sample_followsTheSoftmaxOfTheLogits() {
        float[] logits = {(float) Math.log(3), 0f}; // probabilities 0.75 and 0.25
        Random random = new Random(7);
        int first = 0;
        int n = 20000;
        for (int i = 0; i < n; i++) {
            first += MaiaOpeningSampling.sample(logits, 1.0, random) == 0 ? 1 : 0;
        }
        assertEquals(0.75, first / (double) n, 0.02);
    }
}
