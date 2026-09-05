/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.pegasus.core.transport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class GattOperationQueueTest {

    private ScheduledExecutorService scheduler;

    @Before
    public void setUp() {
        scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    @After
    public void tearDown() {
        scheduler.shutdownNow();
    }

    private static GattOperationQueue.Operation op(String name, List<String> log) {
        return op(name, log, true);
    }

    private static GattOperationQueue.Operation op(String name, List<String> log, boolean startOk) {
        return new GattOperationQueue.Operation() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public boolean start() {
                log.add("start:" + name);
                return startOk;
            }

            @Override
            public void onTimeout() {
                log.add("timeout:" + name);
            }
        };
    }

    @Test
    public void runsOperationsSequentially() {
        List<String> log = new ArrayList<>();
        GattOperationQueue queue = new GattOperationQueue(scheduler, 1000);

        queue.enqueue(op("a", log));
        queue.enqueue(op("b", log));

        // "b" must not start while "a" is active
        assertEquals(List.of("start:a"), log);
        queue.operationCompleted();
        assertEquals(List.of("start:a", "start:b"), log);
        queue.operationCompleted();
        assertTrue(queue.isIdle());
    }

    @Test
    public void skipsOperationThatFailsToStart() {
        List<String> log = new ArrayList<>();
        GattOperationQueue queue = new GattOperationQueue(scheduler, 1000);

        queue.enqueue(op("bad", log, false));
        queue.enqueue(op("good", log));

        assertEquals(List.of("start:bad", "start:good"), log);
    }

    @Test
    public void timeoutUnblocksQueue() throws InterruptedException {
        List<String> log = new ArrayList<>();
        GattOperationQueue queue = new GattOperationQueue(scheduler, 50);
        CountDownLatch latch = new CountDownLatch(1);

        queue.enqueue(op("stuck", log));
        queue.enqueue(
                new GattOperationQueue.Operation() {
                    @Override
                    public String name() {
                        return "next";
                    }

                    @Override
                    public boolean start() {
                        log.add("start:next");
                        latch.countDown();
                        return true;
                    }

                    @Override
                    public void onTimeout() {}
                });

        assertTrue("queue must continue after timeout", latch.await(2, TimeUnit.SECONDS));
        assertEquals(List.of("start:stuck", "timeout:stuck", "start:next"), log);
    }

    @Test
    public void clearDropsPendingOperations() {
        List<String> log = new ArrayList<>();
        GattOperationQueue queue = new GattOperationQueue(scheduler, 1000);

        queue.enqueue(op("a", log));
        queue.enqueue(op("b", log));
        queue.clear();
        queue.operationCompleted();

        assertEquals(List.of("start:a"), log);
        assertTrue(queue.isIdle());
    }
}
