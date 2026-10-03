/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.desktop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import de.schliweb.moveapiece.engine.MaiaEngine;
import de.schliweb.moveapiece.engine.MaiaEngineListener;
import de.schliweb.moveapiece.engine.MaiaRatings;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Runs {@link MaiaEngine} end to end through ONNX Runtime and compares its move with the one the
 * reference PyTorch implementation picks for the same game and rating. The expectations live in
 * {@code maia3_golden.txt}, written by {@code tools/maia3/export_onnx.py} (see MAIA_PROVENANCE.md);
 * only moves that lead the runner-up by at least five percentage points are in there.
 *
 * <p>The games cover openings, castling, a repeated position, promotions that are and are not the
 * best move, an en passant capture on offer, and a batch of random move sequences, each at five
 * ratings from 800 to 2400 - so the history, the flipped view for Black and the move slots all have
 * to be right.
 */
public class MaiaEngineGoldenTest {

    private static final String GOLDEN = "maia3_golden.txt";
    private static final long TIMEOUT_SECONDS = 30;

    private MaiaEngine engine;
    private final BlockingQueue<String> moves = new LinkedBlockingQueue<>();
    private final AtomicReference<Exception> error = new AtomicReference<>();

    @Before
    public void startEngine() throws Exception {
        engine = new MaiaEngine(Runnable::run); // no UI thread in this test - run inline
        CountDownLatch ready = new CountDownLatch(1);
        engine.setListener(
                new MaiaEngineListener() {
                    @Override
                    public void onReady() {
                        ready.countDown();
                    }

                    @Override
                    public void onBestMove(String bestMoveUci, float win, float draw, float loss) {
                        moves.add(bestMoveUci == null ? "(none)" : bestMoveUci);
                    }

                    @Override
                    public void onEngineError(Exception e) {
                        error.set(e);
                        ready.countDown();
                        moves.add("(error)");
                    }
                });
        try (InputStream model =
                MaiaEngineGoldenTest.class.getResourceAsStream(MaiaRatings.MODEL_RESOURCE)) {
            if (model == null) {
                throw new IOException("Missing test resource: " + MaiaRatings.MODEL_RESOURCE);
            }
            engine.start(model);
        }
        if (!ready.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            fail("engine did not become ready in time");
        }
        if (error.get() != null) {
            throw error.get();
        }
    }

    @After
    public void stopEngine() {
        engine.shutdown();
    }

    private String bestMove(int elo, String movesUci) throws Exception {
        engine.setElo(elo);
        engine.setPosition(movesUci);
        engine.go();
        String move = moves.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (move == null) {
            fail("engine did not report a move in time");
        }
        if (error.get() != null) {
            throw error.get();
        }
        return move;
    }

    @Test
    public void everyReferenceCase_picksTheReferenceMove() throws Exception {
        List<String> mismatches = new ArrayList<>();
        int cases = 0;
        try (BufferedReader reader =
                new BufferedReader(
                        new InputStreamReader(
                                MaiaEngineGoldenTest.class.getResourceAsStream(GOLDEN),
                                StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] fields = line.split(";", -1);
                int elo = Integer.parseInt(fields[0]);
                String expected = fields[1];
                String actual = bestMove(elo, fields[3]);
                cases++;
                if (!expected.equals(actual)) {
                    mismatches.add(line + " -> " + actual);
                }
            }
        }
        assertTrue("suspiciously few reference cases: " + cases, cases > 400);
        assertTrue(
                mismatches.size() + " of " + cases + " differ, first: " + first(mismatches),
                mismatches.isEmpty());
    }

    /**
     * The rating is an input of the one model, so it must change the answer without a reload. After
     * a2a4 h7h5 a4a5 h5h4 a5a6 h4h3 a6b7 h3g2 the reference promotes at once at 800 (b7a8q) but
     * first takes back on g2 at 1500 (f1g2).
     */
    @Test
    public void rating_changesTheMoveOnTheSameEngine() throws Exception {
        String line = "a2a4 h7h5 a4a5 h5h4 a5a6 h4h3 a6b7 h3g2";
        String at800 = bestMove(800, line);
        String at1500 = bestMove(1500, line);
        assertEquals("b7a8q", at800);
        assertEquals("f1g2", at1500);
        assertNotEquals(at800, at1500);
    }

    /** A game set up from a position starts its history there; the engine must still answer. */
    @Test
    public void positionFromFen_returnsALegalLookingMove() throws Exception {
        engine.setElo(1500);
        engine.setPosition("4k3/8/8/8/8/8/4P3/4K3 w - - 0 1", "");
        engine.go();
        String move = moves.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertTrue("unexpected move " + move, move != null && move.matches("e[12][a-h][1-8]"));
    }

    private static String first(List<String> items) {
        return items.isEmpty() ? "" : items.get(0);
    }
}
