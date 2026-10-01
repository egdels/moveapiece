/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.logic;

import java.util.ArrayList;
import java.util.List;

/**
 * Works out which {@link BoardType} a device picked from the scan list is, by probing its GATT
 * services: the app connects with the bridge of the most likely type first, and when that transport
 * reports that the type's service is missing, moves on to the next candidate. Every transport
 * already verifies the profile's services after connecting (SERVICE_NOT_FOUND /
 * CHARACTERISTIC_NOT_FOUND), so no transport needs to know about detection.
 *
 * <p>The candidate order is: the type guessed from the advertised name ({@link
 * BoardType#guessFromDeviceName}) if any, otherwise the type the user has configured; then the
 * remaining types in declaration order. With a correct guess the first connect succeeds and nothing
 * is different from before; a wrong guess costs one failed connect attempt.
 *
 * <p>Platform-independent and single-threaded; the host drives it from its UI thread.
 */
public final class BoardTypeDetection {

    private final String address;
    private final BoardType configured;
    private final List<BoardType> candidates = new ArrayList<>();
    private int index;

    private BoardTypeDetection(String address, BoardType configured, BoardType first) {
        this.address = address;
        this.configured = configured;
        candidates.add(first);
        for (BoardType type : BoardType.values()) {
            if (type != first) {
                candidates.add(type);
            }
        }
    }

    /**
     * Starts a detection for the device at {@code address}.
     *
     * @param deviceName advertised name (may be null)
     * @param configured the type currently configured by the user, never null
     */
    public static BoardTypeDetection start(
            String address, String deviceName, BoardType configured) {
        if (address == null || configured == null) {
            throw new IllegalArgumentException("address and configured type must not be null");
        }
        BoardType guess = BoardType.guessFromDeviceName(deviceName);
        return new BoardTypeDetection(address, configured, guess == null ? configured : guess);
    }

    /** Address the detection is for; errors from other devices must not advance it. */
    public String address() {
        return address;
    }

    /** The type the user had configured before the detection started. */
    public BoardType configured() {
        return configured;
    }

    /** The candidate currently being tried. */
    public BoardType current() {
        return candidates.get(index);
    }

    /** True once {@link #current()} is the last candidate. */
    public boolean isLastCandidate() {
        return index == candidates.size() - 1;
    }

    /**
     * Advances to the next candidate after the current one's services were not found. Returns the
     * new {@link #current()}, or null when every type has been tried (the detection is then over
     * and the host should report the failure).
     */
    public BoardType next() {
        if (isLastCandidate()) {
            return null;
        }
        index++;
        return current();
    }

    @Override
    public String toString() {
        return "BoardTypeDetection{" + address + ", " + candidates + ", index=" + index + "}";
    }
}
