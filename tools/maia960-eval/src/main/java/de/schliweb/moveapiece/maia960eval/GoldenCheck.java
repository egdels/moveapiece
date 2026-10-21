/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.maia960eval;

import de.schliweb.moveapiece.engine.MaiaEngine;
import de.schliweb.moveapiece.engine.MaiaEngineListener;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * {@code MaiaEngineGoldenTest} for any model, ONNX Runtime and JVM: plays every reference case of a
 * golden file (written by tools/maia3/export_onnx.py) through {@link MaiaEngine} and prints how
 * many differ and the time per move. Arguments: the model file and the golden file.
 */
public final class GoldenCheck {

    private GoldenCheck() {}

    public static void main(String[] args) throws Exception {
        MaiaEngine engine = new MaiaEngine(Runnable::run);
        CountDownLatch ready = new CountDownLatch(1);
        BlockingQueue<String> moves = new LinkedBlockingQueue<>();
        engine.setListener(
                new MaiaEngineListener() {
                    @Override
                    public void onReady() {
                        ready.countDown();
                    }

                    @Override
                    public void onBestMove(String m, float w, float d, float l) {
                        moves.add(m == null ? "(none)" : m);
                    }

                    @Override
                    public void onEngineError(Exception e) {
                        e.printStackTrace();
                        moves.add("(error)");
                        ready.countDown();
                    }
                });
        long t0 = System.nanoTime();
        try (InputStream in = new FileInputStream(args[0])) {
            engine.start(in);
        }
        ready.await(120, TimeUnit.SECONDS);
        long loadMs = (System.nanoTime() - t0) / 1_000_000;
        List<String> mismatches = new ArrayList<>();
        int cases = 0;
        long inferNs = 0;
        for (String line : Files.readAllLines(Path.of(args[1]))) {
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] f = line.split(";", -1);
            engine.setElo(Integer.parseInt(f[0]));
            engine.setPosition(f[3]);
            long s = System.nanoTime();
            engine.go();
            String actual = moves.poll(60, TimeUnit.SECONDS);
            inferNs += System.nanoTime() - s;
            cases++;
            if (!f[1].equals(actual)) {
                mismatches.add(line + " -> " + actual);
            }
        }
        engine.shutdown();
        System.out.printf(
                "%s: %d cases, %d mismatches, load %d ms, %.1f ms per move, arch %s%n",
                Path.of(args[0]).getFileName(),
                cases,
                mismatches.size(),
                loadMs,
                inferNs / 1e6 / cases,
                System.getProperty("os.arch"));
        mismatches.forEach(m -> System.out.println("  " + m));
        System.exit(0);
    }
}
