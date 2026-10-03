/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.engine;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.move.Move;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class MaiaPositionEncoderTest {

    private static final int TOKEN = MaiaPositionEncoder.TOKEN_SIZE;

    private static float at(float[] tokens, int square, int step, int channel) {
        return tokens[square * TOKEN + step * 12 + channel];
    }

    @Test
    public void startPosition_ownPiecesInTheFirstSixChannels() {
        int[] placement = MaiaPositionEncoder.placement(new Board());
        assertEquals(3, placement[0]); // a1: own rook
        assertEquals(5, placement[4]); // e1: own king
        assertEquals(0, placement[12]); // e2: own pawn
        assertEquals(-1, placement[28]); // e4: empty
        assertEquals(6, placement[52]); // e7: opponent's pawn
        assertEquals(6 + 4, placement[59]); // d8: opponent's queen
    }

    /** With Black to move the board is flipped and the colours swap, so Black sees itself below. */
    @Test
    public void blackToMove_isFlippedAndColourSwapped() {
        Board board = new Board();
        board.doMove(new Move("e2e4", Side.WHITE));
        int[] placement = MaiaPositionEncoder.placement(board);
        assertEquals(5, placement[4]); // e8, seen as e1: own king
        assertEquals(0, placement[12]); // e7, seen as e2: own pawn
        assertEquals(6, placement[36]); // White's e4, seen as e5: opponent's pawn
        assertEquals(-1, placement[52]); // White's e2, seen as e7: empty
    }

    @Test
    public void shortHistory_isPaddedWithItsOldestPosition() {
        Board board = new Board();
        List<int[]> history = new ArrayList<>();
        history.add(MaiaPositionEncoder.placement(board));
        board.doMove(new Move("e2e4", Side.WHITE));
        history.add(MaiaPositionEncoder.placement(board));

        float[] tokens = MaiaPositionEncoder.encode(history);
        assertEquals(64 * TOKEN, tokens.length);
        // Steps 0-6 hold the starting position (own pawn on e2), step 7 the position after 1.e4.
        for (int step = 0; step < 7; step++) {
            assertEquals(1f, at(tokens, 12, step, 0), 0f);
        }
        assertEquals(1f, at(tokens, 36, 7, 6), 0f);
        assertEquals(0f, at(tokens, 36, 6, 6), 0f);
        // The trailing clock channel stays zero.
        assertEquals(0f, tokens[12 * TOKEN + TOKEN - 1], 0f);
    }

    @Test
    public void longHistory_keepsOnlyTheLastEight() {
        Board board = new Board();
        List<int[]> history = new ArrayList<>();
        history.add(MaiaPositionEncoder.placement(board));
        String[] moves = {
            "g1f3", "g8f6", "f3g1", "f6g8", "g1f3", "g8f6", "f3g1", "f6g8", "e2e4", "e7e5"
        };
        for (String uci : moves) {
            board.doMove(new Move(uci, board.getSideToMove()));
            history.add(MaiaPositionEncoder.placement(board));
        }
        List<int[]> lastEight = history.subList(history.size() - 8, history.size());
        assertArrayEquals(
                MaiaPositionEncoder.encode(new ArrayList<>(lastEight)),
                MaiaPositionEncoder.encode(history),
                0f);
    }
}
