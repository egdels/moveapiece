/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.maia960eval;

import de.schliweb.moveapiece.engine.EngineListener;
import de.schliweb.moveapiece.engine.StockfishEngine;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/** The app's {@link StockfishEngine} as the opponent, driven synchronously, one per worker. */
final class SyncStockfish implements AutoCloseable {

    private static final String UCI_OK = "uciok";
    private static final String READY_OK = "readyok";

    private final StockfishEngine engine;
    private final BlockingQueue<Object> events = new LinkedBlockingQueue<>();
    private final long timeoutMs;

    SyncStockfish(String enginePath, String netPath, long timeoutMs) throws Exception {
        this.timeoutMs = timeoutMs;
        engine = new StockfishEngine(enginePath, Runnable::run);
        engine.setListener(
                new EngineListener() {
                    @Override
                    public void onUciOk() {
                        events.add(UCI_OK);
                    }

                    @Override
                    public void onReadyOk() {
                        events.add(READY_OK);
                    }

                    @Override
                    public void onBestMove(String bestMoveUci, String ponderUci) {
                        events.add(new BestMove(bestMoveUci));
                    }

                    @Override
                    public void onInfo(String infoLine) {}

                    @Override
                    public void onEngineError(Exception error) {
                        events.add(error);
                    }
                });
        engine.start();
        await(UCI_OK);
        engine.setEvalFile(netPath);
    }

    /** Like the app before a game: strength, castling rules, ucinewgame. */
    void newGame(int elo, boolean chess960) throws Exception {
        engine.setStrength(elo);
        engine.setChess960(chess960);
        engine.newGame();
        await(READY_OK);
    }

    String move(String startFen, String uciMoves, int movetimeMs) throws Exception {
        engine.setPosition(startFen, uciMoves);
        engine.go(movetimeMs);
        return ((BestMove) await(BestMove.class)).uci();
    }

    private Object await(Object expected) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (true) {
            long left = deadline - System.currentTimeMillis();
            Object event = left <= 0 ? null : events.poll(left, TimeUnit.MILLISECONDS);
            if (event == null) {
                throw new IOException("Stockfish did not answer within " + timeoutMs + " ms");
            }
            if (event instanceof Exception e) {
                throw e;
            }
            if (expected.equals(event) || (expected instanceof Class<?> c && c.isInstance(event))) {
                return event;
            }
        }
    }

    @Override
    public void close() {
        engine.shutdown();
    }

    private record BestMove(String uci) {}
}
