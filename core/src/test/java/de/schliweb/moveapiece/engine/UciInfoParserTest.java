/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.util.OptionalInt;
import org.junit.Test;

public class UciInfoParserTest {

    @Test
    public void parseScoreCp_positive() {
        String line = "info depth 12 seldepth 18 multipv 1 score cp 42 nodes 123456 pv e2e4 e7e5";
        assertEquals(OptionalInt.of(42), UciInfoParser.parseScoreCp(line));
    }

    @Test
    public void parseScoreCp_negative() {
        String line = "info depth 12 score cp -137 pv d7d5";
        assertEquals(OptionalInt.of(-137), UciInfoParser.parseScoreCp(line));
    }

    @Test
    public void parseScoreMate_positive() {
        String line = "info depth 5 score mate 3 pv h5f7";
        assertEquals(OptionalInt.of(3), UciInfoParser.parseScoreMate(line));
    }

    @Test
    public void parseScoreMate_negative() {
        String line = "info depth 5 score mate -2 pv g8h6";
        assertEquals(OptionalInt.of(-2), UciInfoParser.parseScoreMate(line));
    }

    @Test
    public void parseScoreCp_absentWhenLineHasNoScore() {
        String line = "info depth 1 currmove e2e4 currmovenumber 1";
        assertFalse(UciInfoParser.parseScoreCp(line).isPresent());
    }

    @Test
    public void parseScoreMate_absentWhenLineHasCpScoreInstead() {
        String line = "info depth 12 score cp 42 pv e2e4";
        assertFalse(UciInfoParser.parseScoreMate(line).isPresent());
    }

    @Test
    public void parseScoreCp_absentOnNonInfoLines() {
        assertFalse(UciInfoParser.parseScoreCp("bestmove e2e4 ponder e7e5").isPresent());
        assertFalse(UciInfoParser.parseScoreCp("uciok").isPresent());
        assertFalse(UciInfoParser.parseScoreCp(null).isPresent());
        assertFalse(UciInfoParser.parseScoreCp("").isPresent());
    }
}
