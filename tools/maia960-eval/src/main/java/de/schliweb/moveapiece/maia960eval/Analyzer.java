/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.maia960eval;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Full-strength Stockfish searching to a fixed depth, the referee for blunders and adjudication.
 * Speaks UCI directly because the app's {@code StockfishEngine} only searches by time.
 */
final class Analyzer implements AutoCloseable {

    /** Score of a forced mate, in centipawns, from the side to move's point of view. */
    static final int MATE = 100_000;

    private final Process process;
    private final BufferedWriter stdin;
    private final BlockingQueue<String> lines = new LinkedBlockingQueue<>();
    private final int depth;
    private final long timeoutMs;

    Analyzer(String enginePath, String netPath, int depth, long timeoutMs) throws IOException {
        this.depth = depth;
        this.timeoutMs = timeoutMs;
        process = new ProcessBuilder(enginePath).redirectErrorStream(true).start();
        stdin =
                new BufferedWriter(
                        new OutputStreamWriter(
                                process.getOutputStream(), StandardCharsets.US_ASCII));
        Thread reader =
                new Thread(
                        () -> {
                            try (BufferedReader out =
                                    new BufferedReader(
                                            new InputStreamReader(
                                                    process.getInputStream(),
                                                    StandardCharsets.US_ASCII))) {
                                String line;
                                while ((line = out.readLine()) != null) {
                                    lines.add(line);
                                }
                            } catch (IOException ignored) {
                                // Process gone; the next await times out and reports it.
                            }
                        },
                        "analyzer-stdout");
        reader.setDaemon(true);
        reader.start();
        send("uci");
        awaitLine("uciok");
        send("setoption name EvalFile value " + netPath);
        send("setoption name Threads value 1");
        send("setoption name Hash value 16");
    }

    void newGame(boolean chess960) throws IOException {
        send("setoption name UCI_Chess960 value " + chess960);
        send("ucinewgame");
        send("isready");
        awaitLine("readyok");
    }

    /** Score of the position after {@code uciMoves}, for the side to move, mates as ±MATE. */
    int eval(String startFen, String uciMoves) throws IOException {
        send("position fen " + startFen + (uciMoves.isEmpty() ? "" : " moves " + uciMoves));
        send("go depth " + depth);
        int score = 0;
        while (true) {
            String line = awaitLine(null);
            if (line.startsWith("bestmove")) {
                return score;
            }
            String[] t = line.split(" ");
            for (int i = 0; i + 2 < t.length; i++) {
                if (t[i].equals("score")) {
                    int value = Integer.parseInt(t[i + 2]);
                    score = t[i + 1].equals("cp") ? value : (value > 0 ? MATE : -MATE);
                }
            }
        }
    }

    private void send(String command) throws IOException {
        stdin.write(command);
        stdin.write("\n");
        stdin.flush();
    }

    private String awaitLine(String exact) throws IOException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (true) {
            String line;
            try {
                long left = deadline - System.currentTimeMillis();
                line = left <= 0 ? null : lines.poll(left, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException(e);
            }
            if (line == null) {
                throw new IOException("Analyzer did not answer within " + timeoutMs + " ms");
            }
            if (exact == null || line.equals(exact)) {
                return line;
            }
        }
    }

    @Override
    public void close() {
        try {
            send("quit");
        } catch (IOException ignored) {
            // Already gone.
        }
        process.destroy();
    }
}
