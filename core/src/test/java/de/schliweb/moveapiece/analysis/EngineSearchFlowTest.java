/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.analysis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.github.bhlangonijr.chesslib.Side;
import de.schliweb.moveapiece.engine.StockfishEngine;
import de.schliweb.moveapiece.logic.ChessGame;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

/**
 * Drives {@link EngineSearchFlow} with a fake engine that records every command, and plays the
 * engine's answers by hand: which search a "bestmove" belongs to, what is discarded as stale, and
 * what the host is told.
 */
public class EngineSearchFlowTest {

    /** Records the commands instead of talking to a process. */
    private static final class FakeEngine extends StockfishEngine {
        final List<String> commands = new ArrayList<>();

        FakeEngine() {
            super("unused", Runnable::run);
        }

        @Override
        public void stop() {
            commands.add("stop");
        }

        @Override
        public void setPosition(String startFen, String movesUci) {
            commands.add("position " + movesUci);
        }

        @Override
        public void go(int movetimeMs) {
            commands.add("go " + movetimeMs);
        }

        @Override
        public void setFullStrength() {
            commands.add("fullStrength");
        }

        @Override
        public void setMultiPv(int lines) {
            commands.add("multipv " + lines);
        }

        int count(String prefix) {
            int n = 0;
            for (String command : commands) {
                if (command.startsWith(prefix)) {
                    n++;
                }
            }
            return n;
        }
    }

    private static final class FakeHost implements EngineSearchFlow.Host {
        final List<String> events = new ArrayList<>();
        boolean engineReady = true;
        boolean training;
        boolean evaluationEnabled = true;
        boolean boardInteractive = true;
        boolean engineAboutToMove;
        Side autoSide;
        List<String> reportMoves;
        List<Integer> reportEvals;

        @Override
        public boolean isEngineReady() {
            return engineReady;
        }

        @Override
        public boolean isTrainingMode() {
            return training;
        }

        @Override
        public boolean isEvaluationEnabled() {
            return evaluationEnabled;
        }

        @Override
        public boolean isBoardInteractive() {
            return boardInteractive;
        }

        @Override
        public boolean isEngineAboutToMove() {
            return engineAboutToMove;
        }

        @Override
        public Side autoMoveSide() {
            return autoSide;
        }

        @Override
        public void restoreEngineStrength() {
            events.add("restoreStrength");
        }

        @Override
        public void onEngineMove(String uci) {
            events.add("engineMove:" + uci);
        }

        @Override
        public void onHint(String bestMoveUci) {
            events.add("hint:" + bestMoveUci);
        }

        @Override
        public void onHintAvailabilityChanged() {}

        @Override
        public void onEvaluation(int whiteRelativeCp) {
            events.add("eval:" + whiteRelativeCp);
        }

        @Override
        public void onEvaluationMate(int whiteRelativeMateIn) {
            events.add("mate:" + whiteRelativeMateIn);
        }

        @Override
        public void onMoveQuality(MoveQuality quality, int cpLoss) {
            events.add("quality:" + quality + ":" + cpLoss);
        }

        @Override
        public void onPostGameProgress() {}

        @Override
        public void onPostGameReport(List<String> uciMoves, List<Integer> evals) {
            reportMoves = uciMoves;
            reportEvals = evals;
            events.add("report");
        }

        @Override
        public void onSearchesAbandoned() {
            events.add("abandoned");
        }
    }

    private final ChessGame game = new ChessGame();
    private final FakeEngine engine = new FakeEngine();
    private final FakeHost host = new FakeHost();
    private final EngineSearchFlow flow = new EngineSearchFlow(game, host);

    @Before
    public void attachEngine() {
        flow.setEngine(engine);
    }

    @Test
    public void engineMoveSearch_reportsTheBestMove() {
        game.applyUciMove("e2e4");

        flow.startEngineMoveSearch(1200);
        flow.onBestMove("e7e5");

        assertEquals(Arrays.asList("stop", "position e2e4", "go 1200"), engine.commands);
        // Its own move is the one search that runs at the opponent's strength.
        assertEquals(Arrays.asList("restoreStrength", "engineMove:e7e5"), host.events);
    }

    @Test
    public void engineMoveSearch_reportsNullWhenTheEngineHasNoMove() {
        flow.startEngineMoveSearch(1200);
        flow.onBestMove("(none)");

        assertEquals(Arrays.asList("restoreStrength", "engineMove:null"), host.events);
    }

    @Test
    public void analysis_isSkippedWhenNotWantedOrRedundant() {
        host.evaluationEnabled = false;
        flow.maybeStartAnalysis();
        host.evaluationEnabled = true;
        host.training = true;
        flow.maybeStartAnalysis();
        host.training = false;
        host.engineReady = false;
        flow.maybeStartAnalysis();
        host.engineReady = true;
        host.engineAboutToMove = true;
        flow.maybeStartAnalysis();

        assertTrue(engine.commands.isEmpty());

        host.engineAboutToMove = false;
        flow.maybeStartAnalysis();

        assertEquals(1, engine.count("go"));
    }

    @Test
    public void evaluation_isSearchedAtFullStrengthWhateverTheOpponentsElo() {
        flow.startEngineMoveSearch(1200);
        flow.onBestMove("e2e4");
        game.applyUciMove("e2e4");

        flow.maybeStartAnalysis();

        assertEquals(
                Arrays.asList(
                        "stop",
                        "position ",
                        "go 1200",
                        "stop",
                        "fullStrength",
                        "position e2e4",
                        "go 1500"),
                engine.commands);
        assertEquals(1, java.util.Collections.frequency(host.events, "restoreStrength"));
    }

    @Test
    public void evaluation_isReportedForWhiteWhicheverSideIsToMove() {
        flow.maybeStartAnalysis();
        flow.onInfo("info depth 10 score cp 30 pv e2e4");
        flow.onBestMove("e2e4");
        game.applyUciMove("e2e4");
        flow.maybeStartAnalysis();
        flow.onInfo("info depth 10 score cp -25 pv e7e5");
        flow.onInfo("info depth 11 score mate 3 pv e7e5");

        assertEquals(Arrays.asList("eval:30", "eval:25", "mate:-3"), host.events);
    }

    @Test
    public void aReplyToAnAbandonedSearchIsDiscarded() {
        flow.startEngineMoveSearch(1200);
        flow.abandonPendingSearches();
        flow.onInfo("info depth 10 score cp 30 pv e2e4");
        flow.onBestMove("e2e4");

        assertEquals(Arrays.asList("restoreStrength", "abandoned"), host.events);
    }

    @Test
    public void repliesArriveInTheOrderTheSearchesWereStarted() {
        // An analysis search is still running when the engine's own move is asked for: Stockfish
        // answers the stopped one first, and that answer must not be taken for the move.
        flow.maybeStartAnalysis();
        flow.startEngineMoveSearch(1200);

        flow.onBestMove("g1f3");
        assertEquals(Arrays.asList("restoreStrength"), host.events);
        flow.onBestMove("e2e4");

        assertEquals(Arrays.asList("restoreStrength", "engineMove:e2e4"), host.events);
    }

    @Test
    public void engineMoveSearch_runsOncePerPosition() {
        // A new game asks for the engine's move itself and again when the engine reports ready.
        flow.startEngineMoveSearch(1200);
        flow.startEngineMoveSearch(1200);

        assertEquals(1, engine.count("go"));
        flow.onBestMove("e2e4");
        assertEquals(Arrays.asList("restoreStrength", "engineMove:e2e4"), host.events);

        game.applyUciMove("e2e4");
        game.applyUciMove("e7e5");
        flow.startEngineMoveSearch(1200);
        assertEquals(2, engine.count("go"));
    }

    @Test
    public void evaluation_ignoresLinesOfASearchReplacedByANewerOne() {
        flow.maybeStartAnalysis();
        flow.onInfo("info depth 10 score cp 200 pv e2e4");
        flow.recordMoveQualityBaseline();
        game.applyUciMove("e2e4");
        flow.maybeStartAnalysis();
        // Stockfish has not got round to the "stop" yet: one more line about the position before
        // e4. It is neither Black's evaluation nor the result of White's move.
        flow.onInfo("info depth 12 score cp 200 pv e2e4");
        flow.onBestMove("e2e4");

        assertEquals(Arrays.asList("eval:200", "quality:null:0"), host.events);

        flow.onInfo("info depth 10 score cp -190 pv e7e5");
        flow.onBestMove("e7e5");

        assertEquals(
                Arrays.asList("eval:200", "quality:null:0", "eval:190", "quality:null:10"),
                host.events);
    }

    @Test
    public void evaluation_ignoresALineArrivingAfterTheLastMoveOfTheGame() {
        for (String uci : new String[] {"f2f3", "e7e5", "g2g4"}) {
            game.applyUciMove(uci);
        }
        flow.maybeStartAnalysis();
        flow.onInfo("info depth 10 score mate 1 pv d8h4");
        game.applyUciMove("d8h4");
        flow.maybeStartAnalysis(); // game over: nothing left to search

        flow.onInfo("info depth 11 score mate 1 pv d8h4");

        assertEquals(Arrays.asList("mate:-1"), host.events);
    }

    @Test
    public void moveQuality_gradesTheMoveOnceTheResultingPositionIsEvaluated() {
        flow.maybeStartAnalysis();
        flow.onInfo("info depth 10 score cp 30 pv e2e4");
        flow.onBestMove("e2e4");

        flow.recordMoveQualityBaseline();
        game.applyUciMove("f2f3");
        flow.maybeStartAnalysis();
        // Black, now to move, is 130 ahead: White went from +30 to -130.
        flow.onInfo("info depth 10 score cp 130 pv e7e5");
        flow.onBestMove("e7e5");

        assertEquals(
                Arrays.asList("eval:30", "quality:null:0", "eval:-130", "quality:MISTAKE:160"),
                host.events);
    }

    @Test
    public void moveQuality_isNotGradedWithoutAnEvalOfThePositionLeft() {
        flow.recordMoveQualityBaseline();
        game.applyUciMove("f2f3");
        flow.maybeStartAnalysis();
        flow.onInfo("info depth 10 score cp 130 pv e7e5");
        flow.onBestMove("e7e5");

        assertEquals(Arrays.asList("quality:null:0", "eval:-130"), host.events);
    }

    @Test
    public void moveQuality_gradesThePlayersMoveWhenTheOpponentRepliesBeforeTheSearchIsThrough() {
        // Maia answers within 1.4 s, the search of the position takes 1.5 s.
        host.autoSide = Side.BLACK;
        flow.maybeStartAnalysis();
        flow.onInfo("info depth 10 score cp 30 pv e2e4");
        flow.onBestMove("e2e4");
        flow.recordMoveQualityBaseline();
        game.applyUciMove("f2f3");
        flow.maybeStartAnalysis();
        flow.onInfo("info depth 10 score cp 130 pv e7e5");

        flow.recordMoveQualityBaseline();
        game.applyUciMove("e7e5");
        flow.maybeStartAnalysis();
        flow.onBestMove("e7e5");
        flow.onInfo("info depth 10 score cp -140 pv d2d4");
        flow.onBestMove("d2d4");

        // The grade is White's; Black's reply is neither graded nor does it take the grade down.
        assertEquals(
                Arrays.asList(
                        "eval:30",
                        "quality:null:0",
                        "eval:-130",
                        "quality:MISTAKE:160",
                        "eval:-140"),
                host.events);
    }

    @Test
    public void moveQuality_thePlayersGradeOutlastsTheEnginesOwnReply() {
        host.autoSide = Side.BLACK;
        flow.maybeStartAnalysis();
        flow.onInfo("info depth 10 score cp 30 pv e2e4");
        flow.onBestMove("e2e4");
        flow.recordMoveQualityBaseline();
        game.applyUciMove("f2f3");
        flow.startEngineMoveSearch(1200);
        flow.onInfo("info depth 10 score cp 130 pv e7e5");
        flow.onBestMove("e7e5");
        // What the host does with the engine's move:
        flow.recordMoveQualityBaseline();
        game.applyUciMove("e7e5");

        assertEquals(
                Arrays.asList(
                        "eval:30",
                        "quality:null:0",
                        "restoreStrength",
                        "eval:-130",
                        "quality:MISTAKE:160",
                        "engineMove:e7e5"),
                host.events);

        // The player's next move takes it down.
        flow.recordMoveQualityBaseline();
        assertEquals("quality:null:0", host.events.get(host.events.size() - 1));
    }

    @Test
    public void hint_searchesAtFullStrengthAndCollectsTheAlternatives() {
        flow.requestHint();
        assertTrue(flow.isWaitingForHint());
        flow.requestHint(); // a second press while waiting changes nothing

        assertEquals(
                Arrays.asList("stop", "fullStrength", "multipv 3", "position ", "go 1500"),
                engine.commands);

        flow.onInfo("info depth 10 multipv 1 score cp 40 pv e2e4 e7e5");
        flow.onInfo("info depth 10 multipv 2 score cp 30 pv d2d4 d7d5");
        flow.onBestMove("e2e4");

        assertFalse(flow.isWaitingForHint());
        assertEquals(Arrays.asList("hint:e2e4"), host.events);
        assertEquals("d2d4", flow.hintCandidates().move(1));
        assertEquals("multipv 1", engine.commands.get(engine.commands.size() - 1));
    }

    @Test
    public void hint_isGivenUpWhenThePositionMovesOn() {
        flow.requestHint();
        flow.onInfo("info depth 10 multipv 1 score cp 40 pv e2e4 e7e5");
        game.applyUciMove("d2d4");
        flow.maybeStartAnalysis();

        assertFalse(flow.isWaitingForHint());
        // MultiPV is back to 1 before the new search's "go".
        assertEquals(
                Arrays.asList(
                        "stop",
                        "fullStrength",
                        "multipv 3",
                        "position ",
                        "go 1500",
                        "stop",
                        "multipv 1",
                        "fullStrength",
                        "position d2d4",
                        "go 1500"),
                engine.commands);

        flow.onInfo("info depth 11 multipv 2 score cp 10 pv d2d4 d7d5");
        flow.onBestMove("e2e4");
        flow.onInfo("info depth 10 multipv 1 score cp -20 pv d7d5");

        // No arrow for the position left, and the stopped search's lines are no evaluation.
        assertEquals(Arrays.asList("eval:20"), host.events);
    }

    @Test
    public void hint_isNotCutShortByAnEvaluationOfTheSamePosition() {
        flow.requestHint();
        flow.maybeStartAnalysis();

        assertTrue(flow.isWaitingForHint());
        assertEquals(1, engine.count("go"));
    }

    @Test
    public void hint_isRefusedWhileTheBoardCannotBePlayedOn() {
        host.boardInteractive = false;

        flow.requestHint();

        assertFalse(flow.isWaitingForHint());
        assertTrue(engine.commands.isEmpty());
    }

    @Test
    public void abandoningAHintSearchRestoresMultiPv() {
        flow.requestHint();

        flow.abandonPendingSearches();

        assertFalse(flow.isWaitingForHint());
        assertEquals("multipv 1", engine.commands.get(engine.commands.size() - 1));
        // Once the stopped hint search has answered, the next search's info lines are an
        // evaluation again, not hint candidates.
        flow.maybeStartAnalysis();
        flow.onBestMove("e2e4");
        flow.onInfo("info depth 10 score cp 30 pv e2e4");
        assertEquals(Arrays.asList("abandoned", "eval:30"), host.events);
    }

    @Test
    public void postGameAnalysis_replaysEveryPositionAndReportsTheEvals() {
        game.applyUciMove("e2e4");
        game.applyUciMove("e7e5");
        int generationBefore = flow.generation();

        flow.startPostGameAnalysis();

        assertTrue(flow.isPostGameAnalysisRunning());
        assertTrue(flow.generation() != generationBefore);
        assertEquals(3, flow.postGamePositionsTotal());
        int[] scores = {20, -35, 30};
        for (int i = 0; i < scores.length; i++) {
            assertEquals(i, flow.postGamePositionsDone());
            flow.onInfo("info depth 8 score cp " + scores[i] + " pv a2a3");
            flow.onBestMove("a2a3");
        }

        assertFalse(flow.isPostGameAnalysisRunning());
        assertEquals(Arrays.asList("e2e4", "e7e5"), host.reportMoves);
        assertEquals(Arrays.asList(20, -35, 30), host.reportEvals);
        assertEquals(Arrays.asList("abandoned", "report"), host.events);
        assertTrue(
                engine.commands.containsAll(
                        Arrays.asList("position ", "position e2e4", "position e2e4 e7e5")));
        assertEquals(3, engine.count("go 400"));
    }

    @Test
    public void postGameAnalysis_scoresAFinalCheckmateWithoutSearchingIt() {
        for (String uci : new String[] {"f2f3", "e7e5", "g2g4", "d8h4"}) {
            game.applyUciMove(uci);
        }
        assertTrue(game.isCheckmate());

        flow.startPostGameAnalysis();
        for (int i = 0; i < 4; i++) {
            flow.onInfo("info depth 8 score cp 0 pv a2a3");
            flow.onBestMove("a2a3");
        }

        assertEquals(4, engine.count("go"));
        // White, to move in the final position, is mated.
        assertEquals(Integer.valueOf(-MoveQuality.mateToCp(0)), host.reportEvals.get(4));
    }

    @Test
    public void postGameAnalysis_needsMovesToReplay() {
        flow.startPostGameAnalysis();

        assertFalse(flow.isPostGameAnalysisRunning());
        assertTrue(engine.commands.isEmpty());
    }

    @Test
    public void engineError_dropsEverythingQueued() {
        flow.requestHint();

        flow.onEngineError();
        flow.onBestMove("e2e4");

        assertFalse(flow.isWaitingForHint());
        assertTrue(host.events.isEmpty());
        assertNull(flow.hintCandidates().move(0));
    }

    @Test
    public void abandoningBeforeAnEngineExistsIsHarmless() {
        EngineSearchFlow withoutEngine = new EngineSearchFlow(game, host);

        withoutEngine.abandonPendingSearches();

        assertEquals(Arrays.asList("abandoned"), host.events);
    }
}
