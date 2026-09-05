/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.pegasus.core.record;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.io.StringWriter;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;

public class SessionRecorderTest {

    @Test
    public void recordsRelativeTimestampsAndCompactHex() throws IOException {
        StringWriter out = new StringWriter();
        AtomicLong now = new AtomicLong(1000);
        SessionRecorder recorder = new SessionRecorder(out, now::get);

        recorder.record(
                SessionRecorder.Direction.RX,
                "6e400003-b5a3-f393-e0a9-e50e24dcca9e",
                new byte[] {(byte) 0x86, 0x00, 0x43});
        now.set(1181);
        recorder.record(
                SessionRecorder.Direction.TX,
                "6e400002-b5a3-f393-e0a9-e50e24dcca9e",
                new byte[] {0x42});

        String expected =
                "{\"t\":0,\"dir\":\"RX\",\"characteristic\":\"6e400003-b5a3-f393-e0a9-e50e24dcca9e\",\"data\":\"860043\"}\n"
                        + "{\"t\":181,\"dir\":\"TX\",\"characteristic\":\"6e400002-b5a3-f393-e0a9-e50e24dcca9e\",\"data\":\"42\"}\n";
        assertEquals(expected, out.toString());
    }

    @Test
    public void nullCharacteristicBecomesEmptyString() throws IOException {
        StringWriter out = new StringWriter();
        SessionRecorder recorder = new SessionRecorder(out, () -> 5);
        recorder.record(SessionRecorder.Direction.RX, null, new byte[0]);
        assertEquals(
                "{\"t\":0,\"dir\":\"RX\",\"characteristic\":\"\",\"data\":\"\"}\n", out.toString());
    }
}
