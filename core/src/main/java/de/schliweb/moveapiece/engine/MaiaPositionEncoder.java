/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.engine;

import java.util.List;

/**
 * Builds the 112x8x8 input tensor a Maia/lc0 network (declared {@code
 * INPUT_CLASSICAL_112_PLANE}) expects, from a short window of prior board snapshots plus the
 * current position's castling rights and half-move clock. See MAIA_PROVENANCE_TEMPLATE.md for the
 * plane layout this mirrors (sourced from lc0's own {@code encoder.cc}) and for which parts of it
 * are empirically cross-checked versus reasoned-through-but-not-yet-verified.
 *
 * <p><b>Simplification versus lc0's own encoder, confirmed equivalent by test:</b> lc0's internal
 * encoder alternates a per-history-step mirror flag, because its {@code Position} objects are
 * already stored relative to whichever side moved at that specific ply. This class instead applies
 * one single, constant mirror decision - based only on whose turn it is <i>now</i> - uniformly
 * across all 8 history steps, which is equivalent when building the tensor fresh from absolute
 * (non-flipped) board snapshots rather than from lc0's internal incremental storage. Confirmed via
 * {@code MaiaEngineGoldenTest#multiPlyHistory_blackToMove} (three real ply of history, Black to
 * move) and {@code #castlingRightsChanged_blackToMove} (castling rights that actually changed
 * through play, not just the all-rights starting case) against lc0's own native output for the same
 * positions - not just this class in isolation.
 *
 * <p>Also confirmed by that same test battery: the repetition plane ({@code
 * MaiaEngineGoldenTest#repeatedStartingPosition_whiteToMove}, a position reached the second time via
 * a real knight-shuffle move sequence) and the zero-padding rule below actually changing the
 * network's output versus a fresh game reaching the same piece placement - see
 * MAIA_PROVENANCE_TEMPLATE.md for the exact lc0-native reference numbers each test asserts against.
 */
final class MaiaPositionEncoder {

    static final int HISTORY_STEPS = 8;
    static final int PLANES_PER_BOARD = 13;
    static final int AUX_BASE = HISTORY_STEPS * PLANES_PER_BOARD; // 104
    static final int PLANE_COUNT = AUX_BASE + 8; // 112
    private static final int SQUARES = 64;

    // Piece.ordinal() 0-5 (Pawn,Knight,Bishop,Rook,Queen,King) bitboards for White's/Black's side
    // of the standard chess starting position - see #isStartingPosition.
    private static final long[] STARTPOS_WHITE = {0xFF00L, 0x42L, 0x24L, 0x81L, 0x08L, 0x10L};
    private static final long[] STARTPOS_BLACK = {
        0xFF000000000000L,
        0x4200000000000000L,
        0x2400000000000000L,
        0x8100000000000000L,
        0x0800000000000000L,
        0x1000000000000000L
    };

    private MaiaPositionEncoder() {}

    /** One historical position's piece placement plus how many times it had occurred so far. */
    static final class Snapshot {
        /** Indexed by {@code Piece.ordinal()} (0-11; WHITE_PAWN..WHITE_KING, BLACK_PAWN..BLACK_KING). */
        final long[] pieceBitboards;

        /** 0 if this exact position (by placement/side/castling/en-passant) hadn't occurred before. */
        final int repetitions;

        Snapshot(long[] pieceBitboards, int repetitions) {
            this.pieceBitboards = pieceBitboards;
            this.repetitions = repetitions;
        }
    }

    /**
     * @param history current position first, oldest last. Must not be empty. Fewer than {@link
     *     #HISTORY_STEPS} entries is padded to match lc0's own {@code HistoryFill=fen_only}
     *     default: if the oldest available snapshot is the standard chess starting position (true
     *     for every game {@link MaiaEngine} plays, for its first 7 plies), the missing older steps
     *     are left as all-zero planes rather than repeated - lc0's {@code encoder.cc} explicitly
     *     stops padding once it would have to "invent" a pre-game position that turns out to equal
     *     the starting position, rather than fabricating repeated history that never happened. Only
     *     for a hypothetical mid-game FEN import (not something {@link MaiaEngine} does today) would
     *     the oldest snapshot not be the starting position, in which case this falls back to
     *     repeating it - an approximation, not something cross-checked against lc0 itself.
     * @param blackToMove whose turn it is in the current (most recent) position
     * @param halfMoveClock the current position's 50-move-rule ply counter (not normalized - that
     *     only applies to the newer "Hectoplies" input formats, not {@code
     *     INPUT_CLASSICAL_112_PLANE})
     */
    static float[] encode(
            List<Snapshot> history,
            boolean blackToMove,
            int halfMoveClock,
            boolean weCanCastleQueenside,
            boolean weCanCastleKingside,
            boolean theyCanCastleQueenside,
            boolean theyCanCastleKingside) {
        float[] tensor = new float[PLANE_COUNT * SQUARES];

        Snapshot oldest = history.get(history.size() - 1);
        boolean padWithZero = isStartingPosition(oldest);
        for (int step = 0; step < HISTORY_STEPS; step++) {
            if (step < history.size()) {
                writeBoardPlanes(tensor, step * PLANES_PER_BOARD, history.get(step), blackToMove);
            } else if (!padWithZero) {
                writeBoardPlanes(tensor, step * PLANES_PER_BOARD, oldest, blackToMove);
            }
            // else: leave this step's 13 planes at zero (tensor is zero-initialized).
        }

        setPlaneAll(tensor, AUX_BASE + 0, weCanCastleQueenside);
        setPlaneAll(tensor, AUX_BASE + 1, weCanCastleKingside);
        setPlaneAll(tensor, AUX_BASE + 2, theyCanCastleQueenside);
        setPlaneAll(tensor, AUX_BASE + 3, theyCanCastleKingside);
        setPlaneAll(tensor, AUX_BASE + 4, blackToMove);
        fillPlane(tensor, AUX_BASE + 5, halfMoveClock);
        // AUX_BASE + 6 stays all-zero (lc0: "used to be movecount plane, now it's all zeros"
        // outside Armageddon time-odds formats, which Maia's INPUT_CLASSICAL_112_PLANE isn't).
        setPlaneAll(tensor, AUX_BASE + 7, true); // constant "help the NN find board edges" plane

        return tensor;
    }

    /** Whether every piece sits exactly where the standard chess starting position puts it. */
    private static boolean isStartingPosition(Snapshot snapshot) {
        for (int type = 0; type < 6; type++) {
            if (snapshot.pieceBitboards[type] != STARTPOS_WHITE[type]
                    || snapshot.pieceBitboards[6 + type] != STARTPOS_BLACK[type]) {
                return false;
            }
        }
        return true;
    }

    private static void writeBoardPlanes(
            float[] tensor, int base, Snapshot snapshot, boolean blackToMove) {
        // Piece.ordinal(): 0=WHITE_PAWN..5=WHITE_KING, 6=BLACK_PAWN..11=BLACK_KING - both halves
        // list Pawn,Knight,Bishop,Rook,Queen,King in the same order, so a single offset picks
        // "own" vs "their" pieces without needing a per-type lookup.
        int ownOffset = blackToMove ? 6 : 0;
        int theirOffset = blackToMove ? 0 : 6;
        for (int type = 0; type < 6; type++) {
            long own = snapshot.pieceBitboards[ownOffset + type];
            long their = snapshot.pieceBitboards[theirOffset + type];
            if (blackToMove) {
                own = Long.reverseBytes(own);
                their = Long.reverseBytes(their);
            }
            writeBitboardPlane(tensor, base + type, own);
            writeBitboardPlane(tensor, base + 6 + type, their);
        }
        if (snapshot.repetitions >= 1) {
            setPlaneAll(tensor, base + 12, true);
        }
    }

    private static void writeBitboardPlane(float[] tensor, int plane, long bitboard) {
        int offset = plane * SQUARES;
        for (int square = 0; square < SQUARES; square++) {
            if (((bitboard >>> square) & 1L) != 0) {
                tensor[offset + square] = 1.0f;
            }
        }
    }

    private static void setPlaneAll(float[] tensor, int plane, boolean value) {
        if (!value) {
            return; // tensor is already zero-initialized
        }
        int offset = plane * SQUARES;
        for (int i = 0; i < SQUARES; i++) {
            tensor[offset + i] = 1.0f;
        }
    }

    private static void fillPlane(float[] tensor, int plane, float value) {
        int offset = plane * SQUARES;
        for (int i = 0; i < SQUARES; i++) {
            tensor[offset + i] = value;
        }
    }
}
