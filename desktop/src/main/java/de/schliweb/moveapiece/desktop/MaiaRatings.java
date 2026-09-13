/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.desktop;

import java.util.List;

/**
 * The 9 bundled Maia rating levels and where to find each one's ONNX model - see
 * MAIA_PROVENANCE_TEMPLATE.md for their provenance/conversion. Used by {@link GameController} for
 * both the live rating slider ({@link #nearest}) and resource loading ({@link #resourcePath}).
 */
final class MaiaRatings {

    static final List<Integer> ALL =
            List.of(1100, 1200, 1300, 1400, 1500, 1600, 1700, 1800, 1900);

    private MaiaRatings() {}

    /** Resource path relative to a class in this package, e.g. for {@code getResourceAsStream}. */
    static String resourcePath(int rating) {
        return "maia/maia-" + rating + ".onnx";
    }

    /**
     * Snaps {@code raw} to the closest value in {@link #ALL} (clamped to its range first) - assumes
     * {@link #ALL}'s even 100-point spacing, which every caller relies on already.
     *
     * <p>Needed because a JavaFX {@code Slider} with {@code snapToTicks} doesn't reliably report an
     * exact multiple of its tick unit: floating-point arithmetic can leave it a hair off (e.g.
     * {@code 1599.999999...}), and on some platforms the skin's own snap correction lands on the
     * {@code value} property a moment after {@code valueChangingProperty} already flipped back to
     * {@code false}, so code reacting to that flip can still observe the pre-snap value. Also used to
     * sanitize {@link Settings#getMaiaRating()} so a value persisted by an earlier, buggier build of
     * this snapping logic self-heals instead of failing to load forever.
     */
    static int nearest(double raw) {
        int min = ALL.get(0);
        int max = ALL.get(ALL.size() - 1);
        int step = ALL.get(1) - ALL.get(0);
        long snapped = Math.round(Math.max(min, Math.min(max, raw)) / step) * (long) step;
        return (int) snapped;
    }
}
