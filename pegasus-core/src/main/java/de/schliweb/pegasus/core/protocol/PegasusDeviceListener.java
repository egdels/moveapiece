/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.pegasus.core.protocol;

/** Callback interface for decoded {@link PegasusDevice} events. */
public interface PegasusDeviceListener {

    /** Full 64-square snapshot (response to board dump request). */
    void onBoardState(BoardState state);

    /** Single square changed (spontaneous field update). */
    void onFieldUpdate(FieldUpdate update);

    void onBatteryStatus(BatteryStatus status);

    /** ASCII identity message (trademark, serial numbers). */
    void onIdentity(int messageType, String text);

    /** Version message (2 bytes major/minor: VERSION or HARDWARE_VERSION). */
    void onVersion(int messageType, int major, int minor);

    /** Frame with unknown type or undecodable payload; kept raw for diagnosis. */
    void onUnknownFrame(PegasusFrame frame);
}
