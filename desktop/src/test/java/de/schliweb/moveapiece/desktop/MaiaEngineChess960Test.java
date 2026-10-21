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
import de.schliweb.moveapiece.logic.Chess960;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

/**
 * Pins the Chess960 castling bonus ({@code MaiaMoveIndexer.CHESS960_CASTLE_BONUS}, see
 * MAIA_PROVENANCE.md) end to end through the bundled model: a position from a human Chess960 game
 * in which the player castled and in which Maia castles only because of the bonus.
 */
public class MaiaEngineChess960Test {

    /**
     * Chess960 position RBBQKNNR after 1. e4 c5 2. Ng1f3 d6 3. Nf1e3 Nf8e6, from a Lichess game
     * (August 2026) in which White castled here. Without the bonus Maia at 1700 plays c2c3 here.
     */
    @Test
    public void castlesOnlyWithTheBonus() throws Exception {
        assertEquals(
                "e1h1",
                bestMove(
                        Chess960.startFen(Chess960.numberOf("RBBQKNNR")),
                        "e2e4 c7c5 g1f3 d7d6 f1e3 f8e6",
                        1700));
    }

    private static String bestMove(String startFen, String moves, int elo) throws Exception {
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
        engine.setElo(elo);
        engine.setPosition(startFen, moves);
        engine.go();
        if (!moved.await(30, TimeUnit.SECONDS)) {
            fail("engine did not report a move in time");
        }
        if (error.get() != null) {
            throw error.get();
        }
        engine.shutdown();
        return bestMove.get();
    }

    private static InputStream openModel() throws IOException {
        InputStream in =
                MaiaEngineChess960Test.class.getResourceAsStream(MaiaRatings.MODEL_RESOURCE);
        if (in == null) {
            throw new IOException("Missing test resource: " + MaiaRatings.MODEL_RESOURCE);
        }
        return in;
    }
}
