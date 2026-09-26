/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.chessnut.core.game;

import static org.junit.Assert.assertEquals;

import de.schliweb.chessnut.core.protocol.ChessnutBoardReport;
import de.schliweb.pegasus.core.chess.ChessPosition;
import de.schliweb.pegasus.core.chess.Piece;
import de.schliweb.pegasus.core.protocol.BoardState;
import de.schliweb.pegasus.core.protocol.PieceCodes;
import java.util.Arrays;
import org.junit.Test;

public class IdentityProjectionTest {

    /** Start position as the real board reported it (capture 2026-09-26 05:37:28), payload only. */
    private static final byte[] CAPTURED_START_PAYLOAD =
            hex(
                    "58 23 31 85 44 44 44 44 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00"
                            + " 77 77 77 77 a6 c9 9b 6a cb 01 00 00");

    private static byte[] hex(String s) {
        String[] parts = s.trim().split("\\s+");
        byte[] out = new byte[parts.length];
        for (int i = 0; i < parts.length; i++) {
            out[i] = (byte) Integer.parseInt(parts[i], 16);
        }
        return out;
    }

    @Test
    public void projectionOfStartingPositionEqualsWhatTheBoardReports() {
        BoardState projected = IdentityProjection.of(ChessPosition.starting());
        BoardState reported = ChessnutBoardReport.fromPayload(CAPTURED_START_PAYLOAD).state();

        assertEquals(reported, projected);
        assertEquals(PieceCodes.WKING, projected.pieceCodeAt(BoardState.squareIndex("e1")));
        assertEquals(PieceCodes.BQUEEN, projected.pieceCodeAt(BoardState.squareIndex("d8")));
    }

    @Test
    public void everyPieceHasADistinctNonEmptyCode() {
        int[] codes = new int[Piece.values().length];
        for (int i = 0; i < codes.length; i++) {
            codes[i] = IdentityProjection.codeOf(Piece.values()[i]);
        }
        Arrays.sort(codes);
        for (int i = 0; i < codes.length; i++) {
            assertEquals(i + 1, codes[i]);
        }
        assertEquals(PieceCodes.EMPTY, IdentityProjection.codeOf(null));
    }
}
