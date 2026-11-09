/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.puzzle;

import de.schliweb.moveapiece.logic.ChessGame;
import de.schliweb.moveapiece.logic.UciMoves;
import java.util.Locale;

/**
 * The move flow of one puzzle, shared by the desktop and Android controllers: the app plays the
 * puzzle's opening move after a short pause, then checks each of the trainee's moves against the
 * line, plays the reply to a correct one, and takes a wrong one back after showing it for a moment.
 * Any move that gives checkmate solves the puzzle, as on Lichess, even when the stored line has a
 * different (or a longer) way to mate.
 *
 * <p>The puzzle is rated exactly once, through {@link Host#puzzleRated}: as lost at the first wrong
 * move, hint or request for the solution, or as solved when the trainee completes the line without
 * any of them. Completing the line after a mistake still ends the puzzle ({@link
 * Host#puzzleComplete}), just not as a clean solve.
 *
 * <p>Screen only for now: the physical boards come later. No UI and no threading of its own; the
 * host feeds it its events on its main thread, and the flow applies every move to the game itself -
 * the host does not, not even the trainee's. Undo and redo do not exist inside a puzzle.
 */
public final class PuzzleFlow {

    /** Screen-side effects; every call happens on the caller's thread. */
    public interface Host {
        /**
         * The flow just applied {@code uci} to the game: update the last-move highlight, scroll the
         * history, play the move sound.
         */
        void moveApplied(String uci, boolean wasCapture);

        /** The game was reset or a move taken back: clear the last-move highlight. */
        void historyRewritten();

        void refresh();

        /** Runs {@code action} after {@code delayMs}; at most one is pending at a time. */
        void schedule(Runnable action, long delayMs);

        void cancelScheduled();

        /**
         * {@code uci} was the wrong move; it is shown and taken back after {@link #WRONG_MOVE_MS}.
         */
        void wrongMove(String uci);

        /** Called exactly once per puzzle, see the class comment. */
        void puzzleRated(Puzzle puzzle, boolean solved);

        /**
         * The line is complete, by the trainee or shown by the app. {@code solved} is true only for
         * a clean solve, the same value {@link #puzzleRated} got.
         */
        void puzzleComplete(Puzzle puzzle, boolean solved);
    }

    public enum State {
        /** No puzzle. */
        IDLE,
        /** The app's move is scheduled. */
        OPPONENT_TO_MOVE,
        /** Waiting for the trainee. */
        HUMAN_TO_MOVE,
        /** A wrong move is on the board, waiting to be taken back. */
        WRONG_MOVE_SHOWN,
        /** The app is playing the rest of the line. */
        SHOWING_SOLUTION,
        /** The line is complete. */
        COMPLETE
    }

    /** What {@link #onUserMove} made of a move. */
    public enum MoveResult {
        /** Not the trainee's turn, or not a legal move: nothing happened. */
        IGNORED,
        CORRECT,
        WRONG
    }

    /**
     * What a host keeps to pick a puzzle up again after a restart, see {@link #resume}.
     *
     * @param puzzleId the puzzle's id
     * @param ply how many moves of the line are played
     * @param failed whether the puzzle was already rated as lost
     */
    public record Progress(String puzzleId, int ply, boolean failed) {}

    /** Before the opening move: long enough to take in the position. */
    public static final long OPENING_MOVE_MS = 900;

    /** Before the app's reply to a correct move. */
    public static final long REPLY_MS = 500;

    /** How long a wrong move stays on the board. */
    public static final long WRONG_MOVE_MS = 800;

    /** Between the moves when the solution is shown. */
    public static final long SOLUTION_STEP_MS = 900;

    private final ChessGame game;
    private final Host host;

    private Puzzle puzzle;
    private State state = State.IDLE;

    /** Moves of the line played; a wrong move on the board is not counted. */
    private int ply;

    private boolean failed;
    private boolean rated;

    /** Bumped by every schedule and cancel, so a callback that slipped through stays inert. */
    private int generation;

    public PuzzleFlow(ChessGame game, Host host) {
        this.game = game;
        this.host = host;
    }

    // ------------------------------------------------------------------ lifecycle

    /** Sets {@code puzzle} up and schedules its opening move. */
    public void start(Puzzle puzzle) {
        cancelScheduled();
        this.puzzle = puzzle;
        ply = 0;
        failed = false;
        rated = false;
        game.loadFen(puzzle.fen());
        state = State.OPPONENT_TO_MOVE;
        host.historyRewritten();
        host.refresh();
        schedule(this::playOpponentMove, OPENING_MOVE_MS);
    }

    /**
     * Picks {@code puzzle} up where {@link #progress()} left it: the line's first {@code ply} moves
     * are played silently, and the flow continues from there - with the app's move after a pause if
     * it is the app's turn. A puzzle already lost is not rated again. Returns false, with the flow
     * stopped, if {@code ply} lies outside the line or a move does not apply; a complete line also
     * returns false, since there is nothing left to play.
     */
    public boolean resume(Puzzle puzzle, int ply, boolean failed) {
        cancelScheduled();
        if (ply < 0 || ply >= puzzle.moves().size()) {
            stop();
            return false;
        }
        this.puzzle = puzzle;
        game.loadFen(puzzle.fen());
        for (int i = 0; i < ply; i++) {
            if (!game.applyUciMove(puzzle.moves().get(i))) {
                stop();
                return false;
            }
        }
        this.ply = ply;
        this.failed = failed;
        this.rated = failed;
        host.historyRewritten();
        if (ply % 2 == 1) {
            state = State.HUMAN_TO_MOVE;
            host.refresh();
        } else {
            state = State.OPPONENT_TO_MOVE;
            host.refresh();
            schedule(this::playOpponentMove, ply == 0 ? OPENING_MOVE_MS : REPLY_MS);
        }
        return true;
    }

    /**
     * The trainee leaves the puzzle unsolved - a new game, another puzzle, a PGN import. Once its
     * position was posed (the app's first move played), a puzzle not yet rated counts as lost, so
     * leaving is no free way past a hard one; then the flow {@link #stop()}s. Closing the app is
     * not leaving: the host keeps {@link #progress()} and resumes it.
     */
    public void abandon() {
        if (puzzle != null && state != State.COMPLETE && ply >= 1 && !rated) {
            markFailed();
        }
        stop();
    }

    /** Drops the puzzle and any pending move, without rating it; see {@link #abandon()}. */
    public void stop() {
        cancelScheduled();
        puzzle = null;
        state = State.IDLE;
        ply = 0;
        failed = false;
        rated = false;
    }

    // ------------------------------------------------------------------ queries

    /** The current puzzle, or {@code null} outside a puzzle. */
    public Puzzle puzzle() {
        return puzzle;
    }

    public State state() {
        return state;
    }

    public boolean isActive() {
        return puzzle != null;
    }

    public boolean isHumanTurn() {
        return state == State.HUMAN_TO_MOVE;
    }

    /** Whether the puzzle was already rated as lost. */
    public boolean isFailed() {
        return failed;
    }

    /**
     * What to keep for {@link #resume}, or {@code null} outside a puzzle or once it is complete.
     */
    public Progress progress() {
        if (puzzle == null || state == State.COMPLETE) {
            return null;
        }
        return new Progress(puzzle.id(), ply, failed);
    }

    // ------------------------------------------------------------------ events

    /**
     * The trainee played {@code uci} on screen. The flow applies it to the game: as the next move
     * of the line, or as a wrong move that is taken back after {@link #WRONG_MOVE_MS}.
     */
    public MoveResult onUserMove(String uci) {
        if (state != State.HUMAN_TO_MOVE || uci == null) {
            return MoveResult.IGNORED;
        }
        String move = uci.toLowerCase(Locale.ROOT);
        if (!move.matches("[a-h][1-8][a-h][1-8][qrbn]?")) {
            return MoveResult.IGNORED;
        }
        // applyUciMove() alone is not enough: chesslib only checks that the resulting position is
        // valid, not that the piece may move that way (a pawn from e4 to e6 passes).
        if (!game.legalDestinationsFrom(UciMoves.from(move)).contains(UciMoves.to(move))) {
            return MoveResult.IGNORED;
        }
        boolean wasCapture = isCapture(move);
        if (!game.applyUciMove(move)) {
            return MoveResult.IGNORED;
        }
        host.moveApplied(move, wasCapture);
        if (move.equals(puzzle.moves().get(ply)) || game.isCheckmate()) {
            ply++;
            if (ply >= puzzle.moves().size() || game.isCheckmate()) {
                complete();
            } else {
                state = State.OPPONENT_TO_MOVE;
                host.refresh();
                schedule(this::playOpponentMove, REPLY_MS);
            }
            return MoveResult.CORRECT;
        }
        state = State.WRONG_MOVE_SHOWN;
        markFailed();
        host.wrongMove(move);
        host.refresh();
        schedule(this::takeBackWrongMove, WRONG_MOVE_MS);
        return MoveResult.WRONG;
    }

    /**
     * The trainee asked for a hint: returns the line's next move, whose piece the host marks, and
     * rates the puzzle as lost. {@code null} when it is not the trainee's turn.
     */
    public String hint() {
        if (state != State.HUMAN_TO_MOVE) {
            return null;
        }
        markFailed();
        host.refresh();
        return puzzle.moves().get(ply);
    }

    /**
     * Plays the rest of the line, one move every {@link #SOLUTION_STEP_MS}, and rates the puzzle as
     * lost. Returns false if there is nothing to show.
     */
    public boolean showSolution() {
        if (state != State.HUMAN_TO_MOVE
                && state != State.OPPONENT_TO_MOVE
                && state != State.WRONG_MOVE_SHOWN) {
            return false;
        }
        cancelScheduled();
        if (state == State.WRONG_MOVE_SHOWN) {
            game.undoLastMove();
            host.historyRewritten();
        }
        markFailed();
        state = State.SHOWING_SOLUTION;
        playSolutionStep();
        return true;
    }

    // ------------------------------------------------------------------ internals

    private void playOpponentMove() {
        if (state != State.OPPONENT_TO_MOVE) {
            return;
        }
        if (applyLineMove()) {
            state = State.HUMAN_TO_MOVE;
        }
        host.refresh();
    }

    private void takeBackWrongMove() {
        if (state != State.WRONG_MOVE_SHOWN) {
            return;
        }
        game.undoLastMove();
        state = State.HUMAN_TO_MOVE;
        host.historyRewritten();
        host.refresh();
    }

    private void playSolutionStep() {
        if (state != State.SHOWING_SOLUTION) {
            return;
        }
        if (!applyLineMove()) {
            host.refresh();
            return;
        }
        if (ply >= puzzle.moves().size()) {
            complete();
        } else {
            host.refresh();
            schedule(this::playSolutionStep, SOLUTION_STEP_MS);
        }
    }

    /**
     * Applies the line's next move. False only for a broken line, which the data test rules out.
     */
    private boolean applyLineMove() {
        String uci = puzzle.moves().get(ply);
        boolean wasCapture = isCapture(uci);
        if (!game.applyUciMove(uci)) {
            return false;
        }
        ply++;
        host.moveApplied(uci, wasCapture);
        return true;
    }

    private void complete() {
        state = State.COMPLETE;
        if (!rated) {
            rated = true;
            host.puzzleRated(puzzle, true);
        }
        host.refresh();
        host.puzzleComplete(puzzle, !failed);
    }

    private void markFailed() {
        failed = true;
        if (!rated) {
            rated = true;
            host.puzzleRated(puzzle, false);
        }
    }

    private boolean isCapture(String uci) {
        return game.isCapture(UciMoves.from(uci), UciMoves.to(uci));
    }

    private void schedule(Runnable action, long delayMs) {
        int scheduledGeneration = ++generation;
        host.schedule(
                () -> {
                    if (scheduledGeneration == generation) {
                        action.run();
                    }
                },
                delayMs);
    }

    private void cancelScheduled() {
        generation++;
        host.cancelScheduled();
    }
}
