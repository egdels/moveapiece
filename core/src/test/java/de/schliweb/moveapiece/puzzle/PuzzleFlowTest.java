/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.puzzle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.github.bhlangonijr.chesslib.Side;
import de.schliweb.moveapiece.logic.ChessGame;
import de.schliweb.moveapiece.puzzle.PuzzleFlow.MoveResult;
import de.schliweb.moveapiece.puzzle.PuzzleFlow.State;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

/** Drives {@link PuzzleFlow} with a real {@link ChessGame} and a fake screen. */
public class PuzzleFlowTest {

    private static final String AFTER_E4 =
            "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1";

    /** 1. e4 d5 2. exd5 Qxd5 3. Nc3 - two moves for White to find, both answered by a capture. */
    private static final Puzzle SCANDINAVIAN =
            new Puzzle("scand", AFTER_E4, List.of("d7d5", "e4d5", "d8d5", "b1c3"), 1000, List.of());

    /** Back-rank mate in one: after ...a6, Re8#; Rd8# mates as well. */
    private static final Puzzle BACK_RANK =
            new Puzzle(
                    "backrank",
                    "6k1/p4ppp/8/8/8/8/5PPP/3RR1K1 b - - 0 1",
                    List.of("a7a6", "e1e8"),
                    600,
                    List.of("mate", "mateIn1"));

    private static final class FakeHost implements PuzzleFlow.Host {
        final List<String> applied = new ArrayList<>(); // "uci/capture"
        final List<String> wrong = new ArrayList<>();
        final List<String> rated = new ArrayList<>(); // "id/solved"
        final List<String> completed = new ArrayList<>(); // "id/solved"
        int rewrites;
        int refreshes;
        int cancels;
        Runnable scheduled;
        long scheduledDelayMs;

        @Override
        public void moveApplied(String uci, boolean wasCapture) {
            applied.add(uci + "/" + wasCapture);
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
        public void schedule(Runnable action, long delayMs) {
            scheduled = action;
            scheduledDelayMs = delayMs;
        }

        @Override
        public void cancelScheduled() {
            cancels++;
            scheduled = null;
        }

        @Override
        public void wrongMove(String uci) {
            wrong.add(uci);
        }

        @Override
        public void puzzleRated(Puzzle puzzle, boolean solved) {
            rated.add(puzzle.id() + "/" + solved);
        }

        @Override
        public void puzzleComplete(Puzzle puzzle, boolean solved) {
            completed.add(puzzle.id() + "/" + solved);
        }

        /** Runs the pending scheduled action, as the host's timer would. */
        void runScheduled() {
            Runnable action = scheduled;
            assertNotNull("nothing scheduled", action);
            scheduled = null;
            action.run();
        }
    }

    private ChessGame game;
    private FakeHost host;
    private PuzzleFlow flow;

    @Before
    public void setUp() {
        game = new ChessGame();
        host = new FakeHost();
        flow = new PuzzleFlow(game, host);
    }

    /** Starts {@code puzzle} and lets the app play its opening move. */
    private void startAndPlayOpening(Puzzle puzzle) {
        flow.start(puzzle);
        host.runScheduled();
    }

    @Test
    public void start_setsUpPositionAndSchedulesOpeningMove() {
        flow.start(SCANDINAVIAN);

        assertEquals(AFTER_E4, game.toFen());
        assertEquals(State.OPPONENT_TO_MOVE, flow.state());
        assertEquals(PuzzleFlow.OPENING_MOVE_MS, host.scheduledDelayMs);
        assertTrue(host.applied.isEmpty());

        host.runScheduled();

        assertEquals(List.of("d7d5/false"), host.applied);
        assertEquals(State.HUMAN_TO_MOVE, flow.state());
        assertEquals(Side.WHITE, game.sideToMove());
        assertEquals(SCANDINAVIAN.humanSide(), game.sideToMove());
    }

    @Test
    public void correctMoves_solvePuzzleAndRateItOnce() {
        startAndPlayOpening(SCANDINAVIAN);

        assertEquals(MoveResult.CORRECT, flow.onUserMove("e4d5"));
        assertEquals(State.OPPONENT_TO_MOVE, flow.state());
        assertEquals(PuzzleFlow.REPLY_MS, host.scheduledDelayMs);
        host.runScheduled();
        assertEquals(MoveResult.CORRECT, flow.onUserMove("b1c3"));

        assertEquals(List.of("d7d5/false", "e4d5/true", "d8d5/true", "b1c3/false"), host.applied);
        assertEquals(State.COMPLETE, flow.state());
        assertEquals(List.of("scand/true"), host.rated);
        assertEquals(List.of("scand/true"), host.completed);
        assertNull(flow.progress());
        assertNull(host.scheduled);
    }

    @Test
    public void wrongMove_isShownTakenBackAndRatesPuzzleAsLost() {
        startAndPlayOpening(SCANDINAVIAN);
        String before = game.toFen();

        assertEquals(MoveResult.WRONG, flow.onUserMove("g1f3"));

        assertEquals(State.WRONG_MOVE_SHOWN, flow.state());
        assertEquals(List.of("g1f3"), host.wrong);
        assertEquals(List.of("scand/false"), host.rated);
        assertEquals("g1f3", game.lastMoveUci());
        assertEquals(PuzzleFlow.WRONG_MOVE_MS, host.scheduledDelayMs);
        // No second move while the wrong one is on the board.
        assertEquals(MoveResult.IGNORED, flow.onUserMove("g8f6"));

        host.runScheduled();

        assertEquals(State.HUMAN_TO_MOVE, flow.state());
        assertEquals(before, game.toFen());
        assertTrue(flow.isFailed());
    }

    @Test
    public void solvingAfterMistake_completesWithoutSecondRating() {
        startAndPlayOpening(SCANDINAVIAN);
        flow.onUserMove("g1f3");
        host.runScheduled();
        flow.onUserMove("e4d5");
        host.runScheduled();
        flow.onUserMove("f1c4"); // a second mistake: still only one rating
        host.runScheduled();
        flow.onUserMove("b1c3");

        assertEquals(List.of("scand/false"), host.rated);
        assertEquals(List.of("scand/false"), host.completed);
        assertEquals(State.COMPLETE, flow.state());
    }

    @Test
    public void hint_returnsNextMoveAndRatesPuzzleAsLost() {
        startAndPlayOpening(SCANDINAVIAN);

        assertEquals("e4d5", flow.hint());
        assertEquals("e4d5", flow.hint());

        assertEquals(List.of("scand/false"), host.rated);
        assertEquals(State.HUMAN_TO_MOVE, flow.state());
    }

    @Test
    public void hint_isNullWhenNotTraineesTurn() {
        flow.start(SCANDINAVIAN);

        assertNull(flow.hint());
        assertTrue(host.rated.isEmpty());
    }

    @Test
    public void showSolution_playsRestOfLineStepByStep() {
        startAndPlayOpening(SCANDINAVIAN);

        assertTrue(flow.showSolution());

        assertEquals(State.SHOWING_SOLUTION, flow.state());
        assertEquals("e4d5/true", host.applied.get(host.applied.size() - 1));
        assertEquals(PuzzleFlow.SOLUTION_STEP_MS, host.scheduledDelayMs);
        assertEquals(MoveResult.IGNORED, flow.onUserMove("d8d5"));
        host.runScheduled();
        host.runScheduled();

        assertEquals(List.of("d7d5/false", "e4d5/true", "d8d5/true", "b1c3/false"), host.applied);
        assertEquals(State.COMPLETE, flow.state());
        assertEquals(List.of("scand/false"), host.rated);
        assertEquals(List.of("scand/false"), host.completed);
        assertFalse(flow.showSolution());
    }

    @Test
    public void showSolution_takesBackWrongMoveFirst() {
        startAndPlayOpening(SCANDINAVIAN);
        flow.onUserMove("g1f3");

        flow.showSolution();

        assertEquals("e4d5", game.lastMoveUci());
        host.runScheduled();
        host.runScheduled();
        assertEquals(State.COMPLETE, flow.state());
        assertEquals(List.of("scand/false"), host.rated);
    }

    @Test
    public void showSolution_beforeOpeningMove_playsWholeLine() {
        flow.start(SCANDINAVIAN);

        flow.showSolution();
        host.runScheduled();
        host.runScheduled();
        host.runScheduled();

        assertEquals(4, host.applied.size());
        assertEquals(State.COMPLETE, flow.state());
    }

    @Test
    public void mateInOne_storedMoveSolves() {
        startAndPlayOpening(BACK_RANK);

        assertEquals(MoveResult.CORRECT, flow.onUserMove("e1e8"));

        assertTrue(game.isCheckmate());
        assertEquals(List.of("backrank/true"), host.completed);
    }

    @Test
    public void mateInOne_anyMatingMoveSolves() {
        startAndPlayOpening(BACK_RANK);

        assertEquals(MoveResult.CORRECT, flow.onUserMove("d1d8"));

        assertTrue(game.isCheckmate());
        assertEquals(List.of("backrank/true"), host.rated);
        assertEquals(List.of("backrank/true"), host.completed);
    }

    @Test
    public void nonMatingAlternative_isWrong() {
        startAndPlayOpening(BACK_RANK);

        assertEquals(MoveResult.WRONG, flow.onUserMove("e1e7"));
    }

    /** Lichess puzzle jeC91: the stored solution underpromotes to a rook, which mates. */
    private static final Puzzle UNDERPROMOTION =
            new Puzzle(
                    "jeC91",
                    "5k2/2P1ppb1/K2n2p1/8/2NP1PPP/1r6/8/8 b - - 3 38",
                    List.of("d6c4", "c7c8r"),
                    640,
                    List.of("mate", "mateIn1", "promotion"));

    /** Lichess puzzle s59tt: the last move of the solution captures en passant. */
    private static final Puzzle EN_PASSANT =
            new Puzzle(
                    "s59tt",
                    "1r3rk1/5p1p/p5p1/3RP1P1/qp1Q4/2p1P3/PP3PP1/1K5R b - - 2 24",
                    List.of("c3b2", "d4h4", "h7h5", "g5h6"),
                    1917,
                    List.of("crushing", "enPassant"));

    /** Lichess puzzle k2BGn: Black's solution ends castling queenside. */
    private static final Puzzle CASTLING =
            new Puzzle(
                    "k2BGn",
                    "r3kr2/pppqbpQp/2bp3B/8/2B5/8/PPP2PPP/R4RK1 w q - 3 14",
                    List.of("c4f7", "f8f7", "g7g8", "f7f8", "h6f8", "e8c8"),
                    2597,
                    List.of("advantage", "castling"));

    @Test
    public void promotion_storedUnderpromotionSolves() {
        startAndPlayOpening(UNDERPROMOTION);

        assertEquals(MoveResult.CORRECT, flow.onUserMove("c7c8r"));
        assertEquals(List.of("jeC91/true"), host.completed);
    }

    @Test
    public void promotion_toQueenMatesTooAndSolves() {
        startAndPlayOpening(UNDERPROMOTION);

        assertEquals(MoveResult.CORRECT, flow.onUserMove("c7c8q"));
        assertTrue(game.isCheckmate());
    }

    @Test
    public void promotion_toPieceThatDoesNotMate_isWrong() {
        startAndPlayOpening(UNDERPROMOTION);

        assertEquals(MoveResult.WRONG, flow.onUserMove("c7c8b"));
        host.runScheduled();
        assertEquals(MoveResult.CORRECT, flow.onUserMove("c7c8r"));
        assertEquals(List.of("jeC91/false"), host.completed);
    }

    @Test
    public void enPassant_inSolutionIsAcceptedAndSoundsAsCapture() {
        startAndPlayOpening(EN_PASSANT);
        assertEquals(MoveResult.CORRECT, flow.onUserMove("d4h4"));
        host.runScheduled();

        assertEquals(MoveResult.CORRECT, flow.onUserMove("g5h6"));

        assertEquals("g5h6/true", host.applied.get(host.applied.size() - 1));
        assertEquals(List.of("s59tt/true"), host.completed);
    }

    @Test
    public void castling_inSolutionIsAccepted() {
        startAndPlayOpening(CASTLING);
        assertEquals(MoveResult.CORRECT, flow.onUserMove("f8f7"));
        host.runScheduled();
        assertEquals(MoveResult.CORRECT, flow.onUserMove("f7f8"));
        host.runScheduled();

        assertEquals(MoveResult.CORRECT, flow.onUserMove("e8c8"));

        assertEquals(List.of("k2BGn/true"), host.completed);
        assertTrue(game.toFen().startsWith("2kr"));
    }

    @Test
    public void movesOutsideTraineesTurnOrIllegal_areIgnored() {
        flow.start(SCANDINAVIAN);
        assertEquals(MoveResult.IGNORED, flow.onUserMove("e4d5")); // app still to move
        host.runScheduled();

        assertEquals(MoveResult.IGNORED, flow.onUserMove("e4e6")); // illegal
        assertEquals(MoveResult.IGNORED, flow.onUserMove("x"));
        assertEquals(MoveResult.IGNORED, flow.onUserMove("e9e5"));
        assertEquals(MoveResult.IGNORED, flow.onUserMove(null));

        assertTrue(host.rated.isEmpty());
        assertEquals(State.HUMAN_TO_MOVE, flow.state());
    }

    @Test
    public void upperCaseMove_isAccepted() {
        startAndPlayOpening(SCANDINAVIAN);

        assertEquals(MoveResult.CORRECT, flow.onUserMove("E4D5"));
    }

    @Test
    public void staleScheduledMove_ofPreviousPuzzleDoesNothing() {
        flow.start(SCANDINAVIAN);
        Runnable stale = host.scheduled;
        flow.start(BACK_RANK);

        stale.run();

        assertTrue(host.applied.isEmpty());
        assertEquals(State.OPPONENT_TO_MOVE, flow.state());
        host.runScheduled();
        assertEquals(List.of("a7a6/false"), host.applied);
    }

    @Test
    public void stop_dropsPuzzleAndPendingMove() {
        flow.start(SCANDINAVIAN);
        Runnable pending = host.scheduled;

        flow.stop();
        pending.run();

        assertFalse(flow.isActive());
        assertEquals(State.IDLE, flow.state());
        assertTrue(host.applied.isEmpty());
        assertNull(flow.progress());
    }

    @Test
    public void abandon_afterTheFirstMove_ratesUnratedPuzzleAsLost() {
        startAndPlayOpening(SCANDINAVIAN);

        flow.abandon();

        assertEquals(List.of("scand/false"), host.rated);
        assertFalse(flow.isActive());
        assertTrue(host.completed.isEmpty());
    }

    @Test
    public void abandon_beforeTheFirstMove_doesNotRate() {
        flow.start(SCANDINAVIAN);

        flow.abandon();

        assertTrue(host.rated.isEmpty());
        assertFalse(flow.isActive());
    }

    @Test
    public void abandon_ofRatedOrCompletePuzzle_doesNotRateAgain() {
        startAndPlayOpening(SCANDINAVIAN);
        flow.hint();
        flow.abandon();
        assertEquals(List.of("scand/false"), host.rated);

        startAndPlayOpening(BACK_RANK);
        flow.onUserMove("e1e8");
        flow.abandon();
        assertEquals(List.of("scand/false", "backrank/true"), host.rated);
    }

    @Test
    public void abandon_withoutPuzzle_isHarmless() {
        flow.abandon();

        assertTrue(host.rated.isEmpty());
        assertEquals(State.IDLE, flow.state());
    }

    @Test
    public void progress_tracksLineMovesOnly() {
        startAndPlayOpening(SCANDINAVIAN);
        assertEquals(new PuzzleFlow.Progress("scand", 1, false), flow.progress());

        flow.onUserMove("g1f3"); // wrong move on the board: not counted

        assertEquals(new PuzzleFlow.Progress("scand", 1, true), flow.progress());
    }

    @Test
    public void resume_onTraineesTurn_waitsForTrainee() {
        assertTrue(flow.resume(SCANDINAVIAN, 1, false));

        assertEquals(State.HUMAN_TO_MOVE, flow.state());
        assertNull(host.scheduled);
        assertTrue(host.applied.isEmpty()); // replayed silently
        assertEquals("d7d5", game.lastMoveUci());

        flow.onUserMove("e4d5");
        host.runScheduled();
        flow.onUserMove("b1c3");
        assertEquals(List.of("scand/true"), host.rated);
    }

    @Test
    public void resume_onAppsTurn_schedulesReplyAndDoesNotRateLostPuzzleAgain() {
        assertTrue(flow.resume(SCANDINAVIAN, 2, true));

        assertEquals(State.OPPONENT_TO_MOVE, flow.state());
        assertEquals(PuzzleFlow.REPLY_MS, host.scheduledDelayMs);
        host.runScheduled();
        flow.onUserMove("b1c3");

        assertTrue(host.rated.isEmpty());
        assertEquals(List.of("scand/false"), host.completed);
    }

    @Test
    public void resume_atStart_schedulesOpeningMove() {
        assertTrue(flow.resume(SCANDINAVIAN, 0, false));

        assertEquals(PuzzleFlow.OPENING_MOVE_MS, host.scheduledDelayMs);
    }

    @Test
    public void resume_outsideLine_fails() {
        assertFalse(flow.resume(SCANDINAVIAN, 4, false));
        assertFalse(flow.isActive());
        assertFalse(flow.resume(SCANDINAVIAN, -1, false));
        assertFalse(flow.isActive());
    }
}
