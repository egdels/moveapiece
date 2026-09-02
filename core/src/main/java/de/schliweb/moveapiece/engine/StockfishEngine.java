/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.engine;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Thin wrapper around a Stockfish subprocess speaking the UCI protocol over stdin/stdout. All
 * process I/O happens on a dedicated background executor; callbacks to {@link EngineListener} are
 * always posted via the supplied {@code mainThreadDispatcher} (e.g. an Android {@code
 * Handler::post}, or JavaFX's {@code Platform::runLater} on desktop) so listeners can safely touch
 * UI state.
 */
public class StockfishEngine {

    private static final long SHUTDOWN_TIMEOUT_MS = 2000;

    private final String enginePath;
    private final Consumer<Runnable> mainThreadDispatcher;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

    private Process process;
    private BufferedWriter stdin;
    private volatile EngineListener listener;
    private volatile boolean shuttingDown;

    public StockfishEngine(String enginePath, Consumer<Runnable> mainThreadDispatcher) {
        this.enginePath = enginePath;
        this.mainThreadDispatcher = mainThreadDispatcher;
    }

    public void setListener(EngineListener listener) {
        this.listener = listener;
    }

    /** Starts the engine process and requests UCI mode. Call once. */
    public synchronized void start() {
        ioExecutor.execute(
                () -> {
                    try {
                        ProcessBuilder builder = new ProcessBuilder(enginePath);
                        builder.redirectErrorStream(true);
                        process = builder.start();
                        stdin =
                                new BufferedWriter(
                                        new OutputStreamWriter(
                                                process.getOutputStream(),
                                                StandardCharsets.US_ASCII));
                        EngineOutputReader reader =
                                new EngineOutputReader(
                                        process.getInputStream(),
                                        new EngineOutputReader.Callback() {
                                            @Override
                                            public void onLine(String line) {
                                                handleLine(line);
                                            }

                                            @Override
                                            public void onStreamClosed() {
                                                // Engine process ended; nothing further to read.
                                            }
                                        });
                        new Thread(reader, "stockfish-stdout").start();
                        writeLine("uci");
                    } catch (IOException e) {
                        notifyError(e);
                    }
                });
    }

    public void newGame() {
        writeLineAsync("ucinewgame");
        writeLineAsync("isready");
    }

    /**
     * Points the engine at the NNUE network files extracted from APK assets by {@link NnueAssets}
     * (the binary is built with NNUE_EMBEDDING_OFF, so it has no networks of its own). Call once,
     * right after {@code uciok} and before the first {@link #newGame()}.
     */
    public void setEvalFiles(String bigNetPath, String smallNetPath) {
        writeLineAsync("setoption name EvalFile value " + bigNetPath);
        writeLineAsync("setoption name EvalFileSmall value " + smallNetPath);
    }

    /** Limits engine strength to the given Elo (range enforced by Stockfish: ~1320-3190). */
    public void setStrength(int elo) {
        writeLineAsync("setoption name UCI_LimitStrength value true");
        writeLineAsync("setoption name UCI_Elo value " + elo);
    }

    public void setFullStrength() {
        writeLineAsync("setoption name UCI_LimitStrength value false");
    }

    /**
     * @param movesUci space-separated UCI moves from the start position, may be empty
     */
    public void setPosition(String movesUci) {
        if (movesUci == null || movesUci.isEmpty()) {
            writeLineAsync("position startpos");
        } else {
            writeLineAsync("position startpos moves " + movesUci);
        }
    }

    public void go(int movetimeMs) {
        writeLineAsync("go movetime " + movetimeMs);
    }

    public void stop() {
        writeLineAsync("stop");
    }

    /**
     * Sends "quit" and terminates the process. The engine must not be used afterwards. Idempotent -
     * a second call is a no-op rather than trying to resubmit to the (by then shut down) {@link
     * #ioExecutor}, which would otherwise throw {@link
     * java.util.concurrent.RejectedExecutionException}.
     */
    public synchronized void shutdown() {
        if (shuttingDown) {
            return;
        }
        shuttingDown = true;
        writeLineAsync("quit");
        ioExecutor.execute(
                () -> {
                    if (process == null) {
                        return;
                    }
                    // Process#waitFor(long, TimeUnit) and #isAlive() both need API
                    // 26; minSdk is 24, so poll exitValue() (available since API 1;
                    // throws IllegalThreadStateException while still running).
                    long deadline = System.currentTimeMillis() + SHUTDOWN_TIMEOUT_MS;
                    try {
                        while (!hasExited(process) && System.currentTimeMillis() < deadline) {
                            Thread.sleep(50);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    if (!hasExited(process)) {
                        process.destroy();
                    }
                });
        ioExecutor.shutdown();
    }

    private static boolean hasExited(Process process) {
        try {
            process.exitValue();
            return true;
        } catch (IllegalThreadStateException stillRunning) {
            return false;
        }
    }

    private void writeLineAsync(String command) {
        ioExecutor.execute(() -> writeLine(command));
    }

    private void writeLine(String command) {
        if (stdin == null) {
            return;
        }
        try {
            stdin.write(command);
            stdin.write("\n");
            stdin.flush();
        } catch (IOException e) {
            if (!shuttingDown) {
                notifyError(e);
            }
        }
    }

    private void handleLine(String line) {
        if (line == null || line.isEmpty()) {
            return;
        }
        if (line.equals("uciok")) {
            post(EngineListener::onUciOk);
        } else if (line.equals("readyok")) {
            post(EngineListener::onReadyOk);
        } else if (line.startsWith("bestmove")) {
            String[] parts = line.split("\\s+");
            String best = parts.length > 1 ? parts[1] : null;
            String ponder = parts.length > 3 ? parts[3] : null;
            post(l -> l.onBestMove(best, ponder));
        } else if (line.startsWith("info")) {
            post(l -> l.onInfo(line));
        }
    }

    private void notifyError(Exception e) {
        post(l -> l.onEngineError(e));
    }

    private interface ListenerAction {
        void run(EngineListener listener);
    }

    private void post(ListenerAction action) {
        EngineListener l = listener;
        if (l == null) {
            return;
        }
        mainThreadDispatcher.accept(() -> action.run(l));
    }
}
