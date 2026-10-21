/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.maia960eval;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Reads games.csv and writes summary.md: scores with Elo differences and 95 % confidence intervals,
 * the shift Chess960 minus standard per Maia level, castling, blunders and the event counts.
 *
 * <p>Confidence intervals: a position is played twice with colours swapped, so the unit of sampling
 * is the pair, not the game. Score intervals use the standard error of the pair means; the shift
 * intervals are percentile bootstraps over pairs (fixed seed).
 */
final class Summary {

    private static final int BOOTSTRAP = 2000;
    private static final long BOOTSTRAP_SEED = 4242;
    private static final String[] VARIANTS = {"std", "960"};

    /** One row of games.csv. */
    record Row(
            String id,
            String mode,
            String variant,
            int sp,
            int pair,
            boolean whiteMaia,
            int whiteElo,
            boolean blackMaia,
            int blackElo,
            String result,
            String termination,
            int plies,
            int[] w,
            int[] b,
            int collisions,
            int anomalies) {

        static Row parse(String line) {
            String[] f = line.split(",");
            return new Row(
                    f[0],
                    f[1],
                    f[2],
                    Integer.parseInt(f[3]),
                    Integer.parseInt(f[4]),
                    f[5].equals("maia"),
                    Integer.parseInt(f[6]),
                    f[7].equals("maia"),
                    Integer.parseInt(f[8]),
                    f[9],
                    f[10],
                    Integer.parseInt(f[11]),
                    new int[] {
                        Integer.parseInt(f[12]),
                        Integer.parseInt(f[13]),
                        Integer.parseInt(f[14]),
                        Integer.parseInt(f[15])
                    },
                    new int[] {
                        Integer.parseInt(f[16]),
                        Integer.parseInt(f[17]),
                        Integer.parseInt(f[18]),
                        Integer.parseInt(f[19])
                    },
                    Integer.parseInt(f[20]),
                    Integer.parseInt(f[21]));
        }

        boolean ok() {
            return !termination.equals("error");
        }

        double whiteScore() {
            return result.equals("1-0") ? 1 : result.equals("0-1") ? 0 : 0.5;
        }

        /** Score of the player with this rating and kind (Maia or not). */
        double scoreOf(boolean maia, int elo) {
            boolean white = whiteMaia == maia && whiteElo == elo;
            return white ? whiteScore() : 1 - whiteScore();
        }
    }

    /** One pairing's games, grouped by position pair, scores from the tested player's view. */
    record Pairing(int opponentElo, List<double[]> pairs) {
        int games() {
            return pairs.stream().mapToInt(p -> p.length).sum();
        }

        double score() {
            return pairs.stream().flatMapToDouble(Arrays::stream).sum();
        }

        Pairing resample(Random random) {
            List<double[]> out = new ArrayList<>(pairs.size());
            for (int i = 0; i < pairs.size(); i++) {
                out.add(pairs.get(random.nextInt(pairs.size())));
            }
            return new Pairing(opponentElo, out);
        }
    }

    private Summary() {}

    static void write(Config config, List<Integer> positions) throws IOException {
        List<Row> rows = new ArrayList<>();
        List<String> lines = Files.readAllLines(config.out().resolve("games.csv"));
        for (String line : lines.subList(1, lines.size())) {
            rows.add(Row.parse(line));
        }
        StringBuilder md = new StringBuilder();
        md.append("# Maia960 eval: summary\n\n");
        md.append("Generated from games.csv by `Summary`; see REPORT.md for the discussion.\n\n");
        parameters(md, config, positions);
        long errors = rows.stream().filter(r -> !r.ok()).count();
        md.append(
                String.format(
                        "Games in games.csv: %d, of them without a result (error): %d.%n%n",
                        rows.size(), errors));
        rows.removeIf(r -> !r.ok());

        maiaVsStockfish(md, config, rows);
        shift(md, config, rows);
        maiaVsMaia(md, config, rows);
        castling(md, config, rows);
        blunders(md, config, rows);
        diagnostics(md, config, rows);
        terminations(md, rows);
        Files.writeString(config.out().resolve("summary.md"), md.toString());
        System.out.println("Wrote " + config.out().resolve("summary.md"));
    }

    private static void parameters(StringBuilder md, Config c, List<Integer> positions) {
        md.append("## Parameters\n\n");
        md.append("| Parameter | Value |\n|---|---|\n");
        row(md, "Seed (positions, sampling)", "" + c.seed());
        row(
                md,
                "Chess960 positions",
                positions.stream().map(String::valueOf).collect(Collectors.joining(" ")));
        row(md, "Maia ratings", "" + c.maiaElos());
        row(md, "Stockfish UCI_Elo", "" + c.stockfishElos());
        row(
                md,
                "Maia vs Maia",
                c.maiaPairs().stream()
                        .map(p -> p[0] + " vs " + p[1])
                        .collect(Collectors.joining(", ")));
        row(md, "Stockfish movetime", c.movetimeMs() + " ms");
        row(md, "Maia sampled moves (T = 1) per side", "" + c.sampledMoves());
        row(md, "Ply limit (draw)", "" + c.maxPlies());
        row(
                md,
                "Adjudication",
                c.adjudicate()
                        ? "|eval| > "
                                + c.adjudicationCp()
                                + " cp, same side, "
                                + c.adjudicationPlies()
                                + " plies in a row"
                        : "off");
        row(
                md,
                "Referee",
                "Stockfish full strength, depth " + c.analysisDepth() + ", Threads 1, Hash 16");
        row(
                md,
                "Blunder",
                "loss > " + c.blunderCp() + " cp, evals capped at ±" + c.evalCapCp() + " cp");
        row(
                md,
                "Chess960 castle scoring",
                c.castleVariant().isApp()
                        ? "app (" + c.castleVariant() + ")"
                        : c.castleVariant() + " (experimental, not the app)");
        row(md, "Workers", "" + c.threads());
        row(md, "Bootstrap", BOOTSTRAP + " resamples of position pairs, seed " + BOOTSTRAP_SEED);
        md.append('\n');
    }

    private static void row(StringBuilder md, String k, String v) {
        md.append("| ").append(k).append(" | ").append(v).append(" |\n");
    }

    private static List<Row> select(List<Row> rows, Predicate<Row> p) {
        return rows.stream().filter(p).collect(Collectors.toList());
    }

    /** The games of one pairing, scored for the player {@code (maia, elo)}. */
    private static Pairing pairing(List<Row> games, boolean maia, int elo, int opponentElo) {
        Map<Integer, List<Double>> byPair = new TreeMap<>();
        for (Row r : games) {
            byPair.computeIfAbsent(r.pair(), k -> new ArrayList<>()).add(r.scoreOf(maia, elo));
        }
        List<double[]> pairs = new ArrayList<>();
        for (List<Double> s : byPair.values()) {
            pairs.add(s.stream().mapToDouble(Double::doubleValue).toArray());
        }
        return new Pairing(opponentElo, pairs);
    }

    private static void maiaVsStockfish(StringBuilder md, Config c, List<Row> rows) {
        md.append("## Maia against Stockfish\n\n");
        md.append(
                "Score and Elo difference from Maia's view, 95 % CI from the position pairs.\n\n");
        md.append("| Maia | SF | Variant | Games | +/=/- | Score | Elo diff [95 % CI] |\n");
        md.append("|---|---|---|---|---|---|---|\n");
        for (int m : c.maiaElos()) {
            for (int s : c.stockfishElos()) {
                for (String v : VARIANTS) {
                    List<Row> games =
                            select(
                                    rows,
                                    r ->
                                            r.mode().equals("mvs")
                                                    && r.variant().equals(v)
                                                    && maiaElo(r) == m
                                                    && sfElo(r) == s);
                    if (games.isEmpty()) {
                        continue;
                    }
                    Pairing p = pairing(games, true, m, s);
                    md.append(
                            String.format(
                                    Locale.ROOT,
                                    "| %d | %d | %s | %d | %s | %.1f %% | %s |%n",
                                    m,
                                    s,
                                    v,
                                    p.games(),
                                    wdl(games, true, m),
                                    100 * p.score() / p.games(),
                                    eloWithCi(p)));
                }
            }
        }
        md.append('\n');
    }

    private static int maiaElo(Row r) {
        return r.whiteMaia() ? r.whiteElo() : r.blackElo();
    }

    private static int sfElo(Row r) {
        return r.whiteMaia() ? r.blackElo() : r.whiteElo();
    }

    private static String wdl(List<Row> games, boolean maia, int elo) {
        int w = 0;
        int d = 0;
        int l = 0;
        for (Row r : games) {
            double s = r.scoreOf(maia, elo);
            if (s == 1) {
                w++;
            } else if (s == 0) {
                l++;
            } else {
                d++;
            }
        }
        return w + "/" + d + "/" + l;
    }

    private static String eloWithCi(Pairing p) {
        int n = p.games();
        double s = p.score() / n;
        double[] means =
                p.pairs().stream().mapToDouble(a -> Arrays.stream(a).average().orElse(0)).toArray();
        double mean = Arrays.stream(means).average().orElse(0);
        double var =
                Arrays.stream(means).map(x -> (x - mean) * (x - mean)).sum()
                        / Math.max(1, means.length - 1);
        double se = Math.sqrt(var / means.length);
        double floor = 0.5 / n;
        return String.format(
                "%s [%s, %s]",
                fmtElo(s, floor), fmtElo(s - 1.96 * se, floor), fmtElo(s + 1.96 * se, floor));
    }

    /**
     * Elo difference for score {@code s}; beyond half a game from 0 or 1 it is shown as a bound.
     */
    private static String fmtElo(double s, double floor) {
        if (s <= floor) {
            return String.format("< %+d", Math.round(elo(floor)));
        }
        if (s >= 1 - floor) {
            return String.format("> %+d", Math.round(elo(1 - floor)));
        }
        return String.format("%+d", Math.round(elo(s)));
    }

    static double elo(double score) {
        return -400 * Math.log10(1 / score - 1);
    }

    /**
     * Performance rating against the given pairings: the rating whose expected score against all
     * opponents equals the actual score (bisection, limited to 0..4000).
     */
    static double performance(List<Pairing> pairings) {
        double lo = 0;
        double hi = 4000;
        for (int i = 0; i < 60; i++) {
            double mid = (lo + hi) / 2;
            double expected = 0;
            double actual = 0;
            for (Pairing p : pairings) {
                expected += p.games() / (1 + Math.pow(10, (p.opponentElo() - mid) / 400));
                actual += p.score();
            }
            if (expected < actual) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return (lo + hi) / 2;
    }

    private static void shift(StringBuilder md, Config c, List<Row> rows) {
        md.append("## Elo shift Chess960 minus standard per Maia level\n\n");
        md.append(
                "Performance rating against the three Stockfish levels together (on Stockfish's "
                        + "nominal UCI_Elo scale), per variant; shift with a bootstrap 95 % CI.\n\n");
        md.append("| Maia | Perf. standard | Perf. 960 | Shift 960 − std [95 % CI] |\n");
        md.append("|---|---|---|---|\n");
        Random random = new Random(BOOTSTRAP_SEED);
        for (int m : c.maiaElos()) {
            List<Pairing> std = new ArrayList<>();
            List<Pairing> c960 = new ArrayList<>();
            for (int s : c.stockfishElos()) {
                for (String v : VARIANTS) {
                    List<Row> games =
                            select(
                                    rows,
                                    r ->
                                            r.mode().equals("mvs")
                                                    && r.variant().equals(v)
                                                    && maiaElo(r) == m
                                                    && sfElo(r) == s);
                    if (!games.isEmpty()) {
                        (v.equals("std") ? std : c960).add(pairing(games, true, m, s));
                    }
                }
            }
            if (std.isEmpty() || c960.isEmpty()) {
                continue;
            }
            double ps = performance(std);
            double p9 = performance(c960);
            double[] boot = new double[BOOTSTRAP];
            for (int i = 0; i < BOOTSTRAP; i++) {
                boot[i] = performance(resample(c960, random)) - performance(resample(std, random));
            }
            Arrays.sort(boot);
            md.append(
                    String.format(
                            "| %d | %.0f | %.0f | %+.0f [%+.0f, %+.0f] |%n",
                            m,
                            ps,
                            p9,
                            p9 - ps,
                            boot[(int) (0.025 * BOOTSTRAP)],
                            boot[(int) (0.975 * BOOTSTRAP)]));
        }
        md.append('\n');

        md.append(
                "Shift per pairing (Elo diff 960 − standard, bootstrap 95 % CI; scores of 0 "
                        + "or 100 % are clamped to half a game):\n\n");
        md.append("| Maia | SF | Shift [95 % CI] |\n|---|---|---|\n");
        for (int m : c.maiaElos()) {
            for (int s : c.stockfishElos()) {
                List<Pairing> both = new ArrayList<>();
                for (String v : VARIANTS) {
                    List<Row> games =
                            select(
                                    rows,
                                    r ->
                                            r.mode().equals("mvs")
                                                    && r.variant().equals(v)
                                                    && maiaElo(r) == m
                                                    && sfElo(r) == s);
                    both.add(pairing(games, true, m, s));
                }
                if (both.get(0).games() == 0 || both.get(1).games() == 0) {
                    continue;
                }
                double[] boot = new double[BOOTSTRAP];
                for (int i = 0; i < BOOTSTRAP; i++) {
                    boot[i] =
                            clampedElo(both.get(1).resample(random))
                                    - clampedElo(both.get(0).resample(random));
                }
                Arrays.sort(boot);
                md.append(
                        String.format(
                                "| %d | %d | %+.0f [%+.0f, %+.0f] |%n",
                                m,
                                s,
                                clampedElo(both.get(1)) - clampedElo(both.get(0)),
                                boot[(int) (0.025 * BOOTSTRAP)],
                                boot[(int) (0.975 * BOOTSTRAP)]));
            }
        }
        md.append('\n');
    }

    private static List<Pairing> resample(List<Pairing> pairings, Random random) {
        List<Pairing> out = new ArrayList<>();
        for (Pairing p : pairings) {
            out.add(p.resample(random));
        }
        return out;
    }

    private static double clampedElo(Pairing p) {
        double floor = 0.5 / p.games();
        double s = Math.max(floor, Math.min(1 - floor, p.score() / p.games()));
        return elo(s);
    }

    private static void maiaVsMaia(StringBuilder md, Config c, List<Row> rows) {
        md.append("## Maia against Maia\n\n");
        md.append("Score of the higher-rated Maia; nominal gap 600.\n\n");
        md.append("| Pairing | Variant | Games | +/=/- | Score | Elo diff [95 % CI] |\n");
        md.append("|---|---|---|---|---|---|\n");
        Random random = new Random(BOOTSTRAP_SEED + 1);
        List<String> shifts = new ArrayList<>();
        for (int[] pair : c.maiaPairs()) {
            Pairing[] byVariant = new Pairing[2];
            for (int i = 0; i < 2; i++) {
                String v = VARIANTS[i];
                List<Row> games =
                        select(
                                rows,
                                r ->
                                        r.mode().equals("mvm")
                                                && r.variant().equals(v)
                                                && Math.min(r.whiteElo(), r.blackElo()) == pair[0]
                                                && Math.max(r.whiteElo(), r.blackElo()) == pair[1]);
                if (games.isEmpty()) {
                    continue;
                }
                byVariant[i] = pairing(games, true, pair[1], pair[0]);
                md.append(
                        String.format(
                                Locale.ROOT,
                                "| %d vs %d | %s | %d | %s | %.1f %% | %s |%n",
                                pair[1],
                                pair[0],
                                v,
                                byVariant[i].games(),
                                wdl(games, true, pair[1]),
                                100 * byVariant[i].score() / byVariant[i].games(),
                                eloWithCi(byVariant[i])));
            }
            if (byVariant[0] != null && byVariant[1] != null) {
                double[] boot = new double[BOOTSTRAP];
                for (int i = 0; i < BOOTSTRAP; i++) {
                    boot[i] =
                            clampedElo(byVariant[1].resample(random))
                                    - clampedElo(byVariant[0].resample(random));
                }
                Arrays.sort(boot);
                shifts.add(
                        String.format(
                                Locale.ROOT,
                                "| %d vs %d | %+.1f pp | %+.0f [%+.0f, %+.0f] |",
                                pair[1],
                                pair[0],
                                100
                                        * (byVariant[1].score() / byVariant[1].games()
                                                - byVariant[0].score() / byVariant[0].games()),
                                clampedElo(byVariant[1]) - clampedElo(byVariant[0]),
                                boot[(int) (0.025 * BOOTSTRAP)],
                                boot[(int) (0.975 * BOOTSTRAP)]));
            }
        }
        md.append("\n| Pairing | Score 960 − std | Elo gap 960 − std [95 % CI] |\n|---|---|---|\n");
        shifts.forEach(s -> md.append(s).append('\n'));
        md.append('\n');
    }

    /** Per Maia side of a game: {maia elo, castle ply or -1, moves, blunders, top illegal}. */
    private static List<int[]> sides(List<Row> rows, boolean maia) {
        List<int[]> out = new ArrayList<>();
        for (Row r : rows) {
            if (r.whiteMaia() == maia) {
                out.add(new int[] {r.whiteElo(), r.w()[2], r.w()[0], r.w()[1], r.w()[3]});
            }
            if (r.blackMaia() == maia) {
                out.add(new int[] {r.blackElo(), r.b()[2], r.b()[0], r.b()[1], r.b()[3]});
            }
        }
        return out;
    }

    private static void castling(StringBuilder md, Config c, List<Row> rows) {
        md.append("## Castling\n\n");
        md.append(
                "Share of games in which a side castled, and the mean move number "
                        + "(full moves) of the castle. Stockfish's own sides for comparison.\n\n");
        md.append(
                "| Player | Mode | Std: castled | Std: mean move | 960: castled | "
                        + "960: mean move |\n|---|---|---|---|---|---|\n");
        for (String mode : new String[] {"mvs", "mvm"}) {
            for (int m : c.maiaElos()) {
                castlingRow(md, "Maia " + m, mode, rows, true, m);
            }
        }
        for (int s : c.stockfishElos()) {
            castlingRow(md, "Stockfish " + s, "mvs", rows, false, s);
        }
        md.append('\n');
    }

    private static void castlingRow(
            StringBuilder md, String label, String mode, List<Row> rows, boolean maia, int elo) {
        StringBuilder line = new StringBuilder("| " + label + " | " + mode + " |");
        boolean any = false;
        for (String v : VARIANTS) {
            List<int[]> s =
                    sides(select(rows, r -> r.mode().equals(mode) && r.variant().equals(v)), maia)
                            .stream()
                            .filter(x -> x[0] == elo)
                            .collect(Collectors.toList());
            if (s.isEmpty()) {
                line.append(" – | – |");
                continue;
            }
            any = true;
            List<int[]> castled = s.stream().filter(x -> x[1] >= 0).collect(Collectors.toList());
            double meanMove = castled.stream().mapToDouble(x -> x[1] / 2 + 1).average().orElse(0);
            line.append(
                    String.format(
                            Locale.ROOT,
                            " %.0f %% (%d/%d) | %.1f |",
                            100.0 * castled.size() / s.size(),
                            castled.size(),
                            s.size(),
                            meanMove));
        }
        if (any) {
            md.append(line).append('\n');
        }
    }

    private static void blunders(StringBuilder md, Config c, List<Row> rows) {
        md.append("## Blunders\n\n");
        md.append(
                "Maia moves losing more than "
                        + c.blunderCp()
                        + " cp by the referee, per 100 "
                        + "Maia moves (the first "
                        + c.sampledMoves()
                        + " sampled moves per side "
                        + "included).\n\n");
        md.append("| Maia | Mode | Std | 960 |\n|---|---|---|---|\n");
        for (String mode : new String[] {"mvs", "mvm"}) {
            for (int m : c.maiaElos()) {
                StringBuilder line = new StringBuilder("| " + m + " | " + mode + " |");
                boolean any = false;
                for (String v : VARIANTS) {
                    List<int[]> s =
                            sides(
                                            select(
                                                    rows,
                                                    r ->
                                                            r.mode().equals(mode)
                                                                    && r.variant().equals(v)),
                                            true)
                                    .stream()
                                    .filter(x -> x[0] == m)
                                    .collect(Collectors.toList());
                    long moves = s.stream().mapToLong(x -> x[2]).sum();
                    long blunders = s.stream().mapToLong(x -> x[3]).sum();
                    if (moves == 0) {
                        line.append(" – |");
                        continue;
                    }
                    any = true;
                    line.append(
                            String.format(
                                    Locale.ROOT,
                                    " %.2f (%d/%d) |",
                                    100.0 * blunders / moves,
                                    blunders,
                                    moves));
                }
                if (any) {
                    md.append(line).append('\n');
                }
            }
        }
        md.append('\n');
    }

    private static void diagnostics(StringBuilder md, Config c, List<Row> rows) {
        md.append("## Mapping diagnostics\n\n");
        md.append(
                "| Variant | Maia moves | Raw top slot not a legal move (informational) | Positions with two "
                        + "legal moves on one slot | Anomalies (wrong castle, illegal move, diagnostic "
                        + "mismatch) |\n|---|---|---|---|---|\n");
        for (String v : VARIANTS) {
            List<Row> sel = select(rows, r -> r.variant().equals(v));
            List<int[]> s = sides(sel, true);
            long moves = s.stream().mapToLong(x -> x[2]).sum();
            long illegal = s.stream().mapToLong(x -> x[4]).sum();
            long coll = sel.stream().mapToLong(Row::collisions).sum();
            long anom = sel.stream().mapToLong(Row::anomalies).sum();
            md.append(
                    String.format(
                            Locale.ROOT,
                            "| %s | %d | %d (%.2f %%) | %d | %d |%n",
                            v,
                            moves,
                            illegal,
                            moves == 0 ? 0 : 100.0 * illegal / moves,
                            coll,
                            anom));
        }
        md.append(
                "\nEvery case is in events.tsv with its FEN, of the raw top slot only the first 50.\n\n");
    }

    private static void terminations(StringBuilder md, List<Row> rows) {
        md.append("## Game ends\n\n");
        Map<String, Map<String, Integer>> counts = new TreeMap<>();
        Map<String, double[]> plies = new TreeMap<>();
        for (Row r : rows) {
            String key = r.mode() + " " + r.variant();
            counts.computeIfAbsent(key, k -> new TreeMap<>())
                    .merge(r.termination(), 1, Integer::sum);
            double[] p = plies.computeIfAbsent(key, k -> new double[2]);
            p[0] += r.plies();
            p[1]++;
        }
        md.append("| Mode, variant | Mean plies | Endings |\n|---|---|---|\n");
        for (String key : counts.keySet()) {
            md.append(
                    String.format(
                            Locale.ROOT,
                            "| %s | %.0f | %s |%n",
                            key,
                            plies.get(key)[0] / plies.get(key)[1],
                            counts.get(key)));
        }
        md.append('\n');
    }
}
