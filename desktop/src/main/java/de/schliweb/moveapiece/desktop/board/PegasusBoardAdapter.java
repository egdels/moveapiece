/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.desktop.board;

import de.schliweb.chessnut.core.game.InvalidPositionException;
import de.schliweb.moveapiece.desktop.pegasus.DesktopPegasusGameBridge;
import de.schliweb.moveapiece.logic.BoardType;
import de.schliweb.pegasus.core.chess.PieceType;
import de.schliweb.pegasus.core.transport.ConnectionState;
import de.schliweb.pegasus.core.transport.ScanListener;
import java.io.File;
import java.io.IOException;
import java.util.List;

/** {@link PhysicalBoardBridge} over the unchanged {@link DesktopPegasusGameBridge}. */
public final class PegasusBoardAdapter implements PhysicalBoardBridge {

    private final DesktopPegasusGameBridge bridge;

    public PegasusBoardAdapter(DesktopPegasusGameBridge bridge) {
        this.bridge = bridge;
    }

    @Override
    public BoardType type() {
        return BoardType.PEGASUS;
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
        return -1; // the Pegasus asks via a dialog instead
    }

    @Override
    public void resetForNewGame() {
        bridge.resetForNewGame();
    }

    @Override
    public void syncBoardToPosition(String fen) {
        bridge.syncBoardToPosition(fen);
    }

    @Override
    public boolean playMoveSound(boolean capture, boolean check) {
        return false; // no speaker
    }

    @Override
    public boolean canLoadPhysicalPosition() {
        return false; // occupancy only, no piece identity
    }

    @Override
    public String physicalPositionFen(boolean whiteToMove) throws InvalidPositionException {
        throw new InvalidPositionException(InvalidPositionException.Reason.NO_BOARD);
    }

    @Override
    public void selectPromotion(PieceType promotion) {
        bridge.selectPromotion(promotion);
    }

    @Override
    public void selectCandidate(String uci) {
        bridge.selectCandidate(uci);
    }
}
