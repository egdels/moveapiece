/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.training;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.Square;
import de.schliweb.moveapiece.logic.ChessGame;
import java.util.Arrays;
import org.junit.Test;

public class OpeningLinePlayerTest {

    private static final OpeningLine LINE =
            new OpeningLine("test", Arrays.asList("e2e4", "e7e5", "g1f3", "b8c6"));

    @Test
    public void startsAtPlyZero_withNoLastMove() {
        OpeningLinePlayer player = new OpeningLinePlayer(LINE);
        assertEquals(0, player.ply());
        assertFalse(player.hasPrevious());
        assertTrue(player.hasNext());
        assertNull(player.lastMoveSquares());
        assertEquals(0, player.gameAtCurrentPly().moveCount());
    }

    @Test
    public void next_advancesPlyAndTracksLastMove() {
        OpeningLinePlayer player = new OpeningLinePlayer(LINE);
        player.next();
        assertEquals(1, player.ply());
        assertArrayEquals(new Square[] {Square.E2, Square.E4}, player.lastMoveSquares());
        assertEquals(1, player.gameAtCurrentPly().moveCount());
    }

    @Test
    public void next_stopsAtEndOfLine() {
        OpeningLinePlayer player = new OpeningLinePlayer(LINE);
        for (int i = 0; i < LINE.uciMoves().size() + 5; i++) {
            player.next();
        }
        assertEquals(LINE.uciMoves().size(), player.ply());
        assertFalse(player.hasNext());
    }

    @Test
    public void previous_stopsAtStartOfLine() {
        OpeningLinePlayer player = new OpeningLinePlayer(LINE);
        player.previous();
        assertEquals(0, player.ply());
        assertFalse(player.hasPrevious());
    }

    @Test
    public void previous_undoesNext() {
        OpeningLinePlayer player = new OpeningLinePlayer(LINE);
        player.next();
        player.next();
        player.previous();
        assertEquals(1, player.ply());
        assertArrayEquals(new Square[] {Square.E2, Square.E4}, player.lastMoveSquares());
    }

    @Test
    public void gameAtCurrentPly_replaysExactlyThatManyMovesFromTheStart() {
        OpeningLinePlayer player = new OpeningLinePlayer(LINE);
        player.next();
        player.next();
        player.next();
        ChessGame game = player.gameAtCurrentPly();
        assertEquals(3, game.moveCount());
        assertEquals(Side.BLACK, game.sideToMove());
    }
}
