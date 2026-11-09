/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.puzzle;

import com.github.bhlangonijr.chesslib.Side;
import de.schliweb.moveapiece.analysis.MoveCandidate;
import de.schliweb.moveapiece.analysis.MoveQuality;
import de.schliweb.moveapiece.analysis.PostGameReport;
import de.schliweb.moveapiece.logic.ChessGame;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Turns the mistakes of an analysed game into puzzles to practise: every move graded a {@link
 * MoveQuality#MISTAKE} or {@link MoveQuality#BLUNDER}, by a side the trainee wants to practise,
 * becomes a puzzle in the same form as the bundled ones, so {@link PuzzleFlow} plays it unchanged:
 * the position before the opponent's previous move, that move (so the trainee sees what happened),
 * then the engine's best move as the solution. Inaccuracies are left out - often a matter of taste,
 * and too many.
 *
 * <p>Several moves are often about equally good, so a move scoring at most {@link
 * #ACCEPT_WITHIN_CP} below the best among the analysis's {@link MoveCandidate}s solves the puzzle
 * as well ({@link Puzzle#alsoAccepted()}), and when the best move forces mate, so does any other
 * move that forces mate (a mate in three instead of two scores only 100 cp lower); any move that
 * mates at once solves it anyway. A mistake on the game's very first move has no previous move to
 * show and is left out, as is one where the move played is itself among the accepted ones: the
 * grade comes from separate searches of the positions before and after, which can disagree slightly
 * with the best lines of one search.
 */
public final class MistakePractice {

    /** Half a pawn: a move this close to the best counts as finding it. */
    public static final int ACCEPT_WITHIN_CP = 50;

    /**
     * One mistake to practise.
     *
     * @param puzzle the puzzle to play; its id is "mistake-" plus the ply
     * @param moveLabel where in the game it happened, e.g. "14." or "14..."
     * @param playedSan the move that was played instead, in SAN, or in UCI if unknown
     * @param quality how bad it was
     */
    public record Mistake(Puzzle puzzle, String moveLabel, String playedSan, MoveQuality quality) {}

    private MistakePractice() {}

    /**
     * The mistakes of the analysed game to practise, in the order they were played.
     *
     * @param startFen the game's start position
     * @param chess960 whether the game is played under Chess960 rules
     * @param uciMoves the moves played, as the analysis reported them
     * @param evals the analysis's score of each position from its side to move, one more than
     *     {@code uciMoves} (see {@link PostGameReport#of})
     * @param candidates the analysis's best moves in each position, same length as {@code evals}
     * @param sanMoves the moves in SAN, for display; shorter or empty is fine
     * @param sides whose mistakes to practise
     */
    public static List<Mistake> find(
            String startFen,
            boolean chess960,
            List<String> uciMoves,
            List<Integer> evals,
            List<List<MoveCandidate>> candidates,
            List<String> sanMoves,
            Set<Side> sides) {
        List<Mistake> mistakes = new ArrayList<>();
        if (evals.size() != uciMoves.size() + 1 || candidates.size() != evals.size()) {
            return mistakes;
        }
        ChessGame replay = new ChessGame();
        replay.loadFen(startFen, chess960);
        int startPly = replay.startPly();
        String previousFen = null;
        for (int ply = 0; ply < uciMoves.size(); ply++) {
            Side mover = replay.sideToMove();
            String fen = replay.toFen();
            int cpLoss = Math.max(0, evals.get(ply) + evals.get(ply + 1));
            MoveQuality quality = MoveQuality.of(cpLoss);
            if (ply > 0
                    && sides.contains(mover)
                    && !decidedByMate(evals.get(ply), evals.get(ply + 1))
                    && (quality == MoveQuality.MISTAKE || quality == MoveQuality.BLUNDER)) {
                Mistake mistake =
                        mistakeAt(
                                ply,
                                startPly,
                                previousFen,
                                chess960,
                                uciMoves,
                                candidates.get(ply),
                                sanMoves,
                                quality);
                if (mistake != null) {
                    mistakes.add(mistake);
                }
            }
            if (!replay.applyUciMove(uciMoves.get(ply))) {
                break; // a move the replay cannot follow: the rest cannot be trusted either
            }
            previousFen = fen;
        }
        return mistakes;
    }

    /**
     * Whether the move between two positions scored {@code before} (for the mover) and {@code
     * after} (for the opponent) changes nothing that is worth practising: the mover forced mate
     * before and still does - a longer way to mate scores only 100 cp per move lower, so a winning
     * detour in a mating endgame would otherwise count as a blunder - or was already being mated,
     * where the only lesson would be to resist a few moves longer.
     */
    static boolean decidedByMate(int before, int after) {
        boolean stillMating = forcesMate(before) && forcesMate(-after);
        boolean alreadyLost = forcesMate(-before);
        return stillMating || alreadyLost;
    }

    /** Whether finding {@code candidate} counts as finding {@code best}. */
    static boolean isAccepted(MoveCandidate best, MoveCandidate candidate) {
        if (best.cp() - candidate.cp() <= ACCEPT_WITHIN_CP) {
            return true;
        }
        return forcesMate(best.cp()) && forcesMate(candidate.cp());
    }

    /** A score that {@link MoveQuality#mateToCp} gives a forced mate for the mover. */
    private static boolean forcesMate(int cp) {
        return cp >= MoveQuality.mateToCp(100);
    }

    private static Mistake mistakeAt(
            int ply,
            int startPly,
            String previousFen,
            boolean chess960,
            List<String> uciMoves,
            List<MoveCandidate> candidates,
            List<String> sanMoves,
            MoveQuality quality) {
        String played = uciMoves.get(ply);
        if (candidates.isEmpty()) {
            return null;
        }
        MoveCandidate best = candidates.get(0);
        Set<String> alsoAccepted = new HashSet<>();
        for (int rank = 0; rank < candidates.size(); rank++) {
            MoveCandidate candidate = candidates.get(rank);
            if (!isAccepted(best, candidate)) {
                continue;
            }
            if (candidate.uci().equals(played)) {
                return null; // what was played is as good as what would be asked for
            }
            if (rank > 0) {
                alsoAccepted.add(candidate.uci());
            }
        }
        Puzzle puzzle =
                new Puzzle(
                        "mistake-" + (startPly + ply),
                        previousFen,
                        List.of(uciMoves.get(ply - 1), best.uci()),
                        0,
                        List.of(),
                        chess960,
                        alsoAccepted);
        String san = ply < sanMoves.size() ? sanMoves.get(ply) : played;
        return new Mistake(puzzle, PostGameReport.plyLabel(startPly + ply), san, quality);
    }
}
