/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.maia960eval;

import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.File;
import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.PieceType;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.Square;
import com.github.bhlangonijr.chesslib.game.VariationType;
import com.github.bhlangonijr.chesslib.move.Move;
import de.schliweb.moveapiece.engine.MaiaEvalAccess;
import java.util.List;
import java.util.Random;

/**
 * A way of scoring a Chess960 castle from Maia's policy, to compare against the app's {@code
 * MaiaMoveIndexer} ({@link #APP}). Every other move is scored by its plain slot, exactly as in the
 * app.
 *
 * @param mapping {@code king}: the slot of the king's move to its castling square, or the rook's
 *     move when the king already stands there (the app); {@code std}: the slot of standard
 *     castling, e1g1/e1c1 from the side to move's view; {@code max}: the higher of the two
 * @param bonus added to a Chess960 castle's logit
 */
record CastleVariant(String mapping, double bonus) {

    /** What the app does: the king mapping with its castling bonus (1 since 7ec6efb). */
    static final CastleVariant APP =
            new CastleVariant("king", MaiaEvalAccess.CHESS960_CASTLE_BONUS);

    static CastleVariant parse(String spec) {
        String[] p = spec.split(":");
        return new CastleVariant(p[0], p.length > 1 ? Double.parseDouble(p[1]) : 0);
    }

    boolean isApp() {
        return mapping.equals("king") && bonus == MaiaEvalAccess.CHESS960_CASTLE_BONUS;
    }

    @Override
    public String toString() {
        return mapping + ":" + bonus;
    }

    double score(Board board, Move move, float[] policy) {
        if (isApp()) {
            return MaiaEvalAccess.logitOf(board, move, policy);
        }
        if (!isChess960Castle(board, move)) {
            return policy[MaiaEvalAccess.indexOf(board, move)];
        }
        double king = policy[MaiaEvalAccess.indexOf(board, move)];
        boolean kingSide = move.getTo().getFile().ordinal() > move.getFrom().getFile().ordinal();
        Square from = Square.encode(move.getFrom().getRank(), File.FILE_E);
        Square to = Square.encode(move.getFrom().getRank(), kingSide ? File.FILE_G : File.FILE_C);
        double std =
                policy[
                        MaiaEvalAccess.plainIndexOf(
                                new Move(from, to), board.getSideToMove() == Side.BLACK)];
        double base =
                switch (mapping) {
                    case "king" -> king;
                    case "std" -> std;
                    case "max" -> Math.max(king, std);
                    default -> throw new IllegalArgumentException("Unknown mapping " + mapping);
                };
        return base + bonus;
    }

    static boolean isChess960Castle(Board board, Move move) {
        Piece king = board.getPiece(move.getFrom());
        Piece target = board.getPiece(move.getTo());
        return board.getContext().getVariationType() == VariationType.CHESS960
                && king.getPieceType() == PieceType.KING
                && target.getPieceType() == PieceType.ROOK
                && target.getPieceSide() == king.getPieceSide();
    }

    /**
     * Picks a move the way {@code MaiaEngine.chooseMove} does: the first highest score at
     * temperature 0, else a draw from {@code random} weighted by exp(score / temperature).
     */
    static Move choose(List<Move> legal, double[] scores, double temperature, Random random) {
        if (temperature <= 0.0) {
            int best = 0;
            for (int i = 1; i < scores.length; i++) {
                if (scores[i] > scores[best]) {
                    best = i;
                }
            }
            return legal.get(best);
        }
        double max = Double.NEGATIVE_INFINITY;
        for (double s : scores) {
            max = Math.max(max, s);
        }
        double[] weights = new double[scores.length];
        double sum = 0;
        for (int i = 0; i < scores.length; i++) {
            weights[i] = Math.exp((scores[i] - max) / temperature);
            sum += weights[i];
        }
        double pick = random.nextDouble() * sum;
        double acc = 0;
        for (int i = 0; i < weights.length; i++) {
            acc += weights[i];
            if (pick <= acc) {
                return legal.get(i);
            }
        }
        return legal.get(legal.size() - 1);
    }
}
