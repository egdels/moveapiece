/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.board;

import de.schliweb.pegasus.core.chess.PieceType;
import de.schliweb.pegasus.core.transport.ConnectionState;
import de.schliweb.pegasus.core.transport.ScanListener;
import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * What {@code MainActivity} needs from a physical board, whichever one is selected. Implemented by
 * thin adapters around the board-specific bridges so those stay untouched; see {@link
 * PegasusBoardAdapter} and {@link ChessnutBoardAdapter}.
 */
public interface PhysicalBoardBridge {

    BoardType type();

    void startScan(ScanListener listener, long timeoutMs);

    void stopScan();

    void connect(String deviceAddress);

    void disconnect();

    /** Stops delivering events to the activity, e.g. before {@link #shutdown()} on a switch. */
    void detachListener();

    void shutdown();

    ConnectionState getConnectionState();

    void startRecording(File file) throws IOException;

    void stopRecording();

    void guideEngineMove(String uciMove);

    void guideEngineMove(String uciMove, boolean showLed);

    boolean isGuideActive();

    String trackedFen();

    boolean isBoardInSync();

    boolean isBoardMismatched();

    List<Integer> mismatchSquares();

    void resetForNewGame();

    void syncBoardToPosition(String fen);

    /** Resolves a pending promotion prompt; a no-op on boards that identify pieces themselves. */
    void selectPromotion(PieceType promotion);

    /** Resolves a pending ambiguous-move prompt; a no-op on boards that identify pieces. */
    void selectCandidate(String uci);
}
