/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.chessnut.core.protocol;

import static org.junit.Assert.assertEquals;

import de.schliweb.pegasus.core.protocol.PieceCodes;
import org.junit.Test;

public class ChessnutPieceCodesTest {

    @Test
    public void translatesAllTwelvePieces() {
        assertEquals(PieceCodes.EMPTY, ChessnutPieceCodes.toPieceCode(0x0));
        assertEquals(PieceCodes.BQUEEN, ChessnutPieceCodes.toPieceCode(0x1));
        assertEquals(PieceCodes.BKING, ChessnutPieceCodes.toPieceCode(0x2));
        assertEquals(PieceCodes.BBISHOP, ChessnutPieceCodes.toPieceCode(0x3));
        assertEquals(PieceCodes.BPAWN, ChessnutPieceCodes.toPieceCode(0x4));
        assertEquals(PieceCodes.BKNIGHT, ChessnutPieceCodes.toPieceCode(0x5));
        assertEquals(PieceCodes.WROOK, ChessnutPieceCodes.toPieceCode(0x6));
        assertEquals(PieceCodes.WPAWN, ChessnutPieceCodes.toPieceCode(0x7));
        assertEquals(PieceCodes.BROOK, ChessnutPieceCodes.toPieceCode(0x8));
        assertEquals(PieceCodes.WBISHOP, ChessnutPieceCodes.toPieceCode(0x9));
        assertEquals(PieceCodes.WKNIGHT, ChessnutPieceCodes.toPieceCode(0xA));
        assertEquals(PieceCodes.WQUEEN, ChessnutPieceCodes.toPieceCode(0xB));
        assertEquals(PieceCodes.WKING, ChessnutPieceCodes.toPieceCode(0xC));
    }

    @Test
    public void unknownNibblesAreEmptyButRenderAsQuestionMark() {
        for (int n = 0xD; n <= 0xF; n++) {
            assertEquals(PieceCodes.EMPTY, ChessnutPieceCodes.toPieceCode(n));
            assertEquals('?', ChessnutPieceCodes.toChar(n));
        }
        assertEquals('.', ChessnutPieceCodes.toChar(0));
        assertEquals('K', ChessnutPieceCodes.toChar(0xC));
        assertEquals('q', ChessnutPieceCodes.toChar(0x1));
    }
}
