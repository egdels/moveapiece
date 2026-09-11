/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.desktop.pegasus;

import de.schliweb.pegasus.core.chess.ChessPosition;
import de.schliweb.pegasus.core.chess.Move;
import de.schliweb.pegasus.core.chess.OccupancyProjection;
import de.schliweb.pegasus.core.chess.Piece;
import de.schliweb.pegasus.core.chess.PieceColor;
import de.schliweb.pegasus.core.chess.PieceType;
import de.schliweb.pegasus.core.movedetect.BoardSyncGuide;
import de.schliweb.pegasus.core.movedetect.MoveDetectionResult;
import de.schliweb.pegasus.core.movedetect.MoveDetectionState;
import de.schliweb.pegasus.core.movedetect.MoveDetector;
import de.schliweb.pegasus.core.protocol.BatteryStatus;
import de.schliweb.pegasus.core.protocol.BoardState;
import de.schliweb.pegasus.core.protocol.PegasusCommands;
import de.schliweb.pegasus.core.protocol.PegasusFrame;
import de.schliweb.pegasus.core.protocol.PegasusFrameParser;
import de.schliweb.pegasus.core.protocol.PegasusLedController;
import de.schliweb.pegasus.core.protocol.PegasusMessageType;
import de.schliweb.pegasus.core.record.SessionRecorder;
import de.schliweb.pegasus.core.transport.ConnectionState;
import de.schliweb.pegasus.core.transport.PegasusTransport;
import de.schliweb.pegasus.core.transport.ScanListener;
import de.schliweb.pegasus.core.transport.TransportError;
import de.schliweb.pegasus.core.transport.TransportListener;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;

/**
 * Bridges a physical DGT Pegasus board (via {@link PegasusTransport}) to MoveAPiece's own game
 * state, for the desktop app. A direct port of the Android app's {@code
 * de.schliweb.moveapiece.pegasus.PegasusGameBridge}: identical logic (connect/init/
 * move-detection/LED-guidance sequencing, keepalive, board-mismatch handling), only the main-thread
 * dispatch mechanism differs - {@link Platform#runLater} instead of an {@code
 * android.os.Handler(Looper.getMainLooper())}, and {@link #postDelayed}/{@link #cancel} (backed by
 * a single-thread {@link ScheduledExecutorService} that hops back onto the JavaFX Application
 * Thread) instead of {@code Handler.postDelayed}/{@code removeCallbacks}.
 *
 * <p>Keeps its own, independent {@link ChessPosition} in sync with MoveAPiece's chesslib-based
 * {@code ChessGame} purely by replaying the same UCI move strings, for the same reason as the
 * Android version: {@link MoveDetector} hard-references pegasus' own chess classes with no
 * interface seam.
 *
 * <p>Runs entirely on the JavaFX Application Thread; all transport callbacks are marshalled onto it
 * via {@link Platform#runLater}, matching {@code GameController}'s own threading model (see its
 * {@code StockfishEngine} construction, which uses {@code Platform::runLater} the same way).
 */
public class DesktopPegasusGameBridge {

    private static final Logger LOG = Logger.getLogger(DesktopPegasusGameBridge.class.getName());

    /** Physical-board events relevant to MoveAPiece's game flow. */
    public interface Listener {
        void onConnectionStateChanged(ConnectionState state);

        void onPhysicalMoveConfirmed(String uci);

        /** True while the physical board disagrees with the logical position. */
        void onBoardMismatch(boolean mismatched);

        /**
         * A physical pawn reached the back rank; the board can never tell which piece was intended
         * (occupancy-only), so the UI must ask and report back via {@link #selectPromotion}.
         */
        void onPromotionRequired();

        /**
         * Physical occupancy matches several legal moves that aren't a pure promotion choice.
         * Candidates are given as UCI strings; resolve via {@link #selectCandidate}.
         */
        void onAmbiguousMove(List<String> candidateUcis);

        void onEngineMoveGuidanceComplete();

        void onTransportError(TransportError error, String detail);

        /**
         * Reported once per connect (from the init sequence's battery request), and again on any
         * later transition into a critically low battery. Real Pegasus hardware also pushes a
         * fresh reading spontaneously whenever the percentage changes by 1% (CONFIRMED_ON_HARDWARE
         * 2026-09-11) - those routine drift updates are intentionally not forwarded here (would
         * mean a UI notification every few minutes for the whole session); see {@code
         * batteryReportPending} in the implementation for the exact gating. {@code criticallyLow}
         * mirrors {@link BatteryStatus#isCriticallyLow()}: per DGT's protocol document, the board
         * shuts itself down within about 3 minutes once this is true.
         */
        void onBatteryStatus(int percent, boolean criticallyLow);
    }

    private static final long INIT_COMMAND_SPACING_MS = 1500;
    private static final long KEEPALIVE_POLL_INTERVAL_MS = 2000;
    private static final byte[] KEEPALIVE_POLL_COMMAND = {0x45};
    private static final long CHECK_INDICATOR_REFRESH_MS = 900;

    private final PegasusTransport transport;
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(
                    r -> {
                        Thread t = new Thread(r, "pegasus-bridge-timer");
                        t.setDaemon(true);
                        return t;
                    });
    private final PegasusFrameParser frameParser = new PegasusFrameParser();
    private final PegasusLedController ledController;
    private final MoveDetector moveDetector = new MoveDetector(ChessPosition.starting(), null);

    private final BoardSyncGuide syncGuide =
            new BoardSyncGuide(
                    new BoardSyncGuide.Listener() {
                        @Override
                        public void onIndicate(List<Integer> squares) {
                            ledController.showSquares(
                                    shouldRevealGuideLeds(squares)
                                            ? squares
                                            : Collections.emptyList());
                        }

                        @Override
                        public void onTargetReached() {
                            onGuideTargetReached();
                        }
                    });

    private BoardState physicalBoard;

    /**
     * Whether the first battery reading of the current connection is still unreported to {@link
     * Listener#onBatteryStatus} - true right after (re)connect. Real Pegasus hardware pushes a
     * fresh battery status spontaneously every time the percentage changes by 1% (CONFIRMED_ON_HARDWARE
     * 2026-09-11, minutes apart, no re-request needed), not just once per connect as originally
     * assumed; reporting every one of those to the UI would mean a toast every few minutes for the
     * whole session, so only the first reading and later transitions into {@link
     * BatteryStatus#isCriticallyLow()} are forwarded - see {@link #lastBatteryCritical}.
     */
    private boolean batteryReportPending;

    /** Last {@link BatteryStatus#isCriticallyLow()} seen this connection; see {@link #batteryReportPending}. */
    private boolean lastBatteryCritical;
    private boolean guideShowLed = true;
    private int guideExpectedFrom = -1;
    private int guideExpectedTo = -1;
    private String guideMoveUci;
    private ChessPosition guideTargetPosition;
    private Integer guideCaptureSquare;
    private volatile Listener listener;
    private final java.util.Set<Integer> squaresSeenEmpty = new java.util.HashSet<>();
    private SessionRecorder sessionRecorder;
    private ScheduledFuture<?> keepaliveFuture;
    private ScheduledFuture<?> checkIndicatorFuture;

    public DesktopPegasusGameBridge(PegasusTransport transport, Listener listener) {
        this.transport = transport;
        this.listener = listener;
        this.ledController =
                new PegasusLedController(
                        command -> {
                            if (transport.getConnectionState() == ConnectionState.CONNECTED) {
                                transport.write(command);
                            }
                        });
        transport.setListener(
                new TransportListener() {
                    @Override
                    public void onConnectionStateChanged(ConnectionState state) {
                        Platform.runLater(
                                () -> {
                                    if (state == ConnectionState.CONNECTED) {
                                        // Reconnect-safe: drop transient parse/move state and
                                        // re-sync via the board dump in the init sequence,
                                        // but keep the current logical position (an ongoing
                                        // game must survive a reconnect).
                                        frameParser.reset();
                                        physicalBoard = null;
                                        abortGuide();
                                        ledController.resetTracking();
                                        squaresSeenEmpty.clear();
                                        moveDetector.reset(moveDetector.position(), null);
                                        batteryReportPending = true;
                                        lastBatteryCritical = false;
                                        cancel(keepaliveFuture);
                                        sendOfficialInitSequence();
                                    }
                                    Listener l = listener();
                                    if (l != null) {
                                        l.onConnectionStateChanged(state);
                                    }
                                });
                    }

                    @Override
                    public void onDataReceived(String characteristicUuid, byte[] data) {
                        byte[] copy = data.clone();
                        recordIfActive(SessionRecorder.Direction.RX, characteristicUuid, copy);
                        Platform.runLater(() -> onProtocolData(copy));
                    }

                    @Override
                    public void onDataSent(String characteristicUuid, byte[] data) {
                        // No UI action needed; MoveAPiece does not display raw TX traffic.
                        recordIfActive(SessionRecorder.Direction.TX, characteristicUuid, data);
                    }

                    @Override
                    public void onError(TransportError error, String detail) {
                        Platform.runLater(
                                () -> {
                                    Listener l = listener();
                                    if (l != null) {
                                        l.onTransportError(error, detail);
                                    }
                                });
                    }
                });
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    private Listener listener() {
        return listener;
    }

    public void startScan(ScanListener scanListener, long timeoutMs) {
        transport.startScan(scanListener, timeoutMs);
    }

    public void stopScan() {
        transport.stopScan();
    }

    public void connect(String deviceAddress) {
        transport.connect(deviceAddress);
    }

    public void disconnect() {
        transport.disconnect();
    }

    public void shutdown() {
        cancel(keepaliveFuture);
        cancel(checkIndicatorFuture);
        scheduler.shutdownNow();
        transport.disconnect();
        stopRecording();
    }

    /**
     * Starts recording raw BLE traffic (RX/TX) as NDJSON to {@code file} for hardware-verification
     * sessions. Replaces any recording already in progress.
     */
    public void startRecording(File file) throws IOException {
        stopRecording();
        sessionRecorder = new SessionRecorder(new FileWriter(file, false));
    }

    /** Stops and flushes the current recording, if any. Safe to call repeatedly. */
    public void stopRecording() {
        SessionRecorder recorder = sessionRecorder;
        sessionRecorder = null;
        if (recorder != null) {
            try {
                recorder.close();
            } catch (IOException e) {
                LOG.log(Level.WARNING, "stopRecording: close failed", e);
            }
        }
    }

    private void recordIfActive(
            SessionRecorder.Direction dir, String characteristicUuid, byte[] data) {
        SessionRecorder recorder = sessionRecorder;
        if (recorder == null) {
            return;
        }
        try {
            recorder.record(dir, characteristicUuid, data);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "recording: write failed, stopping", e);
            stopRecording();
        }
    }

    public ConnectionState getConnectionState() {
        return transport.getConnectionState();
    }

    /** Schedules {@code action} to run on the JavaFX Application Thread after {@code delayMs}. */
    private ScheduledFuture<?> postDelayed(Runnable action, long delayMs) {
        return scheduler.schedule(() -> Platform.runLater(action), delayMs, TimeUnit.MILLISECONDS);
    }

    private static void cancel(ScheduledFuture<?> future) {
        if (future != null) {
            future.cancel(false);
        }
    }

    /**
     * Replays the exact init sequence captured from the official DGT app on real hardware: reset,
     * dev key, three unlabeled bytes, board-state request, and update mode - spaced 1.5s apart.
     * Denser spacing made the board stop responding to the whole burst on real hardware.
     *
     * <p>The dev-key-state query (0x5A, added after the manufacturer shared their protocol
     * document — DGT Chessboard Communication Protocol v1.2.1) is not part of that captured
     * burst; it is a read-only status request, so inserting it does not change what the official
     * app itself writes to the board. Its response is handled in {@link #onProtocolData}.
     */
    private void sendOfficialInitSequence() {
        byte[][] seq = {
            PegasusCommands.encodeReset(),
            PegasusCommands.encodeDevKey(),
            PegasusCommands.encodeDevKeyStateRequest(),
            {0x55},
            {0x47},
            PegasusCommands.encodeBoardStateRequest(),
            {0x4C},
            PegasusCommands.encodeUpdateMode(),
        };
        for (int i = 0; i < seq.length; i++) {
            byte[] cmd = seq[i];
            postDelayed(() -> transport.write(cmd), INIT_COMMAND_SPACING_MS * i);
        }
        keepaliveFuture =
                postDelayed(this::sendKeepalivePoll, INIT_COMMAND_SPACING_MS * seq.length);
    }

    /**
     * Self-rescheduling keepalive: stops on its own once the connection is no longer CONNECTED
     * (e.g. after disconnect()/shutdown()), matching the reference implementation's design.
     */
    private void sendKeepalivePoll() {
        if (transport.getConnectionState() != ConnectionState.CONNECTED) {
            LOG.log(Level.FINE, "keepalive: skipped, state={0}", transport.getConnectionState());
            return;
        }
        LOG.log(Level.FINE, "keepalive: writing 0x45");
        transport.write(KEEPALIVE_POLL_COMMAND);
        keepaliveFuture = postDelayed(this::sendKeepalivePoll, KEEPALIVE_POLL_INTERVAL_MS);
    }

    private void onProtocolData(byte[] data) {
        for (PegasusFrame frame : frameParser.feed(data)) {
            switch (frame.type()) {
                case PegasusMessageType.BOARD_DUMP:
                    physicalBoard = BoardState.fromBoardDumpPayload(frame.payload());
                    LOG.log(
                            Level.INFO,
                            "board dump: {0}",
                            OccupancyProjection.normalize(physicalBoard));
                    feedDetector();
                    break;
                case PegasusMessageType.FIELD_UPDATE:
                    byte[] payload = frame.payload();
                    if (payload.length == 2 && physicalBoard != null) {
                        int square = payload[0] & 0xFF;
                        int code = payload[1] & 0xFF;
                        if (square < BoardState.SQUARE_COUNT) {
                            if (code == 0) {
                                squaresSeenEmpty.add(square);
                            }
                            physicalBoard = physicalBoard.withSquare(square, code);
                            feedDetector();
                        }
                    }
                    break;
                case PegasusMessageType.BATTERY_STATUS:
                    if (frame.payloadLength() == BatteryStatus.PAYLOAD_LENGTH) {
                        BatteryStatus status = BatteryStatus.fromPayload(frame.payload());
                        LOG.log(Level.INFO, "battery: {0}", status);
                        // Every 1%-change push is logged above for diagnostics, but only the
                        // first reading of the connection and a fresh transition into
                        // isCriticallyLow() reach the UI - see batteryReportPending's javadoc.
                        boolean newlyCritical = status.isCriticallyLow() && !lastBatteryCritical;
                        boolean shouldNotify = batteryReportPending || newlyCritical;
                        batteryReportPending = false;
                        lastBatteryCritical = status.isCriticallyLow();
                        if (shouldNotify) {
                            Listener l = listener();
                            if (l != null) {
                                l.onBatteryStatus(status.percent(), status.isCriticallyLow());
                            }
                        }
                    }
                    break;
                case PegasusMessageType.DEVKEY_STATE:
                    onDevKeyStateFrame(frame);
                    break;
                default:
                    break;
            }
        }
    }

    /**
     * Handles the response to {@link PegasusCommands#encodeDevKeyStateRequest()}: without a
     * developer key accepted by the board, all subsequent board dumps/field updates are withheld
     * (see {@link PegasusCommands#encodeDevKey()}), which otherwise looks like a silently stuck
     * connection rather than a rejected key.
     */
    private void onDevKeyStateFrame(PegasusFrame frame) {
        byte[] payload = frame.payload();
        if (payload.length != 1) {
            return;
        }
        boolean accepted = payload[0] == 1;
        LOG.log(Level.INFO, "dev key state: {0}", accepted ? "accepted" : "rejected");
        if (!accepted) {
            Listener l = listener();
            if (l != null) {
                l.onTransportError(TransportError.DEVKEY_REJECTED, null);
            }
        }
    }

    private void feedDetector() {
        if (syncGuide.isActive()) {
            if (isGuidedCaptureUnproven()) {
                List<Integer> captureSquareOnly = Collections.singletonList(guideCaptureSquare);
                ledController.showSquares(
                        shouldRevealGuideLeds(captureSquareOnly)
                                ? captureSquareOnly
                                : Collections.emptyList());
            } else {
                syncGuide.onPhysicalBoard(physicalBoard);
            }
            ledController.resend();
            return;
        }
        List<Move> priorPending = moveDetector.pendingCandidates();
        MoveDetectionResult result = moveDetector.onPhysicalBoard(physicalBoard);
        if (result.kind() == MoveDetectionResult.Kind.BOARD_MISMATCH
                && priorPending.size() == 1
                && moveDetector.wouldResolveIfCommitted(priorPending.get(0), physicalBoard)) {
            dispatchDetectionResult(moveDetector.commitRecoveredCapture(priorPending.get(0)));
            dispatchDetectionResult(moveDetector.onPhysicalBoard(physicalBoard));
            ledController.resend();
            return;
        }
        dispatchDetectionResult(result);
        ledController.resend();
    }

    private boolean isGuidedCaptureUnproven() {
        return guideCaptureSquare != null
                && !squaresSeenEmpty.contains(guideCaptureSquare)
                && physicalBoard != null
                && OccupancyProjection.normalize(physicalBoard)
                        .equals(OccupancyProjection.occupancyOf(guideTargetPosition));
    }

    private void dispatchDetectionResult(MoveDetectionResult result) {
        LOG.log(Level.INFO, "detection result: {0}", result.kind());
        if (result.kind() == MoveDetectionResult.Kind.CONFIRMED) {
            squaresSeenEmpty.clear();
            Listener l = listener();
            if (l != null) {
                l.onPhysicalMoveConfirmed(result.move().uci());
            }
        } else if (result.kind() == MoveDetectionResult.Kind.IN_PROGRESS
                && !moveDetector.pendingCandidates().isEmpty()) {
            List<Move> pending = moveDetector.pendingCandidates();
            List<Move> proven = new ArrayList<>();
            for (Move candidate : pending) {
                if (squaresSeenEmpty.contains(candidate.to())) {
                    proven.add(candidate);
                }
            }
            if (!proven.isEmpty() && shareDestination(proven)) {
                LOG.log(
                        Level.INFO,
                        "capture destination {0} was seen vacated - confirming immediately",
                        proven.get(0).to());
                dispatchDetectionResult(moveDetector.resolvePendingSubset(proven));
                return;
            }
            LOG.log(Level.INFO, "in-progress: pending candidate(s) {0}", pending);
            return;
        } else if (result.kind() == MoveDetectionResult.Kind.PROMOTION_REQUIRED) {
            Listener l = listener();
            if (l != null) {
                l.onPromotionRequired();
            }
            return;
        } else if (result.kind() == MoveDetectionResult.Kind.AMBIGUOUS) {
            List<String> candidateUcis = new ArrayList<>(result.candidates().size());
            for (Move candidate : result.candidates()) {
                candidateUcis.add(candidate.uci());
            }
            Listener l = listener();
            if (l != null) {
                l.onAmbiguousMove(candidateUcis);
            }
            return;
        }
        updateMismatchLeds(result);
    }

    private static boolean shareDestination(List<Move> candidates) {
        if (candidates.isEmpty()) {
            return false;
        }
        int to = candidates.get(0).to();
        for (Move candidate : candidates) {
            if (candidate.to() != to) {
                return false;
            }
        }
        return true;
    }

    private void updateMismatchLeds(MoveDetectionResult result) {
        Listener l = listener();
        if (result.kind() == MoveDetectionResult.Kind.BOARD_MISMATCH && result.mismatch() != null) {
            List<Integer> squares = new ArrayList<>(result.mismatch().missingOccupied());
            squares.addAll(result.mismatch().unexpectedOccupied());
            LOG.log(
                    Level.INFO,
                    "mismatch: lighting squares {0} (connectionState={1})",
                    new Object[] {squares, transport.getConnectionState()});
            ledController.showSquares(squares);
            if (l != null) {
                l.onBoardMismatch(true);
            }
        } else if (result.kind() != MoveDetectionResult.Kind.NO_CHANGE
                && result.kind() != MoveDetectionResult.Kind.IN_PROGRESS) {
            updateCheckIndicator();
            if (l != null) {
                l.onBoardMismatch(false);
            }
        }
    }

    private void updateCheckIndicator() {
        cancel(checkIndicatorFuture);
        ChessPosition position = moveDetector.position();
        if (!position.inCheck()) {
            ledController.off();
            return;
        }
        Piece king =
                position.sideToMove() == PieceColor.WHITE ? Piece.WHITE_KING : Piece.BLACK_KING;
        for (int square = 0; square < 64; square++) {
            if (position.pieceAt(square) == king) {
                ledController.showSquaresPulsing(square);
                checkIndicatorFuture =
                        postDelayed(this::refreshCheckIndicator, CHECK_INDICATOR_REFRESH_MS);
                return;
            }
        }
    }

    private void refreshCheckIndicator() {
        if (syncGuide.isActive()
                || moveDetector.state() == MoveDetectionState.BOARD_MISMATCH
                || !moveDetector.position().inCheck()) {
            return;
        }
        ledController.resend();
        checkIndicatorFuture = postDelayed(this::refreshCheckIndicator, CHECK_INDICATOR_REFRESH_MS);
    }

    /** Equivalent to {@link #guideEngineMove(String, boolean)} with LEDs enabled. */
    public void guideEngineMove(String uciMove) {
        guideEngineMove(uciMove, true);
    }

    /**
     * Waits for the human to play the given move, guiding them with LEDs on the physical board
     * unless {@code showLed} is {@code false} (the opening trainer's "quiz" mode). Silently ignored
     * if the move is illegal in Pegasus' own parallel position, or the physical board is not
     * currently synchronized with it.
     */
    public void guideEngineMove(String uciMove, boolean showLed) {
        try {
            Move move = Move.fromUci(uciMove == null ? null : uciMove.trim());
            ChessPosition current = moveDetector.position();
            if (!current.legalMoves().contains(move)) {
                return;
            }
            if (physicalBoard == null
                    || !OccupancyProjection.normalize(physicalBoard)
                            .equals(OccupancyProjection.occupancyOf(current))) {
                return;
            }
            cancel(checkIndicatorFuture);
            guideShowLed = showLed;
            guideExpectedFrom = move.from();
            guideExpectedTo = move.to();
            guideMoveUci = move.uci();
            guideTargetPosition = current.apply(move);
            guideCaptureSquare = current.pieceAt(move.to()) != null ? move.to() : null;
            squaresSeenEmpty.clear();
            syncGuide.start(OccupancyProjection.occupancyOf(guideTargetPosition), physicalBoard);
            if (guideCaptureSquare != null && showLed) {
                ledController.showMove(move.from(), move.to());
            }
        } catch (IllegalArgumentException ignored) {
            // Malformed UCI; nothing sensible to guide toward.
        }
    }

    private boolean shouldRevealGuideLeds(List<Integer> squares) {
        if (guideShowLed) {
            return true;
        }
        for (int square : squares) {
            if (square != guideExpectedFrom && square != guideExpectedTo) {
                return true;
            }
        }
        return false;
    }

    private void onGuideTargetReached() {
        ChessPosition newPosition = guideTargetPosition;
        guideMoveUci = null;
        guideTargetPosition = null;
        guideCaptureSquare = null;
        squaresSeenEmpty.clear();
        moveDetector.reset(newPosition, physicalBoard);
        updateCheckIndicator();
        Listener l = listener();
        if (l != null) {
            l.onEngineMoveGuidanceComplete();
        }
    }

    private void abortGuide() {
        syncGuide.cancel();
        guideMoveUci = null;
        guideTargetPosition = null;
        guideCaptureSquare = null;
        guideShowLed = true;
        guideExpectedFrom = -1;
        guideExpectedTo = -1;
    }

    /** Resynchronizes Pegasus' own parallel position tracking for a new game. */
    public void resetForNewGame() {
        cancel(checkIndicatorFuture);
        abortGuide();
        ledController.off();
        squaresSeenEmpty.clear();
        moveDetector.reset(ChessPosition.starting(), physicalBoard);
    }

    /**
     * Overwrites the bridge's own tracked position with {@code fen} - the authoritative position
     * from MoveAPiece's own {@code ChessGame} - and re-evaluates it against the physical board.
     * Call this after every (re)connect, not just after an unexpected drop, since it is a no-op
     * when the physical board already matches.
     */
    public void syncBoardToPosition(String fen) {
        try {
            ChessPosition target = ChessPosition.fromFen(fen);
            abortGuide();
            squaresSeenEmpty.clear();
            LOG.log(
                    Level.INFO,
                    "syncBoardToPosition: fen={0} physicalBoard={1}",
                    new Object[] {
                        fen,
                        physicalBoard == null
                                ? "null (not yet received)"
                                : ("\n" + OccupancyProjection.normalize(physicalBoard))
                    });
            MoveDetectionResult result = moveDetector.reset(target, physicalBoard);
            updateMismatchLeds(result);
        } catch (IllegalArgumentException e) {
            LOG.log(Level.WARNING, "syncBoardToPosition: malformed FEN " + fen, e);
        }
    }

    /**
     * Resolves a pending {@link Listener#onPromotionRequired()} with the piece the player chose in
     * the UI. A no-op if nothing is pending.
     */
    public void selectPromotion(PieceType promotion) {
        if (moveDetector.state() != MoveDetectionState.PROMOTION_PENDING) {
            return;
        }
        dispatchDetectionResult(moveDetector.selectPromotion(promotion));
    }

    /**
     * Resolves a pending {@link Listener#onAmbiguousMove} with the UCI the player chose in the UI.
     * A no-op if nothing is pending or the UCI no longer matches a pending candidate.
     */
    public void selectCandidate(String uci) {
        if (moveDetector.state() != MoveDetectionState.AMBIGUOUS) {
            return;
        }
        for (Move candidate : moveDetector.pendingCandidates()) {
            if (candidate.uci().equals(uci)) {
                dispatchDetectionResult(moveDetector.selectCandidate(candidate));
                return;
            }
        }
    }
}
