/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.collector;

import com.github.bhlangonijr.chesslib.move.MoveList;
import java.util.ArrayList;
import java.util.List;

/**
 * The positions to photograph: the empty board and Chess960 starts, three old games played through
 * a few moves at a time, so that one position follows from the last with little work on the board,
 * and some endings set up afresh. Different positions keep the recogniser from learning which piece
 * usually stands on a square.
 */
final class Positions {

    /** One position to set up and photograph. */
    static final class Position {
        /** Short name for folders and files, e.g. {@code opera_12}. */
        final String id;

        /** What the user reads, e.g. "Opera game, 12 of 33". */
        final String title;

        /** How to get here from the position before: the moves to play, or "set up". */
        final String moves;

        /** Piece placement as in a FEN. */
        final String placement;

        Position(String id, String title, String moves, String placement) {
            this.id = id;
            this.title = title;
            this.moves = moves;
            this.placement = placement;
        }
    }

    /** A new position every this many half-moves of a game. */
    private static final int STEP = 4;

    private static final String[][] GAMES = {
        {
            "opera",
            "Opera game",
            "e4 e5 Nf3 d6 d4 Bg4 dxe5 Bxf3 Qxf3 dxe5 Bc4 Nf6 Qb3 Qe7 Nc3 c6 Bg5 b5 Nxb5 cxb5 Bxb5+"
                    + " Nbd7 O-O-O Rd8 Rxd7 Rxd7 Rd1 Qe6 Bxd7+ Nxd7 Qb8+ Nxb8 Rd8#"
        },
        {
            "immortal",
            "Immortal game",
            "e4 e5 f4 exf4 Bc4 Qh4+ Kf1 b5 Bxb5 Nf6 Nf3 Qh6 d3 Nh5 Nh4 Qg5 Nf5 c6 g4 Nf6 Rg1 cxb5 h4"
                    + " Qg6 h5 Qg5 Qf3 Ng8 Bxf4 Qf6 Nc3 Bc5 Nd5 Qxb2 Bd6 Bxg1 e5 Qxa1+ Ke2 Na6 Nxg7+"
                    + " Kd8 Qf6+ Nxf6 Be7#"
        },
        {
            "evergreen",
            "Evergreen game",
            "e4 e5 Nf3 Nc6 Bc4 Bc5 b4 Bxb4 c3 Ba5 d4 exd4 O-O d3 Qb3 Qf6 e5 Qg6 Re1 Nge7 Ba3 b5 Qxb5"
                    + " Rb8 Qa4 Bb6 Nbd2 Bb7 Ne4 Qf5 Bxd3 Qh5 Nf6+ gxf6 exf6 Rg8 Rad1 Qxf3 Rxe7+ Nxe7"
                    + " Qxd7+ Kxd7 Bf5+ Ke8 Bd7+ Kf8 Bxe7#"
        },
    };

    /**
     * What comes first, with the least work on the board: the empty board (pieces cut out of single
     * photos are put on it later), the starting position and Chess960 starts, which mix the back
     * rank: id, title, placement.
     */
    private static final String[][] FIRST = {
        {"empty_00", "Empty board", "8/8/8/8/8/8/8/8"},
        {"start_00", "Starting position", "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR"},
        {
            "chess960_01",
            "Chess960 start B N R K R B N Q",
            "bnrkrbnq/pppppppp/8/8/8/8/PPPPPPPP/BNRKRBNQ"
        },
        {
            "chess960_02",
            "Chess960 start R K N B B Q N R",
            "rknbbqnr/pppppppp/8/8/8/8/PPPPPPPP/RKNBBQNR"
        },
        {
            "chess960_03",
            "Chess960 start N Q R B K R B N",
            "nqrbkrbn/pppppppp/8/8/8/8/PPPPPPPP/NQRBKRBN"
        },
    };

    /** Endings and a position with promoted pieces, set up afresh: id, title, placement. */
    private static final String[][] SET_UP = {
        {"ending_rook", "Rook ending", "1K1k4/1P6/8/8/8/8/r7/2R5"},
        {"ending_pawns", "Pawn ending", "8/5pk1/6p1/7p/5P1P/6P1/6K1/8"},
        {"ending_minor", "Bishop against knight", "8/4k3/3n1p2/8/3BP3/5K2/8/8"},
        {
            "ending_queens",
            "Second white queen (skip without one)",
            "3q2k1/5ppp/8/8/1Q6/8/5PPP/3Q2K1"
        },
    };

    private Positions() {}

    static List<Position> all() {
        List<Position> out = new ArrayList<>();
        for (String[] first : FIRST) {
            out.add(new Position(first[0], first[1], "", first[2]));
        }
        for (String[] game : GAMES) {
            MoveList moves = new MoveList();
            try {
                moves.loadFromSan(game[2]);
            } catch (Exception e) {
                throw new IllegalStateException("game " + game[0] + " does not load", e);
            }
            String[] sans = game[2].trim().split("\\s+");
            int last = 0;
            for (int ply = 0; ply <= sans.length; ply = next(ply, sans.length)) {
                String how;
                if (ply == 0) {
                    how = "";
                } else {
                    StringBuilder text = new StringBuilder();
                    for (int i = last; i < ply; i++) {
                        if (i % 2 == 0) {
                            text.append(i / 2 + 1).append(". ");
                        } else if (i == last) {
                            text.append(i / 2 + 1).append("... ");
                        }
                        text.append(sans[i]).append(' ');
                    }
                    how = text.toString().trim();
                }
                String fen = ply == 0 ? moves.getStartFen() : moves.getFen(ply);
                out.add(
                        new Position(
                                game[0] + "_" + (ply < 10 ? "0" : "") + ply,
                                game[1] + ", " + ply + "/" + sans.length,
                                how,
                                fen.split(" ")[0]));
                last = ply;
                if (ply == sans.length) {
                    break;
                }
            }
        }
        for (String[] setUp : SET_UP) {
            out.add(new Position(setUp[0], setUp[1], "", setUp[2]));
        }
        return out;
    }

    private static int next(int ply, int length) {
        return Math.min(length, ply + STEP);
    }
}
