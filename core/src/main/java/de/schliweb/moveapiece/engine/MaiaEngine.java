/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.engine;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.CastleRight;
import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.move.Move;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Runs a Maia network (original CSSLab/maia-chess weights, converted to ONNX - see
 * MAIA_PROVENANCE.md) via ONNX Runtime for a single "what would a human of this network's rating
 * play here" forward pass, no search involved.
 *
 * <p>Unlike {@link StockfishEngine}, there is no subprocess and no UCI protocol: inference runs
 * in-process on a dedicated background executor (a forward pass is cheap for this network's size -
 * see the provenance doc - but must still not block the caller's thread), and callbacks go through
 * {@link MaiaEngineListener} via the supplied {@code mainThreadDispatcher}, same convention as
 * {@link StockfishEngine}.
 *
 * <p><b>Verified</b> (see {@code MaiaEngineSmokeTest} and {@code MaiaEngineGoldenTest}): the
 * starting position, three-ply history with Black to move, castling rights that have actually
 * changed through play, and a position reached the second time (repetition plane) all round-trip
 * end to end via ONNX Runtime and match lc0's own native output exactly - not just the winning
 * move, but (for the starting position) closely the logit gap to the runner-up too. See
 * MAIA_PROVENANCE.md for the exact reference numbers each test asserts against.
 *
 * <p>Two real bugs were caught by these tests during development, both fixed before the tests were
 * considered passing: this class's history padding originally repeated the current position for
 * missing history steps, which disagreed with lc0's own {@code encoder.cc} (it leaves missing
 * pre-game history as all-zero instead, once the available history bottoms out at the standard
 * starting position - see {@link MaiaPositionEncoder}'s Javadoc); and the initial per-history-step
 * alternating-mirror design question (also documented there) turned out to already be handled
 * correctly by this class's simpler constant-mirror approach once actually tested against a real
 * multi-ply, Black-to-move position rather than left as a reasoned guess.
 *
 * <p>All 9 rating levels (1100-1900) are downloaded, converted and covered by {@code
 * MaiaEngineGoldenTest}; the queen-promotion case is in the golden test too. <b>Deliberately not
 * covered</b> by a live ONNX comparison (see MAIA_PROVENANCE.md): en passant, which has no input
 * plane of its own in the classical 112-plane format, so a golden test would exercise no additional
 * code path; and underpromotion, which is only unit-tested at the move-string level.
 */
public class MaiaEngine {

    private static final String START_FEN =
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
    private static final String POLICY_OUTPUT_NAME = "/output/policy";
    private static final String WDL_OUTPUT_NAME = "/output/wdl";
    private static final String PLANES_INPUT_NAME = "/input/planes";

    private final Consumer<Runnable> mainThreadDispatcher;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final Random random;

    private OrtEnvironment environment;
    private OrtSession session;
    private volatile MaiaEngineListener listener;
    private volatile boolean shuttingDown;

    // Replay state, touched only on ioExecutor - same single-thread-confinement discipline as
    // StockfishEngine's process I/O.
    private final Board board = new Board();
    private final Deque<MaiaPositionEncoder.Snapshot> history = new ArrayDeque<>();
    private final Map<String, Integer> repetitionCounts = new HashMap<>();

    public MaiaEngine(Consumer<Runnable> mainThreadDispatcher) {
        this(mainThreadDispatcher, new Random());
    }

    /**
     * @param random used only for temperature-based sampling in {@link #go(double)} - exposed for
     *     tests.
     */
    MaiaEngine(Consumer<Runnable> mainThreadDispatcher, Random random) {
        this.mainThreadDispatcher = mainThreadDispatcher;
        this.random = random;
        board.loadFromFen(START_FEN);
    }

    public void setListener(MaiaEngineListener listener) {
        this.listener = listener;
    }

    /**
     * Loads the ONNX model and starts the inference session. Call once, off the main thread.
     *
     * <p>Reads {@code modelStream} fully before returning - unlike the rest of this class, this
     * part is synchronous, so callers can safely close the stream (e.g. via try-with-resources)
     * immediately after this call returns, the same convention {@link NnueAssets} uses. Only the
     * actual session creation, which is the potentially slow part, happens on the background
     * executor.
     */
    public void start(InputStream modelStream) throws IOException {
        byte[] modelBytes = readAll(modelStream);
        ioExecutor.execute(
                () -> {
                    try {
                        environment = OrtEnvironment.getEnvironment();
                        // ONNX Runtime's native core bundles Microsoft's cross-platform "1DS"
                        // telemetry system (see THIRD-PARTY-NOTICES.md) - on by default, so it's
                        // turned off explicitly rather than relying on the app never opting in.
                        // Confirmed via OrtEnvironment#setTelemetry's OrtException signature
                        // that this call can itself fail; letting Maia continue to work even
                        // then is more important than telemetry actually being off, so a failure
                        // here is swallowed rather than treated like a fatal engine-start error.
                        try {
                            environment.setTelemetry(false);
                        } catch (OrtException ignored) {
                            // Best-effort; native telemetry staying on is not worth failing
                            // Maia's startup over.
                        }
                        session =
                                environment.createSession(
                                        modelBytes, new OrtSession.SessionOptions());
                        resetReplayState();
                        post(MaiaEngineListener::onReady);
                    } catch (Exception e) {
                        notifyError(e);
                    } catch (LinkageError e) {
                        // OrtEnvironment.getEnvironment() is where ONNX Runtime extracts and
                        // loads its native library, and that fails with an
                        // UnsatisfiedLinkError / ExceptionInInitializerError - an Error, not an
                        // Exception. Left uncaught it silently kills this executor thread: the
                        // listener never sees onReady *or* onEngineError, so the UI (and any
                        // test) just waits forever. Surface it as a reported startup failure
                        // instead.
                        notifyError(nativeLoadFailure(e));
                    }
                });
    }

    /**
     * @param movesUci space-separated UCI moves from the start position, may be empty or null
     */
    public void setPosition(String movesUci) {
        setPosition(null, movesUci);
    }

    /**
     * @param startFen position the moves start from, {@code null} for the standard start. The
     *     network's history planes then begin at that position, exactly as lc0 handles a game set
     *     up from a FEN (earlier history steps stay zero, see {@link MaiaPositionEncoder}).
     * @param movesUci space-separated UCI moves from {@code startFen}, may be empty or null
     */
    public void setPosition(String startFen, String movesUci) {
        ioExecutor.execute(
                () -> {
                    resetReplayState(startFen == null || startFen.isEmpty() ? START_FEN : startFen);
                    if (movesUci == null || movesUci.trim().isEmpty()) {
                        return;
                    }
                    for (String uci : movesUci.trim().split("\\s+")) {
                        Move move = new Move(uci, board.getSideToMove());
                        if (!board.doMove(move, true)) {
                            notifyError(
                                    new IllegalStateException("Illegal move in position: " + uci));
                            return;
                        }
                        recordSnapshot();
                    }
                });
    }

    /** Runs one forward pass and reports the highest-scoring legal move (no sampling). */
    public void go() {
        go(0.0);
    }

    /**
     * Runs one forward pass and reports a move sampled from the legal-move policy distribution.
     *
     * @param temperature 0.0 picks the single highest-scoring legal move (deterministic); 1.0 uses
     *     the network's own logits unscaled. lc0 reports {@code PolicyTemperature: 1.359} as this
     *     network's own default (see MAIA_PROVENANCE.md) if a more human-like spread of choices is
     *     wanted instead of the strongest-by-policy move every time.
     */
    public void go(double temperature) {
        ioExecutor.execute(() -> runInference(temperature));
    }

    public synchronized void shutdown() {
        if (shuttingDown) {
            return;
        }
        shuttingDown = true;
        ioExecutor.execute(
                () -> {
                    if (session != null) {
                        try {
                            session.close();
                        } catch (OrtException ignored) {
                            // Nothing to recover - the session is going away either way.
                        }
                    }
                });
        ioExecutor.shutdown();
    }

    private void resetReplayState() {
        resetReplayState(START_FEN);
    }

    private void resetReplayState(String fen) {
        board.loadFromFen(fen);
        history.clear();
        repetitionCounts.clear();
        recordSnapshot();
    }

    private void recordSnapshot() {
        long[] bitboards = new long[Piece.values().length];
        for (Piece p : Piece.values()) {
            if (p == Piece.NONE) {
                continue;
            }
            bitboards[p.ordinal()] = board.getBitboard(p);
        }
        int repetitions = repetitionCounts.merge(repetitionKey(), 1, Integer::sum) - 1;
        history.push(new MaiaPositionEncoder.Snapshot(bitboards, repetitions));
    }

    /**
     * Piece placement + side to move + castling + en-passant only (chesslib's FEN also has the
     * half-move/full-move counters, which must not participate in repetition comparison).
     */
    private String repetitionKey() {
        String[] fields = board.getFen().split(" ");
        return fields[0] + ' ' + fields[1] + ' ' + fields[2] + ' ' + fields[3];
    }

    private void runInference(double temperature) {
        try {
            boolean blackToMove = board.getSideToMove() == Side.BLACK;
            CastleRight ourRights = board.getCastleRight(board.getSideToMove());
            CastleRight theirRights = board.getCastleRight(board.getSideToMove().flip());

            float[] tensor =
                    MaiaPositionEncoder.encode(
                            new ArrayList<>(history),
                            blackToMove,
                            board.getHalfMoveCounter(),
                            canCastleQueenside(ourRights),
                            canCastleKingside(ourRights),
                            canCastleQueenside(theirRights),
                            canCastleKingside(theirRights));

            List<Move> legalMoves = board.legalMoves();
            if (legalMoves.isEmpty()) {
                post(l -> l.onBestMove(null, 0f, 0f, 0f));
                return;
            }

            try (OnnxTensor input =
                    OnnxTensor.createTensor(
                            environment, FloatBuffer.wrap(tensor), new long[] {1, 112, 8, 8})) {
                Map<String, OnnxTensor> inputs = Collections.singletonMap(PLANES_INPUT_NAME, input);
                try (OrtSession.Result result = session.run(inputs)) {
                    float[] policy = extractRow(result, POLICY_OUTPUT_NAME);
                    float[] wdl = extractRow(result, WDL_OUTPUT_NAME);

                    Move chosen = chooseMove(legalMoves, policy, temperature);
                    String bestMoveUci = chosen == null ? null : chosen.toString();
                    post(l -> l.onBestMove(bestMoveUci, wdl[0], wdl[1], wdl[2]));
                }
            }
        } catch (Exception e) {
            notifyError(e);
        } catch (LinkageError e) {
            // Same reasoning as in start(): lazily-initialized ONNX Runtime classes can still
            // fail to link here, and an Error must not silently swallow the reply.
            notifyError(nativeLoadFailure(e));
        }
    }

    private Move chooseMove(List<Move> legalMoves, float[] policy, double temperature) {
        List<Move> candidates = new ArrayList<>(legalMoves.size());
        List<Float> logits = new ArrayList<>(legalMoves.size());
        for (Move move : legalMoves) {
            String networkMove = MaiaMoveIndexer.toNetworkMove(board, move);
            int idx = MaiaPolicyIndex.indexOf(networkMove);
            if (idx < 0) {
                // Should not happen for a genuinely legal move once this encoder/indexer pair is
                // fully verified - see the class-level "Not yet done" note. Skipping rather than
                // failing the whole move keeps one indexing bug from making the engine unplayable.
                continue;
            }
            candidates.add(move);
            logits.add(policy[idx]);
        }
        if (candidates.isEmpty()) {
            return legalMoves.get(0);
        }
        if (temperature <= 0.0) {
            int bestAt = 0;
            for (int i = 1; i < logits.size(); i++) {
                if (logits.get(i) > logits.get(bestAt)) {
                    bestAt = i;
                }
            }
            return candidates.get(bestAt);
        }
        return sample(candidates, logits, temperature);
    }

    private Move sample(List<Move> candidates, List<Float> logits, double temperature) {
        double max = Collections.max(logits);
        double[] weights = new double[logits.size()];
        double sum = 0;
        for (int i = 0; i < logits.size(); i++) {
            weights[i] = Math.exp((logits.get(i) - max) / temperature);
            sum += weights[i];
        }
        double pick = random.nextDouble() * sum;
        double acc = 0;
        for (int i = 0; i < weights.length; i++) {
            acc += weights[i];
            if (pick <= acc) {
                return candidates.get(i);
            }
        }
        return candidates.get(candidates.size() - 1);
    }

    private static float[] extractRow(OrtSession.Result result, String outputName)
            throws OrtException {
        Optional<OnnxValue> value = result.get(outputName);
        if (!value.isPresent()) {
            throw new IllegalStateException("Model has no output named " + outputName);
        }
        float[][] batch = (float[][]) value.get().getValue();
        return batch[0];
    }

    private static boolean canCastleQueenside(CastleRight right) {
        return right == CastleRight.QUEEN_SIDE || right == CastleRight.KING_AND_QUEEN_SIDE;
    }

    private static boolean canCastleKingside(CastleRight right) {
        return right == CastleRight.KING_SIDE || right == CastleRight.KING_AND_QUEEN_SIDE;
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[64 * 1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private void notifyError(Exception e) {
        post(l -> l.onEngineError(e));
    }

    private static IllegalStateException nativeLoadFailure(LinkageError e) {
        return new IllegalStateException("ONNX Runtime native library failed to load: " + e, e);
    }

    private interface ListenerAction {
        void run(MaiaEngineListener listener);
    }

    private void post(ListenerAction action) {
        MaiaEngineListener l = listener;
        if (l == null) {
            return;
        }
        mainThreadDispatcher.accept(() -> action.run(l));
    }
}
