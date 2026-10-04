/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.board;

import de.schliweb.chessnut.core.game.InvalidPositionException;
import de.schliweb.chessnut.core.protocol.ChessnutTones;
import de.schliweb.pegasus.core.chess.PieceColor;
import de.schliweb.pegasus.core.transport.ConnectionState;
import de.schliweb.pegasus.core.transport.ScanListener;
import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * What {@link ChessnutBoardAdapter} needs from a platform's Chessnut game bridge. Android and
 * desktop each have their own implementation, which differ in threading and transport only.
 */
public interface ChessnutBridge {

    void startScan(ScanListener listener, long timeoutMs);

    void stopScan();

    void connect(String deviceAddress);

    void disconnect();

    void detachListener();

    void shutdown();

    ConnectionState getConnectionState();

    void playTones(List<ChessnutTones.Tone> tones);

    void startRecording(File file) throws IOException;

    void stopRecording();

    void guideEngineMove(String uciMove);

    void guideEngineMove(String uciMove, boolean showLed);

    boolean isGuideActive();

    String trackedFen();

    boolean isBoardInSync();

    boolean isBoardMismatched();

    List<Integer> mismatchSquares();

    int promotionSquareAwaitingPiece();

    String physicalPositionFen(PieceColor sideToMove) throws InvalidPositionException;

    void resetForNewGame();

    void syncBoardToPosition(String fen);
}
