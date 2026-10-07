/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.logic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.github.bhlangonijr.chesslib.Square;
import de.schliweb.pegasus.core.transport.TransportError;
import org.junit.Test;

public class UciMovesTest {

    @Test
    public void fromAndTo_readTheSquaresOfAPlainMoveAndAPromotion() {
        assertEquals(Square.E2, UciMoves.from("e2e4"));
        assertEquals(Square.E4, UciMoves.to("e2e4"));
        assertEquals(Square.E7, UciMoves.from("e7e8q"));
        assertEquals(Square.E8, UciMoves.to("e7e8q"));
    }

    @Test
    public void wrongProfileErrors_areTheMissingServiceAndCharacteristic() {
        assertTrue(BoardTypeDetection.isWrongProfileError(TransportError.SERVICE_NOT_FOUND));
        assertTrue(BoardTypeDetection.isWrongProfileError(TransportError.CHARACTERISTIC_NOT_FOUND));
        for (TransportError error : TransportError.values()) {
            if (error != TransportError.SERVICE_NOT_FOUND
                    && error != TransportError.CHARACTERISTIC_NOT_FOUND) {
                assertFalse(error.name(), BoardTypeDetection.isWrongProfileError(error));
            }
        }
    }
}
