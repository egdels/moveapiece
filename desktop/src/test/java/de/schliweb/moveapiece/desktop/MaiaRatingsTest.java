/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.desktop;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Locks in {@link MaiaRatings#nearest} against the exact bug it was written to fix: a JavaFX
 * {@code Slider} (or a value persisted by an earlier build's buggier rounding) reporting something
 * a hair off an exact multiple of 100, or clearly outside {@link MaiaRatings#ALL}'s 1100-1900
 * range, must still resolve to a rating a bundled model actually exists for.
 */
public class MaiaRatingsTest {

    @Test
    public void exactTickValues_mapToThemselves() {
        for (int rating : MaiaRatings.ALL) {
            assertEquals(rating, MaiaRatings.nearest(rating));
        }
    }

    @Test
    public void floatingPointNoise_snapsToTheIntendedTick() {
        assertEquals(1600, MaiaRatings.nearest(1599.999999));
        assertEquals(1600, MaiaRatings.nearest(1600.000001));
    }

    @Test
    public void offTickValue_roundsToTheClosestRating() {
        assertEquals(1100, MaiaRatings.nearest(1117));
        assertEquals(1200, MaiaRatings.nearest(1167));
    }

    @Test
    public void outOfRangeValue_clampsToTheNearestBound() {
        assertEquals(1100, MaiaRatings.nearest(800));
        assertEquals(1900, MaiaRatings.nearest(5000));
    }
}
