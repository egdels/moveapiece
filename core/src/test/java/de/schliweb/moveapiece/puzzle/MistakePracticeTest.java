/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.puzzle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.github.bhlangonijr.chesslib.Side;
import de.schliweb.moveapiece.analysis.MoveCandidate;
import de.schliweb.moveapiece.analysis.MoveQuality;
import de.schliweb.moveapiece.logic.ChessGame;
import de.schliweb.moveapiece.puzzle.MistakePractice.Mistake;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.Test;

/** {@link MistakePractice}, and its puzzles played through {@link PuzzleFlow}. */
public class MistakePracticeTest {

    private static final String START = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    /** 1. e4 e5 2. Qh5 Nc6 3. Bc4 Nf6?? 4. Qxf7# - Black's third move is the blunder. */
    private static final List<String> SCHOLARS_MATE =
            List.of("e2e4", "e7e5", "d1h5", "b8c6", "f1c4", "g8f6", "h5f7");

    private static final List<String> SCHOLARS_MATE_SAN =
            List.of("e4", "e5", "Qh5", "Nc6", "Bc4", "Nf6", "Qxf7#");

    /** Every move but 3...Nf6 loses under 50 cp; that one loses 850. */
    private static final List<Integer> SCHOLARS_MATE_EVALS =
            List.of(30, -30, 40, -40, 40, -50, 900, -MoveQuality.mateToCp(0));

    /** g6 is best, Qe7 nearly as good (20 cp), Qf6 not (90 cp). */
    private static List<List<MoveCandidate>> scholarsMateCandidates() {
        List<List<MoveCandidate>> candidates = new ArrayList<>();
        for (int i = 0; i < SCHOLARS_MATE_EVALS.size(); i++) {
            candidates.add(List.of(new MoveCandidate("a2a3", 0)));
        }
        candidates.set(
                5,
                List.of(
                        new MoveCandidate("g7g6", -60),
                        new MoveCandidate("d8e7", -80),
                        new MoveCandidate("d8f6", -150)));
        candidates.set(7, List.of()); // the final checkmate is not searched
        return candidates;
    }

    private static List<Mistake> scholarsMateMistakes(Set<Side> sides) {
        return MistakePractice.find(
                START,
                false,
                SCHOLARS_MATE,
                SCHOLARS_MATE_EVALS,
                scholarsMateCandidates(),
                SCHOLARS_MATE_SAN,
                sides);
    }

    @Test
    public void find_turnsTheBlunderIntoAPuzzleFromThePreviousMove() {
        List<Mistake> mistakes = scholarsMateMistakes(Set.of(Side.BLACK));

        assertEquals(1, mistakes.size());
        Mistake mistake = mistakes.get(0);
        assertEquals("3...", mistake.moveLabel());
        assertEquals("Nf6", mistake.playedSan());
        assertEquals(MoveQuality.BLUNDER, mistake.quality());

        Puzzle puzzle = mistake.puzzle();
        ChessGame beforeBc4 = new ChessGame();
        for (String uci : SCHOLARS_MATE.subList(0, 4)) {
            beforeBc4.applyUciMove(uci);
        }
        assertEquals(beforeBc4.toFen(), puzzle.fen());
        assertEquals(List.of("f1c4", "g7g6"), puzzle.moves());
        assertEquals(Side.BLACK, puzzle.humanSide());
        assertEquals(Set.of("d8e7"), puzzle.alsoAccepted());
        assertEquals("mistake-5", puzzle.id());
    }

    @Test
    public void find_leavesOutTheOtherSidesMistakes() {
        assertTrue(scholarsMateMistakes(Set.of(Side.WHITE)).isEmpty());
        assertEquals(1, scholarsMateMistakes(Set.of(Side.WHITE, Side.BLACK)).size());
    }

    @Test
    public void find_leavesOutInaccuracies() {
        List<Integer> evals = new ArrayList<>(SCHOLARS_MATE_EVALS);
        evals.set(6, 70); // 3...Nf6 now loses 120 cp: an inaccuracy only

        assertTrue(
                MistakePractice.find(
                                START,
                                false,
                                SCHOLARS_MATE,
                                evals,
                                scholarsMateCandidates(),
                                SCHOLARS_MATE_SAN,
                                Set.of(Side.BLACK))
                        .isEmpty());
    }

    @Test
    public void find_leavesOutAMistakeOnTheFirstMove() {
        List<Integer> evals = new ArrayList<>(SCHOLARS_MATE_EVALS);
        evals.set(1, 300); // 1. e4 now "loses" 330 cp

        List<Mistake> mistakes =
                MistakePractice.find(
                        START,
                        false,
                        SCHOLARS_MATE,
                        evals,
                        scholarsMateCandidates(),
                        SCHOLARS_MATE_SAN,
                        Set.of(Side.WHITE, Side.BLACK));

        for (Mistake mistake : mistakes) {
            assertTrue(!mistake.puzzle().id().equals("mistake-0"));
        }
    }

    @Test
    public void find_leavesOutAMistakeWhoseBestMoveIsTheOnePlayed() {
        List<List<MoveCandidate>> candidates = scholarsMateCandidates();
        candidates.set(5, List.of(new MoveCandidate("g8f6", -60)));

        assertTrue(
                MistakePractice.find(
                                START,
                                false,
                                SCHOLARS_MATE,
                                SCHOLARS_MATE_EVALS,
                                candidates,
                                SCHOLARS_MATE_SAN,
                                Set.of(Side.BLACK))
                        .isEmpty());
    }

    @Test
    public void find_leavesOutAMistakeWhosePlayedMoveIsNearlyAsGoodAsTheBest() {
        List<List<MoveCandidate>> candidates = scholarsMateCandidates();
        candidates.set(5, List.of(new MoveCandidate("g7g6", -60), new MoveCandidate("g8f6", -90)));

        assertTrue(
                MistakePractice.find(
                                START,
                                false,
                                SCHOLARS_MATE,
                                SCHOLARS_MATE_EVALS,
                                candidates,
                                SCHOLARS_MATE_SAN,
                                Set.of(Side.BLACK))
                        .isEmpty());
    }

    @Test
    public void isAccepted_withinHalfAPawnOrAnotherForcedMate() {
        MoveCandidate best = new MoveCandidate("a", 100);
        assertTrue(MistakePractice.isAccepted(best, new MoveCandidate("b", 50)));
        assertTrue(!MistakePractice.isAccepted(best, new MoveCandidate("c", 49)));

        MoveCandidate mateInTwo = new MoveCandidate("a", MoveQuality.mateToCp(2));
        assertTrue(
                MistakePractice.isAccepted(
                        mateInTwo, new MoveCandidate("b", MoveQuality.mateToCp(5))));
        assertTrue(!MistakePractice.isAccepted(mateInTwo, new MoveCandidate("c", 900)));
    }

    /** KQ vs K: a winning detour (mate in 8 instead of 5) is no mistake to practise. */
    @Test
    public void find_leavesOutALongerWayToMate() {
        String start = "7k/8/8/8/8/8/8/K5Q1 b - - 0 1";
        List<String> moves = List.of("h8h7", "a1b1");
        List<Integer> evals =
                List.of(
                        -MoveQuality.mateToCp(6),
                        MoveQuality.mateToCp(5),
                        -MoveQuality.mateToCp(8));
        List<List<MoveCandidate>> candidates =
                List.of(
                        List.of(new MoveCandidate("h8h7", -MoveQuality.mateToCp(6))),
                        List.of(new MoveCandidate("g1g5", MoveQuality.mateToCp(5))),
                        List.of(new MoveCandidate("h7h8", -MoveQuality.mateToCp(8))));

        assertTrue(
                MistakePractice.find(
                                start,
                                false,
                                moves,
                                evals,
                                candidates,
                                List.of(),
                                Set.of(Side.WHITE, Side.BLACK))
                        .isEmpty());
    }

    @Test
    public void decidedByMate_onlyWhenTheMateIsKeptOrTheGameWasLostAnyway() {
        int mateIn3 = MoveQuality.mateToCp(3);
        assertTrue(MistakePractice.decidedByMate(mateIn3, -MoveQuality.mateToCp(6)));
        assertTrue(MistakePractice.decidedByMate(-mateIn3, MoveQuality.mateToCp(1)));
        // Throwing a forced mate away is a mistake worth practising.
        assertTrue(!MistakePractice.decidedByMate(mateIn3, -200));
        assertTrue(!MistakePractice.decidedByMate(300, -100));
    }

    @Test
    public void find_withMismatchedLists_findsNothing() {
        assertTrue(
                MistakePractice.find(
                                START,
                                false,
                                SCHOLARS_MATE,
                                SCHOLARS_MATE_EVALS.subList(0, 5),
                                scholarsMateCandidates(),
                                SCHOLARS_MATE_SAN,
                                Set.of(Side.BLACK))
                        .isEmpty());
    }

    // ------------------------------------------------------------ played through PuzzleFlow

    private static final class SilentHost implements PuzzleFlow.Host {
        final List<String> completed = new ArrayList<>();
        Runnable scheduled;

        @Override
        public void moveApplied(String uci, boolean wasCapture) {}

        @Override
        public void historyRewritten() {}

        @Override
        public void refresh() {}

        @Override
        public void schedule(Runnable action, long delayMs) {
            scheduled = action;
        }

        @Override
        public void cancelScheduled() {
            scheduled = null;
        }

        @Override
        public void wrongMove(String uci) {}

        @Override
        public void puzzleRated(Puzzle puzzle, boolean solved) {}

        @Override
        public void puzzleComplete(Puzzle puzzle, boolean solved) {
            completed.add(puzzle.id() + "/" + solved);
        }

        void runScheduled() {
            Runnable action = scheduled;
            scheduled = null;
            action.run();
        }
    }

    private static PuzzleFlow started(Puzzle puzzle, ChessGame game, SilentHost host) {
        PuzzleFlow flow = new PuzzleFlow(game, host);
        flow.start(puzzle);
        host.runScheduled(); // the opponent's previous move
        return flow;
    }

    @Test
    public void flow_acceptsAMoveNearlyAsGoodAsTheBest() {
        Puzzle puzzle = scholarsMateMistakes(Set.of(Side.BLACK)).get(0).puzzle();
        SilentHost host = new SilentHost();

        PuzzleFlow flow = started(puzzle, new ChessGame(), host);

        assertEquals(PuzzleFlow.MoveResult.CORRECT, flow.onUserMove("d8e7"));
        assertEquals(List.of("mistake-5/true"), host.completed);
    }

    @Test
    public void flow_rejectsAMoveClearlyWorseThanTheBest() {
        Puzzle puzzle = scholarsMateMistakes(Set.of(Side.BLACK)).get(0).puzzle();

        PuzzleFlow flow = started(puzzle, new ChessGame(), new SilentHost());

        assertEquals(PuzzleFlow.MoveResult.WRONG, flow.onUserMove("d8f6"));
    }

    /**
     * A Chess960 game whose best reply was to castle: the trainee puts the king on its castling
     * square, as on the board, which solves the puzzle stored in the engine's notation (the king
     * onto its rook).
     */
    @Test
    public void flow_acceptsAChess960CastleMadeAsOnTheBoard() {
        String start = "r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R3K2R w HAha - 0 1";
        List<String> moves = List.of("a2a3", "h7h6");
        List<Integer> evals = List.of(10, -10, 200); // ...h6 loses 190 cp
        List<List<MoveCandidate>> candidates =
                List.of(
                        List.of(new MoveCandidate("a2a4", 10)),
                        List.of(new MoveCandidate("e8h8", -5)),
                        List.of(new MoveCandidate("a3a4", 200)));
        List<Mistake> mistakes =
                MistakePractice.find(
                        start, true, moves, evals, candidates, List.of(), Set.of(Side.BLACK));
        assertEquals(1, mistakes.size());
        Puzzle puzzle = mistakes.get(0).puzzle();
        assertTrue(puzzle.chess960());
        assertEquals("h7h6", mistakes.get(0).playedSan()); // no SAN given: shown in UCI

        ChessGame game = new ChessGame();
        SilentHost host = new SilentHost();
        PuzzleFlow flow = started(puzzle, game, host);

        assertEquals(PuzzleFlow.MoveResult.CORRECT, flow.onUserMove("e8g8"));
        assertEquals(List.of("mistake-1/true"), host.completed);
        assertEquals("e8h8", game.lastMoveUci());
        assertTrue(game.toFen().startsWith("r4rk1"));
    }
}
