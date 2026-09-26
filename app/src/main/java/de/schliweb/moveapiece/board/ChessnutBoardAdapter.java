/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.board;

import de.schliweb.moveapiece.chessnut.ChessnutGameBridge;
import de.schliweb.pegasus.core.chess.PieceType;
import de.schliweb.pegasus.core.transport.ConnectionState;
import de.schliweb.pegasus.core.transport.ScanListener;
import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * {@link PhysicalBoardBridge} over {@link ChessnutGameBridge}; promotion and ambiguity never arise
 * there, so those two resolvers are no-ops.
 */
public final class ChessnutBoardAdapter implements PhysicalBoardBridge {

    private final ChessnutGameBridge bridge;

    public ChessnutBoardAdapter(ChessnutGameBridge bridge) {
        this.bridge = bridge;
    }

    @Override
    public BoardType type() {
        return BoardType.CHESSNUT;
    }

    @Override
    public void startScan(ScanListener listener, long timeoutMs) {
        bridge.startScan(listener, timeoutMs);
    }

    @Override
    public void stopScan() {
        bridge.stopScan();
    }

    @Override
    public void connect(String deviceAddress) {
        bridge.connect(deviceAddress);
    }

    @Override
    public void disconnect() {
        bridge.disconnect();
    }

    @Override
    public void detachListener() {
        bridge.setListener(null);
    }

    @Override
    public void shutdown() {
        bridge.shutdown();
    }

    @Override
    public ConnectionState getConnectionState() {
        return bridge.getConnectionState();
    }

    @Override
    public void startRecording(File file) throws IOException {
        bridge.startRecording(file);
    }

    @Override
    public void stopRecording() {
        bridge.stopRecording();
    }

    @Override
    public void guideEngineMove(String uciMove) {
        bridge.guideEngineMove(uciMove);
    }

    @Override
    public void guideEngineMove(String uciMove, boolean showLed) {
        bridge.guideEngineMove(uciMove, showLed);
    }

    @Override
    public boolean isGuideActive() {
        return bridge.isGuideActive();
    }

    @Override
    public String trackedFen() {
        return bridge.trackedFen();
    }

    @Override
    public boolean isBoardInSync() {
        return bridge.isBoardInSync();
    }

    @Override
    public boolean isBoardMismatched() {
        return bridge.isBoardMismatched();
    }

    @Override
    public List<Integer> mismatchSquares() {
        return bridge.mismatchSquares();
    }

    @Override
    public int promotionSquareAwaitingPiece() {
        return bridge.promotionSquareAwaitingPiece();
    }

    @Override
    public void resetForNewGame() {
        bridge.resetForNewGame();
    }

    @Override
    public void syncBoardToPosition(String fen) {
        bridge.syncBoardToPosition(fen);
    }

    /**
     * Tones on the board's piezo standing in for move.mp3, capture.mp3 and check.mp3: a short click
     * for a move, a lower and longer one for a capture, a high and long one for check.
     */
    @Override
    public boolean playMoveSound(boolean capture, boolean check) {
        if (bridge.getConnectionState() != ConnectionState.CONNECTED) {
            return false;
        }
        if (check) {
            bridge.beep(1800, 250);
        } else if (capture) {
            bridge.beep(800, 140);
        } else {
            bridge.beep(1200, 70);
        }
        return true;
    }

    @Override
    public void selectPromotion(PieceType promotion) {
        // The Chessnut reads the promotion piece off the board; nothing is ever pending.
    }

    @Override
    public void selectCandidate(String uci) {
        // Piece identity makes every completed move unique; nothing is ever pending.
    }
}
