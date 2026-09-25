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
import de.schliweb.pegasus.core.movedetect.BoardMismatch;
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

        /**
         * While a {@link #guideEngineMove} is in progress, the board has (or no longer has) pieces
         * standing or missing on squares other than the guided move's own - the same thing a
         * BOARD_MISMATCH means outside a guide, which {@link #onBoardMismatch} cannot report then
         * because physical events are routed to the guide. Fired on every guide indication while
         * deviating and once when the deviation clears; see {@link #isBoardMismatched()} and {@link
         * #mismatchSquares()} for the current state.
         */
        default void onGuideDeviation(boolean deviating) {}

        void onTransportError(TransportError error, String detail);

        /**
         * Reported once per connect (from the init sequence's battery request), and again on any
         * later transition into a critically low battery. Real Pegasus hardware also pushes a fresh
         * reading spontaneously whenever the percentage changes by 1% (CONFIRMED_ON_HARDWARE
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

    /**
     * Re-send interval for the check indicator (pulse speed, which fades after ~1-2 s on real
     * hardware - CONFIRMED_ON_HARDWARE 2026-08-28). Only that pattern is refreshed on a timer: the
     * default speed 0x02 alternate-blinks the listed squares, and re-sending such a pattern
     * restarts the alternation at its first square - a periodic refresh of a two-square move
     * indication left only one of the squares ever visible (observed on hardware 2026-09-25).
     */
    private static final long CHECK_INDICATOR_REFRESH_MS = 900;

    /**
     * How long a guided capture may sit "unproven" - the physical board already matches the guide's
     * target occupancy, but the destination square was never observed going empty - with no further
     * physical events before the guide is completed anyway. Swapping the captured piece for the
     * attacker on the destination can be quicker than the board's scan, so the square is never
     * reported empty in between; without this window the guide would then wait forever for a proof
     * that can no longer arrive, silently blocking every subsequent move (observed on hardware
     * 2026-09-25, LEDs showing only the destination). Completing early is safe here, unlike for a
     * detected move: a guided move is forced, and whatever physical steps are still outstanding at
     * that point (removing the captured piece, setting the attacker down) are all lift/replace
     * events on the destination that the detector resolves as POSITION_RESTORED.
     */
    private static final long GUIDED_CAPTURE_SETTLE_MS = 1000;

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
                            LOG.log(
                                    Level.INFO,
                                    "guide: board differs from target of {0} on {1}",
                                    new Object[] {guideMoveUci, squareNames(squares)});
                            ledController.showSquares(
                                    shouldRevealGuideLeds(squares)
                                            ? squares
                                            : Collections.emptyList());
                            notifyGuideDeviation();
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
     * fresh battery status spontaneously every time the percentage changes by 1%
     * (CONFIRMED_ON_HARDWARE 2026-09-11, minutes apart, no re-request needed), not just once per
     * connect as originally assumed; reporting every one of those to the UI would mean a toast
     * every few minutes for the whole session, so only the first reading and later transitions into
     * {@link BatteryStatus#isCriticallyLow()} are forwarded - see {@link #lastBatteryCritical}.
     */
    private boolean batteryReportPending;

    /**
     * Last {@link BatteryStatus#isCriticallyLow()} seen this connection; see {@link
     * #batteryReportPending}.
     */
    private boolean lastBatteryCritical;

    private boolean guideShowLed = true;
    private int guideExpectedFrom = -1;
    private int guideExpectedTo = -1;
    private String guideMoveUci;
    private ChessPosition guideTargetPosition;
    private Integer guideCaptureSquare;
    private boolean guideDeviating;
    private volatile Listener listener;
    private final java.util.Set<Integer> squaresSeenEmpty = new java.util.HashSet<>();
    private SessionRecorder sessionRecorder;
    private ScheduledFuture<?> keepaliveFuture;
    private ScheduledFuture<?> checkIndicatorFuture;
    private ScheduledFuture<?> guidedCaptureSettleFuture;

    /**
     * Pending, not-yet-fired writes of {@link #sendOfficialInitSequence()}. The sequence spans 12 s
     * (8 commands x 1.5 s); a disconnect during that window (manual toolbar click, unexpected drop)
     * must cancel the remaining commands, otherwise each one fails with {@code WRITE_FAILED} on the
     * now-disconnected transport and surfaces as its own error dialog.
     */
    private final List<ScheduledFuture<?>> initSequenceFutures = new ArrayList<>();

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
                                        cancelInitSequence();
                                        sendOfficialInitSequence();
                                    } else {
                                        cancelInitSequence();
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
        cancelInitSequence();
        transport.disconnect();
    }

    public void shutdown() {
        cancelInitSequence();
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
     * <p>The dev-key-state query (0x5A, added after the manufacturer shared their protocol document
     * — DGT Chessboard Communication Protocol v1.2.1) is not part of that captured burst; it is a
     * read-only status request, so inserting it does not change what the official app itself writes
     * to the board. Its response is handled in {@link #onProtocolData}.
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
            initSequenceFutures.add(
                    postDelayed(
                            () -> {
                                if (transport.getConnectionState() == ConnectionState.CONNECTED) {
                                    transport.write(cmd);
                                }
                            },
                            INIT_COMMAND_SPACING_MS * i));
        }
        // The first board dump (answer to the board-state request above) arrives
        // while the burst is still going, and a mismatch found in it lights LEDs
        // right away - i.e. in between the remaining init commands. Observed on
        // hardware 2026-09-25: that pattern was logged as sent but never showed
        // on the board (init still in progress, or wiped by the update-mode
        // command). Re-assert whatever is currently lit once the burst is over;
        // a no-op when nothing is (or no longer) lit.
        initSequenceFutures.add(
                postDelayed(this::reassertLedsAfterInit, INIT_COMMAND_SPACING_MS * seq.length));
        keepaliveFuture =
                postDelayed(this::sendKeepalivePoll, INIT_COMMAND_SPACING_MS * seq.length);
    }

    /** Cancels pending init-sequence writes and the keepalive; see {@link #initSequenceFutures}. */
    private void cancelInitSequence() {
        for (ScheduledFuture<?> future : initSequenceFutures) {
            cancel(future);
        }
        initSequenceFutures.clear();
        cancel(keepaliveFuture);
        keepaliveFuture = null;
    }

    /**
     * Self-rescheduling keepalive: stops on its own once the connection is no longer CONNECTED
     * (e.g. after disconnect()/shutdown()), matching the reference implementation's design.
     */
    private void reassertLedsAfterInit() {
        LOG.log(
                Level.INFO,
                "init sequence done - re-asserting LEDs (anything lit: {0})",
                ledController.isAnyLit());
        ledController.resend();
    }

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
            cancel(guidedCaptureSettleFuture);
            if (isGuidedCaptureUnproven()) {
                LOG.log(
                        Level.INFO,
                        "guide: board matches target of {0}, but capture square {1} was never seen"
                                + " empty - settling in {2,number,#} ms unless the board changes",
                        new Object[] {
                            guideMoveUci,
                            BoardState.squareName(guideCaptureSquare),
                            GUIDED_CAPTURE_SETTLE_MS
                        });
                List<Integer> captureSquareOnly = Collections.singletonList(guideCaptureSquare);
                ledController.showSquares(
                        shouldRevealGuideLeds(captureSquareOnly)
                                ? captureSquareOnly
                                : Collections.emptyList());
                guidedCaptureSettleFuture =
                        postDelayed(this::settleUnprovenGuidedCapture, GUIDED_CAPTURE_SETTLE_MS);
                ledController.resend();
                return;
            }
            if (!isGuidedCaptureProvenByFollowUp()) {
                syncGuide.onPhysicalBoard(physicalBoard);
                ledController.resend();
                return;
            }
            LOG.log(
                    Level.INFO,
                    "guide: board explained as play continuing after {0} - treating it as executed",
                    guideMoveUci);
            completeGuideProvenByFollowUp();
            // Fall through: the event that proved the capture is itself the
            // first physical event of the next move, so the detector must see it.
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

    /**
     * Fires {@link #GUIDED_CAPTURE_SETTLE_MS} after the last physical event left the guide in the
     * "capture unproven" state (see {@link #isGuidedCaptureUnproven}): treats the capture as
     * executed and lets the guide complete. Every physical event in between cancels and reschedules
     * it ({@link #feedDetector}), so it only ever fires on a board at rest.
     */
    private void settleUnprovenGuidedCapture() {
        if (!syncGuide.isActive() || !isGuidedCaptureUnproven()) {
            return;
        }
        LOG.log(
                Level.INFO,
                "guide: capture square {0} still unproven after {1,number,#} ms of silence"
                        + " - treating {2} as executed",
                new Object[] {
                    BoardState.squareName(guideCaptureSquare),
                    GUIDED_CAPTURE_SETTLE_MS,
                    guideMoveUci
                });
        squaresSeenEmpty.add(guideCaptureSquare);
        syncGuide.onPhysicalBoard(physicalBoard);
        ledController.resend();
    }

    /**
     * The other way out of an unproven guided capture (see {@link #isGuidedCaptureUnproven}),
     * without waiting for {@link #GUIDED_CAPTURE_SETTLE_MS}: the player has already gone on to play
     * from the resulting position. The guided move's own squares are in their post-move state
     * (origin empty, destination occupied), the board differs from the target elsewhere, and a
     * fresh detector on the target position explains that difference as a legal move in progress or
     * completed rather than a BOARD_MISMATCH. Same reasoning as {@link
     * MoveDetector#wouldResolveIfCommitted} for detected captures: a continuation that only makes
     * sense once the capture is treated as finished is itself proof of it. Deliberately excludes
     * the capture's own intermediate states (captured piece removed, attacker still in hand) -
     * those only touch the guided move's squares and stay with the guide until proven.
     */
    private boolean isGuidedCaptureProvenByFollowUp() {
        if (guideCaptureSquare == null || physicalBoard == null) {
            return false;
        }
        BoardState physical = OccupancyProjection.normalize(physicalBoard);
        BoardState target = OccupancyProjection.occupancyOf(guideTargetPosition);
        if (physical.equals(target)
                || physical.isOccupied(guideExpectedFrom)
                || !physical.isOccupied(guideCaptureSquare)) {
            return false;
        }
        MoveDetector trial = new MoveDetector(guideTargetPosition, null);
        trial.onPhysicalBoard(target);
        return trial.onPhysicalBoard(physical).kind() != MoveDetectionResult.Kind.BOARD_MISMATCH;
    }

    /**
     * Completes the guide per {@link #isGuidedCaptureProvenByFollowUp}. Unlike {@link
     * #onGuideTargetReached}, the detector is resynchronized to the target <em>occupancy</em>
     * rather than the current physical board: the board is already mid-move, and a detector reset
     * against it would only report BOARD_MISMATCH (no detection before the first exact match) - the
     * caller feeds the real physical state through the normal detection path right after.
     */
    private void completeGuideProvenByFollowUp() {
        clearGuideDeviation();
        ChessPosition newPosition = guideTargetPosition;
        cancel(guidedCaptureSettleFuture);
        syncGuide.cancel();
        guideMoveUci = null;
        guideTargetPosition = null;
        guideCaptureSquare = null;
        squaresSeenEmpty.clear();
        moveDetector.reset(newPosition, OccupancyProjection.occupancyOf(newPosition));
        updateCheckIndicator();
        Listener l = listener();
        if (l != null) {
            l.onEngineMoveGuidanceComplete();
        }
    }

    private static List<String> squareNames(List<Integer> squares) {
        List<String> names = new ArrayList<>(squares.size());
        for (int square : squares) {
            names.add(BoardState.squareName(square));
        }
        return names;
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

    /**
     * FEN of the bridge's own tracked position (see the class javadoc on why it keeps one). Lets a
     * host compare it with its authoritative game position, e.g. to tell whether a pending guided
     * move has already been executed on the board.
     */
    public String trackedFen() {
        return moveDetector.position().toFen();
    }

    /**
     * Whether the physical board is known to match the tracked position closely enough for play to
     * continue from it: synchronized, or merely with pieces lifted / a move pending resolution.
     * False before the first board dump of a connection and while the board is mismatched - the
     * host holds any automatic move (engine reply, book move) back until this is true again.
     */
    public boolean isBoardInSync() {
        MoveDetectionState state = moveDetector.state();
        return state != MoveDetectionState.AWAITING_BOARD
                && state != MoveDetectionState.BOARD_MISMATCH;
    }

    /**
     * Whether the board currently disagrees with the tracked position (LEDs show the squares) -
     * outside a guide per the detector, during a guide per {@link #guideDeviationSquares()}.
     */
    public boolean isBoardMismatched() {
        return moveDetector.state() == MoveDetectionState.BOARD_MISMATCH
                || !guideDeviationSquares().isEmpty();
    }

    /**
     * The squares the board currently disagrees on while {@link #isBoardMismatched()}: those that
     * should be occupied but are empty, followed by those occupied although they should be empty -
     * the same set the LEDs show, as DGT square indices ({@link BoardState#squareName}). Empty when
     * not mismatched.
     */
    public List<Integer> mismatchSquares() {
        if (syncGuide.isActive()) {
            return guideDeviationSquares();
        }
        BoardState physical = moveDetector.lastPhysical();
        if (moveDetector.state() != MoveDetectionState.BOARD_MISMATCH || physical == null) {
            return Collections.emptyList();
        }
        BoardMismatch diff = BoardMismatch.between(moveDetector.expectedOccupancy(), physical);
        List<Integer> squares = new ArrayList<>(diff.missingOccupied());
        squares.addAll(diff.unexpectedOccupied());
        return squares;
    }

    /**
     * Squares on which the board deviates from an active guide's target beyond the guided move's
     * own origin/destination (which are expected to differ until the move is played): pieces lifted
     * or set down elsewhere meanwhile. Empty when no guide is active.
     */
    private List<Integer> guideDeviationSquares() {
        if (!syncGuide.isActive() || physicalBoard == null) {
            return Collections.emptyList();
        }
        BoardMismatch diff =
                BoardMismatch.between(
                        OccupancyProjection.occupancyOf(guideTargetPosition),
                        OccupancyProjection.normalize(physicalBoard));
        List<Integer> squares = new ArrayList<>();
        for (int square : diff.missingOccupied()) {
            if (square != guideExpectedFrom && square != guideExpectedTo) {
                squares.add(square);
            }
        }
        for (int square : diff.unexpectedOccupied()) {
            if (square != guideExpectedFrom && square != guideExpectedTo) {
                squares.add(square);
            }
        }
        return squares;
    }

    private void clearGuideDeviation() {
        if (guideDeviating) {
            guideDeviating = false;
            Listener l = listener();
            if (l != null) {
                l.onGuideDeviation(false);
            }
        }
    }

    /**
     * Reports the current guide deviation state to the listener (see Listener#onGuideDeviation).
     */
    private void notifyGuideDeviation() {
        boolean deviating = !guideDeviationSquares().isEmpty();
        boolean changed = deviating != guideDeviating;
        guideDeviating = deviating;
        if (deviating || changed) {
            Listener l = listener();
            if (l != null) {
                l.onGuideDeviation(deviating);
            }
        }
    }

    /** Whether a {@link #guideEngineMove} is currently in progress. */
    public boolean isGuideActive() {
        return syncGuide.isActive();
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
                LOG.log(
                        Level.INFO,
                        "guide: ignoring {0} - not legal in the tracked position",
                        move);
                return;
            }
            if (physicalBoard == null) {
                LOG.log(Level.INFO, "guide: ignoring {0} - no board state received yet", move);
                return;
            }
            // The board must show the current position - or that position with some pieces
            // merely lifted off it (typically the very piece about to be moved, picked up before
            // the line was started; observed on hardware 2026-09-25). Anything standing on a
            // square it shouldn't means a genuinely different position: leave that to the
            // mismatch LEDs, the host retries once the board is restored.
            BoardMismatch diff =
                    BoardMismatch.between(
                            OccupancyProjection.occupancyOf(current),
                            OccupancyProjection.normalize(physicalBoard));
            if (!diff.unexpectedOccupied().isEmpty()) {
                LOG.log(
                        Level.INFO,
                        "guide: ignoring {0} - unexpected pieces on {1}",
                        new Object[] {move, squareNames(diff.unexpectedOccupied())});
                return;
            }
            if (!diff.missingOccupied().isEmpty()) {
                LOG.log(
                        Level.INFO,
                        "guide: {0} requested with pieces in hand from {1} - guiding anyway",
                        new Object[] {move, squareNames(diff.missingOccupied())});
            }
            cancel(checkIndicatorFuture);
            guideShowLed = showLed;
            guideExpectedFrom = move.from();
            guideExpectedTo = move.to();
            guideMoveUci = move.uci();
            guideTargetPosition = current.apply(move);
            guideCaptureSquare = current.pieceAt(move.to()) != null ? move.to() : null;
            squaresSeenEmpty.clear();
            LOG.log(
                    Level.INFO,
                    "guide: started for {0} (capture square {1}, leds {2})",
                    new Object[] {
                        guideMoveUci,
                        guideCaptureSquare == null
                                ? "none"
                                : BoardState.squareName(guideCaptureSquare),
                        showLed
                    });
            // Route the current board through feedDetector() rather than handing it to
            // start() directly: with the attacker of a capture already in hand, the board
            // matches the target occupancy from the outset, and only feedDetector() knows
            // that this is the unproven-capture state (LED on the destination, settle window)
            // rather than "target reached".
            syncGuide.start(OccupancyProjection.occupancyOf(guideTargetPosition), null);
            feedDetector();
            if (guideCaptureSquare != null && showLed && syncGuide.isActive()) {
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
        clearGuideDeviation();
        LOG.log(Level.INFO, "guide: target reached, {0} executed on the board", guideMoveUci);
        cancel(guidedCaptureSettleFuture);
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
        clearGuideDeviation();
        cancel(guidedCaptureSettleFuture);
        syncGuide.cancel();
        guideMoveUci = null;
        guideTargetPosition = null;
        guideCaptureSquare = null;
        guideShowLed = true;
        guideExpectedFrom = -1;
        guideExpectedTo = -1;
    }

    /**
     * Resynchronizes Pegasus' own parallel position tracking for a new game. A board that does not
     * match the starting position is lit up as a mismatch right away - {@code off()} above cleared
     * whatever the previous game showed, and until 2026-09-25 the reset's own result was simply
     * dropped, so a mismatch that already existed (e.g. a piece in hand while the line was started)
     * went dark and stayed dark: nothing lights it again until the next physical event.
     */
    public void resetForNewGame() {
        cancel(checkIndicatorFuture);
        abortGuide();
        ledController.off();
        squaresSeenEmpty.clear();
        MoveDetectionResult result = moveDetector.reset(ChessPosition.starting(), physicalBoard);
        if (result.kind() == MoveDetectionResult.Kind.BOARD_MISMATCH) {
            updateMismatchLeds(result);
        }
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
