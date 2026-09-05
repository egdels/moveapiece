/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.pegasus.core.transport;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Minimal serialized queue for GATT operations.
 *
 * <p>Android BLE only allows one outstanding GATT operation at a time (discover, descriptor write,
 * characteristic write, ...). This queue guarantees exactly one active operation; the next one
 * starts only after {@link #operationCompleted()} is called or the timeout fires.
 *
 * <p>Intentionally small; no generic BLE framework (see phase 1 scope).
 */
public final class GattOperationQueue {

    /** A single GATT operation. {@code start()} triggers the async call. */
    public interface Operation {
        String name();

        /**
         * @return false if starting failed synchronously (operation is skipped).
         */
        boolean start();

        /** Called when the operation did not complete within the timeout. */
        void onTimeout();
    }

    public static final long DEFAULT_TIMEOUT_MS = 5000;

    private final ScheduledExecutorService scheduler;
    private final long timeoutMs;
    private final Deque<Operation> pending = new ArrayDeque<>();
    private Operation active;
    private ScheduledFuture<?> timeoutFuture;

    public GattOperationQueue(ScheduledExecutorService scheduler) {
        this(scheduler, DEFAULT_TIMEOUT_MS);
    }

    public GattOperationQueue(ScheduledExecutorService scheduler, long timeoutMs) {
        this.scheduler = scheduler;
        this.timeoutMs = timeoutMs;
    }

    public synchronized void enqueue(Operation operation) {
        pending.addLast(operation);
        if (active == null) {
            startNext();
        }
    }

    /** Must be called from the GATT callback when the active operation finished. */
    public synchronized void operationCompleted() {
        cancelTimeout();
        active = null;
        startNext();
    }

    public synchronized void clear() {
        cancelTimeout();
        active = null;
        pending.clear();
    }

    public synchronized boolean isIdle() {
        return active == null && pending.isEmpty();
    }

    private void startNext() {
        while (active == null && !pending.isEmpty()) {
            Operation next = pending.removeFirst();
            if (next.start()) {
                active = next;
                timeoutFuture =
                        scheduler.schedule(
                                () -> handleTimeout(next), timeoutMs, TimeUnit.MILLISECONDS);
            }
            // start() == false: skip and try the next operation
        }
    }

    private synchronized void handleTimeout(Operation operation) {
        if (active == operation) {
            active = null;
            timeoutFuture = null;
            operation.onTimeout();
            startNext();
        }
    }

    private void cancelTimeout() {
        if (timeoutFuture != null) {
            timeoutFuture.cancel(false);
            timeoutFuture = null;
        }
    }
}
