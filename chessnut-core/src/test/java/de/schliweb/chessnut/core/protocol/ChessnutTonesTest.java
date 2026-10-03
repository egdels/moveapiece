/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.chessnut.core.protocol;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ChessnutTonesTest {

    @Test
    public void ordinaryMovesAreSilent() {
        assertTrue(ChessnutTones.forMove(false, false).isEmpty());
    }

    @Test
    public void checkIsOneToneAndMateTwo() {
        assertEquals(1, ChessnutTones.forMove(true, false).size());
        assertEquals(2, ChessnutTones.forMove(true, true).size());
    }
}
