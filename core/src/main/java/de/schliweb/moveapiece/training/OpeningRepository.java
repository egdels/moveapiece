/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.training;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Fixed, built-in library of well-known opening lines for the opening trainer. */
public final class OpeningRepository {

    public static final List<OpeningLine> ALL =
            Collections.unmodifiableList(
                    Arrays.asList(
                            line(
                                    "ruy_lopez",
                                    "e2e4",
                                    "e7e5",
                                    "g1f3",
                                    "b8c6",
                                    "f1b5",
                                    "a7a6",
                                    "b5a4",
                                    "g8f6",
                                    "e1g1",
                                    "f8e7"),
                            line(
                                    "italian", "e2e4", "e7e5", "g1f3", "b8c6", "f1c4", "f8c5",
                                    "c2c3", "g8f6", "d2d3", "e8g8"),
                            line(
                                    "sicilian_najdorf",
                                    "e2e4",
                                    "c7c5",
                                    "g1f3",
                                    "d7d6",
                                    "d2d4",
                                    "c5d4",
                                    "f3d4",
                                    "g8f6",
                                    "b1c3",
                                    "a7a6"),
                            line(
                                    "queens_gambit_declined",
                                    "d2d4",
                                    "d7d5",
                                    "c2c4",
                                    "e7e6",
                                    "b1c3",
                                    "g8f6",
                                    "c1g5",
                                    "f8e7",
                                    "e2e3",
                                    "e8g8"),
                            line(
                                    "kings_indian",
                                    "d2d4",
                                    "g8f6",
                                    "c2c4",
                                    "g7g6",
                                    "b1c3",
                                    "f8g7",
                                    "e2e4",
                                    "d7d6",
                                    "g1f3",
                                    "e8g8"),
                            line(
                                    "french", "e2e4", "e7e6", "d2d4", "d7d5", "b1c3", "g8f6",
                                    "c1g5", "f8e7", "e4e5", "f6d7"),
                            line(
                                    "caro_kann",
                                    "e2e4",
                                    "c7c6",
                                    "d2d4",
                                    "d7d5",
                                    "b1c3",
                                    "d5e4",
                                    "c3e4",
                                    "c8f5",
                                    "e4g3",
                                    "f5g6"),
                            line(
                                    "english", "c2c4", "e7e5", "b1c3", "g8f6", "g1f3", "b8c6",
                                    "g2g3", "d7d5", "c4d5", "f6d5"),
                            line(
                                    "scandinavian",
                                    "e2e4",
                                    "d7d5",
                                    "e4d5",
                                    "d8d5",
                                    "b1c3",
                                    "d5a5",
                                    "d2d4",
                                    "g8f6",
                                    "g1f3",
                                    "c7c6"),
                            line(
                                    "slav", "d2d4", "d7d5", "c2c4", "c7c6", "g1f3", "g8f6", "b1c3",
                                    "d5c4", "a2a4", "c8f5"),
                            line(
                                    "nimzo_indian",
                                    "d2d4",
                                    "g8f6",
                                    "c2c4",
                                    "e7e6",
                                    "b1c3",
                                    "f8b4",
                                    "e2e3",
                                    "e8g8",
                                    "f1d3",
                                    "d7d5"),
                            line(
                                    "gruenfeld",
                                    "d2d4",
                                    "g8f6",
                                    "c2c4",
                                    "g7g6",
                                    "b1c3",
                                    "d7d5",
                                    "c4d5",
                                    "f6d5",
                                    "e2e4",
                                    "d5c3"),
                            line(
                                    "scotch", "e2e4", "e7e5", "g1f3", "b8c6", "d2d4", "e5d4",
                                    "f3d4", "g8f6", "b1c3", "f8b4"),
                            line(
                                    "pirc", "e2e4", "d7d6", "d2d4", "g8f6", "b1c3", "g7g6", "f2f4",
                                    "f8g7", "g1f3", "e8g8"),
                            line(
                                    "london", "d2d4", "d7d5", "c1f4", "g8f6", "e2e3", "e7e6",
                                    "g1f3", "f8d6", "f4g3", "e8g8"),
                            line(
                                    "vienna", "e2e4", "e7e5", "b1c3", "g8f6", "f2f4", "d7d5",
                                    "f4e5", "f6e4", "g1f3", "f8c5"),
                            line(
                                    "kings_gambit",
                                    "e2e4",
                                    "e7e5",
                                    "f2f4",
                                    "e5f4",
                                    "g1f3",
                                    "g7g5",
                                    "h2h4",
                                    "g5g4",
                                    "f3e5",
                                    "g8f6"),
                            line(
                                    "benoni", "d2d4", "g8f6", "c2c4", "c7c5", "d4d5", "e7e6",
                                    "b1c3", "e6d5", "c4d5", "d7d6"),
                            line(
                                    "catalan", "d2d4", "g8f6", "c2c4", "e7e6", "g2g3", "d7d5",
                                    "f1g2", "f8e7", "g1f3", "e8g8"),
                            line(
                                    "trompowsky",
                                    "d2d4",
                                    "g8f6",
                                    "c1g5",
                                    "f6e4",
                                    "g5f4",
                                    "c7c5",
                                    "f2f3",
                                    "d8a5",
                                    "c2c3",
                                    "e4f6")));

    private OpeningRepository() {}

    public static OpeningLine byId(String id) {
        for (OpeningLine line : ALL) {
            if (line.id().equals(id)) {
                return line;
            }
        }
        return null;
    }

    private static OpeningLine line(String id, String... uciMoves) {
        return new OpeningLine(id, new ArrayList<>(Arrays.asList(uciMoves)));
    }
}
