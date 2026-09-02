/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.training;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class OpeningSearchTest {

    private static final OpeningLine ALPHA_ONE =
            new OpeningLine("alpha_one", Arrays.asList("e2e4"));
    private static final OpeningLine ALPHA_TWO =
            new OpeningLine("alpha_two", Arrays.asList("e2e4"));
    private static final OpeningLine BETA = new OpeningLine("beta", Arrays.asList("e2e4"));

    private static final List<OpeningLine> LINES = Arrays.asList(ALPHA_ONE, ALPHA_TWO, BETA);
    private static final List<String> NAMES = Arrays.asList("Alpha One", "Alpha Two", "Beta Three");

    @Test
    public void blankQuery_returnsAllInOriginalOrder() {
        assertEquals(LINES, OpeningSearch.filter(LINES, NAMES, ""));
        assertEquals(LINES, OpeningSearch.filter(LINES, NAMES, null));
        assertEquals(LINES, OpeningSearch.filter(LINES, NAMES, "   "));
    }

    @Test
    public void query_matchesCaseInsensitiveSubstring() {
        assertEquals(
                Collections.singletonList(ALPHA_ONE), OpeningSearch.filter(LINES, NAMES, "one"));
        assertEquals(
                Collections.singletonList(ALPHA_ONE), OpeningSearch.filter(LINES, NAMES, "ONE"));
        assertEquals(
                Arrays.asList(ALPHA_ONE, ALPHA_TWO), OpeningSearch.filter(LINES, NAMES, "alpha"));
    }

    @Test
    public void query_withNoMatch_returnsEmptyList() {
        assertTrue(OpeningSearch.filter(LINES, NAMES, "xyz").isEmpty());
    }

    @Test
    public void mismatchedListSizes_throws() {
        try {
            OpeningSearch.filter(LINES, Collections.singletonList("only one"), "a");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }
}
