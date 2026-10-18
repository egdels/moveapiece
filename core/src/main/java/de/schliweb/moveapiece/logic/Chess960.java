/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.logic;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * The 960 starting positions of Chess960, numbered 0 to 959 the usual way (Scharnagl): the number
 * places the light-squared bishop, the dark-squared bishop, the queen and the two knights, and the
 * three squares left over take rook, king and rook in that order. Number 518 is the standard
 * starting position.
 */
public final class Chess960 {

    public static final int POSITIONS = 960;

    /** The number of the standard starting position. */
    public static final int STANDARD = 518;

    /** Knight placements on the five squares left after bishops and queen, by number 0 to 9. */
    private static final int[][] KNIGHTS = {
        {0, 1}, {0, 2}, {0, 3}, {0, 4}, {1, 2}, {1, 3}, {1, 4}, {2, 3}, {2, 4}, {3, 4}
    };

    private static final Map<String, Integer> NUMBERS = new HashMap<>();

    static {
        for (int number = 0; number < POSITIONS; number++) {
            NUMBERS.put(backRank(number), number);
        }
    }

    private Chess960() {}

    /** White's back rank from the a- to the h-file, e.g. "RNBQKBNR" for {@link #STANDARD}. */
    public static String backRank(int number) {
        if (number < 0 || number >= POSITIONS) {
            throw new IllegalArgumentException("No Chess960 position " + number);
        }
        char[] rank = new char[8];
        int n = number;
        rank[(n % 4) * 2 + 1] = 'B';
        n /= 4;
        rank[(n % 4) * 2] = 'B';
        n /= 4;
        placeOnFree(rank, n % 6, 'Q');
        n /= 6;
        // The second knight goes first: placing the first one would shift the free squares.
        placeOnFree(rank, KNIGHTS[n][1], 'N');
        placeOnFree(rank, KNIGHTS[n][0], 'N');
        placeOnFree(rank, 0, 'R');
        placeOnFree(rank, 0, 'K');
        placeOnFree(rank, 0, 'R');
        return new String(rank);
    }

    private static void placeOnFree(char[] rank, int freeIndex, char piece) {
        int free = 0;
        for (int file = 0; file < rank.length; file++) {
            if (rank[file] == 0) {
                if (free == freeIndex) {
                    rank[file] = piece;
                    return;
                }
                free++;
            }
        }
        throw new IllegalStateException("No free square " + freeIndex);
    }

    /**
     * The number of the starting position with this back rank (White's pieces from a to h, in
     * either case), or -1 if it is none of the 960.
     */
    public static int numberOf(String backRank) {
        Integer number = backRank == null ? null : NUMBERS.get(backRank.toUpperCase());
        return number == null ? -1 : number;
    }

    /**
     * The starting position as FEN, with the castling rights in Shredder notation (the files of the
     * rooks, e.g. "HAha"), so that the rooks are named unambiguously and chesslib recognises the
     * game as Chess960 even for {@link #STANDARD}.
     */
    public static String startFen(int number) {
        String white = backRank(number);
        String rights = "";
        int king = white.indexOf('K');
        for (int file = 7; file >= 0; file--) {
            if (file != king && white.charAt(file) == 'R') {
                rights += (char) ('A' + file);
            }
        }
        return white.toLowerCase()
                + "/pppppppp/8/8/8/8/PPPPPPPP/"
                + white
                + " w "
                + rights
                + rights.toLowerCase()
                + " - 0 1";
    }

    /** A starting position number picked at random. */
    public static int random(Random random) {
        return random.nextInt(POSITIONS);
    }
}
