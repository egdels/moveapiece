/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.puzzle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.github.bhlangonijr.chesslib.Side;
import de.schliweb.moveapiece.analysis.MoveQuality;
import de.schliweb.moveapiece.logic.ChessGame;
import de.schliweb.moveapiece.puzzle.PuzzleSession.NextAction;
import de.schliweb.moveapiece.puzzle.PuzzleSession.Practice;
import de.schliweb.moveapiece.puzzle.PuzzleSession.Status;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.Before;
import org.junit.Test;

public class PuzzleSessionTest {

    private static final String AFTER_E4 =
            "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1";

    private static Puzzle puzzle(String id, int rating) {
        return new Puzzle(id, AFTER_E4, List.of("d7d5", "e4d5"), rating, List.of());
    }

    private static MistakePractice.Mistake mistake(String id) {
        return new MistakePractice.Mistake(puzzle(id, 0), "2.", "Nf3", MoveQuality.MISTAKE);
    }

    private static Practice<String> practice(int mistakes) {
        List<MistakePractice.Mistake> list = new ArrayList<>();
        for (int i = 0; i < mistakes; i++) {
            list.add(mistake("m" + i));
        }
        return new Practice<>(
                list, new ChessGame().snapshot(), false, "ENGINE", Side.BLACK, true, true);
    }

    private final List<PuzzleRating> savedRatings = new ArrayList<>();
    private final List<String> savedRecent = new ArrayList<>();
    private PuzzleSession<String> session;

    @Before
    public void setUp() {
        session =
                new PuzzleSession<>(
                        new PuzzleRating(1500, 50),
                        new RecentPuzzles(),
                        new PuzzleSession.Store() {
                            @Override
                            public void savePuzzleRating(PuzzleRating rating) {
                                savedRatings.add(rating);
                            }

                            @Override
                            public void saveRecentPuzzles(RecentPuzzles recent) {
                                savedRecent.add(recent.toKey());
                            }
                        });
    }

    @Test
    public void pickPuzzle_aimsAtRatingPlusDifficultyAndRemembersIt() {
        List<Puzzle> all = List.of(puzzle("low", 1500), puzzle("high", 1800));

        Puzzle picked = session.pickPuzzle(all, PuzzleDifficulty.HARDER, new Random(1));

        assertEquals("high", picked.id());
        assertEquals(List.of("high"), savedRecent);
        // Played lately: the other one comes next even though it is further away.
        assertEquals("low", session.pickPuzzle(all, PuzzleDifficulty.HARDER, new Random(1)).id());
    }

    @Test
    public void pickPuzzle_withoutPuzzlesIsNullAndSavesNothing() {
        assertNull(session.pickPuzzle(List.of(), PuzzleDifficulty.NORMAL, new Random(1)));
        assertTrue(savedRecent.isEmpty());
    }

    @Test
    public void puzzleRated_updatesAndSavesTheRatingWithItsChange() {
        Puzzle puzzle = puzzle("p", 1500);
        PuzzleRating expected = new PuzzleRating(1500, 50).after(1500, true);

        session.puzzleRated(puzzle, true);

        assertEquals(expected, session.rating());
        assertEquals(List.of(expected), savedRatings);
        assertEquals(
                Integer.valueOf(new PuzzleRating(1500, 50).delta(1500, true)),
                session.ratingDelta());
        session.puzzleStarted();
        assertNull(session.ratingDelta());
    }

    @Test
    public void practice_isNeverRated() {
        session.startPractice(practice(2));

        session.puzzleRated(puzzle("p", 1500), false);

        assertEquals(new PuzzleRating(1500, 50), session.rating());
        assertTrue(savedRatings.isEmpty());
        assertNull(session.ratingDelta());
    }

    @Test
    public void practice_walksThroughItsMistakesThenEnds() {
        Practice<String> practice = practice(2);
        session.startPractice(practice);
        assertTrue(session.isPractising());

        assertEquals("m0", session.nextMistake().id());
        assertEquals(1, practice.number());
        assertEquals(2, practice.count());
        assertEquals(NextAction.NEXT_MISTAKE, session.nextAction());
        assertEquals("m1", session.nextMistake().id());
        assertEquals(NextAction.BACK_TO_GAME, session.nextAction());
        assertNull(session.nextMistake());

        assertSame(practice, session.endPractice());
        assertFalse(session.isPractising());
        assertEquals(NextAction.NEXT_PUZZLE, session.nextAction());
        assertEquals("ENGINE", practice.mode());
        assertEquals(Side.BLACK, practice.side());
        assertTrue(practice.flipped());
        assertTrue(practice.newGameStarted());
    }

    @Test
    public void leave_dropsPractice() {
        session.startPractice(practice(1));

        session.leave();

        assertFalse(session.isPractising());
        assertNull(session.practice());
    }

    @Test
    public void practice_needsAMistake() {
        assertThrows(IllegalArgumentException.class, () -> practice(0));
    }

    @Test
    public void advancesByItself_onlyAfterACleanSolveAndNotAfterTheLastMistake() {
        assertTrue(session.advancesByItself(true));
        assertFalse(session.advancesByItself(false));

        session.startPractice(practice(2));
        session.nextMistake();
        assertTrue(session.advancesByItself(true));
        session.nextMistake();
        assertFalse(session.advancesByItself(true));
    }

    @Test
    public void canGoNext_whenCompleteOrAnytimeWhilePractising() {
        assertFalse(session.canGoNext(PuzzleFlow.State.HUMAN_TO_MOVE));
        assertTrue(session.canGoNext(PuzzleFlow.State.COMPLETE));

        session.startPractice(practice(1));
        assertTrue(session.canGoNext(PuzzleFlow.State.HUMAN_TO_MOVE));
    }

    @Test
    public void status_followsTheFlow() {
        RecordingHost host = new RecordingHost();
        PuzzleFlow flow = new PuzzleFlow(new ChessGame(), host);

        assertEquals(Status.WHITE_TO_MOVE, session.status(flow, Side.WHITE));
        flow.start(puzzle("p", 1500));
        assertEquals(Status.BLACK_TO_MOVE, session.status(flow, Side.BLACK));
        host.scheduled.run(); // the opening move: the trainee (White) is to move
        assertEquals(Status.FIND_MOVE_WHITE, session.status(flow, Side.WHITE));
        flow.onUserMove("g1f3");
        assertEquals(Status.WRONG_MOVE, session.status(flow, Side.BLACK));
        host.scheduled.run(); // taken back
        flow.onUserMove("e4d5");
        assertEquals(Status.FINISHED, session.status(flow, Side.BLACK));

        session.startPractice(practice(1));
        session.nextMistake();
        assertEquals(Status.PRACTICE_DONE, session.status(flow, Side.BLACK));
    }

    @Test
    public void status_aPractisedMistakeNotSolvedCleanlyIsPractisedNotFinished() {
        RecordingHost host = new RecordingHost();
        PuzzleFlow flow = new PuzzleFlow(new ChessGame(), host);
        session.startPractice(practice(2));
        flow.start(session.nextMistake());
        host.scheduled.run();
        flow.onUserMove("g1f3"); // wrong
        host.scheduled.run(); // taken back
        flow.onUserMove("e4d5");

        assertEquals(Status.MISTAKE_PRACTISED, session.status(flow, Side.BLACK));
        flow.start(session.nextMistake());
        host.scheduled.run();
        flow.onUserMove("g1f3");
        host.scheduled.run();
        flow.onUserMove("e4d5");
        assertEquals(Status.PRACTICE_DONE, session.status(flow, Side.BLACK));
    }

    @Test
    public void status_solvedCleanly() {
        RecordingHost host = new RecordingHost();
        PuzzleFlow flow = new PuzzleFlow(new ChessGame(), host);
        flow.start(puzzle("p", 1500));
        host.scheduled.run();

        flow.onUserMove("e4d5");

        assertEquals(Status.SOLVED, session.status(flow, Side.BLACK));
    }

    private static final class RecordingHost implements PuzzleFlow.Host {
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
        public void puzzleComplete(Puzzle puzzle, boolean solved) {}
    }
}
