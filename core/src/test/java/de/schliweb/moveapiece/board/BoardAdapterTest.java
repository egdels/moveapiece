/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.board;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import de.schliweb.chessnut.core.game.InvalidPositionException;
import de.schliweb.moveapiece.logic.BoardType;
import de.schliweb.pegasus.core.chess.PieceType;
import de.schliweb.pegasus.core.transport.ConnectionState;
import org.junit.After;
import org.junit.Test;

/**
 * What the two {@link PhysicalBoardBridge} adapters answer themselves rather than pass on: the
 * capabilities one board has and the other lacks.
 */
public class BoardAdapterTest {

    private final TestMainThread main = new TestMainThread();
    private final FakeTransport transport = new FakeTransport();

    @After
    public void stopMainThread() {
        main.close();
    }

    private PhysicalBoardBridge pegasus() {
        PhysicalBoardBridge[] ref = new PhysicalBoardBridge[1];
        main.runSync(
                () ->
                        ref[0] =
                                new PegasusBoardAdapter(
                                        new PegasusGameBridge(transport, main, null)));
        return ref[0];
    }

    private PhysicalBoardBridge chessnut() {
        PhysicalBoardBridge[] ref = new PhysicalBoardBridge[1];
        main.runSync(
                () ->
                        ref[0] =
                                new ChessnutBoardAdapter(
                                        new ChessnutGameBridge(transport, main, null)));
        return ref[0];
    }

    @Test
    public void pegasus_knowsOccupancyOnly() {
        PhysicalBoardBridge board = pegasus();

        main.runSync(
                () -> {
                    assertEquals(BoardType.PEGASUS, board.type());
                    assertEquals(ConnectionState.DISCONNECTED, board.getConnectionState());
                    assertFalse(board.canLoadPhysicalPosition());
                    assertFalse(board.playMoveSound(true, true, true));
                    assertEquals(-1, board.promotionSquareAwaitingPiece());
                    try {
                        board.physicalPositionFen(true);
                        fail("a Pegasus cannot report a position");
                    } catch (InvalidPositionException e) {
                        assertEquals(InvalidPositionException.Reason.NO_BOARD, e.reason());
                    }
                });
    }

    @Test
    public void chessnut_identifiesPiecesAndNeedsNoPromptsOrHints() {
        PhysicalBoardBridge board = chessnut();

        main.runSync(
                () -> {
                    assertEquals(BoardType.CHESSNUT, board.type());
                    assertTrue(board.canLoadPhysicalPosition());
                    assertEquals(-1, board.liftedPieceSquare());
                    assertTrue(board.liftedPieceDestinations().isEmpty());
                    assertFalse(board.liftedPieceBelongsToOpponent());
                    assertEquals(-1, board.pendingCaptureSquare());
                    // Never pending on this board; must simply do nothing.
                    board.selectPromotion(PieceType.QUEEN);
                    board.selectCandidate("e2e4");
                    board.playNewGameArmedSound();
                    assertTrue(transport.written.isEmpty());
                    try {
                        board.physicalPositionFen(true);
                        fail("no board report was received yet");
                    } catch (InvalidPositionException e) {
                        assertEquals(InvalidPositionException.Reason.NO_BOARD, e.reason());
                    }
                });
    }

    @Test
    public void bothPassTheConnectionOnAndDetachTheirListener() {
        for (PhysicalBoardBridge board : new PhysicalBoardBridge[] {pegasus(), chessnut()}) {
            main.runSync(
                    () -> {
                        board.connect("AA:BB:CC:DD:EE:FF");
                        assertEquals(ConnectionState.CONNECTED, board.getConnectionState());
                        board.detachListener();
                        board.disconnect();
                        assertEquals(ConnectionState.DISCONNECTED, board.getConnectionState());
                        board.shutdown();
                    });
        }
    }
}
