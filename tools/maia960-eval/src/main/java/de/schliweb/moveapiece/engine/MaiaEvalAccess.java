/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.engine;

import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.move.Move;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

/**
 * Opens the package-private parts of the Maia integration to the measurement harness in
 * tools/maia960-eval. Lives in that tool, not in :core, so the apps never see it.
 */
public final class MaiaEvalAccess {

    public static final int POLICY_SIZE = MaiaMoveIndexer.POLICY_SIZE;
    public static final int SQUARES = MaiaPositionEncoder.SQUARES;
    public static final int TOKEN_SIZE = MaiaPositionEncoder.TOKEN_SIZE;
    public static final int HISTORY_STEPS = MaiaPositionEncoder.HISTORY_STEPS;

    private MaiaEvalAccess() {}

    /** A {@link MaiaEngine} whose sampling draws from {@code random}, so it can be seeded. */
    public static MaiaEngine newEngine(Consumer<Runnable> dispatcher, Random random) {
        return new MaiaEngine(dispatcher, random);
    }

    public static int[] placement(Board board) {
        return MaiaPositionEncoder.placement(board);
    }

    public static float[] encode(List<int[]> history) {
        return MaiaPositionEncoder.encode(history);
    }

    /** The bonus the app adds to a Chess960 castle's logit. */
    public static final float CHESS960_CASTLE_BONUS = MaiaMoveIndexer.CHESS960_CASTLE_BONUS;

    /** The logit the app ranks {@code move} by, castling bonus included. */
    public static float logitOf(Board board, Move move, float[] policy) {
        return MaiaMoveIndexer.logitOf(board, move, policy);
    }

    public static int indexOf(Board board, Move move) {
        return MaiaMoveIndexer.indexOf(board, move);
    }

    /** The slot of {@code move} without any Chess960 castling translation. */
    public static int plainIndexOf(Move move, boolean blackToMove) {
        return MaiaMoveIndexer.indexOf(move, blackToMove);
    }
}
