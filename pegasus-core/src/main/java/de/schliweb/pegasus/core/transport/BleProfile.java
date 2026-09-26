/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.pegasus.core.transport;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The GATT endpoints a transport has to wire up for one kind of board: where commands are written
 * and which characteristics to subscribe to. The Pegasus uses a single Nordic UART service; the
 * Chessnut Air spreads command replies and board reports over two services, hence a list.
 *
 * <p>Transports built with {@link #PEGASUS} behave exactly as before this class existed.
 */
public final class BleProfile {

    /** One characteristic to subscribe to (notify or indicate), inside its service. */
    public static final class Subscription {
        private final String serviceUuid;
        private final String characteristicUuid;

        public Subscription(String serviceUuid, String characteristicUuid) {
            this.serviceUuid = requireUuid(serviceUuid);
            this.characteristicUuid = requireUuid(characteristicUuid);
        }

        public String serviceUuid() {
            return serviceUuid;
        }

        public String characteristicUuid() {
            return characteristicUuid;
        }

        @Override
        public String toString() {
            return characteristicUuid + " in " + serviceUuid;
        }
    }

    /** DGT Pegasus: Nordic UART, one write and one notify characteristic in the same service. */
    public static final BleProfile PEGASUS =
            new BleProfile(
                    "DGT Pegasus",
                    PegasusUuids.UART_SERVICE,
                    PegasusUuids.UART_WRITE_CHARACTERISTIC,
                    Collections.singletonList(
                            new Subscription(
                                    PegasusUuids.UART_SERVICE,
                                    PegasusUuids.UART_NOTIFY_CHARACTERISTIC)));

    private final String displayName;
    private final String writeServiceUuid;
    private final String writeCharacteristicUuid;
    private final List<Subscription> subscriptions;

    public BleProfile(
            String displayName,
            String writeServiceUuid,
            String writeCharacteristicUuid,
            List<Subscription> subscriptions) {
        if (displayName == null || displayName.isEmpty()) {
            throw new IllegalArgumentException("displayName must not be empty");
        }
        if (subscriptions == null || subscriptions.isEmpty()) {
            throw new IllegalArgumentException("at least one subscription is required");
        }
        this.displayName = displayName;
        this.writeServiceUuid = requireUuid(writeServiceUuid);
        this.writeCharacteristicUuid = requireUuid(writeCharacteristicUuid);
        this.subscriptions = Collections.unmodifiableList(new ArrayList<>(subscriptions));
    }

    /** Human-readable board name for logs and error messages. */
    public String displayName() {
        return displayName;
    }

    public String writeServiceUuid() {
        return writeServiceUuid;
    }

    public String writeCharacteristicUuid() {
        return writeCharacteristicUuid;
    }

    /** Characteristics to subscribe to, in this order; the transport is connected once all are. */
    public List<Subscription> subscriptions() {
        return subscriptions;
    }

    private static String requireUuid(String uuid) {
        if (uuid == null || uuid.isEmpty()) {
            throw new IllegalArgumentException("uuid must not be empty");
        }
        return uuid.toLowerCase(java.util.Locale.ROOT);
    }

    @Override
    public String toString() {
        return displayName
                + " (write "
                + writeCharacteristicUuid
                + ", subscribe "
                + subscriptions
                + ")";
    }
}
