/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.pegasus.core.transport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ReconnectPolicyTest {

    @Test
    public void reconnectsUpToMaxAttempts() {
        ReconnectPolicy policy = new ReconnectPolicy(3, 100);
        policy.onConnectRequested();
        assertTrue(policy.shouldReconnect());
        assertTrue(policy.shouldReconnect());
        assertTrue(policy.shouldReconnect());
        assertFalse(policy.shouldReconnect());
        assertEquals(3, policy.getAttemptsMade());
    }

    @Test
    public void manualDisconnectBlocksReconnect() {
        ReconnectPolicy policy = new ReconnectPolicy(3, 100);
        policy.onConnectRequested();
        policy.onManualDisconnect();
        assertFalse(policy.shouldReconnect());
    }

    @Test
    public void successfulConnectionResetsAttempts() {
        ReconnectPolicy policy = new ReconnectPolicy(2, 100);
        policy.onConnectRequested();
        assertTrue(policy.shouldReconnect());
        assertTrue(policy.shouldReconnect());
        assertFalse(policy.shouldReconnect());
        policy.onConnected();
        assertTrue(policy.shouldReconnect());
    }

    @Test
    public void newConnectRequestClearsManualDisconnect() {
        ReconnectPolicy policy = new ReconnectPolicy(1, 100);
        policy.onManualDisconnect();
        assertFalse(policy.shouldReconnect());
        policy.onConnectRequested();
        assertTrue(policy.shouldReconnect());
    }
}
