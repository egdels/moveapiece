/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.puzzle;

import com.github.bhlangonijr.chesslib.Side;
import de.schliweb.moveapiece.logic.ChessGame;
import java.util.List;
import java.util.Random;

/**
 * What both apps keep around {@link PuzzleFlow} in puzzle mode: the trainee's rating and recently
 * played puzzles, which puzzle comes next, and the practice of a game's mistakes (see {@link
 * MistakePractice}). It also decides what the screen says and offers, so both apps behave alike;
 * showing it, timers, dialogs and the board stay with the app.
 *
 * @param <M> the app's game mode, kept to return to after practising
 */
public final class PuzzleSession<M> {

    /** Where the app keeps what this session changes. */
    public interface Store {
        void savePuzzleRating(PuzzleRating rating);

        void saveRecentPuzzles(RecentPuzzles recent);
    }

    /** What the status line says about the puzzle. */
    public enum Status {
        FIND_MOVE_WHITE,
        FIND_MOVE_BLACK,
        WRONG_MOVE,
        SHOWING_SOLUTION,
        SOLVED,
        FINISHED,
        /** A practised mistake completed, but not solved cleanly; more are to come. */
        MISTAKE_PRACTISED,
        /** The last practised mistake completed, solved or not. */
        PRACTICE_DONE,
        WHITE_TO_MOVE,
        BLACK_TO_MOVE
    }

    /** What the "next" button does, and so what it is labelled. */
    public enum NextAction {
        NEXT_PUZZLE,
        NEXT_MISTAKE,
        BACK_TO_GAME
    }

    /**
     * Practising the mistakes of an analysed game: the mistakes, which one is on the board, and the
     * game they come from, to return to afterwards as it was.
     *
     * @param <M> the app's game mode
     */
    public static final class Practice<M> {
        private final List<MistakePractice.Mistake> mistakes;
        private final ChessGame.Snapshot game;
        private final boolean gameOver;
        private final M mode;
        private final Side side;
        private final boolean flipped;
        private final boolean newGameStarted;
        private int index = -1;

        /**
         * @param mistakes the mistakes to practise, at least one
         * @param game the game they come from
         * @param gameOver whether that game was already over: the next launch then starts afresh
         *     instead of restoring it (as for any finished game)
         * @param mode the game's mode
         * @param side the game's side the app keeps (the trainee's on desktop, the engine's on
         *     Android)
         * @param flipped whether the board was turned
         * @param newGameStarted whether the game began with New Game (Maia's opening variety)
         */
        public Practice(
                List<MistakePractice.Mistake> mistakes,
                ChessGame.Snapshot game,
                boolean gameOver,
                M mode,
                Side side,
                boolean flipped,
                boolean newGameStarted) {
            if (mistakes.isEmpty()) {
                throw new IllegalArgumentException("no mistakes to practise");
            }
            this.mistakes = List.copyOf(mistakes);
            this.game = game;
            this.gameOver = gameOver;
            this.mode = mode;
            this.side = side;
            this.flipped = flipped;
            this.newGameStarted = newGameStarted;
        }

        public ChessGame.Snapshot game() {
            return game;
        }

        public boolean gameOver() {
            return gameOver;
        }

        public M mode() {
            return mode;
        }

        public Side side() {
            return side;
        }

        public boolean flipped() {
            return flipped;
        }

        public boolean newGameStarted() {
            return newGameStarted;
        }

        /** The mistake on the board. */
        public MistakePractice.Mistake current() {
            return mistakes.get(index);
        }

        /** The current mistake's place, counting from 1. */
        public int number() {
            return index + 1;
        }

        public int count() {
            return mistakes.size();
        }

        public boolean isLast() {
            return index >= mistakes.size() - 1;
        }
    }

    private final Store store;
    private final RecentPuzzles recent;
    private PuzzleRating rating;
    private Integer ratingDelta;
    private Practice<M> practice;

    public PuzzleSession(PuzzleRating rating, RecentPuzzles recent, Store store) {
        this.rating = rating;
        this.recent = recent;
        this.store = store;
    }

    // ------------------------------------------------------------------ rating

    public PuzzleRating rating() {
        return rating;
    }

    /** How the current puzzle changed the rating, once it was rated; null before. */
    public Integer ratingDelta() {
        return ratingDelta;
    }

    /**
     * Picks the next puzzle near the trainee's rating, shifted by {@code difficulty}, and not one
     * played lately, and remembers it as played. Null if there is none.
     */
    public Puzzle pickPuzzle(List<Puzzle> all, PuzzleDifficulty difficulty, Random random) {
        Puzzle puzzle =
                PuzzleSelector.next(all, difficulty.target(rating.rating()), recent, random);
        if (puzzle != null) {
            recent.add(puzzle.id());
            store.saveRecentPuzzles(recent);
        }
        return puzzle;
    }

    /** A puzzle (or practised mistake) was put on the board: it is not rated yet. */
    public void puzzleStarted() {
        ratingDelta = null;
    }

    /**
     * {@link PuzzleFlow.Host#puzzleRated}: updates and saves the rating. A practised mistake does
     * not count.
     */
    public void puzzleRated(Puzzle puzzle, boolean solved) {
        if (practice != null) {
            return;
        }
        ratingDelta = rating.delta(puzzle.rating(), solved);
        rating = rating.after(puzzle.rating(), solved);
        store.savePuzzleRating(rating);
    }

    /**
     * Whether a completed puzzle moves on by itself (after a short pause): only one solved cleanly,
     * and not the last practised mistake, after which the button leads back to the game.
     */
    public boolean advancesByItself(boolean solved) {
        return solved && !(practice != null && practice.isLast());
    }

    // ------------------------------------------------------------------ practice

    public void startPractice(Practice<M> practice) {
        this.practice = practice;
        ratingDelta = null;
    }

    public boolean isPractising() {
        return practice != null;
    }

    /** The practice going on, or null. */
    public Practice<M> practice() {
        return practice;
    }

    /**
     * Moves practice on to its next mistake and returns that puzzle; null after the last one, when
     * the app returns to the game ({@link #endPractice}).
     */
    public Puzzle nextMistake() {
        if (practice.isLast()) {
            return null;
        }
        practice.index++;
        return practice.current().puzzle();
    }

    /** Ends practice and returns it, so the app can put its game back. */
    public Practice<M> endPractice() {
        Practice<M> ended = practice;
        practice = null;
        ratingDelta = null;
        return ended;
    }

    /**
     * Puzzle mode is left for something else; practice ends without returning to its game. The app
     * abandons the flow itself ({@link PuzzleFlow#abandon()}).
     */
    public void leave() {
        practice = null;
        ratingDelta = null;
    }

    // ------------------------------------------------------------------ what the screen shows

    /** The status line for {@code flow}'s state; {@code sideToMove} for the states between. */
    public Status status(PuzzleFlow flow, Side sideToMove) {
        return switch (flow.state()) {
            case HUMAN_TO_MOVE ->
                    flow.puzzle().humanSide() == Side.WHITE
                            ? Status.FIND_MOVE_WHITE
                            : Status.FIND_MOVE_BLACK;
            case WRONG_MOVE_SHOWN -> Status.WRONG_MOVE;
            case SHOWING_SOLUTION -> Status.SHOWING_SOLUTION;
            case COMPLETE -> {
                if (practice != null && practice.isLast()) {
                    yield Status.PRACTICE_DONE;
                }
                if (!flow.isFailed()) {
                    yield Status.SOLVED;
                }
                // A practised mistake is no puzzle of its own, and "finished" would sound like
                // practice were over.
                yield practice != null ? Status.MISTAKE_PRACTISED : Status.FINISHED;
            }
            default -> sideToMove == Side.WHITE ? Status.WHITE_TO_MOVE : Status.BLACK_TO_MOVE;
        };
    }

    /**
     * Whether the "next" button can be pressed: once the puzzle is complete, and anytime while
     * practising - nothing is rated then, and it is also the way back to the game early.
     */
    public boolean canGoNext(PuzzleFlow.State state) {
        return practice != null || state == PuzzleFlow.State.COMPLETE;
    }

    public NextAction nextAction() {
        if (practice == null) {
            return NextAction.NEXT_PUZZLE;
        }
        return practice.isLast() ? NextAction.BACK_TO_GAME : NextAction.NEXT_MISTAKE;
    }
}
