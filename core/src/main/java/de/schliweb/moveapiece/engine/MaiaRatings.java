/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.engine;

import java.util.List;

/**
 * The 9 bundled Maia rating levels and where to find each one's ONNX model - see
 * MAIA_PROVENANCE_TEMPLATE.md for their provenance/conversion. Lives in {@code core} (not the
 * desktop module, despite the original integration being desktop-only) since both desktop and
 * Android need the same rating list, resource-path convention, and snapping logic - each platform
 * just opens {@link #resourcePath} differently (desktop: {@code Class#getResourceAsStream} against
 * a classpath resource under {@code desktop/src/main/resources/.../maia/}; Android: {@code
 * AssetManager#open} against {@code app/src/main/assets/maia/}), both bundling the identical set of
 * 9 {@code .onnx} files at that same relative path.
 */
public final class MaiaRatings {

    public static final List<Integer> ALL =
            List.of(1100, 1200, 1300, 1400, 1500, 1600, 1700, 1800, 1900);

    private MaiaRatings() {}

    /**
     * Path relative to each platform's own model resource root - see the class Javadoc for how
     * desktop and Android each resolve it to an actual {@code InputStream}.
     */
    public static String resourcePath(int rating) {
        return "maia/maia-" + rating + ".onnx";
    }

    /**
     * Snaps {@code raw} to the closest value in {@link #ALL} (clamped to its range first) - assumes
     * {@link #ALL}'s even 100-point spacing, which every caller relies on already.
     *
     * <p>Needed on desktop because a JavaFX {@code Slider} with {@code snapToTicks} doesn't
     * reliably report an exact multiple of its tick unit: floating-point arithmetic can leave it a
     * hair off (e.g. {@code 1599.999999...}), and on some platforms the skin's own snap correction
     * lands on the {@code value} property a moment after {@code valueChangingProperty} already
     * flipped back to {@code false}, so code reacting to that flip can still observe the pre-snap
     * value. Also used to sanitize a persisted rating setting so a value written by an earlier,
     * buggier build of that snapping logic self-heals instead of failing to load forever. Android's
     * own rating picker is a plain {@code Spinner} over the 9 exact values (no free-form slider, so
     * no snapping to get wrong) but reuses this for the same persisted-settings self-healing.
     */
    public static int nearest(double raw) {
        int min = ALL.get(0);
        int max = ALL.get(ALL.size() - 1);
        int step = ALL.get(1) - ALL.get(0);
        long snapped = Math.round(Math.max(min, Math.min(max, raw)) / step) * (long) step;
        return (int) snapped;
    }
}
