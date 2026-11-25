/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.vision;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import de.schliweb.chessnut.core.game.InvalidPositionException;
import de.schliweb.chessnut.core.game.PhysicalPosition;
import de.schliweb.pegasus.core.chess.PieceColor;
import de.schliweb.pegasus.core.protocol.BoardState;
import de.schliweb.pegasus.core.protocol.PieceCodes;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

/**
 * Reads the position on a DGT Pegasus from a photo of it and the occupancy the board reports.
 *
 * <ol>
 *   <li>the playing area, from the frame of the board ({@link BoardFrameFinder});
 *   <li>one upright window per square ({@link PieceWindows});
 *   <li>the piece in every window, by a small network run with ONNX Runtime;
 *   <li>the occupancy and the usual pieces per side give the position ({@link PositionDecoder}).
 * </ol>
 *
 * <p>The network knows the pieces the Pegasus comes with. How it is trained and how well the whole
 * chain does is described in {@code training/README.md}; it is not always right, so the result is a
 * suggestion the user has to be able to correct, and {@link Result#uncertainSquares} says where to
 * look first.
 *
 * <p>Everything runs on the device. Recognising one photo takes a moment and must not run on the UI
 * thread.
 */
public final class BoardPhotoRecognizer implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger("BoardPhotoRecognizer");

    private static final int BATCH = 16;

    /**
     * Squares whose piece the network is less sure of than this are reported as uncertain.
     *
     * <p>The mark is a weak hint, not a check: measured with a window read as it is and with its
     * neighbours hidden, about half of the wrong squares of a single photo lie below 0.9 and four
     * in ten below 0.7, and nine in ten marked squares are right either way. With several photos
     * read together the averages are lower throughout; 0.9 then marked five squares of a board that
     * was right, 0.7 one or two, and both still held the wrong ones.
     */
    private static final double CERTAIN = 0.7;

    /**
     * What is left of a window to both sides of its middle when the neighbours are hidden, as a
     * share of its width (the window is 2.6 squares wide: about two thirds of a square).
     */
    private static final double KEPT_BESIDE = 0.26;

    private final OrtEnvironment environment;
    private final OrtSession session;

    /** What the network makes of one photo; several of one position can be read together. */
    public static final class Reading {
        /** 64 windows in the order of the photo, 13 class probabilities each. */
        final double[][] probabilities;

        /** The playing area in the photo: its four corners, clockwise from the top left. */
        public final double[][] corners;

        /** How the camera was held, judged from the playing area. */
        public final PhotoGuide guide;

        /** Milliseconds spent on finding the playing area, cutting the windows, the network. */
        public final long[] millis;

        Reading(double[][] probabilities, double[][] corners, PhotoGuide guide, long[] millis) {
            this.probabilities = probabilities;
            this.corners = corners;
            this.guide = guide;
            this.millis = millis;
        }
    }

    /** The position read from a photo. */
    public static final class Result {
        /** The piece placement as in a FEN, e.g. {@code rnbqkbnr/pppppppp/8/8/8/8/...}. */
        public final String placement;

        /** Occupied squares the user should check, such as {@code "c1"}, least certain first. */
        public final List<String> uncertainSquares;

        /**
         * The occupancy looks the same after turning the board by half, so only the colours of the
         * pieces told where White sits. If the result looks upside down, this is why.
         */
        public final boolean whiteSideUncertain;

        /** The playing area in the photo: its four corners, clockwise from the top left. */
        public final double[][] corners;

        /** How the camera was held, judged from the playing area. */
        public final PhotoGuide guide;

        /** Milliseconds spent on finding the playing area, cutting the windows, the network. */
        public final long[] millis;

        Result(
                String placement,
                List<String> uncertainSquares,
                boolean whiteSideUncertain,
                double[][] corners,
                PhotoGuide guide,
                long[] millis) {
            this.placement = placement;
            this.uncertainSquares = Collections.unmodifiableList(uncertainSquares);
            this.whiteSideUncertain = whiteSideUncertain;
            this.corners = corners;
            this.guide = guide;
            this.millis = millis;
        }
    }

    /**
     * @param model the piece recogniser as an ONNX file (input "input": windows of 224 x 224, RGB,
     *     0..1; output "logits": 13 classes)
     */
    public BoardPhotoRecognizer(byte[] model) throws OrtException {
        environment = OrtEnvironment.getEnvironment();
        try {
            // No telemetry: the app makes no network calls (see THIRD-PARTY-NOTICES.md).
            environment.setTelemetry(false);
        } catch (OrtException e) {
            // Best effort, as for Maia: not worth failing over.
        }
        session = environment.createSession(model, new OrtSession.SessionOptions());
    }

    /**
     * The position in the photo, or {@code null} if no board is found in it - the user should then
     * take the photo again, with the whole board in the picture.
     *
     * @param photo upright photo of the board, taken at an angle as a player sees it
     * @param occupied {@code occupied[rank][file]}: whether the board reports a piece on the
     *     square, rank 0 being the first rank and file 0 the a-file
     */
    public Result recognize(Photo photo, boolean[][] occupied) throws OrtException {
        Reading reading = read(photo);
        return reading == null ? null : recognize(Collections.singletonList(reading), occupied);
    }

    /**
     * What the network sees in a photo, or {@code null} if no board is found in it.
     *
     * <p>Every window is shown to the network twice, as it is and with what stands left and right
     * of the piece greyed out, and the two answers are averaged. The neighbours give a piece away
     * in the starting position the network was trained on (beside the knight in the corner stands a
     * rook) and mislead it elsewhere; in photos of played positions the average read as well or
     * better than either alone, in the starting position as well (see {@code
     * training/EVALUATION.md}).
     */
    public Reading read(Photo photo) throws OrtException {
        long start = System.nanoTime();
        BoardFrameFinder.Result area = BoardFrameFinder.find(photo);
        long found = System.nanoTime();
        if (area == null) {
            LOG.info("no board found, " + (found - start) / 1_000_000 + " ms");
            return null;
        }
        float[] windows = PieceWindows.cut(photo, area.corners);
        long cut = System.nanoTime();
        double[][] probabilities = classify(windows);
        hideNeighbours(windows);
        double[][] alone = classify(windows);
        for (int cell = 0; cell < 64; cell++) {
            for (int k = 0; k < probabilities[cell].length; k++) {
                probabilities[cell][k] = (probabilities[cell][k] + alone[cell][k]) / 2;
            }
        }
        long[] millis = {
            (found - start) / 1_000_000,
            (cut - found) / 1_000_000,
            (System.nanoTime() - cut) / 1_000_000
        };
        LOG.info(
                "playing area "
                        + millis[0]
                        + " ms, windows "
                        + millis[1]
                        + " ms, pieces "
                        + millis[2]
                        + " ms");
        return new Reading(
                probabilities,
                area.corners,
                PhotoGuide.of(area.corners, photo.width, photo.height),
                millis);
    }

    /**
     * The position from one or more photos of it. Each photo is turned to where White sits in it;
     * what the network holds likely for a square is then averaged over the photos, and only after
     * that the pieces are handed out. Photos from different sides help most: a piece hidden or seen
     * from behind in one is plain in another.
     *
     * @param readings at least one, of the same position
     * @param occupied {@code occupied[rank][file]}: whether the board reports a piece on the
     *     square, rank 0 being the first rank and file 0 the a-file
     * @return the position; corners, guide and times are those of the last photo
     */
    public Result recognize(List<Reading> readings, boolean[][] occupied) {
        double[][] together = new double[64][];
        boolean ambiguous = false;
        for (Reading reading : readings) {
            PositionDecoder.Orientation orientation =
                    PositionDecoder.whiteSide(reading.probabilities, occupied);
            ambiguous |= orientation.ambiguous;
            double[][] turned =
                    PositionDecoder.seenFromWhite(reading.probabilities, orientation.side);
            for (int cell = 0; cell < 64; cell++) {
                if (together[cell] == null) {
                    together[cell] = new double[turned[cell].length];
                }
                for (int k = 0; k < turned[cell].length; k++) {
                    together[cell][k] += turned[cell][k] / readings.size();
                }
            }
        }
        PositionDecoder.Position position =
                PositionDecoder.decode(together, PositionDecoder.WhiteSide.BOTTOM, occupied);

        List<int[]> uncertain = new ArrayList<>();
        for (int rank = 0; rank < 8; rank++) {
            for (int file = 0; file < 8; file++) {
                if (position.board[rank][file] != 0 && position.confidence[rank][file] < CERTAIN) {
                    uncertain.add(new int[] {rank, file});
                }
            }
        }
        uncertain.sort(
                (a, b) ->
                        Double.compare(
                                position.confidence[a[0]][a[1]], position.confidence[b[0]][b[1]]));
        List<String> names = new ArrayList<>();
        for (int[] square : uncertain) {
            names.add("" + (char) ('a' + square[1]) + (char) ('1' + square[0]));
        }
        Reading last = readings.get(readings.size() - 1);
        return new Result(position.fen(), names, ambiguous, last.corners, last.guide, last.millis);
    }

    /** Greys out, in all 64 windows, what stands left and right of the piece in the middle. */
    private static void hideNeighbours(float[] windows) {
        int size = PieceWindows.SIZE;
        int plane = size * size;
        int from = size / 2 - (int) (size * KEPT_BESIDE);
        int to = size / 2 + (int) (size * KEPT_BESIDE);
        for (int offset = 0; offset < windows.length; offset += plane) {
            double sum = 0;
            for (int i = 0; i < plane; i++) {
                sum += windows[offset + i];
            }
            float fill = (float) (sum / plane);
            for (int row = 0; row < size; row++) {
                for (int col = 0; col < size; col++) {
                    if (col < from || col >= to) {
                        windows[offset + row * size + col] = fill;
                    }
                }
            }
        }
    }

    /**
     * A FEN the game can load from a piece placement (as in {@link Result#placement}, possibly
     * corrected by the user) and the side to move. Castling rights are assumed wherever king and
     * rook stand on their starting squares, as for boards that identify the pieces themselves.
     *
     * @throws InvalidPositionException if the placement is not a playable position
     */
    public static String fen(String placement, boolean whiteToMove)
            throws InvalidPositionException {
        BoardState board = BoardState.empty();
        String[] rows = placement.split("/");
        if (rows.length != 8) {
            throw new InvalidPositionException(InvalidPositionException.Reason.UNPLAYABLE);
        }
        for (int i = 0; i < 8; i++) {
            int file = 0;
            for (char c : rows[i].toCharArray()) {
                if (Character.isDigit(c)) {
                    file += c - '0';
                    continue;
                }
                int code = pieceCode(c);
                if (code == PieceCodes.EMPTY || file > 7) {
                    throw new InvalidPositionException(InvalidPositionException.Reason.UNPLAYABLE);
                }
                String square = "" + (char) ('a' + file) + (char) ('8' - i);
                board = board.withSquare(BoardState.squareIndex(square), code);
                file++;
            }
        }
        return PhysicalPosition.fenOf(board, whiteToMove ? PieceColor.WHITE : PieceColor.BLACK);
    }

    private static int pieceCode(char symbol) {
        for (int code = 0; code <= PieceCodes.BQUEEN; code++) {
            if (code != PieceCodes.EMPTY && PieceCodes.toChar(code) == symbol) {
                return code;
            }
        }
        return PieceCodes.EMPTY;
    }

    /**
     * Which squares look occupied in the photo, without help from the board: {@code [row][col]} as
     * seen in the photo, the top left square first. {@code null} if no board is found.
     *
     * <p>Not exact - a square or two can be wrong - but enough to notice that a board shows a quite
     * different position from the one expected.
     */
    public boolean[][] seenOccupied(Photo photo) throws OrtException {
        BoardFrameFinder.Result area = BoardFrameFinder.find(photo);
        if (area == null) {
            return null;
        }
        double[][] probabilities = classify(PieceWindows.cut(photo, area.corners));
        boolean[][] out = new boolean[8][8];
        for (int cell = 0; cell < 64; cell++) {
            int best = 0;
            for (int k = 1; k < probabilities[cell].length; k++) {
                if (probabilities[cell][k] > probabilities[cell][best]) {
                    best = k;
                }
            }
            out[cell / 8][cell % 8] = best != 0;
        }
        return out;
    }

    /** Class probabilities for the 64 windows. */
    private double[][] classify(float[] windows) throws OrtException {
        int perWindow = 3 * PieceWindows.SIZE * PieceWindows.SIZE;
        double[][] out = new double[64][];
        for (int start = 0; start < 64; start += BATCH) {
            long[] shape = {BATCH, 3, PieceWindows.SIZE, PieceWindows.SIZE};
            FloatBuffer buffer = FloatBuffer.wrap(windows, start * perWindow, BATCH * perWindow);
            try (OnnxTensor input = OnnxTensor.createTensor(environment, buffer, shape);
                    OrtSession.Result result =
                            session.run(Collections.singletonMap("input", input))) {
                float[][] logits = (float[][]) result.get("logits").get().getValue();
                for (int i = 0; i < BATCH; i++) {
                    out[start + i] = softmax(logits[i]);
                }
            }
        }
        return out;
    }

    private static double[] softmax(float[] logits) {
        double max = -Double.MAX_VALUE;
        for (float v : logits) {
            max = Math.max(max, v);
        }
        double[] out = new double[logits.length];
        double total = 0;
        for (int i = 0; i < logits.length; i++) {
            out[i] = Math.exp(logits[i] - max);
            total += out[i];
        }
        for (int i = 0; i < out.length; i++) {
            out[i] /= total;
        }
        return out;
    }

    @Override
    public void close() throws OrtException {
        session.close();
    }
}
