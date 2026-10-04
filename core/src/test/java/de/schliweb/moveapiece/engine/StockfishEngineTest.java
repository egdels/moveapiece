/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Test;

/**
 * {@link StockfishEngine}'s side of the UCI dialogue against a scripted process: which commands it
 * writes, how it reads the engine's lines, and what it reports when the process dies. The real
 * binary is covered by the instrumented test of the same name in :app.
 */
public class StockfishEngineTest {

    private static final long TIMEOUT_SECONDS = 5;

    /** A process that exists only as two pipes: the test reads its stdin and writes its stdout. */
    private static final class ScriptedProcess extends Process {
        private final PipedOutputStream stdin = new PipedOutputStream();
        private final PipedInputStream stdout = new PipedInputStream();
        private final PipedOutputStream stdoutFeed;
        final LinkedBlockingQueue<String> commands = new LinkedBlockingQueue<>();
        private volatile boolean exited;

        ScriptedProcess() throws IOException {
            stdoutFeed = new PipedOutputStream(stdout);
            PipedInputStream stdinTap = new PipedInputStream(stdin);
            Thread tap =
                    new Thread(
                            () -> {
                                try (BufferedReader reader =
                                        new BufferedReader(
                                                new InputStreamReader(
                                                        stdinTap, StandardCharsets.US_ASCII))) {
                                    String line;
                                    while ((line = reader.readLine()) != null) {
                                        commands.add(line);
                                    }
                                } catch (IOException ignored) {
                                    // The engine side closed or its writer thread ended.
                                }
                            },
                            "scripted-stdin");
            tap.setDaemon(true);
            tap.start();
        }

        /** The engine "prints" a line. */
        void print(String line) throws IOException {
            stdoutFeed.write((line + "\n").getBytes(StandardCharsets.US_ASCII));
            stdoutFeed.flush();
        }

        /** The engine process ends: its stdout closes. */
        void exit() throws IOException {
            exited = true;
            stdoutFeed.close();
        }

        @Override
        public OutputStream getOutputStream() {
            return stdin;
        }

        @Override
        public InputStream getInputStream() {
            return stdout;
        }

        @Override
        public InputStream getErrorStream() {
            return InputStream.nullInputStream();
        }

        @Override
        public int waitFor() {
            return 0;
        }

        @Override
        public int exitValue() {
            if (!exited) {
                throw new IllegalThreadStateException();
            }
            return 0;
        }

        @Override
        public void destroy() {
            exited = true;
        }
    }

    private static final class RecordingListener implements EngineListener {
        final LinkedBlockingQueue<String> events = new LinkedBlockingQueue<>();

        @Override
        public void onUciOk() {
            events.add("uciok");
        }

        @Override
        public void onReadyOk() {
            events.add("readyok");
        }

        @Override
        public void onBestMove(String bestMoveUci, String ponderUci) {
            events.add("bestmove:" + bestMoveUci + ":" + ponderUci);
        }

        @Override
        public void onInfo(String infoLine) {
            events.add(infoLine);
        }

        @Override
        public void onEngineError(Exception error) {
            events.add("error:" + error.getMessage());
        }
    }

    private final RecordingListener listener = new RecordingListener();
    private ScriptedProcess process;
    private StockfishEngine engine;

    @After
    public void shutDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    /** Starts the engine against a fresh scripted process and swallows its opening "uci". */
    private void startEngine() throws Exception {
        process = new ScriptedProcess();
        engine =
                new StockfishEngine("unused", Runnable::run) {
                    @Override
                    Process launchProcess() {
                        return process;
                    }
                };
        engine.setListener(listener);
        engine.start();
        assertEquals("uci", nextCommand());
    }

    private String nextCommand() throws InterruptedException {
        return process.commands.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private String nextEvent() throws InterruptedException {
        return listener.events.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    public void start_asksForUciModeAndReportsTheAnswer() throws Exception {
        startEngine();

        process.print("id name Stockfish");
        process.print("uciok");

        assertEquals("uciok", nextEvent());
    }

    @Test
    public void newGame_asksWhetherTheEngineIsReady() throws Exception {
        startEngine();

        engine.newGame();
        process.print("readyok");

        assertEquals("ucinewgame", nextCommand());
        assertEquals("isready", nextCommand());
        assertEquals("readyok", nextEvent());
    }

    @Test
    public void options_areSentAsSetoptionCommands() throws Exception {
        startEngine();

        engine.setEvalFile("/data/nn.nnue");
        engine.setStrength(1500);
        engine.setFullStrength();
        engine.setMultiPv(3);

        assertEquals("setoption name EvalFile value /data/nn.nnue", nextCommand());
        assertEquals("setoption name UCI_LimitStrength value true", nextCommand());
        assertEquals("setoption name UCI_Elo value 1500", nextCommand());
        assertEquals("setoption name UCI_LimitStrength value false", nextCommand());
        assertEquals("setoption name MultiPV value 3", nextCommand());
    }

    @Test
    public void setPosition_usesStartposForTheStandardStartAndFenOtherwise() throws Exception {
        startEngine();
        String standardStart = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
        String endgame = "8/8/8/4k3/8/8/4P3/4K3 w - - 0 1";

        engine.setPosition("");
        engine.setPosition("e2e4 e7e5");
        engine.setPosition(standardStart, "e2e4");
        engine.setPosition(endgame, null);
        engine.setPosition(endgame, "e2e4");

        assertEquals("position startpos", nextCommand());
        assertEquals("position startpos moves e2e4 e7e5", nextCommand());
        assertEquals("position startpos moves e2e4", nextCommand());
        assertEquals("position fen " + endgame, nextCommand());
        assertEquals("position fen " + endgame + " moves e2e4", nextCommand());
    }

    @Test
    public void goAndStop_areSentAndTheBestMoveIsParsed() throws Exception {
        startEngine();

        engine.go(1200);
        engine.stop();
        process.print("info depth 12 score cp 31 pv e2e4 e7e5");
        process.print("bestmove e2e4 ponder e7e5");
        process.print("bestmove g1f3");
        process.print("bestmove (none)");

        assertEquals("go movetime 1200", nextCommand());
        assertEquals("stop", nextCommand());
        assertEquals("info depth 12 score cp 31 pv e2e4 e7e5", nextEvent());
        assertEquals("bestmove:e2e4:e7e5", nextEvent());
        assertEquals("bestmove:g1f3:null", nextEvent());
        assertEquals("bestmove:(none):null", nextEvent());
    }

    @Test
    public void unknownAndEmptyLines_areIgnored() throws Exception {
        startEngine();

        process.print("");
        process.print("Stockfish 19 by the Stockfish developers");
        process.print("option name Hash type spin default 16");
        process.print("readyok");

        assertEquals("readyok", nextEvent());
    }

    @Test
    public void aProcessThatEndsOnACriticalError_reportsStockfishsOwnReason() throws Exception {
        startEngine();

        process.print("info string CRITICAL ERROR: Illegal move: e2e5");
        process.exit();

        assertEquals("error:Illegal move: e2e5", nextEvent());
    }

    @Test
    public void aProcessThatJustEnds_isReportedToo() throws Exception {
        startEngine();

        process.exit();

        assertEquals("error:Engine process ended unexpectedly", nextEvent());
    }

    @Test
    public void shutdown_sendsQuitAndIsNotAnError() throws Exception {
        startEngine();

        engine.shutdown();
        engine.shutdown(); // a second call is a no-op
        assertEquals("quit", nextCommand());
        process.exit();

        assertNull(listener.events.poll(300, TimeUnit.MILLISECONDS));
    }

    @Test
    public void aBinaryThatCannotBeLaunched_isReportedAsAnError() throws Exception {
        engine = new StockfishEngine("/nonexistent/stockfish-binary", Runnable::run);
        engine.setListener(listener);

        engine.start();

        String event = nextEvent();
        assertTrue(event, event != null && event.startsWith("error:"));
    }
}
