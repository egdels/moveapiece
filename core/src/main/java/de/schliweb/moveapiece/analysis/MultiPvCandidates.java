/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.analysis;

import de.schliweb.moveapiece.engine.UciInfoParser;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * The best lines of a MultiPV search, collected from its "info" stream. Later lines for the same
 * rank (deeper iterations) overwrite earlier ones, so what is left once the search ends is the
 * converged answer.
 */
public final class MultiPvCandidates {

    private final String[] moveByRank;
    private final int[] cpByRank;

    public MultiPvCandidates(int lines) {
        this.moveByRank = new String[lines];
        this.cpByRank = new int[lines];
    }

    /** Forgets every line, before a new search. */
    public void clear() {
        Arrays.fill(moveByRank, null);
    }

    /**
     * Records one "info" line's move and score; lines without a rank, move or score are skipped.
     */
    public void capture(String infoLine) {
        OptionalInt multiPv = UciInfoParser.parseMultiPv(infoLine);
        if (multiPv.isEmpty() || multiPv.getAsInt() < 1 || multiPv.getAsInt() > moveByRank.length) {
            return;
        }
        Optional<String> pvMove = UciInfoParser.parsePvFirstMove(infoLine);
        if (pvMove.isEmpty()) {
            return;
        }
        OptionalInt mate = UciInfoParser.parseScoreMate(infoLine);
        OptionalInt cp =
                mate.isPresent() ? OptionalInt.empty() : UciInfoParser.parseScoreCp(infoLine);
        if (mate.isEmpty() && cp.isEmpty()) {
            return;
        }
        int rank = multiPv.getAsInt() - 1;
        moveByRank[rank] = pvMove.get();
        cpByRank[rank] = mate.isPresent() ? MoveQuality.mateToCp(mate.getAsInt()) : cp.getAsInt();
    }

    /** The number of lines asked for. */
    public int lines() {
        return moveByRank.length;
    }

    /** UCI move of the line with the given 0-based rank, or {@code null} if none was reported. */
    public String move(int rank) {
        return moveByRank[rank];
    }

    /** Score of that line in centipawns, from the mover's perspective. */
    public int cp(int rank) {
        return cpByRank[rank];
    }

    /** That line's move as plain "from-to" squares, e.g. "e2-e4". */
    public String squares(int rank) {
        String uci = moveByRank[rank];
        return uci.substring(0, 2).toLowerCase(Locale.ROOT)
                + "-"
                + uci.substring(2, 4).toLowerCase(Locale.ROOT);
    }
}
