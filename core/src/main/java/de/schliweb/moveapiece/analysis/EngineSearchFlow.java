/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.analysis;

import com.github.bhlangonijr.chesslib.Side;
import de.schliweb.moveapiece.engine.StockfishEngine;
import de.schliweb.moveapiece.engine.UciInfoParser;
import de.schliweb.moveapiece.logic.ChessGame;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalInt;

/**
 * Everything the one shared Stockfish process is asked to search, and what becomes of its answers:
 * the engine's own reply move, the evaluation display with its move-quality grading, the hint and
 * the post-game analysis. Shared by the Android and the desktop app, which implement {@link Host}
 * and forward the engine's "bestmove" and "info" lines here.
 *
 * <p>Runs entirely on the main thread, like the engine's own callbacks.
 */
public final class EngineSearchFlow {

    private static final int ANALYSIS_MOVETIME_MS = 1500;
    private static final int HINT_MOVETIME_MS = 1500;
    private static final int HINT_MULTI_PV_LINES = 3;
    private static final int POST_GAME_MOVETIME_MS = 400;
    private static final String NO_MOVE = "(none)";

    /** What the flow needs to know from the app, and what it reports back. */
    public interface Host {

        /** Whether Stockfish has answered "readyok" and can be searched with. */
        boolean isEngineReady();

        /** The opening trainer never shows evaluations, hints or move grades. */
        boolean isTrainingMode();

        boolean isEvaluationEnabled();

        /** Whether the player could move a piece right now; a hint is only given then. */
        boolean isBoardInteractive();

        /**
         * Whether Stockfish is the opponent and to move: its own search then covers the evaluation
         * too, and a separate analysis search would be redundant.
         */
        boolean isEngineAboutToMove();

        /**
         * Sets the engine to the opponent's strength, right before it searches for its own move.
         * Every other search (evaluation, hint, post-game analysis) runs at full strength.
         */
        void restoreEngineStrength();

        /** The engine's reply from {@link #startEngineMoveSearch}; {@code null} if it has none. */
        void onEngineMove(String uci);

        /**
         * A hint search has finished: the best move ({@code null} if there is none) and, in {@link
         * #hintCandidates()}, the lines after it.
         */
        void onHint(String bestMoveUci);

        /** {@link #isWaitingForHint()} or {@link #isPostGameAnalysisRunning()} changed. */
        void onHintAvailabilityChanged();

        /** A fresh evaluation of the position on the board, in centipawns for White. */
        void onEvaluation(int whiteRelativeCp);

        /** Same for a forced mate: positive when White mates, in that many moves. */
        void onEvaluationMate(int whiteRelativeMateIn);

        /**
         * The move just played has been graded, or - with {@code quality == null} - an earlier
         * grade no longer applies (the move was fine, or the next move is being made).
         */
        void onMoveQuality(MoveQuality quality, int cpLoss);

        /** The post-game analysis started, advanced one position, or ended. */
        void onPostGameProgress();

        /**
         * The post-game analysis is complete. The live searches it cancelled are the host's to
         * resume.
         */
        void onPostGameReport(List<String> uciMoves, List<Integer> evals);

        /**
         * Every outstanding search was given up ({@link #abandonPendingSearches}); whatever the
         * host was waiting for will not arrive.
         */
        void onSearchesAbandoned();
    }

    /**
     * Analysis and real-move searches share one Stockfish process, so a "go" for one can still be
     * in flight when the other wants to search. Every search stops whatever is running first and
     * records its purpose; Stockfish answers a stopped search with its own "bestmove" too, so
     * replies arrive in exactly the order the searches were started in - {@link #onBestMove} pops
     * this queue instead of relying on a single "am I waiting for a move" flag, which cannot tell a
     * stale analysis reply from the real one.
     */
    private enum SearchPurpose {
        REAL_MOVE,
        ANALYSIS,
        HINT,
        POST_GAME
    }

    private static final class PendingSearch {
        final SearchPurpose purpose;
        final int generation;

        /** The position searched: its ply and who is to move there. */
        final int moveCount;

        final Side sideToMove;

        /** A hint search given up for a newer search; its answer is no longer a hint. */
        boolean superseded;

        PendingSearch(SearchPurpose purpose, int generation, int moveCount, Side sideToMove) {
            this.purpose = purpose;
            this.generation = generation;
            this.moveCount = moveCount;
            this.sideToMove = sideToMove;
        }
    }

    private final ChessGame game;
    private final Host host;
    private StockfishEngine engine;

    private final ArrayDeque<PendingSearch> pendingSearches = new ArrayDeque<>();

    /**
     * Bumped whenever outstanding searches are deliberately abandoned (new game, PGN import) so a
     * cancelled search's eventual reply - still in {@link #pendingSearches} since it was stopped,
     * not un-queued - is recognized as belonging to a position since left, even though its queue
     * slot and purpose look otherwise valid.
     */
    private int generation;

    /**
     * The hint search in flight, or null. It runs with MultiPV raised, which has to be taken back
     * before any other search starts: none of them wants a non-PV-1 line's score.
     */
    private PendingSearch hintSearch;

    private final MultiPvCandidates hintCandidates = new MultiPvCandidates(HINT_MULTI_PV_LINES);

    /**
     * Freshest known eval of the position on the board, from the side to move's own perspective
     * (the raw UCI score), pinned to a ply by {@link #lastPositionEvalMoveCount}; -1 there means
     * "none yet".
     */
    private int lastPositionEvalCp;

    private int lastPositionEvalMoveCount = -1;

    /**
     * Snapshot of the eval above taken right before a graded move is applied, compared against the
     * eval of the resulting position once that comes in. -1 as the move count means "not currently
     * grading a move".
     */
    private int moveQualityBaselineCp;

    private int moveQualityBaselineMoveCount = -1;

    /** The game's moves while a post-game analysis is replaying them; null when idle. */
    private List<String> postGameUciMoves;

    /** Raw (side-to-move-relative) evals collected so far, one per position. */
    private List<Integer> postGamePositionEvals;

    /** Latest raw score seen for the position the post-game analysis is searching right now. */
    private int postGameLiveScoreCp;

    public EngineSearchFlow(ChessGame game, Host host) {
        this.game = game;
        this.host = host;
    }

    /** The engine to search with; until one is set, nothing is searched. */
    public void setEngine(StockfishEngine engine) {
        this.engine = engine;
    }

    // ------------------------------------------------------------ state

    /**
     * Identifies the current run of searches; changes with every {@link #abandonPendingSearches}.
     */
    public int generation() {
        return generation;
    }

    public boolean isWaitingForHint() {
        return hintSearch != null;
    }

    public boolean isPostGameAnalysisRunning() {
        return postGameUciMoves != null;
    }

    /** Positions evaluated so far by the running post-game analysis. */
    public int postGamePositionsDone() {
        return postGamePositionEvals.size();
    }

    /** Positions the running post-game analysis evaluates in total. */
    public int postGamePositionsTotal() {
        return postGameUciMoves.size() + 1;
    }

    /**
     * The lines of the last hint search; rank 0 is the hint itself, the others are alternatives.
     */
    public MultiPvCandidates hintCandidates() {
        return hintCandidates;
    }

    // ------------------------------------------------------------ searches

    /** Searches the current position for the engine's own reply; see {@link Host#onEngineMove}. */
    public void startEngineMoveSearch(int movetimeMs) {
        PendingSearch newest = pendingSearches.peekLast();
        if (newest != null
                && newest.purpose == SearchPurpose.REAL_MOVE
                && newest.generation == generation
                && newest.moveCount == game.moveCount()) {
            // Already searching for this very move: a new game asks for it once itself and once
            // more when the engine reports ready. A second search would cut the first one short,
            // and both answers would arrive as the engine's move.
            return;
        }
        startSearch(SearchPurpose.REAL_MOVE, movetimeMs);
    }

    private PendingSearch newSearch(SearchPurpose purpose) {
        return new PendingSearch(purpose, generation, game.moveCount(), game.sideToMove());
    }

    /**
     * The strength a search runs at is set anew for every search: the opponent's for its own move,
     * full strength for everything that judges a position. An evaluation computed at a limited Elo
     * is the score of a deliberately weakened search and can be off by a piece or more.
     */
    private void setStrengthFor(SearchPurpose purpose) {
        if (purpose == SearchPurpose.REAL_MOVE) {
            host.restoreEngineStrength();
        } else {
            engine.setFullStrength();
        }
    }

    /**
     * Starts a dedicated evaluation search when the evaluation is wanted and nothing else is
     * already searching the current position.
     */
    public void maybeStartAnalysis() {
        if (!host.isEvaluationEnabled() || host.isTrainingMode() || game.isGameOver()) {
            return;
        }
        if (!host.isEngineReady() || host.isEngineAboutToMove()) {
            return;
        }
        if (hintSearch != null && hintSearch.moveCount == game.moveCount()) {
            // A hint is being searched for this very position; it is not cut short for a number.
            return;
        }
        startSearch(SearchPurpose.ANALYSIS, ANALYSIS_MOVETIME_MS);
    }

    /**
     * Stops whatever the engine is currently doing, queues the purpose of the search being started
     * and kicks it off from the current position, so that neither a real-move nor an analysis
     * search can silently run into the other's still-active "go".
     */
    private void startSearch(SearchPurpose purpose, int movetimeMs) {
        engine.stop();
        supersedeHint();
        pendingSearches.add(newSearch(purpose));
        setStrengthFor(purpose);
        engine.setPosition(game.startFen(), game.toUciMoveList());
        engine.go(movetimeMs);
    }

    /**
     * A hint still being searched when another search starts is given up: the position has moved
     * on, so its answer would be an arrow for a position no longer on the board. MultiPV goes back
     * to 1 before the new search's "go", or that search would report three lines as well.
     */
    private void supersedeHint() {
        if (hintSearch == null) {
            return;
        }
        hintSearch.superseded = true;
        hintSearch = null;
        engine.setMultiPv(1);
        host.onHintAvailabilityChanged();
    }

    /**
     * Asks Stockfish for the best move in the current position without applying it - the player
     * decides whether to play it. Searches at full strength, so a low-Elo opponent does not leak
     * into the hint.
     */
    public void requestHint() {
        if (!host.isEngineReady()
                || host.isTrainingMode()
                || hintSearch != null
                || postGameUciMoves != null
                || !host.isBoardInteractive()) {
            return;
        }
        engine.stop();
        hintSearch = newSearch(SearchPurpose.HINT);
        pendingSearches.add(hintSearch);
        host.onHintAvailabilityChanged();
        hintCandidates.clear();
        setStrengthFor(SearchPurpose.HINT);
        engine.setMultiPv(HINT_MULTI_PV_LINES);
        engine.setPosition(game.startFen(), game.toUciMoveList());
        engine.go(HINT_MOVETIME_MS);
    }

    /**
     * Cancels any outstanding search and marks its eventual reply (and any other already-queued
     * one) as belonging to a position since left - wherever the game is reset or rewound out from
     * under a possibly in-flight search (new game, undo, PGN import).
     */
    public void abandonPendingSearches() {
        if (engine != null) {
            engine.stop();
        }
        generation++;
        if (hintSearch != null) {
            // A hint search was interrupted mid-flight - restore MultiPV so the next (unrelated)
            // search does not report three lines as well.
            hintSearch = null;
            if (engine != null) {
                engine.setMultiPv(1);
            }
        }
        lastPositionEvalMoveCount = -1;
        moveQualityBaselineMoveCount = -1;
        postGameUciMoves = null;
        postGamePositionEvals = null;
        host.onSearchesAbandoned();
    }

    // ------------------------------------------------------------ move quality

    /**
     * Snapshots the eval of the position about to be left, so the move can be graded once a fresh
     * eval for the resulting position comes in. To be called right before any move is committed to
     * the game; silently skips grading this move in training mode or when no eval is known for
     * exactly the current position - e.g. right after the evaluation was switched on, or on the
     * game's very first ply before any search has finished.
     */
    public void recordMoveQualityBaseline() {
        if (host.isTrainingMode() || lastPositionEvalMoveCount != game.moveCount()) {
            moveQualityBaselineMoveCount = -1;
            return;
        }
        moveQualityBaselineCp = lastPositionEvalCp;
        moveQualityBaselineMoveCount = game.moveCount();
        host.onMoveQuality(null, 0);
    }

    /**
     * If a move is being graded and the eval that just finished belongs to the resulting position,
     * reports the move's centipawn loss: baseline eval minus the resulting position's eval, both
     * from the mover's perspective - the latter is the raw score of the position with the opponent
     * to move, so adding rather than subtracting it does the flip.
     */
    private void maybeFinalizeMoveQuality() {
        if (moveQualityBaselineMoveCount < 0
                || lastPositionEvalMoveCount != moveQualityBaselineMoveCount + 1) {
            return;
        }
        int cpLoss = moveQualityBaselineCp + lastPositionEvalCp;
        moveQualityBaselineMoveCount = -1;
        host.onMoveQuality(MoveQuality.of(cpLoss), cpLoss);
    }

    // ------------------------------------------------------------ post-game analysis

    /**
     * Replays the game played so far from the start, one ply at a time, and reports every
     * position's eval once done ({@link Host#onPostGameReport}). Works whether the game has ended
     * or is still in progress - there only has to be something to replay. Searches at full
     * strength.
     *
     * <p>Unlike every other search, this does not send "ucinewgame" before replaying (its "readyok"
     * would, in a still-live game, fire off an unwanted real move). The live searches cancelled
     * here are therefore the host's to restart once the report is in. The host must also keep the
     * board from being played on meanwhile: a move made mid-replay would go through the same engine
     * without this flow noticing.
     */
    public void startPostGameAnalysis() {
        if (!host.isEngineReady()
                || host.isTrainingMode()
                || game.moveCount() == 0
                || postGameUciMoves != null) {
            return;
        }
        abandonPendingSearches();
        postGameUciMoves = Arrays.asList(game.toUciMoveList().split(" "));
        postGamePositionEvals = new ArrayList<>(postGameUciMoves.size() + 1);
        host.onHintAvailabilityChanged();
        host.onPostGameProgress();
        setStrengthFor(SearchPurpose.POST_GAME);
        requestPostGameEvalFor(0);
    }

    /**
     * The final replayed position is the live game's own current one - if it is checkmate or
     * stalemate, there is nothing for Stockfish to search (no "info score" line ever arrives, which
     * would leave {@link #postGameLiveScoreCp} on the previous position's stale value and badly
     * mis-grade the final move). Score it from the game's own verdict instead: very bad for whoever
     * is mated, neutral for a stalemate.
     */
    private void requestPostGameEvalFor(int positionIndex) {
        if (positionIndex == postGameUciMoves.size()
                && (game.isCheckmate() || game.isStalemate())) {
            recordPostGameEval(game.isCheckmate() ? -MoveQuality.mateToCp(0) : 0);
            return;
        }
        pendingSearches.add(newSearch(SearchPurpose.POST_GAME));
        engine.setPosition(
                game.startFen(), String.join(" ", postGameUciMoves.subList(0, positionIndex)));
        engine.go(POST_GAME_MOVETIME_MS);
    }

    private void recordPostGameEval(int cp) {
        postGamePositionEvals.add(cp);
        int nextPositionIndex = postGamePositionEvals.size();
        host.onPostGameProgress();
        if (nextPositionIndex <= postGameUciMoves.size()) {
            requestPostGameEvalFor(nextPositionIndex);
            return;
        }
        List<String> uciMoves = postGameUciMoves;
        List<Integer> evals = postGamePositionEvals;
        postGameUciMoves = null;
        postGamePositionEvals = null;
        host.onHintAvailabilityChanged();
        host.onPostGameProgress();
        host.onPostGameReport(uciMoves, evals);
    }

    // ------------------------------------------------------------ engine callbacks

    /** To be called with every "bestmove" the engine reports. */
    public void onBestMove(String bestMoveUci) {
        PendingSearch search = pendingSearches.poll();
        if (search == null || search.generation != generation) {
            // Either unexpected (defensive only - every go() sent queues an entry), or a reply for
            // a search given up by abandonPendingSearches(); discard it.
            return;
        }
        maybeFinalizeMoveQuality();
        String move = bestMoveUci == null || NO_MOVE.equals(bestMoveUci) ? null : bestMoveUci;
        switch (search.purpose) {
            case ANALYSIS:
                // onInfo() already streamed the evaluation for it.
                return;
            case HINT:
                if (search.superseded) {
                    return;
                }
                hintSearch = null;
                engine.setMultiPv(1);
                host.onHintAvailabilityChanged();
                host.onHint(move);
                return;
            case POST_GAME:
                recordPostGameEval(postGameLiveScoreCp);
                return;
            default:
                host.onEngineMove(move);
        }
    }

    /** To be called with every "info" line the engine reports. */
    public void onInfo(String infoLine) {
        // Stockfish answers its searches in the order they were started in, so a line always
        // belongs to the oldest search still unanswered.
        PendingSearch search = pendingSearches.peek();
        if (search == null || search.generation != generation) {
            return;
        }
        if (search.purpose == SearchPurpose.HINT) {
            if (!search.superseded) {
                hintCandidates.capture(infoLine);
            }
            return;
        }
        OptionalInt mate = UciInfoParser.parseScoreMate(infoLine);
        OptionalInt cp =
                mate.isPresent() ? OptionalInt.empty() : UciInfoParser.parseScoreCp(infoLine);
        if (mate.isEmpty() && cp.isEmpty()) {
            return;
        }
        int rawCp = mate.isPresent() ? MoveQuality.mateToCp(mate.getAsInt()) : cp.getAsInt();
        if (search.purpose == SearchPurpose.POST_GAME) {
            // Post-game analysis replays past positions - never the live eval/move-quality state.
            postGameLiveScoreCp = rawCp;
            return;
        }
        // engine.stop() is fire-and-forget - Stockfish can still emit a few more "info" lines for
        // a search before it finally replies with "bestmove". Once a newer search is queued behind
        // it, or a move was made without one (the last of a game), such a line is about a position
        // no longer on the board and must be neither shown nor graded as the current one's.
        if (pendingSearches.size() > 1 || search.moveCount != game.moveCount()) {
            return;
        }
        if (!host.isEvaluationEnabled() || host.isTrainingMode()) {
            return;
        }
        lastPositionEvalCp = rawCp;
        lastPositionEvalMoveCount = search.moveCount;
        int sign = search.sideToMove == Side.BLACK ? -1 : 1;
        if (mate.isPresent()) {
            host.onEvaluationMate(sign * mate.getAsInt());
        } else {
            host.onEvaluation(sign * cp.getAsInt());
        }
    }

    /** The engine process failed: nothing queued will be answered any more. */
    public void onEngineError() {
        pendingSearches.clear();
        hintSearch = null;
        postGameUciMoves = null;
        postGamePositionEvals = null;
    }
}
