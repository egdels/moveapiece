/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.engine;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Drives the *real* Stockfish subprocess installed alongside the app under test (the app's own
 * libstockfish.so, found via nativeLibraryDir) end to end over UCI. There is no meaningful way to
 * test process I/O plus the Handler/Looper callback marshalling without a real Android runtime, so
 * this runs on-device/emulator rather than as a plain JVM unit test.
 */
@RunWith(AndroidJUnit4.class)
public class StockfishEngineTest {

    private static final long TIMEOUT_SECONDS = 15;

    private StockfishEngine engine;

    @Before
    public void setUp() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String enginePath =
                context.getApplicationInfo().nativeLibraryDir + File.separator + "libstockfish.so";
        assertTrue(
                "engine binary should be installed as a native lib: " + enginePath,
                new File(enginePath).exists());
        engine = new StockfishEngine(enginePath, new Handler(Looper.getMainLooper())::post);
    }

    @After
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    private static class Recorder implements EngineListener {
        final CountDownLatch uciOk = new CountDownLatch(1);
        final CountDownLatch readyOk = new CountDownLatch(1);
        final CountDownLatch bestMove = new CountDownLatch(1);
        final AtomicReference<String> lastBestMove = new AtomicReference<>();
        final AtomicReference<Exception> error = new AtomicReference<>();

        @Override
        public void onUciOk() {
            uciOk.countDown();
        }

        @Override
        public void onReadyOk() {
            readyOk.countDown();
        }

        @Override
        public void onBestMove(String bestMoveUci, String ponderUci) {
            lastBestMove.set(bestMoveUci);
            bestMove.countDown();
        }

        @Override
        public void onInfo(String infoLine) {
            // ignored
        }

        @Override
        public void onEngineError(Exception e) {
            error.set(e);
        }
    }

    private void assertNoError(Recorder recorder) {
        Exception e = recorder.error.get();
        if (e != null) {
            fail("engine reported an error: " + e);
        }
    }

    /**
     * Starts the engine and waits for uciok. Does NOT load NNUE nets, so it must not be followed by
     * {@code go()}: the binary is built with NNUE_EMBEDDING_OFF (see app/stockfish.gradle) and has
     * no network to evaluate with until {@link StockfishEngine#setEvalFiles} is called, exactly
     * like {@code MainActivity.onUciOk()} always does before any search in the real app.
     */
    private Recorder startEngine() throws InterruptedException {
        Recorder recorder = new Recorder();
        engine.setListener(recorder);
        engine.start();
        assertTrue(
                "expected uciok within " + TIMEOUT_SECONDS + "s",
                recorder.uciOk.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertNoError(recorder);
        return recorder;
    }

    /**
     * Starts the engine, loads the real NNUE nets, and starts a new game - ready to {@code go()}.
     */
    private Recorder startReadyToPlayEngine() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        NnueAssets.Paths paths =
                NnueAssets.extractIfNeeded(
                        path -> context.getAssets().open(path), context.getFilesDir());

        Recorder recorder = startEngine();
        engine.setEvalFiles(paths.bigNetPath, paths.smallNetPath);
        engine.newGame();
        assertTrue(
                "expected readyok within " + TIMEOUT_SECONDS + "s",
                recorder.readyOk.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertNoError(recorder);
        return recorder;
    }

    @Test
    public void start_receivesUciOk() throws InterruptedException {
        startEngine();
    }

    @Test
    public void newGameWithoutEvalFiles_stillReceivesReadyOk() throws InterruptedException {
        // ucinewgame/isready must work even before any EvalFile is set -
        // MainActivity relies on this to detect "engine process is alive"
        // independently of the (separate, asset-extraction-dependent) NNUE
        // loading step.
        Recorder recorder = startEngine();

        engine.newGame();

        assertTrue(
                "expected readyok within " + TIMEOUT_SECONDS + "s",
                recorder.readyOk.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertNoError(recorder);
    }

    @Test
    public void goFromStartPosition_returnsLegalLookingMove() throws Exception {
        Recorder recorder = startReadyToPlayEngine();

        engine.setPosition("");
        engine.go(300);

        assertTrue(
                "expected bestmove within " + TIMEOUT_SECONDS + "s",
                recorder.bestMove.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertNoError(recorder);
        String move = recorder.lastBestMove.get();
        assertNotNull(move);
        assertTrue(
                "bestmove '" + move + "' should look like UCI notation",
                move.matches("[a-h][1-8][a-h][1-8][qrbn]?"));
    }

    @Test
    public void goAfterMoves_stillReturnsAMove() throws Exception {
        Recorder recorder = startReadyToPlayEngine();

        engine.setPosition("e2e4 e7e5");
        engine.go(300);

        assertTrue(recorder.bestMove.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertNoError(recorder);
        assertNotNull(recorder.lastBestMove.get());
    }
}
