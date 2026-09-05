/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.pegasus.core.record;

import de.schliweb.pegasus.core.util.HexUtil;
import java.io.IOException;
import java.io.Writer;

/**
 * Records raw BLE traffic as NDJSON for later replay/regression tests.
 *
 * <p>Format (one JSON object per line, see docs/TESTING.md):
 *
 * <pre>
 * {"t":0,"dir":"RX","characteristic":"6e400003-...","data":"860043"}
 * {"t":181,"dir":"TX","characteristic":"6e400002-...","data":"42"}
 * </pre>
 *
 * - {@code t}: milliseconds relative to the first recorded entry, - {@code dir}: "RX" (board → app)
 * or "TX" (app → board), - {@code data}: compact uppercase hex.
 *
 * <p>No MAC addresses and no personal data are recorded.
 */
public final class SessionRecorder {

    public enum Direction {
        RX,
        TX
    }

    private final Writer writer;
    private final Clock clock;
    private long firstTimestamp = -1;

    /** Millisecond time source; injectable for tests. */
    public interface Clock {
        long nowMillis();
    }

    public SessionRecorder(Writer writer) {
        this(writer, System::currentTimeMillis);
    }

    public SessionRecorder(Writer writer, Clock clock) {
        this.writer = writer;
        this.clock = clock;
    }

    public synchronized void record(Direction dir, String characteristicUuid, byte[] data)
            throws IOException {
        long now = clock.nowMillis();
        if (firstTimestamp < 0) {
            firstTimestamp = now;
        }
        long t = now - firstTimestamp;
        writer.write(
                "{\"t\":"
                        + t
                        + ",\"dir\":\""
                        + dir.name()
                        + "\",\"characteristic\":\""
                        + (characteristicUuid == null ? "" : characteristicUuid)
                        + "\",\"data\":\""
                        + HexUtil.toCompactHex(data)
                        + "\"}\n");
        writer.flush();
    }

    public synchronized void close() throws IOException {
        writer.close();
    }
}
