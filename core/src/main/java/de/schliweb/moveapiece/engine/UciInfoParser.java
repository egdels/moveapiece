/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.engine;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts fields from a raw UCI "info ..." line ({@link StockfishEngine} forwards these unparsed
 * via {@link EngineListener#onInfo}). {@code score cp}/{@code score mate} are relative to the side
 * that was asked to search, per the UCI spec — converting to an absolute (White-relative)
 * perspective is the caller's job, since only the caller knows which side that was. {@code multipv}
 * and the first {@code pv} move are only present when the engine's MultiPV option is above 1 (see
 * {@link StockfishEngine#setMultiPv}).
 */
public final class UciInfoParser {

    private static final Pattern SCORE_CP = Pattern.compile("score cp (-?\\d+)");
    private static final Pattern SCORE_MATE = Pattern.compile("score mate (-?\\d+)");
    private static final Pattern MULTIPV = Pattern.compile("multipv (\\d+)");
    private static final Pattern PV_FIRST_MOVE = Pattern.compile("\\bpv (\\S+)");

    private UciInfoParser() {}

    public static OptionalInt parseScoreCp(String infoLine) {
        return parse(SCORE_CP, infoLine);
    }

    public static OptionalInt parseScoreMate(String infoLine) {
        return parse(SCORE_MATE, infoLine);
    }

    /**
     * 1-based rank of this line's principal variation, e.g. 1 for the best line, 2 for the next.
     */
    public static OptionalInt parseMultiPv(String infoLine) {
        return parse(MULTIPV, infoLine);
    }

    /**
     * The first (i.e. the move to actually play) move of this line's principal variation, in UCI.
     */
    public static Optional<String> parsePvFirstMove(String infoLine) {
        if (infoLine == null) {
            return Optional.empty();
        }
        Matcher matcher = PV_FIRST_MOVE.matcher(infoLine);
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    private static OptionalInt parse(Pattern pattern, String infoLine) {
        if (infoLine == null) {
            return OptionalInt.empty();
        }
        Matcher matcher = pattern.matcher(infoLine);
        if (!matcher.find()) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(Integer.parseInt(matcher.group(1)));
    }
}
