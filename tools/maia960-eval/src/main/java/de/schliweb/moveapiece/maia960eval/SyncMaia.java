/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.maia960eval;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.move.Move;
import de.schliweb.moveapiece.engine.MaiaEngine;
import de.schliweb.moveapiece.engine.MaiaEngineListener;
import de.schliweb.moveapiece.engine.MaiaEvalAccess;
import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * The app's {@link MaiaEngine}, driven synchronously and shared by all games: calls are serialized,
 * a forward pass takes milliseconds. Next to it runs a second ONNX session on the same model, for
 * diagnostics only: it exposes the raw policy, so the harness can see whether the network's top
 * slot was a legal move at all. MaiaEngine itself only ever looks at the legal moves' slots.
 */
final class SyncMaia implements AutoCloseable {

    /**
     * What Maia played and what the raw policy said about it.
     *
     * @param move the move played: the engine's, or the castle variant's choice
     * @param engineMove what MaiaEngine itself chose
     * @param appMove the app's choice recomputed from the diagnostic pass; must equal engineMove
     */
    record Decision(
            String move,
            String engineMove,
            boolean topSlotLegal,
            int topSlot,
            String appMove,
            List<String> slotCollisions) {}

    private final Random random = new Random();
    private final MaiaEngine engine;
    private final BlockingQueue<Object> replies = new LinkedBlockingQueue<>();
    private final OrtEnvironment environment;
    private final OrtSession diagSession;
    private final long timeoutMs;

    SyncMaia(Path model, long timeoutMs) throws Exception {
        this.timeoutMs = timeoutMs;
        engine = MaiaEvalAccess.newEngine(Runnable::run, random);
        engine.setListener(
                new MaiaEngineListener() {
                    @Override
                    public void onReady() {
                        replies.add(Boolean.TRUE);
                    }

                    @Override
                    public void onBestMove(String uci, float win, float draw, float loss) {
                        replies.add(uci == null ? "(none)" : uci);
                    }

                    @Override
                    public void onEngineError(Exception error) {
                        replies.add(error);
                    }
                });
        try (InputStream in = Files.newInputStream(model)) {
            engine.start(in);
        }
        await();
        environment = OrtEnvironment.getEnvironment();
        diagSession =
                environment.createSession(
                        Files.readAllBytes(model), new OrtSession.SessionOptions());
    }

    /**
     * Asks Maia for a move exactly as the app does (setElo, setPosition, go), with {@code
     * temperature} > 0 only for the sampled opening moves. With a {@code variant} other than the
     * app's, the move played is instead chosen from the diagnostic policy with that variant's
     * castle scoring, drawing from the same seed; with {@code topK} > 0 a sampled move is drawn
     * from the {@code topK} highest-scoring moves only.
     *
     * @param board the current position, replayed the same way as inside MaiaEngine
     * @param history placements of the last positions, oldest first, as inside MaiaEngine
     */
    synchronized Decision play(
            String startFen,
            String uciMoves,
            int elo,
            double temperature,
            long seed,
            Board board,
            List<int[]> history,
            CastleVariant variant,
            int topK)
            throws Exception {
        random.setSeed(seed);
        engine.setElo(elo);
        engine.setPosition(startFen, uciMoves);
        engine.go(temperature);
        String engineMove = (String) await();

        float[] policy = policy(history, elo, elo);
        int top = 0;
        for (int i = 1; i < policy.length; i++) {
            if (policy[i] > policy[top]) {
                top = i;
            }
        }
        boolean topLegal = false;
        Map<Integer, String> bySlot = new HashMap<>();
        List<String> collisions = new ArrayList<>();
        List<Move> legal = board.legalMoves();
        double[] appScores = new double[legal.size()];
        double[] variantScores = new double[legal.size()];
        for (int i = 0; i < legal.size(); i++) {
            Move move = legal.get(i);
            int slot = MaiaEvalAccess.indexOf(board, move);
            topLegal |= slot == top;
            String other = bySlot.put(slot, move.toString());
            if (other != null) {
                collisions.add(other + "=" + move);
            }
            appScores[i] = CastleVariant.APP.score(board, move, policy);
            variantScores[i] = variant.score(board, move, policy);
        }
        String appMove =
                legal.isEmpty()
                        ? "(none)"
                        : CastleVariant.choose(legal, appScores, temperature, new Random(seed))
                                .toString();
        String played;
        if (legal.isEmpty()) {
            played = engineMove;
        } else if (topK > 0 && temperature > 0) {
            played =
                    CastleVariant.chooseTopK(
                                    legal, variantScores, topK, temperature, new Random(seed))
                            .toString();
        } else if (variant.isApp()) {
            played = engineMove;
        } else {
            played =
                    CastleVariant.choose(legal, variantScores, temperature, new Random(seed))
                            .toString();
        }
        return new Decision(played, engineMove, topLegal, top, appMove, collisions);
    }

    /** The raw policy logits for a position, as MaiaEngine's forward pass computes them. */
    synchronized float[] policy(List<int[]> history, int selfElo, int opponentElo)
            throws OrtException {
        return rawPolicy(history, selfElo, opponentElo);
    }

    private float[] rawPolicy(List<int[]> history, int selfElo, int opponentElo)
            throws OrtException {
        float[] tokens = MaiaEvalAccess.encode(history);
        float[] self = {selfElo};
        float[] opponent = {opponentElo};
        long[] shape = {1, MaiaEvalAccess.SQUARES, MaiaEvalAccess.TOKEN_SIZE};
        try (OnnxTensor input =
                        OnnxTensor.createTensor(environment, FloatBuffer.wrap(tokens), shape);
                OnnxTensor selfTensor = OnnxTensor.createTensor(environment, self);
                OnnxTensor opponentTensor = OnnxTensor.createTensor(environment, opponent)) {
            Map<String, OnnxTensor> inputs = new HashMap<>();
            inputs.put("tokens", input);
            inputs.put("self_elo", selfTensor);
            inputs.put("oppo_elo", opponentTensor);
            try (OrtSession.Result result = diagSession.run(inputs)) {
                return ((float[][]) result.get("policy").orElseThrow().getValue())[0];
            }
        }
    }

    private Object await() throws Exception {
        Object reply = replies.poll(timeoutMs, TimeUnit.MILLISECONDS);
        if (reply == null) {
            throw new IOException("Maia did not answer within " + timeoutMs + " ms");
        }
        if (reply instanceof Exception e) {
            throw e;
        }
        return reply;
    }

    @Override
    public void close() throws OrtException {
        engine.shutdown();
        diagSession.close();
    }
}
