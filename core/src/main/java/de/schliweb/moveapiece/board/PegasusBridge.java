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
 * What {@link PegasusBoardAdapter} needs from a platform's Pegasus game bridge. Android and desktop
 * each have their own implementation, which differ in threading and transport only.
 */
public interface PegasusBridge {

    void startScan(ScanListener listener, long timeoutMs);

    void stopScan();

    void connect(String deviceAddress);

    void disconnect();

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

    int pendingCaptureSquare();

    int liftedPieceSquare();

    List<Integer> liftedPieceDestinations();

    boolean liftedPieceBelongsToOpponent();

    void resetForNewGame();

    void syncBoardToPosition(String fen);

    void selectPromotion(PieceType promotion);

    void selectCandidate(String uci);
}
