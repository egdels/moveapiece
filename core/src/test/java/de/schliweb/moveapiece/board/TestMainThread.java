/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.board;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * A real single thread standing in for the platform's main thread in bridge tests, with real
 * timers: the bridges' settle windows and re-sends then behave as they do in the apps.
 */
final class TestMainThread implements BoardScheduler {

    private final ScheduledExecutorService executor =
            Executors.newSingleThreadScheduledExecutor(
                    r -> {
                        Thread t = new Thread(r, "test-main");
                        t.setDaemon(true);
                        return t;
                    });
    private final List<ScheduledFuture<?>> delayed = new ArrayList<>();

    @Override
    public void post(Runnable action) {
        executor.execute(action);
    }

    @Override
    public synchronized Task postDelayed(Runnable action, long delayMs) {
        ScheduledFuture<?> future = executor.schedule(action, delayMs, TimeUnit.MILLISECONDS);
        delayed.add(future);
        return () -> future.cancel(false);
    }

    @Override
    public synchronized void shutdown() {
        for (ScheduledFuture<?> future : delayed) {
            future.cancel(false);
        }
        delayed.clear();
    }

    /** Runs {@code action} on the main thread and waits for it, like a test's own UI call. */
    void runSync(Runnable action) {
        try {
            executor.submit(action).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        } catch (ExecutionException e) {
            throw new AssertionError(e.getCause());
        }
    }

    /** Ends the thread; for the test's tear-down. */
    void close() {
        executor.shutdownNow();
    }
}
