/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.puzzle;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * The ids of the puzzles played last, so that {@link PuzzleSelector} does not hand one out again
 * soon. Holds at most {@link #CAPACITY} ids, dropping the oldest. {@link #toKey()} and {@link
 * #fromKey} are the persisted form (ids separated by spaces; Lichess ids are alphanumeric).
 */
public final class RecentPuzzles {

    /**
     * About a tenth of the bundled set: no repeat within the last 500 puzzles, while enough stay
     * available around any rating (a 100-point band holds 210).
     */
    public static final int CAPACITY = 500;

    private final int capacity;
    private final Deque<String> order = new ArrayDeque<>();
    private final Set<String> ids = new HashSet<>();

    public RecentPuzzles() {
        this(CAPACITY);
    }

    RecentPuzzles(int capacity) {
        this.capacity = capacity;
    }

    /** Records {@code id} as just played; one already recorded moves to the newest place. */
    public void add(String id) {
        if (ids.remove(id)) {
            order.remove(id);
        }
        order.addLast(id);
        ids.add(id);
        while (order.size() > capacity) {
            ids.remove(order.removeFirst());
        }
    }

    public boolean contains(String id) {
        return ids.contains(id);
    }

    public int size() {
        return order.size();
    }

    /** The persisted form: the ids, oldest first, separated by spaces. */
    public String toKey() {
        return String.join(" ", order);
    }

    /** Reads {@link #toKey()}; null or empty is an empty history. */
    public static RecentPuzzles fromKey(String key) {
        RecentPuzzles recent = new RecentPuzzles();
        if (key != null) {
            for (String id : key.trim().split("\\s+")) {
                if (!id.isEmpty()) {
                    recent.add(id);
                }
            }
        }
        return recent;
    }
}
