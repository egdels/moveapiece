/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.pegasus.core.protocol;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Protocol-level orchestrator: encodes requests through a transport-agnostic {@link CommandSink},
 * reassembles incoming notification bytes with a {@link PegasusFrameParser} and dispatches decoded
 * messages to a {@link PegasusDeviceListener}.
 *
 * <p>Initialization sequence (INFERRED from official app behaviour, see docs/PEGASUS_PROTOCOL.md):
 * identity (G, M, H, E, U), battery (L), then board dump (B); field updates arrive spontaneously
 * afterwards.
 *
 * <p>Not thread-safe; drive from one thread (e.g. the transport callback thread).
 */
public final class PegasusDevice {

    /** Transport-agnostic byte sink (writes to the UART write characteristic). */
    public interface CommandSink {
        void write(byte[] data);
    }

    private final CommandSink sink;
    private final PegasusDeviceListener listener;
    private final PegasusFrameParser parser = new PegasusFrameParser();

    public PegasusDevice(CommandSink sink, PegasusDeviceListener listener) {
        if (sink == null || listener == null) {
            throw new IllegalArgumentException("sink and listener must not be null");
        }
        this.sink = sink;
        this.listener = listener;
    }

    /** Sends the full initialization sequence (identity, battery, board dump). */
    public void initialize() {
        sink.write(PegasusCommands.encodeTrademarkRequest());
        sink.write(PegasusCommands.encodeVersionRequest());
        sink.write(PegasusCommands.encodeHardwareVersionRequest());
        sink.write(PegasusCommands.encodeSerialNumberRequest());
        sink.write(PegasusCommands.encodeLongSerialNumberRequest());
        sink.write(PegasusCommands.encodeBatteryRequest());
        sink.write(PegasusCommands.encodeBoardStateRequest());
    }

    public void requestBoardState() {
        sink.write(PegasusCommands.encodeBoardStateRequest());
    }

    public void requestBattery() {
        sink.write(PegasusCommands.encodeBatteryRequest());
    }

    /** Lights the given squares (see {@link PegasusCommands#encodeLeds}). */
    public void showLeds(int ledSpeed, int mode, int intensity, int... squareIndices) {
        sink.write(PegasusCommands.encodeLeds(ledSpeed, mode, intensity, squareIndices));
    }

    public void ledsOff() {
        sink.write(PegasusCommands.encodeLedsOff());
    }

    /** Feeds raw notification bytes; dispatches every completed frame. */
    public void onDataReceived(byte[] data) {
        List<PegasusFrame> frames = parser.feed(data);
        for (PegasusFrame frame : frames) {
            dispatch(frame);
        }
    }

    /** Drops any partial frame, e.g. after a reconnect. */
    public void resetParser() {
        parser.reset();
    }

    private void dispatch(PegasusFrame frame) {
        try {
            switch (frame.type()) {
                case PegasusMessageType.BOARD_DUMP:
                    listener.onBoardState(BoardState.fromBoardDumpPayload(frame.payload()));
                    break;
                case PegasusMessageType.FIELD_UPDATE:
                    listener.onFieldUpdate(FieldUpdate.fromPayload(frame.payload()));
                    break;
                case PegasusMessageType.BATTERY_STATUS:
                    listener.onBatteryStatus(BatteryStatus.fromPayload(frame.payload()));
                    break;
                case PegasusMessageType.TRADEMARK:
                case PegasusMessageType.SERIALNR:
                case PegasusMessageType.LONG_SERIALNR:
                    listener.onIdentity(
                            frame.type(),
                            new String(frame.payload(), StandardCharsets.US_ASCII).trim());
                    break;
                case PegasusMessageType.VERSION:
                case PegasusMessageType.HARDWARE_VERSION:
                    byte[] payload = frame.payload();
                    if (payload.length != 2) {
                        listener.onUnknownFrame(frame);
                    } else {
                        listener.onVersion(frame.type(), payload[0] & 0xFF, payload[1] & 0xFF);
                    }
                    break;
                default:
                    listener.onUnknownFrame(frame);
                    break;
            }
        } catch (IllegalArgumentException e) {
            // Payload did not match the expected format; surface raw frame.
            listener.onUnknownFrame(frame);
        }
    }
}
