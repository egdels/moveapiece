/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.desktop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import de.schliweb.moveapiece.engine.MaiaEngine;
import de.schliweb.moveapiece.engine.MaiaEngineListener;
import de.schliweb.moveapiece.engine.MaiaRatings;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

/**
 * End-to-end check of {@link MaiaEngine} against the bundled Maia-3 model (see MAIA_PROVENANCE.md
 * for its provenance) - runs the actual ONNX Runtime inference, not a mock.
 *
 * <p>Only covers the starting position at the engine's default rating; {@code MaiaEngineGoldenTest}
 * is the wider battery. The expected move comes from the reference PyTorch implementation: e2e4
 * with 64 % of the policy mass at 1500.
 */
public class MaiaEngineSmokeTest {

    @Test
    public void startingPosition_topMoveMatchesReference() throws Exception {
        MaiaEngine engine = new MaiaEngine(Runnable::run); // no UI thread in this test - run inline
        CountDownLatch ready = new CountDownLatch(1);
        CountDownLatch moved = new CountDownLatch(1);
        AtomicReference<String> bestMove = new AtomicReference<>();
        AtomicReference<Exception> error = new AtomicReference<>();

        engine.setListener(
                new MaiaEngineListener() {
                    @Override
                    public void onReady() {
                        ready.countDown();
                    }

                    @Override
                    public void onBestMove(String bestMoveUci, float win, float draw, float loss) {
                        bestMove.set(bestMoveUci);
                        moved.countDown();
                    }

                    @Override
                    public void onEngineError(Exception e) {
                        error.set(e);
                        ready.countDown();
                        moved.countDown();
                    }
                });

        try (InputStream model = openModel()) {
            engine.start(model);
        }
        if (!ready.await(30, TimeUnit.SECONDS)) {
            fail("engine did not become ready in time");
        }
        if (error.get() != null) {
            throw error.get();
        }

        engine.go(); // temperature 0.0: deterministic top-policy move
        if (!moved.await(30, TimeUnit.SECONDS)) {
            fail("engine did not report a move in time");
        }
        if (error.get() != null) {
            throw error.get();
        }

        assertEquals("e2e4", bestMove.get());
        engine.shutdown();
    }

    private static InputStream openModel() throws IOException {
        InputStream in = MaiaEngineSmokeTest.class.getResourceAsStream(MaiaRatings.MODEL_RESOURCE);
        if (in == null) {
            throw new IOException("Missing test resource: " + MaiaRatings.MODEL_RESOURCE);
        }
        return in;
    }
}
