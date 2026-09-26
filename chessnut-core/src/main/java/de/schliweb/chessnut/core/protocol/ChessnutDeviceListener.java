/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.chessnut.core.protocol;

import de.schliweb.pegasus.core.protocol.BoardState;

/** Callback interface for decoded {@link ChessnutDevice} events. */
public interface ChessnutDeviceListener {

    /**
     * The board's 64 squares changed (or this is the first report since {@link
     * ChessnutDevice#reset()}). Identical consecutive reports are filtered out by the device, so
     * this fires per physical change, not ten times per second.
     */
    void onBoardState(BoardState state, long uptimeSeconds);

    void onBatteryStatus(ChessnutBatteryStatus status);

    /** NEW GAME was pressed. Already debounced: a long press yields one call, not two. */
    void onNewGameButton();

    /** Generic acknowledgement ({@code 23 01 00}) for the most recent command. */
    void onAck();

    /** Frame with unknown type or undecodable payload; kept raw for diagnosis. */
    void onUnknownFrame(String characteristicUuid, ChessnutFrame frame);
}
