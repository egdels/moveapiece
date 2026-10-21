/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.maia960eval;

import de.schliweb.moveapiece.logic.Chess960;
import de.schliweb.moveapiece.maia960eval.GameRunner.Job;
import de.schliweb.moveapiece.maia960eval.GameRunner.Player;
import de.schliweb.moveapiece.maia960eval.GameRunner.Result;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Maia in Chess960 against standard chess: Maia against Stockfish at fixed strengths and Maia
 * against Maia, every position played with both colour assignments. Writes games.csv (one row per
 * game), one PGN file per mode and variant, events.tsv (every anomaly with its FEN) and summary.md.
 * A run picks up where an interrupted one stopped: games already in games.csv are skipped.
 */
public final class Maia960Eval {

    static final String CSV_HEADER =
            "id,mode,variant,sp,pair,white,white_elo,black,black_elo,result,termination,plies,"
                    + "w_maia_moves,w_blunders,w_castle_ply,w_top_illegal,"
                    + "b_maia_moves,b_blunders,b_castle_ply,b_top_illegal,"
                    + "slot_collisions,anomalies,ms";

    private Maia960Eval() {}

    public static void main(String[] args) throws Exception {
        Config config = Config.parse(args);
        Files.createDirectories(config.out());
        List<Integer> positions = positions(config);
        if (!config.only().equals("summary")) {
            run(config, jobs(config, positions));
        }
        Summary.write(config, positions);
    }

    /** {@code config.positions()} distinct Chess960 starting positions, never the standard one. */
    static List<Integer> positions(Config config) {
        Random random = new Random(config.seed());
        Set<Integer> picked = new LinkedHashSet<>();
        while (picked.size() < config.positions()) {
            int n = Chess960.random(random);
            if (n != Chess960.STANDARD) {
                picked.add(n);
            }
        }
        return new ArrayList<>(picked);
    }

    static List<Job> jobs(Config config, List<Integer> positions) {
        List<Job> jobs = new ArrayList<>();
        for (int p = 0; p < positions.size(); p++) {
            for (boolean c960 : new boolean[] {false, true}) {
                if (config.only().contains("960only") && !c960) {
                    continue;
                }
                int sp = c960 ? positions.get(p) : -1;
                String v = c960 ? "960" : "std";
                if (!config.only().contains("mvm")) {
                    for (int m : config.maiaElos()) {
                        for (int s : config.stockfishElos()) {
                            Player maia = new Player(true, m);
                            Player sf = new Player(false, s);
                            String base = String.format("mvs-%s-m%d-s%d-p%02d", v, m, s, p);
                            jobs.add(job(config, base + "-mw", false, sp, p, maia, sf));
                            jobs.add(job(config, base + "-mb", false, sp, p, sf, maia));
                        }
                    }
                }
                if (!config.only().contains("mvs")) {
                    for (int[] pair : config.maiaPairs()) {
                        Player low = new Player(true, pair[0]);
                        Player high = new Player(true, pair[1]);
                        String base = String.format("mvm-%s-a%d-b%d-p%02d", v, pair[0], pair[1], p);
                        jobs.add(job(config, base + "-lw", true, sp, p, low, high));
                        jobs.add(job(config, base + "-hw", true, sp, p, high, low));
                    }
                }
            }
        }
        return jobs;
    }

    private static Job job(
            Config config, String id, boolean mvm, int sp, int pair, Player w, Player b) {
        long seed = new SplittableRandom(config.seed() * 31 + id.hashCode()).nextLong();
        return new Job(id, mvm, sp, pair, w, b, seed);
    }

    private static void run(Config config, List<Job> all) throws Exception {
        Path csv = config.out().resolve("games.csv");
        Set<String> done = new HashSet<>();
        if (Files.exists(csv)) {
            for (String line : Files.readAllLines(csv)) {
                done.add(line.split(",", 2)[0]);
            }
        } else {
            Files.writeString(csv, CSV_HEADER + "\n");
        }
        ConcurrentLinkedQueue<Job> queue = new ConcurrentLinkedQueue<>();
        for (Job job : all) {
            if (!done.contains(job.id())) {
                queue.add(job);
            }
        }
        int total = queue.size();
        System.out.printf(
                "%d games to play (%d already done), %d workers%n",
                total, all.size() - total, config.threads());
        if (total == 0) {
            return;
        }

        EvalLog log = new EvalLog(config.out().resolve("events.tsv"));
        AtomicInteger finished = new AtomicInteger();
        long started = System.currentTimeMillis();
        try (SyncMaia maia = new SyncMaia(config.model(), config.timeoutMs())) {
            List<Thread> workers = new ArrayList<>();
            for (int w = 0; w < config.threads(); w++) {
                Thread t =
                        new Thread(
                                () -> work(config, maia, queue, log, finished, total, started),
                                "worker-" + w);
                t.start();
                workers.add(t);
            }
            for (Thread t : workers) {
                t.join();
            }
        }
        System.out.println("Events: " + log.counts());
    }

    private static void work(
            Config config,
            SyncMaia maia,
            ConcurrentLinkedQueue<Job> queue,
            EvalLog log,
            AtomicInteger finished,
            int total,
            long started) {
        SyncStockfish stockfish = null;
        Analyzer analyzer = null;
        Job job;
        while ((job = queue.poll()) != null) {
            Result result = null;
            for (int attempt = 1; attempt <= 2 && result == null; attempt++) {
                try {
                    if (stockfish == null) {
                        stockfish =
                                new SyncStockfish(
                                        config.stockfish(), config.net(), config.timeoutMs());
                        analyzer =
                                new Analyzer(
                                        config.stockfish(),
                                        config.net(),
                                        config.analysisDepth(),
                                        config.timeoutMs());
                    }
                    result = new GameRunner(config, maia, stockfish, analyzer, log).play(job);
                } catch (Exception e) {
                    log.event("engine-error", job.id(), -1, "-", "attempt " + attempt + ": " + e);
                    if (stockfish != null) {
                        stockfish.close();
                        analyzer.close();
                    }
                    stockfish = null;
                    analyzer = null;
                }
            }
            try {
                record(config, job, result);
            } catch (IOException e) {
                log.event("write-error", job.id(), -1, "-", e.toString());
            }
            int n = finished.incrementAndGet();
            if (n % 20 == 0 || n == total) {
                long elapsed = System.currentTimeMillis() - started;
                System.out.printf(
                        "%d/%d games, %d min elapsed, ~%d min left%n",
                        n, total, elapsed / 60000, elapsed * (total - n) / n / 60000);
            }
        }
        if (stockfish != null) {
            stockfish.close();
            analyzer.close();
        }
    }

    private static synchronized void record(Config config, Job job, Result r) throws IOException {
        String row;
        if (r == null) {
            row =
                    csvRow(
                            job,
                            "error",
                            "error",
                            0,
                            new GameRunner.SideStats(),
                            new GameRunner.SideStats(),
                            0,
                            1,
                            0);
        } else {
            row =
                    csvRow(
                            job,
                            r.result(),
                            r.termination(),
                            r.plies(),
                            r.white(),
                            r.black(),
                            r.slotCollisions(),
                            r.anomalies(),
                            r.millis());
            String pgnFile =
                    "games-"
                            + (job.mvm() ? "mvm" : "mvs")
                            + "-"
                            + (job.chess960() ? "960" : "std")
                            + ".pgn";
            Files.writeString(
                    config.out().resolve(pgnFile),
                    r.pgn() + "\n",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        }
        Files.writeString(
                config.out().resolve("games.csv"),
                row + "\n",
                StandardCharsets.UTF_8,
                StandardOpenOption.APPEND);
    }

    private static String csvRow(
            Job job,
            String result,
            String termination,
            int plies,
            GameRunner.SideStats w,
            GameRunner.SideStats b,
            int collisions,
            int anomalies,
            long ms) {
        return String.join(
                ",",
                job.id(),
                job.mvm() ? "mvm" : "mvs",
                job.chess960() ? "960" : "std",
                "" + job.sp(),
                "" + job.pair(),
                job.white().maia() ? "maia" : "sf",
                "" + job.white().elo(),
                job.black().maia() ? "maia" : "sf",
                "" + job.black().elo(),
                result,
                termination,
                "" + plies,
                "" + w.maiaMoves,
                "" + w.blunders,
                "" + w.castlePly,
                "" + w.topIllegal,
                "" + b.maiaMoves,
                "" + b.blunders,
                "" + b.castlePly,
                "" + b.topIllegal,
                "" + collisions,
                "" + anomalies,
                "" + ms);
    }
}
