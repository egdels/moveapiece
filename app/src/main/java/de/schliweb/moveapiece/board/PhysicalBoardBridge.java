/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.board;

import de.schliweb.chessnut.core.game.InvalidPositionException;
import de.schliweb.moveapiece.logic.BoardType;
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

    /**
     * Square on which a pawn stands that should be replaced by the promotion piece, or -1. Only
     * boards that identify pieces can need this; the others always return -1.
     */
    int promotionSquareAwaitingPiece();

    /**
     * Destination of a capture the board may have executed but cannot prove (occupancy-only
     * boards), or -1. Boards that identify pieces always return -1.
     */
    int pendingCaptureSquare();

    /**
     * Square a single piece is currently lifted from, or -1. Only occupancy-only boards need the
     * hint built from this and the two methods below; others always return -1.
     */
    int liftedPieceSquare();

    /** Legal destinations of the lifted piece, empty if none (pinned, check, wrong side). */
    List<Integer> liftedPieceDestinations();

    /** Whether the lifted piece belongs to the side not to move. */
    boolean liftedPieceBelongsToOpponent();

    void resetForNewGame();

    void syncBoardToPosition(String fen);

    /**
     * Plays the move sound on the board instead of the phone, if the board has a speaker and is
     * connected. Returns {@code true} when the board took care of it, {@code false} when the caller
     * should play the phone sound.
     */
    boolean playMoveSound(boolean capture, boolean check);

    /** Whether {@link #physicalPositionFen} can work at all: only boards that identify pieces. */
    boolean canLoadPhysicalPosition();

    /**
     * FEN of the pieces currently on the board with the given side to move, for taking a position
     * over from the board. Throws for boards that cannot ({@link #canLoadPhysicalPosition}) and for
     * unplayable positions.
     */
    String physicalPositionFen(boolean whiteToMove) throws InvalidPositionException;

    /** Resolves a pending promotion prompt; a no-op on boards that identify pieces themselves. */
    void selectPromotion(PieceType promotion);

    /** Resolves a pending ambiguous-move prompt; a no-op on boards that identify pieces. */
    void selectCandidate(String uci);
}
