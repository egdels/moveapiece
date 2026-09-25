/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.training;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.github.bhlangonijr.chesslib.Side;
import de.schliweb.moveapiece.logic.ChessGame;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

/**
 * The trainer/board transitions, each one a case that was reached on real hardware on 2026-09-25 or
 * is its mirror image. Drives {@link TrainingFlow} with a real {@link ChessGame} and fakes for the
 * board and the screen.
 */
public class TrainingFlowTest {

    private static final String START_FEN =
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    /** 1. e4 e5 2. Nf3 Nc6 3. Bb5 a6 4. Ba4 Nf6 5. O-O Be7 - the Ruy Lopez line of the trainer. */
    private static final OpeningLine RUY_LOPEZ =
            new OpeningLine(
                    "ruy_lopez",
                    List.of(
                            "e2e4", "e7e5", "g1f3", "b8c6", "f1b5", "a7a6", "b5a4", "g8f6", "e1g1",
                            "f8e7"));

    /** 1. e4 d5 2. exd5 - the book side captures on its second move. */
    private static final OpeningLine SCANDINAVIAN =
            new OpeningLine("scandinavian", List.of("e2e4", "d7d5", "e4d5"));

    /** A two-ply line: complete after the trainee's first move. */
    private static final OpeningLine SHORT = new OpeningLine("short", List.of("e2e4", "e7e5"));

    private static final class FakeBoard implements TrainingFlow.Board {
        boolean connected = true;
        boolean inSync = true;
        boolean guideActive;

        /** Whether guideMove() actually starts a guide (false = the bridge ignored it). */
        boolean guidesStart = true;

        String trackedFen = START_FEN;
        final List<String> guided = new ArrayList<>();
        final List<Boolean> guidedWithLeds = new ArrayList<>();
        final List<String> synced = new ArrayList<>();

        @Override
        public boolean isConnected() {
            return connected;
        }

        @Override
        public boolean isInSync() {
            return inSync;
        }

        @Override
        public boolean isGuideActive() {
            return guideActive;
        }

        @Override
        public String trackedFen() {
            return trackedFen;
        }

        @Override
        public void guideMove(String uci, boolean showLeds) {
            guided.add(uci);
            guidedWithLeds.add(showLeds);
            if (guidesStart) {
                guideActive = true;
            }
        }

        @Override
        public void syncToPosition(String fen) {
            synced.add(fen);
            trackedFen = fen;
        }

        /** The board finished the active guide: the bridge tracks the resulting position now. */
        void finishGuide(String resultingFen) {
            guideActive = false;
            trackedFen = resultingFen;
        }
    }

    private static final class FakeHost implements TrainingFlow.Host {
        final List<String> applied = new ArrayList<>(); // "uci/capture/sound"
        final List<Boolean> sounds = new ArrayList<>();
        int refreshes;
        int rewrites;
        boolean completeShown;
        Runnable scheduled;
        long scheduledDelayMs;
        int cancelled;
        final List<String> logs = new ArrayList<>();

        @Override
        public void moveApplied(String uci, boolean wasCapture, boolean withSound) {
            applied.add(uci + "/" + wasCapture + "/" + withSound);
        }

        @Override
        public void playMoveSound(boolean wasCapture) {
            sounds.add(wasCapture);
        }

        @Override
        public void historyRewritten() {
            rewrites++;
        }

        @Override
        public void refresh() {
            refreshes++;
        }

        @Override
        public void showTrainingComplete() {
            completeShown = true;
        }

        @Override
        public void scheduleBookMove(Runnable action, long delayMs) {
            scheduled = action;
            scheduledDelayMs = delayMs;
        }

        @Override
        public void cancelScheduledBookMove() {
            cancelled++;
            scheduled = null;
        }

        @Override
        public void log(String message) {
            logs.add(message);
        }
    }

    private final ChessGame game = new ChessGame();
    private final FakeBoard board = new FakeBoard();
    private final FakeHost host = new FakeHost();
    private final TrainingFlow flow = new TrainingFlow(game, board, host);

    private void start(OpeningLine line, Side side) {
        flow.start(line, side, true);
        flow.advance();
    }

    // ------------------------------------------------------------ connected board

    @Test
    public void bookMove_appliedSilentlyGuidedAndSoundedOnConfirmation() {
        start(RUY_LOPEZ, Side.BLACK);

        assertEquals(List.of("e2e4/false/false"), host.applied);
        assertEquals(List.of("e2e4"), board.guided);
        assertEquals(List.of(true), board.guidedWithLeds);
        assertTrue(flow.isBookMovePending());
        assertTrue(host.sounds.isEmpty());
        assertEquals(0, flow.session().plyIndex());

        board.finishGuide(game.toFen());
        assertTrue(flow.onGuidanceComplete());

        assertEquals(List.of(false), host.sounds);
        assertFalse(flow.isBookMovePending());
        assertEquals(1, flow.session().plyIndex());
        // ... and the trainee's own move is guided right away, hints on.
        assertEquals(List.of("e2e4", "e7e5"), board.guided);
        assertEquals(List.of(true, true), board.guidedWithLeds);
    }

    @Test
    public void humanGuidedMove_appliedWithSoundOnConfirmationThenNextBookMove() {
        start(RUY_LOPEZ, Side.BLACK);
        board.finishGuide(game.toFen());
        flow.onGuidanceComplete(); // e4 confirmed, e7e5 guided

        board.finishGuide(null);
        flow.onGuidanceComplete(); // e5 played on the board

        assertEquals(
                List.of("e2e4/false/false", "e7e5/false/true", "g1f3/false/false"), host.applied);
        assertEquals(2, flow.session().plyIndex());
        assertEquals("g1f3", board.guided.get(2));
    }

    @Test
    public void quizMode_guidesHumanMoveWithoutLeds() {
        flow.start(RUY_LOPEZ, Side.WHITE, false);
        flow.advance();
        assertEquals(List.of("e2e4"), board.guided);
        assertEquals(List.of(false), board.guidedWithLeds);
        assertTrue(host.applied.isEmpty());
    }

    @Test
    public void bookCapture_soundsAsCaptureOnConfirmation() {
        start(SCANDINAVIAN, Side.BLACK);
        board.finishGuide(game.toFen());
        flow.onGuidanceComplete(); // e4 confirmed, d7d5 guided
        board.finishGuide(null);
        flow.onGuidanceComplete(); // d5 played; book's e4d5 applied and guided

        assertEquals("e4d5/true/false", host.applied.get(2));
        board.finishGuide(game.toFen());
        flow.onGuidanceComplete();
        assertEquals(List.of(false, true), host.sounds);
    }

    @Test
    public void advance_doesNotRestartAnActiveGuide() {
        start(RUY_LOPEZ, Side.BLACK);
        flow.advance();
        flow.advance();
        assertEquals(List.of("e2e4"), board.guided);
        assertEquals(List.of("e2e4/false/false"), host.applied);

        board.finishGuide(game.toFen());
        flow.onGuidanceComplete();
        flow.advance();
        assertEquals(List.of("e2e4", "e7e5"), board.guided);
    }

    // ------------------------------------------------------------ board out of sync

    @Test
    public void bookMove_heldWhileBoardOutOfSync_playedOnResync() {
        board.inSync = false;
        start(RUY_LOPEZ, Side.BLACK);

        assertTrue(host.applied.isEmpty());
        assertTrue(board.guided.isEmpty());
        assertFalse(flow.isBookMovePending());
        assertTrue(flow.isAutoMoveHeld());
        assertEquals(1, host.logs.size());

        board.inSync = true;
        flow.onBoardInSync();

        assertEquals(List.of("e2e4/false/false"), host.applied);
        assertEquals(List.of("e2e4"), board.guided);
        assertTrue(flow.isBookMovePending());
        assertFalse(flow.isAutoMoveHeld());
    }

    @Test
    public void skippedGuide_isGuidedAgainOnceTheBoardShowsThePositionBeforeIt() {
        board.guidesStart = false; // e.g. a piece in hand: the bridge ignored the request
        start(RUY_LOPEZ, Side.BLACK);
        assertEquals(List.of("e2e4/false/false"), host.applied);
        assertTrue(flow.isBookMovePending());
        assertFalse(board.isGuideActive());

        board.guidesStart = true;
        board.trackedFen = START_FEN; // the board is back at the starting position
        flow.onBoardInSync();

        assertEquals(List.of("e2e4", "e2e4"), board.guided);
        assertTrue(board.isGuideActive());
        assertEquals(1, host.applied.size()); // not applied twice
        assertEquals(0, flow.session().plyIndex());
    }

    @Test
    public void skippedGuide_bookMoveAlreadyOnTheBoard_countsAsConfirmed() {
        board.guidesStart = false;
        start(RUY_LOPEZ, Side.BLACK);

        board.guidesStart = true;
        board.trackedFen = game.toFen(); // the board was set up to the position on screen
        flow.onBoardInSync();

        assertEquals(1, flow.session().plyIndex());
        assertEquals(List.of(false), host.sounds);
        assertFalse(flow.isBookMovePending());
        assertEquals(List.of("e2e4", "e7e5"), board.guided);
    }

    @Test
    public void humanGuideRetried_onResync() {
        start(RUY_LOPEZ, Side.WHITE); // trainee moves first
        assertEquals(List.of("e2e4"), board.guided);
        board.guideActive = false; // the bridge dropped it (board not in sync at the time)

        flow.onBoardInSync();
        assertEquals(List.of("e2e4", "e2e4"), board.guided);
    }

    // ------------------------------------------------------------ detection instead of guide

    @Test
    public void detectedExpectedMove_isAcceptedAndLineContinues() {
        start(RUY_LOPEZ, Side.WHITE);
        board.guideActive = false; // guide never started; the move arrives via detection

        flow.onPhysicalMoveConfirmed("e2e4");

        assertEquals("e2e4/false/true", host.applied.get(0));
        assertEquals(1, flow.session().plyIndex());
        assertEquals(List.of(game.toFen()).get(0).split(" ")[1], "w"); // book's e7e5 applied too
        assertEquals("e7e5/false/false", host.applied.get(1));
        assertEquals("e7e5", board.guided.get(1));
    }

    @Test
    public void detectedWrongMove_pullsBoardBackOntoTheGame() {
        start(RUY_LOPEZ, Side.WHITE);
        board.guideActive = false;

        flow.onPhysicalMoveConfirmed("d2d4");

        assertTrue(host.applied.isEmpty());
        assertEquals(0, flow.session().plyIndex());
        assertEquals(List.of(START_FEN), board.synced);
    }

    // ------------------------------------------------------------ no board

    @Test
    public void disconnected_bookMoveAutoPlaysAfterPauseAndTapAdvances() {
        board.connected = false;
        start(RUY_LOPEZ, Side.BLACK);

        assertTrue(host.applied.isEmpty());
        assertNotNull(host.scheduled);
        assertEquals(TrainingFlow.AUTO_MOVE_DELAY_MS, host.scheduledDelayMs);
        assertTrue(flow.isBookMoveScheduled());

        host.scheduled.run();
        assertEquals(List.of("e2e4/false/true"), host.applied);
        assertFalse(flow.isBookMoveScheduled());
        assertEquals(1, flow.session().plyIndex());
        assertTrue(board.guided.isEmpty());

        assertFalse(flow.onUserMove("d7d5")); // wrong tap: ignored
        assertTrue(flow.onUserMove("e7e5"));
        assertEquals("e7e5/false/true", host.applied.get(1));
        assertEquals(2, flow.session().plyIndex());
        assertNotNull(host.scheduled); // book reply queued again
    }

    @Test
    public void disconnected_undoCancelsTheQueuedBookMove() {
        board.connected = false;
        start(RUY_LOPEZ, Side.BLACK);
        host.scheduled.run(); // 1. e4
        flow.onUserMove("e7e5"); // queues 2. Nf3

        assertTrue(flow.undo());
        assertEquals(1, host.cancelled);
        assertNull(host.scheduled);
        // Undo lands on the trainee's turn: e5 is taken back, the book's 1. e4 stays.
        assertEquals(1, flow.session().plyIndex());
        assertEquals("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR", game.toFen().split(" ")[0]);
    }

    // ------------------------------------------------------------ undo / completion

    @Test
    public void undo_rollsBackAPendingBookMoveToo() {
        start(RUY_LOPEZ, Side.BLACK);
        board.finishGuide(game.toFen());
        flow.onGuidanceComplete(); // e4 confirmed
        board.finishGuide(null);
        flow.onGuidanceComplete(); // e5 confirmed, Nf3 applied + pending

        assertTrue(flow.isBookMovePending());
        assertTrue(flow.undo());

        assertFalse(flow.isBookMovePending());
        // The pending Nf3 and the trainee's e5 are both taken back; 1. e4 stays (trainee's turn).
        assertEquals(1, flow.session().plyIndex());
        assertEquals("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR", game.toFen().split(" ")[0]);
        assertEquals(1, host.rewrites);
        assertEquals(game.toFen(), board.synced.get(board.synced.size() - 1));
    }

    @Test
    public void completion_showsTheDialogOnce() {
        start(SHORT, Side.BLACK);
        board.finishGuide(game.toFen());
        flow.onGuidanceComplete(); // e4 confirmed, e7e5 guided
        board.finishGuide(null);
        flow.onGuidanceComplete(); // e5 played: line complete

        assertTrue(flow.session().isComplete());
        assertTrue(host.completeShown);
        assertEquals(2, host.applied.size());
    }

    @Test
    public void stop_dropsSessionAndTimer() {
        board.connected = false;
        start(RUY_LOPEZ, Side.BLACK);
        flow.stop();
        assertNull(flow.session());
        assertFalse(flow.isActive());
        assertEquals(1, host.cancelled);
        assertFalse(flow.onGuidanceComplete());
    }
}
