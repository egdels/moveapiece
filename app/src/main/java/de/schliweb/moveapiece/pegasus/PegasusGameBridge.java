/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.pegasus;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
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

/**
 * Bridges a physical DGT Pegasus board (via {@link PegasusTransport}) to MoveAPiece's own game
 * state. Ports the hardware-verified connect/init/move- detection/LED-guidance sequencing from the
 * sibling "pegasus" project's {@code TransportViewModel}
 * (external/pegasus/app/.../ui/TransportViewModel.java), minus its Lichess- and
 * developer-UI-specific parts.
 *
 * <p>Keeps its own, independent {@link ChessPosition} in sync with MoveAPiece's chesslib-based
 * {@code ChessGame} purely by replaying the same UCI move strings; {@link MoveDetector}
 * hard-references pegasus' own chess classes with no interface seam, so no attempt is made to unify
 * the two.
 *
 * <p>Runs entirely on the main thread; all transport callbacks are marshalled onto it via {@link
 * #mainHandler}, matching the reference implementation.
 */
public class PegasusGameBridge {

    private static final String TAG = "PegasusGameBridge";

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
         * Physical occupancy matches several legal moves that aren't a pure promotion choice
         * (structurally near-unreachable in practice, since distinct origins almost always yield
         * distinct occupancy diffs, but handled defensively rather than silently stalling).
         * Candidates are given as UCI strings; resolve via {@link #selectCandidate}.
         */
        void onAmbiguousMove(List<String> candidateUcis);

        void onEngineMoveGuidanceComplete();

        void onTransportError(TransportError error, String detail);

        /** Reported once per connect, from the init sequence's battery request. */
        void onBatteryStatus(int percent);
    }

    private static final long INIT_COMMAND_SPACING_MS = 1500;

    /**
     * Keepalive poll interval: on real hardware the board drops the connection (GATT status 8,
     * connection timeout) after roughly a minute of silence. The official DGT app polls 'E' (0x45)
     * once per second the whole session; 2s is the interval the pegasus project verified as
     * sufficient on hardware (see external/pegasus docs/PEGASUS_PROTOCOL.md).
     */
    private static final long KEEPALIVE_POLL_INTERVAL_MS = 2000;

    private static final byte[] KEEPALIVE_POLL_COMMAND = {0x45};

    /**
     * Re-send interval for the check indicator while the board is otherwise idle (no physical
     * events to hang a {@link PegasusLedController#resend()} off, unlike e.g. capture guidance -
     * see {@link #updateCheckIndicator()}). Purely a display refresh, not a factor in any
     * move-confirmation decision - unlike the settle window above, this one is fine to be
     * timer-driven. Comfortably under the ~1-2s fade observed on real hardware for the pulse speed
     * specifically (CONFIRMED_ON_HARDWARE 2026-08-28; steady non-pulse patterns were separately
     * observed to hold for minutes unattended).
     */
    private static final long CHECK_INDICATOR_REFRESH_MS = 900;

    private final PegasusTransport transport;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final PegasusFrameParser frameParser = new PegasusFrameParser();
    private final PegasusLedController ledController;
    private final Runnable keepalivePollRunnable = this::sendKeepalivePoll;
    private final Runnable checkIndicatorRefreshRunnable = this::refreshCheckIndicator;
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
     * Whether the currently active guide (if any) is allowed to light LEDs for the plain expected
     * move itself - {@code false} for the opening trainer's "quiz" mode, where the guide still
     * silently validates the trainee's move (only completes on the exact expected occupancy) but
     * must not reveal which squares it's waiting for. Always {@code true} for guiding the
     * book/engine side's own move, which isn't a memory test. See {@link #shouldRevealGuideLeds}
     * for what "quiz mode" still shows.
     */
    private boolean guideShowLed = true;

    /** Origin/destination of the currently guided move, for {@link #shouldRevealGuideLeds}. */
    private int guideExpectedFrom = -1;

    private int guideExpectedTo = -1;
    private String guideMoveUci;
    private ChessPosition guideTargetPosition;

    /**
     * Destination square of the move currently being guided via {@link #guideEngineMove}, if that
     * move is a capture; {@code null} otherwise. See {@link #isGuidedCaptureUnproven()}.
     */
    private Integer guideCaptureSquare;

    private volatile Listener listener;

    /**
     * Squares directly observed going empty (FIELD_UPDATE code 0) since the board was last in a
     * settled state. Executing a capture by hand normally means lifting the attacker, then
     * explicitly removing the captured piece (its square goes empty too, if only briefly) before
     * setting the attacker down - unlike merely lifting a piece and not yet deciding where it goes,
     * which never touches the destination square at all. Seeing the destination itself go empty is
     * therefore positive proof of a deliberate capture; see the IN_PROGRESS branch of {@link
     * #dispatchDetectionResult}.
     */
    private final java.util.Set<Integer> squaresSeenEmpty = new java.util.HashSet<>();

    /**
     * Raw BLE traffic recorder for hardware-verification sessions; {@code null} when not recording.
     */
    private SessionRecorder sessionRecorder;

    public PegasusGameBridge(PegasusTransport transport, Listener listener) {
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
                        mainHandler.post(
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
                                        mainHandler.removeCallbacks(keepalivePollRunnable);
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
                        mainHandler.post(() -> onProtocolData(copy));
                    }

                    @Override
                    public void onDataSent(String characteristicUuid, byte[] data) {
                        // No UI action needed; MoveAPiece does not display raw TX traffic.
                        recordIfActive(SessionRecorder.Direction.TX, characteristicUuid, data);
                    }

                    @Override
                    public void onError(TransportError error, String detail) {
                        mainHandler.post(
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
        mainHandler.removeCallbacksAndMessages(null);
        transport.disconnect();
        stopRecording();
    }

    /**
     * Starts recording raw BLE traffic (RX/TX, both GATT callback threads — {@link
     * SessionRecorder#record} is synchronized) as NDJSON to {@code file} for hardware-verification
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
                Log.w(TAG, "stopRecording: close failed", e);
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
            Log.w(TAG, "recording: write failed, stopping", e);
            stopRecording();
        }
    }

    public ConnectionState getConnectionState() {
        return transport.getConnectionState();
    }

    /**
     * Replays the exact init sequence captured from the official DGT app on real hardware
     * (docs/PEGASUS_PROTOCOL.md in external/pegasus): reset, dev key, three unlabeled bytes,
     * board-state request, and update mode — spaced 1.5s apart. Denser spacing made the board stop
     * responding to the whole burst on real hardware.
     */
    private void sendOfficialInitSequence() {
        byte[][] seq = {
            PegasusCommands.encodeReset(),
            PegasusCommands.encodeDevKey(),
            {0x55},
            {0x47},
            PegasusCommands.encodeBoardStateRequest(),
            {0x4C},
            PegasusCommands.encodeUpdateMode(),
        };
        for (int i = 0; i < seq.length; i++) {
            byte[] cmd = seq[i];
            mainHandler.postDelayed(() -> transport.write(cmd), INIT_COMMAND_SPACING_MS * i);
        }
        mainHandler.postDelayed(keepalivePollRunnable, INIT_COMMAND_SPACING_MS * seq.length);
    }

    /**
     * Self-rescheduling keepalive: stops on its own once the connection is no longer CONNECTED
     * (e.g. after disconnect()/shutdown()), matching the reference implementation's design — no
     * explicit cancellation needed beyond what shutdown() already does.
     */
    private void sendKeepalivePoll() {
        if (transport.getConnectionState() != ConnectionState.CONNECTED) {
            Log.d(TAG, "keepalive: skipped, state=" + transport.getConnectionState());
            return;
        }
        Log.d(TAG, "keepalive: writing 0x45");
        transport.write(KEEPALIVE_POLL_COMMAND);
        mainHandler.postDelayed(keepalivePollRunnable, KEEPALIVE_POLL_INTERVAL_MS);
    }

    private void onProtocolData(byte[] data) {
        for (PegasusFrame frame : frameParser.feed(data)) {
            switch (frame.type()) {
                case PegasusMessageType.BOARD_DUMP:
                    physicalBoard = BoardState.fromBoardDumpPayload(frame.payload());
                    Log.i(TAG, "board dump: " + OccupancyProjection.normalize(physicalBoard));
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
                        Log.i(TAG, "battery: " + status);
                        Listener l = listener();
                        if (l != null) {
                            l.onBatteryStatus(status.percent());
                        }
                    }
                    break;
                default:
                    break;
            }
        }
    }

    private void feedDetector() {
        if (syncGuide.isActive()) {
            // Opponent-move guidance: route physical states to the guide;
            // the detector is resynchronized once the target is reached.
            if (isGuidedCaptureUnproven()) {
                // Same ambiguity MoveDetector guards against for detected
                // moves (see squaresSeenEmpty above): a capture's
                // destination square stays continuously occupied throughout
                // - lifting the attacker off its origin already matches the
                // guide's target occupancy, before the captured piece has
                // actually been removed and the attacker placed down.
                // BoardSyncGuide is occupancy-only and cannot tell the
                // difference itself, so keep indicating the destination
                // instead of forwarding this state and letting it declare
                // the target reached prematurely.
                List<Integer> captureSquareOnly = Collections.singletonList(guideCaptureSquare);
                ledController.showSquares(
                        shouldRevealGuideLeds(captureSquareOnly)
                                ? captureSquareOnly
                                : Collections.emptyList());
            } else {
                syncGuide.onPhysicalBoard(physicalBoard);
            }
            // Every physical event during an active capture guide must
            // re-assert the current pattern: real hardware has been
            // observed to clear it on its own once the indicated square's
            // occupancy changes (e.g. lifting the captured piece), even
            // though the two branches above may have computed the exact
            // same square set as before and skipped sending it (dedupe).
            // Purely event-driven - fires on every physical update, never
            // on a timer, so it holds regardless of how fast or slowly the
            // player moves. No-op once the target was reached (LEDs off).
            ledController.resend();
            return;
        }
        List<Move> priorPending = moveDetector.pendingCandidates();
        MoveDetectionResult result = moveDetector.onPhysicalBoard(physicalBoard);
        if (result.kind() == MoveDetectionResult.Kind.BOARD_MISMATCH
                && priorPending.size() == 1
                && moveDetector.wouldResolveIfCommitted(priorPending.get(0), physicalBoard)) {
            // physicalBoard doesn't explain as a continuation of the
            // still-open capture from the previous event - but does
            // explain cleanly once that capture is treated as already
            // finished.
            // A move that only makes sense from there - typically the
            // opponent's reply - is itself proof of that, the same
            // reasoning as the "positive proof" shortcut below, just
            // sourced from a later, unrelated physical event instead of
            // the destination square itself. Commit it for real, then
            // re-evaluate physicalBoard for real against the result - no
            // settle window, no player confirmation needed.
            dispatchDetectionResult(moveDetector.commitRecoveredCapture(priorPending.get(0)));
            dispatchDetectionResult(moveDetector.onPhysicalBoard(physicalBoard));
            ledController.resend();
            return;
        }
        dispatchDetectionResult(result);
        // Same reasoning as the capture-guide resend() above: a TX sent
        // right around when an RX FIELD_UPDATE arrives has been observed
        // to only flash briefly on real hardware before clearing itself,
        // even for squares unrelated to the one that just changed (e.g.
        // the check indicator on the king's square, see
        // updateCheckIndicator() - CONFIRMED_ON_HARDWARE 2026-08-28). Keep
        // re-asserting whatever is currently shown (a mismatch, or the
        // check indicator) on every physical event; a no-op once nothing
        // is lit.
        ledController.resend();
    }

    /**
     * True while the physical board already superficially matches the active guide's target
     * occupancy for a capture move whose destination square was never actually observed going empty
     * - i.e. the attacker was lifted but the captured piece has not (yet, provably) been removed
     * and replaced. See {@link #feedDetector}.
     */
    private boolean isGuidedCaptureUnproven() {
        return guideCaptureSquare != null
                && !squaresSeenEmpty.contains(guideCaptureSquare)
                && physicalBoard != null
                && OccupancyProjection.normalize(physicalBoard)
                        .equals(OccupancyProjection.occupancyOf(guideTargetPosition));
    }

    private void dispatchDetectionResult(MoveDetectionResult result) {
        Log.i(TAG, "detection result: " + result.kind());
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
                // Every proven candidate's destination was itself directly
                // observed empty at some point - the normal way to execute
                // a capture by hand is lifting the attacker, then
                // explicitly removing the captured piece (briefly vacating
                // the destination too), then placing the attacker down.
                // That sequence is positive proof of a deliberate capture,
                // unlike merely lifting a piece and not yet deciding where
                // it goes (which never touches any destination at all).
                // Apply it immediately - no settle window, no player
                // confirmation needed. Covers capture-promotions too
                // (multiple candidates, one per promotion piece, all
                // sharing the same from/to) - resolvePendingSubset() still
                // routes those through PROMOTION_REQUIRED, never silently
                // picks a piece. Also covers a pawn diagonally adjacent to
                // two different enemy pieces (occupancy can't tell which
                // one it captured either, until one destination is
                // actually observed vacated): candidates targeting the
                // *other*, never-disturbed destination are excluded from
                // proven, not just left unconfirmed - real physical
                // evidence (this destination emptied) rules them out, it
                // doesn't merely fail to confirm them.
                Log.i(
                        TAG,
                        "capture destination "
                                + proven.get(0).to()
                                + " was seen vacated - confirming immediately");
                dispatchDetectionResult(moveDetector.resolvePendingSubset(proven));
                return;
            }
            // Otherwise genuinely ambiguous (e.g. a two-handed "swap" that
            // never showed the destination empty): stays pending with no
            // further action here. No settle-window prompt - a UI element
            // asking the player to confirm turned out to have an
            // unreliably narrow window in practice (see git history):
            // essentially any further physical event, even an unrelated
            // one or the next half of the *same* upcoming move, moves this
            // detector on (to a fresh in-progress/mismatch/confirmed state)
            // before a real person reliably notices and taps a prompt in
            // time - and once that's happened, the prompt would be a
            // no-op anyway. Left to resolve via a later, otherwise-
            // unexplained physical event proving it by itself (see the
            // BOARD_MISMATCH recovery in feedDetector()), or manual
            // correction if truly nothing else ever explains the board.
            // No LED indication either, on purpose: showSquares() is also
            // how guideEngineMove() tells the player where to move for an
            // engine move, and reusing that for "this might be your own
            // move" reads as the app suggesting a move rather than
            // reporting what it's still unsure about - the player decides
            // the move, not the board.
            Log.i(TAG, "in-progress: pending candidate(s) " + pending);
            return;
        } else if (result.kind() == MoveDetectionResult.Kind.PROMOTION_REQUIRED) {
            // Not a mismatch and not yet confirmed - the detector is
            // legitimately waiting on selectPromotion(); routing this
            // through updateMismatchLeds would incorrectly turn the LEDs
            // off and report onBoardMismatch(false) ("back in sync") for a
            // move that was never actually applied.
            Listener l = listener();
            if (l != null) {
                l.onPromotionRequired();
            }
            return;
        } else if (result.kind() == MoveDetectionResult.Kind.AMBIGUOUS) {
            // Same reasoning as PROMOTION_REQUIRED above: not a mismatch,
            // not yet confirmed, must not be routed through updateMismatchLeds.
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

    /**
     * True if every pending candidate targets the same square - covers a plain single-candidate
     * capture as well as a capture-promotion's four promotion-piece candidates (same from/to,
     * differing only in the promoted piece), which occupancy can never tell apart from each other
     * either way - so "was the destination seen vacated" is exactly as valid a positive-proof
     * signal for either shape.
     */
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

    /** BOARD_MISMATCH -> light the deviating squares; back in sync -> LEDs off. */
    private void updateMismatchLeds(MoveDetectionResult result) {
        Listener l = listener();
        if (result.kind() == MoveDetectionResult.Kind.BOARD_MISMATCH && result.mismatch() != null) {
            List<Integer> squares = new ArrayList<>(result.mismatch().missingOccupied());
            squares.addAll(result.mismatch().unexpectedOccupied());
            Log.i(
                    TAG,
                    "mismatch: lighting squares "
                            + squares
                            + " (connectionState="
                            + transport.getConnectionState()
                            + ")");
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

    /**
     * Shows the side-to-move's king pulsing ({@link PegasusLedController#showSquaresPulsing}) if it
     * is currently in check, otherwise turns LEDs off. Checkmate needs no special case here: it's
     * still "in check" at the final position, just with no legal moves left - the phone screen
     * distinguishes an ongoing check from checkmate via the game-over text, the board only ever
     * needs to show "this king is in check" either way. Called instead of an unconditional {@code
     * off()} wherever a move has just been confirmed/resynced, so the indicator persists (as this
     * controller's new "current pattern") across whatever the next physical event does until a
     * following move changes the position again.
     */
    private void updateCheckIndicator() {
        mainHandler.removeCallbacks(checkIndicatorRefreshRunnable);
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
                mainHandler.postDelayed(checkIndicatorRefreshRunnable, CHECK_INDICATOR_REFRESH_MS);
                return;
            }
        }
    }

    /**
     * Timer tick for {@link #updateCheckIndicator()}: unlike capture guidance, a check can leave
     * the board fully idle for a while (the player is just thinking) with no physical event to hang
     * a {@link PegasusLedController#resend()} off, so this keeps the pattern alive on a plain
     * interval instead. Self-cancelling: only re-sends and reschedules itself while the indicator
     * is still the right thing to show (still in check, no board mismatch, no active engine-move
     * guidance) - any of those transitions already calls {@link #updateCheckIndicator()} (which
     * cancels this) or {@link #guideEngineMove} from their own call sites, so this only ever needs
     * to stand down, never to reassert priority over them.
     */
    private void refreshCheckIndicator() {
        if (syncGuide.isActive()
                || moveDetector.state() == MoveDetectionState.BOARD_MISMATCH
                || !moveDetector.position().inCheck()) {
            return;
        }
        ledController.resend();
        mainHandler.postDelayed(checkIndicatorRefreshRunnable, CHECK_INDICATOR_REFRESH_MS);
    }

    /** Equivalent to {@link #guideEngineMove(String, boolean)} with LEDs enabled. */
    public void guideEngineMove(String uciMove) {
        guideEngineMove(uciMove, true);
    }

    /**
     * Waits for the human to play the given move, guiding them with LEDs on the physical board
     * unless {@code showLed} is {@code false} (the opening trainer's "quiz" mode: still validates
     * silently - only {@link Listener#onEngineMoveGuidanceComplete()} fires once the target
     * occupancy is reached, exactly as with LEDs on. With {@code showLed} false, the plain expected
     * move itself stays dark, but if the player deviates beyond it - a wrong move - the resulting
     * mismatch lights up exactly like an ordinary out-of-sync board, and stays lit until it's
     * undone; see {@link #shouldRevealGuideLeds}). Silently ignored if the move is illegal in
     * Pegasus' own parallel position, or the physical board is not currently synchronized with it —
     * both are transient conditions the caller cannot usefully act on immediately.
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
            mainHandler.removeCallbacks(checkIndicatorRefreshRunnable);
            guideShowLed = showLed;
            guideExpectedFrom = move.from();
            guideExpectedTo = move.to();
            guideMoveUci = move.uci();
            guideTargetPosition = current.apply(move);
            // A capture's destination is occupied before AND after the
            // move (by the captured piece, then the attacker) - only its
            // origin square differs, so lifting the attacker alone already
            // matches the target occupancy. Track it for isGuidedCaptureUnproven().
            guideCaptureSquare = current.pieceAt(move.to()) != null ? move.to() : null;
            squaresSeenEmpty.clear();
            syncGuide.start(OccupancyProjection.occupancyOf(guideTargetPosition), physicalBoard);
            if (guideCaptureSquare != null && showLed) {
                // syncGuide's occupancy-only diff sees the destination as
                // already "correct" (still occupied by the piece about to
                // be captured) and so only lit the origin above. Show both
                // squares from the start so the player has a visual cue
                // this is a capture before they've lifted anything
                // (hardware-verified gap).
                ledController.showMove(move.from(), move.to());
            }
        } catch (IllegalArgumentException ignored) {
            // Malformed UCI; nothing sensible to guide toward.
        }
    }

    /**
     * Whether the given occupancy-diff squares should actually light up. {@code true}
     * unconditionally when the guide is allowed to show LEDs ({@link #guideShowLed}). Otherwise
     * (opening trainer "quiz" mode): only once the diff contains a square outside the plain
     * expected move's own origin/destination - i.e. the player has done something beyond "not yet
     * played the move" - so a wrong move surfaces exactly like any other out-of-sync board (same
     * LEDs, same "stays lit until undone" behavior), without ever revealing the answer for an
     * attempt that hasn't deviated yet.
     */
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
        mainHandler.removeCallbacks(checkIndicatorRefreshRunnable);
        abortGuide();
        ledController.off();
        squaresSeenEmpty.clear();
        moveDetector.reset(ChessPosition.starting(), physicalBoard);
    }

    /**
     * Overwrites the bridge's own tracked position with {@code fen} — the authoritative position
     * from MoveAPiece's own {@code ChessGame} — and re-evaluates it against the physical board.
     * Needed because the bridge only replays moves it actually observed (physical moves, guided
     * engine moves); on-screen tap-to-move play while the board was disconnected leaves the
     * bridge's own position stale, so a plain reconnect alone is not enough to resume physical play
     * correctly.
     *
     * <p>If the physical board doesn't match {@code fen}, the deviating squares are lit via LEDs
     * exactly like any other board mismatch (same mechanism {@link #guideEngineMove} and normal
     * move detection already use) — no separate guidance path needed. Call this after every
     * (re)connect, not just after an unexpected drop, since it is a no-op when the board already
     * matches.
     */
    public void syncBoardToPosition(String fen) {
        try {
            ChessPosition target = ChessPosition.fromFen(fen);
            abortGuide();
            squaresSeenEmpty.clear();
            Log.i(
                    TAG,
                    "syncBoardToPosition: fen="
                            + fen
                            + " physicalBoard="
                            + (physicalBoard == null
                                    ? "null (not yet received)"
                                    : ("\n" + OccupancyProjection.normalize(physicalBoard))));
            MoveDetectionResult result = moveDetector.reset(target, physicalBoard);
            updateMismatchLeds(result);
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "syncBoardToPosition: malformed FEN " + fen, e);
        }
    }

    /**
     * Resolves a pending {@link Listener#onPromotionRequired()} with the piece the player chose in
     * the UI. A no-op if nothing is pending (e.g. the physical board changed again before the
     * dialog was answered) - matches {@code MoveDetector#selectPromotion}'s own precondition rather
     * than throwing.
     */
    public void selectPromotion(PieceType promotion) {
        if (moveDetector.state() != MoveDetectionState.PROMOTION_PENDING) {
            return;
        }
        dispatchDetectionResult(moveDetector.selectPromotion(promotion));
    }

    /**
     * Resolves a pending {@link Listener#onAmbiguousMove} with the UCI the player chose in the UI.
     * A no-op if nothing is pending or the UCI no longer matches a pending candidate (e.g. the
     * physical board changed again before the dialog was answered).
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
