/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.maia960eval;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Every parameter of a run; all of them end up in the summary. */
record Config(
        Path out,
        Path model,
        String stockfish,
        String net,
        long seed,
        int positions,
        List<Integer> maiaElos,
        List<Integer> stockfishElos,
        List<int[]> maiaPairs,
        int movetimeMs,
        int sampledMoves,
        int maxPlies,
        boolean adjudicate,
        int adjudicationCp,
        int adjudicationPlies,
        int analysisDepth,
        int blunderCp,
        int evalCapCp,
        int threads,
        long timeoutMs,
        String only,
        CastleVariant castleVariant) {

    static Config parse(String[] args) {
        Map<String, String> a = new HashMap<>();
        for (String arg : args) {
            if (!arg.startsWith("--") || !arg.contains("=")) {
                throw new IllegalArgumentException("Expected --name=value, got " + arg);
            }
            a.put(arg.substring(2, arg.indexOf('=')), arg.substring(arg.indexOf('=') + 1));
        }
        String sfDir = "desktop/build/stockfish/" + hostDir();
        return new Config(
                Path.of(a.getOrDefault("out", "tools/maia960-eval/results")),
                Path.of(
                        a.getOrDefault(
                                "model",
                                "desktop/src/main/resources/de/schliweb/moveapiece/desktop/"
                                        + "maia/maia3-5m.onnx")),
                a.getOrDefault("stockfish", sfDir + "/stockfish"),
                a.getOrDefault("net", sfDir + "/nn-1a298aa575a0.nnue"),
                Long.parseLong(a.getOrDefault("seed", "960")),
                Integer.parseInt(a.getOrDefault("positions", "50")),
                ints(a.getOrDefault("maia", "1400,1700,2000,2300")),
                ints(a.getOrDefault("sf", "1400,1800,2200")),
                pairs(a.getOrDefault("pairs", "1400-2000,1700-2300")),
                Integer.parseInt(a.getOrDefault("movetime", "100")),
                Integer.parseInt(a.getOrDefault("sampled", "2")),
                Integer.parseInt(a.getOrDefault("maxplies", "300")),
                Boolean.parseBoolean(a.getOrDefault("adjudicate", "true")),
                Integer.parseInt(a.getOrDefault("adjcp", "1000")),
                Integer.parseInt(a.getOrDefault("adjplies", "10")),
                Integer.parseInt(a.getOrDefault("depth", "12")),
                Integer.parseInt(a.getOrDefault("blundercp", "200")),
                Integer.parseInt(a.getOrDefault("evalcap", "1000")),
                Integer.parseInt(
                        a.getOrDefault(
                                "threads",
                                "" + Math.max(1, Runtime.getRuntime().availableProcessors() - 1))),
                Long.parseLong(a.getOrDefault("timeout", "60000")),
                a.getOrDefault("only", ""),
                a.containsKey("castle") ? CastleVariant.parse(a.get("castle")) : CastleVariant.APP);
    }

    private static String hostDir() {
        String os = System.getProperty("os.name").toLowerCase();
        String arch = System.getProperty("os.arch");
        String name = os.contains("mac") ? "macos" : os.contains("win") ? "windows" : "linux";
        boolean arm = arch.equals("aarch64") || arch.equals("arm64");
        return name + "-" + (arm ? "arm64" : "x86_64");
    }

    private static List<Integer> ints(String csv) {
        List<Integer> out = new ArrayList<>();
        for (String s : csv.split(",")) {
            if (!s.isBlank()) {
                out.add(Integer.parseInt(s.trim()));
            }
        }
        return out;
    }

    private static List<int[]> pairs(String csv) {
        List<int[]> out = new ArrayList<>();
        for (String s : csv.split(",")) {
            if (!s.isBlank()) {
                String[] p = s.trim().split("-");
                out.add(new int[] {Integer.parseInt(p[0]), Integer.parseInt(p[1])});
            }
        }
        return out;
    }
}
