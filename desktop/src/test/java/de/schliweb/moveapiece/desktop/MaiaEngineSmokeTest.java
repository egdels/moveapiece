/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.desktop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import de.schliweb.moveapiece.engine.MaiaEngine;
import de.schliweb.moveapiece.engine.MaiaEngineListener;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

/**
 * First real, end-to-end correctness check of {@link MaiaEngine} against the bundled {@code
 * maia-1500.onnx} (see MAIA_PROVENANCE_TEMPLATE.md for its provenance) - runs the actual ONNX
 * Runtime inference, not a mock.
 *
 * <p>Only covers the starting position, which is the <b>simplest</b> case for {@code
 * MaiaPositionEncoder} (White to move, no mirroring, full castling rights on both sides, no real
 * history yet, no en passant) - it does not exercise the reasoned-but-not-yet-cross-checked
 * multi-ply/Black-to-move history mirroring flagged in that class's Javadoc. A pass here is a
 * meaningful first signal (bitboard-to-plane bit order, tensor shape, castling/aux planes, the
 * policy index round-trip all have to be right for this to work at all) but is not the full
 * golden-test battery MAIA_PROVENANCE_TEMPLATE.md still lists as open.
 *
 * <p>The expected move comes from running lc0 itself natively (not this code) on the same network
 * and position during the provenance investigation: {@code bestmove e2e4} with 50.22% policy mass,
 * far ahead of the second choice (d2d4, 23.34%) - see MAIA_PROVENANCE_TEMPLATE.md.
 */
public class MaiaEngineSmokeTest {

    @Test
    public void startingPosition_topMoveMatchesNativeLc0Output() throws Exception {
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
        InputStream in = MaiaEngineSmokeTest.class.getResourceAsStream("maia/maia-1500.onnx");
        if (in == null) {
            throw new IOException("Missing test resource: maia/maia-1500.onnx");
        }
        return in;
    }
}
