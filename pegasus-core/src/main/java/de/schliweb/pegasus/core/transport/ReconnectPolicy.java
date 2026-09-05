/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.pegasus.core.transport;

/**
 * Simple, bounded reconnect policy.
 *
 * <p>Behavior (documented in docs/ANDROID_BLE.md): - only unexpected disconnects trigger reconnect
 * attempts, - a manual disconnect resets the policy and never reconnects, - at most {@code
 * maxAttempts} attempts with a fixed delay, - a successful connection resets the attempt counter.
 */
public final class ReconnectPolicy {

    public static final int DEFAULT_MAX_ATTEMPTS = 3;
    public static final long DEFAULT_DELAY_MS = 2000;

    private final int maxAttempts;
    private final long delayMs;
    private int attemptsMade;
    private boolean manualDisconnect;

    public ReconnectPolicy() {
        this(DEFAULT_MAX_ATTEMPTS, DEFAULT_DELAY_MS);
    }

    public ReconnectPolicy(int maxAttempts, long delayMs) {
        this.maxAttempts = maxAttempts;
        this.delayMs = delayMs;
    }

    /** Call when the user explicitly disconnects. */
    public synchronized void onManualDisconnect() {
        manualDisconnect = true;
        attemptsMade = 0;
    }

    /** Call when a new (manual) connect is started. */
    public synchronized void onConnectRequested() {
        manualDisconnect = false;
        attemptsMade = 0;
    }

    /** Call when a connection is fully established. */
    public synchronized void onConnected() {
        attemptsMade = 0;
    }

    /**
     * Call after an unexpected disconnect.
     *
     * @return true if a reconnect attempt should be scheduled.
     */
    public synchronized boolean shouldReconnect() {
        if (manualDisconnect) {
            return false;
        }
        if (attemptsMade >= maxAttempts) {
            return false;
        }
        attemptsMade++;
        return true;
    }

    public synchronized int getAttemptsMade() {
        return attemptsMade;
    }

    public long getDelayMs() {
        return delayMs;
    }
}
