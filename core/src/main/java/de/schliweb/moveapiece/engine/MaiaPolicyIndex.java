/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.engine;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The fixed 1858-entry move index that lc0/Maia policy outputs are addressed by, loaded from a
 * bundled resource rather than re-derived from the AlphaZero-style 8x8x73 plane geometry - see
 * MAIA_PROVENANCE_TEMPLATE.md ("Policy-Index-Tabelle real gegengeprüft") for how the resource was
 * generated and cross-checked against lc0's own live {@code VerboseMoveStats} output.
 *
 * <p>Every entry is a UCI-shaped move string (e.g. "e2e4") in <b>network space</b>: always as if
 * White is to move and sitting at the bottom of the board, and with castling written as "king
 * captures own rook" (e.g. "e1h1" for White's short castle, never "e1g1"). Translating a real board
 * move to and from network space is {@link MaiaMoveIndexer}'s job, not this class's.
 */
final class MaiaPolicyIndex {

    static final int SIZE = 1858;
    private static final String RESOURCE = "maia/policy_index_1858.txt";

    private static final List<String> BY_INDEX = load();
    private static final Map<String, Integer> INDEX_OF = buildLookup(BY_INDEX);

    private MaiaPolicyIndex() {}

    static String moveAt(int index) {
        return BY_INDEX.get(index);
    }

    /**
     * @return the policy index for a network-space move string, or -1 if it names no slot.
     */
    static int indexOf(String networkMove) {
        Integer idx = INDEX_OF.get(networkMove);
        return idx == null ? -1 : idx;
    }

    private static List<String> load() {
        List<String> result = new ArrayList<>(SIZE);
        try (InputStream in = MaiaPolicyIndex.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing resource: " + RESOURCE);
            }
            BufferedReader reader =
                    new BufferedReader(new InputStreamReader(in, StandardCharsets.US_ASCII));
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isEmpty()) {
                    result.add(line);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load " + RESOURCE, e);
        }
        if (result.size() != SIZE) {
            throw new IllegalStateException(
                    "Expected " + SIZE + " policy index entries, got " + result.size());
        }
        return result;
    }

    private static Map<String, Integer> buildLookup(List<String> byIndex) {
        Map<String, Integer> map = new HashMap<>(byIndex.size() * 2);
        for (int i = 0; i < byIndex.size(); i++) {
            map.put(byIndex.get(i), i);
        }
        return map;
    }
}
