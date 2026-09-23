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
 * Wider golden-test battery beyond {@link MaiaEngineSmokeTest}'s starting position, specifically
 * targeting the parts of {@code MaiaPositionEncoder} its own Javadoc flagged as
 * "reasoned-but-not-yet-cross-checked": real multi-ply history, Black to move, castling rights that
 * have actually changed through play (not just the all-rights starting case), and the repetition
 * plane. See MAIA_PROVENANCE.md for how each expected move/ranking below was obtained (lc0 itself,
 * native {@code eigen} backend, {@code VerboseMoveStats}, same network) - none of it is guessed.
 *
 * <p>Not covered here: underpromotion in a live ONNX comparison (only unit-tested at the
 * move-string level in {@code MaiaMoveIndexerTest}) and a from-scratch FEN import (this project
 * always starts games from the standard position, so {@code MaiaPositionEncoder}'s "oldest history
 * entry is the starting position" zero-padding assumption is never exercised against anything
 * else).
 */
public class MaiaEngineGoldenTest {

    /**
     * 1.e4 e5 2.Nf3, Black to move - three real ply of history (not the trivial single-position
     * case {@link MaiaEngineSmokeTest} covers), still short of 8 plies so the zero-padding rule and
     * the multi-ply Black-to-move mirroring both have to be right at once for this to land on the
     * right move. lc0 native: b8c6 at 49.95% policy mass, far ahead of d7d6 (20.93%) and g8f6
     * (9.68%).
     */
    @Test
    public void multiPlyHistory_blackToMove() throws Exception {
        assertBestMove("e2e4 e7e5 g1f3", "b8c6");
    }

    /**
     * Same idea but with White having actually castled (1.e4 e5 2.Nf3 Nc6 3.Bc4 Bc5 4.O-O), so
     * White's castling planes must have flipped from "1" to "0" while Black's stayed "1" - the
     * starting-position smoke test can't distinguish "castling plane always 1" from "castling plane
     * correctly tracks rights", since every right is 1 there. lc0 native: g8f6 at 45.59%, d7d6 at
     * 25.21%.
     */
    @Test
    public void castlingRightsChanged_blackToMove() throws Exception {
        assertBestMove("e2e4 e7e5 g1f3 b8c6 f1c4 f8c5 e1g1", "g8f6");
    }

    /**
     * 1.Nf3 Nf6 2.Ng1 Ng8 returns to the exact starting position - but this time as the
     * <i>second</i> occurrence, so the repetition plane (base+12) must read "1" for the current
     * history step. This is a sensitive test: if the repetition plane (or the history feeding it)
     * is wrong, the engine would very likely fall back to fresh-game behavior and pick e2e4 (that
     * position's own top move when reached on move 1, per {@link MaiaEngineSmokeTest}) instead -
     * lc0 native output for the actual repeated position is a completely different distribution
     * (g1f3 15.08%, d2d4 14.56%, e2e4 only 12.11%), confirming the network really does react to the
     * repetition rather than just to the piece placement.
     */
    @Test
    public void repeatedStartingPosition_whiteToMove() throws Exception {
        assertBestMove("g1f3 g8f6 f3g1 f6g8", "g1f3");
    }

    /**
     * A cooperative pawn-race sequence (1.a4 h5 2.a5 h4 3.a6 h3 4.axb7 hxg2) legal-checked by
     * lc0/chesslib acceptance rather than by hand, reaching a position where White has two
     * promotion-capture options (b7xa8, b7xc8, each with all four promotion pieces) - unlike {@code
     * MaiaMoveIndexerTest}'s promotion coverage (move-string level only, e.g. "e7e8q"), this runs
     * those moves' real policy indices (1798/1799/1804/1805 for the queen/rook variants, close to
     * the end of the 1858-entry table, as expected for the "underpromotion" plane group) through
     * the actual ONNX model. lc0 native does <i>not</i> pick a promotion here - its top move is
     * f1g2 (bishop recaptures the pawn that just took on g2) at 45.95%, well ahead of both queen
     * promotions (b7a8q 29.74%, b7c8q 22.85%) - a good test precisely because the right answer
     * isn't "just grab the highest-index promotion", it's picking correctly among a mixed set.
     */
    @Test
    public void promotionMovesAvailable_notNecessarilyChosen() throws Exception {
        assertBestMove("a2a4 h7h5 a4a5 h5h4 a5a6 h4h3 a6b7 h3g2", "f1g2");
    }

    /**
     * Complements {@link #promotionMovesAvailable_notNecessarilyChosen} with the opposite case: a
     * position where promoting genuinely is the top move, not just legally available. Reached by
     * White's h-pawn capturing on g7 while Black shuffles a knight back and forth rather than
     * creating any competing threat (unlike the other promotion test's position, where a real
     * counter-threat - Black's own pawn one step from promoting - made a defensive bishop move the
     * actual top choice instead). lc0 native: g7h8q at 54.08%, ahead of the other capturing
     * promotion g7f8q (42.94%) - both promotions dominate every non-promoting legal move, which sit
     * at a small fraction of a percent each.
     */
    @Test
    public void promotionMove_isActuallyBest() throws Exception {
        assertBestMove("h2h4 b8c6 h4h5 c6b4 h5h6 b4c6 h6g7 c6e5", "g7h8q");
    }

    /**
     * {@link MaiaEngineSmokeTest} only exercises {@code maia-1500.onnx} in detail; this is a
     * lighter-weight sanity check that the other 8 bundled models (now individually selectable via
     * the desktop app's live rating slider, see {@link MaiaRatings}) are wired up correctly too -
     * same starting position, each rating's own real lc0-native top move rather than assuming they
     * all agree. All 9 happen to pick e2e4 here (a very common human first move at every skill
     * level), with policy mass tapering from ~51% at the lower ratings to ~44% at 1900 as d2d4 (and
     * other moves) pick up relative weight - not tested here since only the top move matters for
     * this sanity check, not the full distribution ({@link #multiPlyHistory_blackToMove} and
     * friends already cover distribution-sensitive cases in depth for 1500).
     */
    @Test
    public void otherRatingLevels_startingPositionTopMoveIsE2E4() throws Exception {
        for (int rating : MaiaRatings.ALL) {
            if (rating == 1500) {
                continue; // covered in full detail by MaiaEngineSmokeTest
            }
            assertBestMove(rating, "", "e2e4");
        }
    }

    private void assertBestMove(String movesUci, String expectedBestMove) throws Exception {
        assertBestMove(1500, movesUci, expectedBestMove);
    }

    private void assertBestMove(int rating, String movesUci, String expectedBestMove)
            throws Exception {
        MaiaEngine engine = new MaiaEngine(Runnable::run);
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

        try (InputStream model = openModel(rating)) {
            engine.start(model);
        }
        if (!ready.await(30, TimeUnit.SECONDS)) {
            fail("engine did not become ready in time");
        }
        if (error.get() != null) {
            throw error.get();
        }

        engine.setPosition(movesUci);
        engine.go();
        if (!moved.await(30, TimeUnit.SECONDS)) {
            fail("engine did not report a move in time");
        }
        if (error.get() != null) {
            throw error.get();
        }

        assertEquals(expectedBestMove, bestMove.get());
        engine.shutdown();
    }

    private static InputStream openModel(int rating) throws IOException {
        String resource = MaiaRatings.resourcePath(rating);
        InputStream in = MaiaEngineGoldenTest.class.getResourceAsStream(resource);
        if (in == null) {
            throw new IOException("Missing test resource: " + resource);
        }
        return in;
    }
}
