/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.training;

import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.Square;
import de.schliweb.moveapiece.logic.ChessGame;
import java.util.Locale;

/**
 * The opening trainer's move flow, shared by the desktop and Android controllers: which side is to
 * play the line's next move, how the book side's move gets onto the game (applied right away and
 * guided on a connected Pegasus board, or auto-played after a short pause without one), how the
 * trainee's own move is accepted (guided on the board, tapped on screen, or detected by the board),
 * and every transition in between that the physical board can trigger - board back in sync, guide
 * finished, guide skipped because the board was mid-move.
 *
 * <p>No UI and no threading of its own: the two hosts feed it their events on their main thread and
 * implement {@link Host} (screen effects) and {@link Board} (the Pegasus bridge). Everything that
 * was observed to go wrong on real hardware on 2026-09-25 lives here as a testable transition - see
 * {@code TrainingFlowTest}.
 */
public final class TrainingFlow {

    /** The physical board as the trainer sees it (the Pegasus bridge, or a fake in tests). */
    public interface Board {
        boolean isConnected();

        /**
         * Synchronized, or merely with pieces lifted; false before the first dump or mismatched.
         */
        boolean isInSync();

        boolean isGuideActive();

        /** FEN of the position the bridge tracks - can lag the game after an on-screen move. */
        String trackedFen();

        void guideMove(String uci, boolean showLeds);

        void syncToPosition(String fen);
    }

    /** Screen-side effects; every call happens on the caller's thread. */
    public interface Host {
        /**
         * The flow just applied {@code uci} to the game: update the last-move highlight, scroll the
         * history and, only if {@code withSound}, play the move sound. A guided book move is
         * applied silently; its sound follows via {@link #playMoveSound} once the board confirms
         * it.
         */
        void moveApplied(String uci, boolean wasCapture, boolean withSound);

        void playMoveSound(boolean wasCapture);

        /** Moves were undone/redone: clear the last-move highlight. */
        void historyRewritten();

        void refresh();

        void showTrainingComplete();

        /** Without a board, the book side's move is auto-played after this delay. */
        void scheduleBookMove(Runnable action, long delayMs);

        void cancelScheduledBookMove();

        void log(String message);
    }

    public static final long AUTO_MOVE_DELAY_MS = 600;

    private final ChessGame game;
    private final Board board;
    private final Host host;

    private TrainingSession session;

    /**
     * The book side's move has been applied to the game (so the screen updates at once) but the
     * board has not confirmed it yet; {@link TrainingSession#advance()} waits for that. Undo must
     * roll the optimistic apply back too.
     */
    private boolean bookMovePending;

    private boolean bookMoveWasCapture;

    /** The trainee's own move is being guided on the board and has not been applied yet. */
    private boolean guidingHumanMove;

    /** A disconnected book move is queued via {@link Host#scheduleBookMove}. */
    private boolean bookMoveScheduled;

    public TrainingFlow(ChessGame game, Board board, Host host) {
        this.game = game;
        this.board = board;
        this.host = host;
    }

    // ------------------------------------------------------------------ lifecycle

    /**
     * Starts a line: resets the game and this flow's state. The host then does its own screen reset
     * (and resets the board's tracking) and calls {@link #advance()}.
     */
    public void start(OpeningLine line, Side humanSide, boolean hintsEnabled) {
        cancelScheduledBookMove();
        session = new TrainingSession(line, humanSide, hintsEnabled);
        bookMovePending = false;
        bookMoveWasCapture = false;
        guidingHumanMove = false;
        game.reset();
    }

    /** Leaves training (new game, free play, PGN import): drops the session and any timer. */
    public void stop() {
        cancelScheduledBookMove();
        session = null;
        bookMovePending = false;
        guidingHumanMove = false;
    }

    // ------------------------------------------------------------------ queries

    /** The current session, or {@code null} outside training. */
    public TrainingSession session() {
        return session;
    }

    public boolean isActive() {
        return session != null;
    }

    public boolean isBookMovePending() {
        return bookMovePending;
    }

    public boolean isBookMoveScheduled() {
        return bookMoveScheduled;
    }

    /**
     * The book side is to move but its move is being held back because the connected board is out
     * of sync - what the mismatch banner reports as "the app's move is waiting".
     */
    public boolean isAutoMoveHeld() {
        return session != null
                && !session.isComplete()
                && !session.isHumanTurnNow()
                && !bookMovePending
                && board.isConnected()
                && !board.isInSync();
    }

    // ------------------------------------------------------------------ transitions

    /**
     * Drives the line forward from the current ply: guides the trainee's next move on a connected
     * board (the screen stays interactive either way), or plays the book side's move - applied at
     * once and guided when a board is connected and in sync, held back while it is not, or
     * auto-played after a pause without a board.
     */
    public void advance() {
        if (session == null || game.isGameOver()) {
            return;
        }
        if (session.isComplete()) {
            host.showTrainingComplete();
            return;
        }
        boolean connected = board.isConnected();
        if (session.isHumanTurnNow()) {
            if (connected && !board.isGuideActive()) {
                guidingHumanMove = true;
                board.guideMove(session.currentExpectedUci(), session.hintsEnabled());
            }
            host.refresh();
            return;
        }
        if (connected) {
            if (bookMovePending && board.isGuideActive()) {
                return; // already applied and being guided
            }
            if (!board.isInSync()) {
                // Never run ahead of a board that can't follow: onBoardInSync() comes back here.
                host.log(
                        "holding book move "
                                + session.currentExpectedUci()
                                + ": board not in sync");
                host.refresh();
                return;
            }
            String uci = session.currentExpectedUci();
            boolean wasCapture = isCapture(uci);
            if (applyToGame(uci, false)) { // sound follows on physical confirmation
                bookMoveWasCapture = wasCapture;
            }
            bookMovePending = true;
            host.refresh();
            board.guideMove(uci, true);
            // session.advance() happens in onGuidanceComplete(), once physically confirmed.
            return;
        }
        host.refresh();
        bookMoveScheduled = true;
        host.scheduleBookMove(
                () -> {
                    bookMoveScheduled = false;
                    if (session != null && !session.isComplete()) {
                        applyTrainingMove(session.currentExpectedUci());
                    }
                },
                AUTO_MOVE_DELAY_MS);
    }

    /**
     * The board confirmed the move it was guiding. Returns {@code false} outside training (the host
     * then handles its engine move's own follow-up).
     */
    public boolean onGuidanceComplete() {
        if (session == null) {
            return false;
        }
        if (guidingHumanMove) {
            // The trainee's own move was only guided, never applied - now it was played.
            guidingHumanMove = false;
            applyToGame(session.currentExpectedUci(), true);
        } else if (bookMovePending) {
            host.playMoveSound(bookMoveWasCapture);
        }
        bookMovePending = false;
        session.advance();
        host.refresh();
        advance();
        return true;
    }

    /**
     * The connected board is back in sync after a mismatch (or reported in sync for the first
     * time). Retries whatever the mismatch had blocked: the trainee's guide, a held-back book move,
     * or a book move whose guide was skipped - the latter is either guided now (board shows the
     * position before it) or already on the board (board shows the position with it).
     */
    public void onBoardInSync() {
        if (session == null || session.isComplete()) {
            return;
        }
        if (session.isHumanTurnNow() || !bookMovePending) {
            advance();
            return;
        }
        if (samePosition(board.trackedFen(), game.toFen())) {
            guidingHumanMove = false;
            onGuidanceComplete();
        } else {
            advance();
        }
    }

    /**
     * A move confirmed by plain move detection rather than by a guide - possible when the board was
     * already mid-move as the trainee's guide would have started. Accepted if it is the line's
     * expected move; otherwise the board is pulled back onto the game's position so the wrong move
     * lights up as a mismatch until undone.
     */
    public void onPhysicalMoveConfirmed(String uci) {
        guidingHumanMove = false;
        if (session != null
                && !session.isComplete()
                && session.isHumanTurnNow()
                && uci.equals(session.currentExpectedUci())) {
            applyTrainingMove(uci);
            return;
        }
        syncBoard();
    }

    /**
     * The trainee played on screen. Only the line's expected move is accepted, silently ignoring
     * anything else; returns whether it was.
     */
    public boolean onUserMove(String uci) {
        if (session == null || session.isComplete() || !session.isHumanTurnNow()) {
            return false;
        }
        if (!uci.equals(session.currentExpectedUci())) {
            return false;
        }
        applyTrainingMove(uci);
        return true;
    }

    /**
     * Undoes the trainee's last move together with the book reply that followed it (a pending,
     * optimistically applied book move first). Returns {@code false} if there is nothing to undo.
     */
    public boolean undo() {
        if (session == null || (!bookMovePending && session.plyIndex() == 0)) {
            return false;
        }
        cancelScheduledBookMove();
        guidingHumanMove = false;
        if (bookMovePending) {
            game.undoLastMove();
            bookMovePending = false;
        }
        if (session.plyIndex() > 0) {
            game.undoLastMove();
            session.retreat();
            if (session.plyIndex() > 0 && !session.isHumanTurnNow()) {
                game.undoLastMove();
                session.retreat();
            }
        }
        host.historyRewritten();
        host.refresh();
        syncBoard();
        return true;
    }

    /**
     * Replays the trainee's next expected move plus the book reply, mirroring {@link #undo()}'s
     * pairing. Returns {@code false} if there is nothing to redo.
     */
    public boolean redo() {
        if (session == null || bookMovePending || session.isComplete()) {
            return false;
        }
        cancelScheduledBookMove();
        guidingHumanMove = false;
        String humanUci = session.currentExpectedUci();
        if (humanUci == null || !game.applyUciMove(humanUci)) {
            return false;
        }
        session.advance();
        if (!session.isComplete() && !session.isHumanTurnNow()) {
            String replyUci = session.currentExpectedUci();
            if (replyUci != null && game.applyUciMove(replyUci)) {
                session.advance();
            }
        }
        host.historyRewritten();
        host.refresh();
        syncBoard();
        return true;
    }

    // ------------------------------------------------------------------ internals

    /** Applies a move the trainee played (tap, detection, auto-play) and moves the line on. */
    private void applyTrainingMove(String uci) {
        if (!applyToGame(uci, true)) {
            host.refresh();
            return;
        }
        session.advance();
        host.refresh();
        syncBoard();
        advance();
    }

    private boolean applyToGame(String uci, boolean withSound) {
        boolean wasCapture = isCapture(uci);
        if (!game.applyUciMove(uci)) {
            return false;
        }
        host.moveApplied(uci, wasCapture, withSound);
        return true;
    }

    private boolean isCapture(String uci) {
        Square to = Square.fromValue(uci.substring(2, 4).toUpperCase(Locale.ROOT));
        return game.pieceAt(to) != Piece.NONE;
    }

    private void syncBoard() {
        if (board.isConnected()) {
            board.syncToPosition(game.toFen());
        }
    }

    private void cancelScheduledBookMove() {
        if (bookMoveScheduled) {
            bookMoveScheduled = false;
            host.cancelScheduledBookMove();
        }
    }

    /** Piece placement and side to move only - the bridge's FEN and chesslib's differ elsewhere. */
    static boolean samePosition(String fenA, String fenB) {
        String[] a = fenA.split(" ");
        String[] b = fenB.split(" ");
        return a.length >= 2 && b.length >= 2 && a[0].equals(b[0]) && a[1].equals(b[1]);
    }
}
