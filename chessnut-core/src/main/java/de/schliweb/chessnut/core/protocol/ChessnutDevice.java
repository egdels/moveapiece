/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.chessnut.core.protocol;

import de.schliweb.pegasus.core.protocol.BoardState;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * Protocol-level orchestrator for a Chessnut Air: encodes requests through a transport-agnostic
 * {@link CommandSink}, reassembles notification bytes per characteristic and dispatches decoded
 * messages to a {@link ChessnutDeviceListener}.
 *
 * <p>Two things the raw stream needs before a game bridge can use it, both handled here:
 *
 * <ul>
 *   <li>The board repeats the full state about ten times per second. Only reports whose 64 squares
 *       differ from the previous one are dispatched.
 *   <li>A long press of NEW GAME sends the button event twice, on press and on release (3.2 s apart
 *       on hardware). Events within {@link #BUTTON_DEBOUNCE_MS} of the last dispatched one are
 *       dropped.
 * </ul>
 *
 * <p>Not thread-safe; drive from one thread (e.g. the transport callback thread).
 */
public final class ChessnutDevice {

    /** Transport-agnostic byte sink (writes to the command characteristic). */
    public interface CommandSink {
        void write(byte[] data);
    }

    /** Button events closer together than this are one press (long press = 2 events, 3.2 s). */
    public static final long BUTTON_DEBOUNCE_MS = 4000;

    private final CommandSink sink;
    private final ChessnutDeviceListener listener;
    private final LongSupplier clockMs;
    private final Map<String, ChessnutFrameParser> parsers = new HashMap<>();

    private BoardState lastDispatchedState;
    private long lastButtonMs = Long.MIN_VALUE / 2;

    public ChessnutDevice(CommandSink sink, ChessnutDeviceListener listener) {
        this(sink, listener, System::currentTimeMillis);
    }

    /** Test seam: injectable clock for the button debounce. */
    ChessnutDevice(CommandSink sink, ChessnutDeviceListener listener, LongSupplier clockMs) {
        if (sink == null || listener == null || clockMs == null) {
            throw new IllegalArgumentException("sink, listener and clock must not be null");
        }
        this.sink = sink;
        this.listener = listener;
        this.clockMs = clockMs;
    }

    /** Enables board reports and asks for the battery; call after every (re)connect. */
    public void initialize() {
        sink.write(ChessnutCommands.encodeEnableReports());
        sink.write(ChessnutCommands.encodeBatteryRequest());
    }

    public void requestBattery() {
        sink.write(ChessnutCommands.encodeBatteryRequest());
    }

    /** Lights exactly the given squares ({@link BoardState} numbering). */
    public void showLeds(int... boardSquareIndices) {
        sink.write(ChessnutCommands.encodeLeds(boardSquareIndices));
    }

    public void ledsOff() {
        sink.write(ChessnutCommands.encodeLedsOff());
    }

    /**
     * Feeds raw notification bytes from one characteristic; dispatches every completed frame.
     * Fragments of different characteristics are reassembled independently.
     */
    public void onDataReceived(String characteristicUuid, byte[] data) {
        String key = characteristicUuid == null ? "" : characteristicUuid.toLowerCase(Locale.ROOT);
        ChessnutFrameParser parser = parsers.computeIfAbsent(key, k -> new ChessnutFrameParser());
        List<ChessnutFrame> frames = parser.feed(data);
        for (ChessnutFrame frame : frames) {
            dispatch(key, frame);
        }
    }

    /**
     * Drops partial frames and the board-state dedupe memory, e.g. after a reconnect, so the next
     * report is dispatched even if the position did not change while disconnected.
     */
    public void reset() {
        parsers.clear();
        lastDispatchedState = null;
    }

    private void dispatch(String characteristicUuid, ChessnutFrame frame) {
        switch (frame.type()) {
            case ChessnutMessageType.BOARD_REPORT:
                if (frame.payloadLength() != ChessnutBoardReport.PAYLOAD_LENGTH) {
                    listener.onUnknownFrame(characteristicUuid, frame);
                    return;
                }
                ChessnutBoardReport report = ChessnutBoardReport.fromPayload(frame.payload());
                if (report.state().equals(lastDispatchedState)) {
                    return;
                }
                lastDispatchedState = report.state();
                listener.onBoardState(report.state(), report.uptimeSeconds());
                return;
            case ChessnutMessageType.BATTERY_STATUS:
                if (frame.payloadLength() < ChessnutBatteryStatus.PAYLOAD_LENGTH) {
                    listener.onUnknownFrame(characteristicUuid, frame);
                    return;
                }
                listener.onBatteryStatus(ChessnutBatteryStatus.fromPayload(frame.payload()));
                return;
            case ChessnutMessageType.ACK:
                listener.onAck();
                return;
            case ChessnutMessageType.BUTTON:
                if (frame.payloadLength() == 1
                        && (frame.payload()[0] & 0xFF) == ChessnutMessageType.BUTTON_NEW_GAME) {
                    long now = clockMs.getAsLong();
                    if (now - lastButtonMs < BUTTON_DEBOUNCE_MS) {
                        return;
                    }
                    lastButtonMs = now;
                    listener.onNewGameButton();
                    return;
                }
                listener.onUnknownFrame(characteristicUuid, frame);
                return;
            default:
                listener.onUnknownFrame(characteristicUuid, frame);
        }
    }
}
