/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.analysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The outcome of replaying a game through the engine: per side the average centipawn loss and the
 * number of inaccuracies, mistakes and blunders, plus every flagged move in order.
 */
public final class PostGameReport {

    /** One side's totals. */
    public static final class SideSummary {
        private int plies;
        private double lossSum;
        private final int[] counts = new int[MoveQuality.values().length];

        /** Average loss per move in pawns; 0 for a side that has not moved. */
        public double averageLossPawns() {
            return plies == 0 ? 0.0 : lossSum / plies / 100.0;
        }

        public int count(MoveQuality quality) {
            return counts[quality.ordinal()];
        }
    }

    /** A move graded as an inaccuracy or worse. */
    public static final class FlaggedMove {
        private final int ply;
        private final String san;
        private final MoveQuality quality;
        private final int cpLoss;

        FlaggedMove(int ply, String san, MoveQuality quality, int cpLoss) {
            this.ply = ply;
            this.san = san;
            this.quality = quality;
            this.cpLoss = cpLoss;
        }

        /** "12." for a white move, "12..." for a black one. */
        public String plyLabel() {
            return PostGameReport.plyLabel(ply);
        }

        public String san() {
            return san;
        }

        public MoveQuality quality() {
            return quality;
        }

        /** The loss in pawns as shown to the player: negative. */
        public double lossPawns() {
            return -cpLoss / 100.0;
        }
    }

    private final SideSummary white = new SideSummary();
    private final SideSummary black = new SideSummary();
    private final List<FlaggedMove> flaggedMoves = new ArrayList<>();

    private PostGameReport() {}

    /**
     * Grades every move of a game.
     *
     * @param uciMoves the moves played, in order
     * @param evals the engine's score for each position from its side to move, in centipawns: one
     *     more than {@code uciMoves}, starting with the position before the first move
     * @param sanMoves the same moves in SAN, for display; a move missing there is shown in UCI
     * @param startPly the half-moves between White's move 1 and the first of {@code uciMoves}: 0
     *     for a game from the initial position, odd when Black moved first (a position taken over
     *     from the board)
     */
    public static PostGameReport of(
            List<String> uciMoves, List<Integer> evals, List<String> sanMoves, int startPly) {
        PostGameReport report = new PostGameReport();
        for (int ply = 0; ply < uciMoves.size(); ply++) {
            int gamePly = startPly + ply;
            SideSummary side = gamePly % 2 == 0 ? report.white : report.black;
            side.plies++;
            // Both scores are from their own side to move, so the second one is already the
            // mover's eval with the sign flipped: adding it is the subtraction.
            int cpLoss = Math.max(0, evals.get(ply) + evals.get(ply + 1));
            side.lossSum += cpLoss;
            MoveQuality quality = MoveQuality.of(cpLoss);
            if (quality == null) {
                continue;
            }
            side.counts[quality.ordinal()]++;
            String san = ply < sanMoves.size() ? sanMoves.get(ply) : uciMoves.get(ply);
            report.flaggedMoves.add(new FlaggedMove(gamePly, san, quality, cpLoss));
        }
        return report;
    }

    public SideSummary white() {
        return white;
    }

    public SideSummary black() {
        return black;
    }

    public List<FlaggedMove> flaggedMoves() {
        return Collections.unmodifiableList(flaggedMoves);
    }

    /**
     * Splits every SAN move out of numbered movetext (e.g. "1. e4 e5 2. Nf3" -&gt; ["e4", "e5",
     * "Nf3"]) - ply-ordered, so it lines up 1:1 with the game's UCI move list.
     */
    public static List<String> sanMoveList(String movetext) {
        List<String> moves = new ArrayList<>();
        for (String token : movetext.split("\\s+")) {
            if (!token.isEmpty() && !token.matches("\\d+\\.")) {
                moves.add(token);
            }
        }
        return moves;
    }

    static String plyLabel(int plyIndex) {
        int moveNumber = plyIndex / 2 + 1;
        return plyIndex % 2 == 0 ? moveNumber + "." : moveNumber + "...";
    }
}
