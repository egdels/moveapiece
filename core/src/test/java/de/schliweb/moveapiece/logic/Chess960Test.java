/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.logic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.junit.Test;

/**
 * Checks the Chess960 numbering against well-known positions; all 960 were also compared once
 * against python-chess's {@code Board.from_chess960_pos}.
 */
public class Chess960Test {

    @Test
    public void backRank_knownPositions() {
        assertEquals("BBQNNRKR", Chess960.backRank(0));
        assertEquals("RNBQKBNR", Chess960.backRank(Chess960.STANDARD));
        assertEquals("RKRNNQBB", Chess960.backRank(959));
    }

    @Test
    public void everyPositionIsValidAndDistinct() {
        Set<String> seen = new HashSet<>();
        for (int number = 0; number < Chess960.POSITIONS; number++) {
            String rank = Chess960.backRank(number);
            assertTrue(rank, seen.add(rank));
            int king = rank.indexOf('K');
            int firstRook = rank.indexOf('R');
            int lastRook = rank.lastIndexOf('R');
            assertTrue(rank, firstRook < king && king < lastRook);
            int bishop = rank.indexOf('B');
            int otherBishop = rank.lastIndexOf('B');
            assertTrue(rank, (bishop + otherBishop) % 2 == 1);
            assertEquals(number, Chess960.numberOf(rank));
        }
    }

    @Test
    public void numberOf_acceptsEitherCaseAndRejectsOthers() {
        assertEquals(Chess960.STANDARD, Chess960.numberOf("rnbqkbnr"));
        assertEquals(-1, Chess960.numberOf("RRKNNQBB")); // king not between the rooks
        assertEquals(-1, Chess960.numberOf("RBQBKNNR")); // bishops on squares of one colour
        assertEquals(-1, Chess960.numberOf(null));
    }

    @Test
    public void startFen_namesTheRookFilesInShredderNotation() {
        assertEquals(
                "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w HAha - 0 1",
                Chess960.startFen(Chess960.STANDARD));
        assertEquals(
                "bbqnnrkr/pppppppp/8/8/8/8/PPPPPPPP/BBQNNRKR w HFhf - 0 1", Chess960.startFen(0));
    }

    @Test
    public void random_staysInRange() {
        Random random = new Random(1);
        for (int i = 0; i < 1000; i++) {
            int number = Chess960.random(random);
            assertTrue(number >= 0 && number < Chess960.POSITIONS);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void backRank_rejectsOutOfRange() {
        Chess960.backRank(960);
    }
}
