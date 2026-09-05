/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.pegasus.core.movedetect;

import de.schliweb.pegasus.core.chess.ChessPosition;
import de.schliweb.pegasus.core.chess.Move;
import de.schliweb.pegasus.core.chess.OccupancyProjection;
import de.schliweb.pegasus.core.chess.PieceType;
import de.schliweb.pegasus.core.protocol.BoardState;
import java.util.ArrayList;
import java.util.List;

/**
 * Occupancy-based move detection: combines physical {@link BoardState} snapshots (occupied/empty
 * only — the Pegasus reports no piece identity) with the logical {@link ChessPosition} and its
 * legal moves.
 *
 * <p>Core principle (docs/MOVE_DETECTION.md): for every legal move the resulting position is
 * projected to occupancy and compared with the actual physical state. Exactly one match confirms
 * the move; several matches that differ only in the promotion piece require a UI selection; no
 * match is either a plausible intermediate state (pieces lifted / move in progress) or a board
 * mismatch.
 *
 * <p>Deterministic and single-threaded by design: feed physical states in a serialized order (e.g.
 * from the transport callback thread). Input may come from board dumps or accumulated field updates
 * — the detector does not distinguish. Repeated identical states are idempotent (no double moves).
 */
public final class MoveDetector {

    /** Max simultaneously lifted pieces still treated as "move in progress". */
    static final int MAX_LIFTED_PIECES = 3;

    /** Max lifted pieces unrelated to a candidate move (e.g. accidental lift). */
    static final int MAX_UNRELATED_LIFTS = 2;

    private final MoveDetectionListener listener;

    private ChessPosition position;
    private BoardState expected; // occupancyOf(position)
    private BoardState lastPhysical; // normalized, null until first input
    private MoveDetectionState state = MoveDetectionState.AWAITING_BOARD;
    private List<Move> pendingCandidates = new ArrayList<>();

    /** Cached legal moves + their resulting occupancies for {@link #position}. */
    private List<Move> legalMoves;

    private List<BoardState> legalOccupancies;

    public MoveDetector(ChessPosition initialPosition, MoveDetectionListener listener) {
        if (initialPosition == null) {
            throw new IllegalArgumentException("initialPosition must not be null");
        }
        this.listener = listener == null ? new MoveDetectionListener() {} : listener;
        setPosition(initialPosition);
    }

    /** Current logical position (updated atomically on confirmed moves). */
    public ChessPosition position() {
        return position;
    }

    /** Expected physical occupancy for the current logical position. */
    public BoardState expectedOccupancy() {
        return expected;
    }

    /** Last processed physical occupancy or {@code null} before first input. */
    public BoardState lastPhysical() {
        return lastPhysical;
    }

    public MoveDetectionState state() {
        return state;
    }

    /** Candidates of a pending PROMOTION_PENDING/AMBIGUOUS state (else empty). */
    public List<Move> pendingCandidates() {
        return new ArrayList<>(pendingCandidates);
    }

    /**
     * Controlled resynchronization (new game, FEN load, BLE reconnect): drops all transient move
     * state and re-evaluates the given physical state — result is SYNCHRONIZED or BOARD_MISMATCH.
     * Pass {@code physical = null} to only set the position and wait for the next board dump.
     */
    public MoveDetectionResult reset(ChessPosition newPosition, BoardState physical) {
        if (newPosition == null) {
            throw new IllegalArgumentException("newPosition must not be null");
        }
        setPosition(newPosition);
        pendingCandidates = new ArrayList<>();
        lastPhysical = null;
        state = MoveDetectionState.AWAITING_BOARD;
        if (physical == null) {
            return MoveDetectionResult.noChange();
        }
        return onPhysicalBoard(physical);
    }

    /**
     * Feeds a new physical board state (from a board dump or accumulated field updates), evaluates
     * it and fires listener callbacks.
     */
    public MoveDetectionResult onPhysicalBoard(BoardState physical) {
        if (physical == null) {
            throw new IllegalArgumentException("physical must not be null");
        }
        BoardState normalized = OccupancyProjection.normalize(physical);
        if (normalized.equals(lastPhysical)) {
            return MoveDetectionResult.noChange(); // idempotent
        }
        lastPhysical = normalized;
        return evaluate(normalized);
    }

    /**
     * Resolves a PROMOTION_PENDING state with the user's choice. The physical board cannot tell the
     * promotion piece (docs/PEGASUS_PROTOCOL.md), so this must come from the UI.
     */
    public MoveDetectionResult selectPromotion(PieceType promotion) {
        if (state != MoveDetectionState.PROMOTION_PENDING) {
            throw new IllegalStateException("No promotion pending (state=" + state + ")");
        }
        for (Move candidate : pendingCandidates) {
            if (candidate.promotion() == promotion) {
                return confirm(candidate);
            }
        }
        throw new IllegalArgumentException("No pending candidate promotes to " + promotion);
    }

    /** Resolves a generic AMBIGUOUS (or PROMOTION_PENDING) state. */
    public MoveDetectionResult selectCandidate(Move move) {
        if (state != MoveDetectionState.AMBIGUOUS
                && state != MoveDetectionState.PROMOTION_PENDING) {
            throw new IllegalStateException("No ambiguous move pending (state=" + state + ")");
        }
        if (!pendingCandidates.contains(move)) {
            throw new IllegalArgumentException("Not a pending candidate: " + move);
        }
        return confirm(move);
    }

    // ------------------------------------------------------------- internals

    private MoveDetectionResult evaluate(BoardState physical) {
        // 1. Board equals the current logical position → synchronized.
        if (physical.equals(expected)) {
            boolean wasSynchronized =
                    state == MoveDetectionState.SYNCHRONIZED
                            || state == MoveDetectionState.AWAITING_BOARD;
            pendingCandidates = new ArrayList<>();
            changeState(MoveDetectionState.SYNCHRONIZED);
            if (wasSynchronized) {
                return MoveDetectionResult.synchronizedResult();
            }
            listener.onPositionRestored();
            return MoveDetectionResult.positionRestored();
        }

        // 2. No move detection before the board was synchronized once
        // (initial dump, reconnect) or while in mismatch: require a full
        // restore first (docs/MOVE_DETECTION.md, "Startbedingung").
        if (state == MoveDetectionState.AWAITING_BOARD
                || state == MoveDetectionState.BOARD_MISMATCH) {
            BoardMismatch initialDiff = BoardMismatch.between(expected, physical);
            pendingCandidates = new ArrayList<>();
            changeState(MoveDetectionState.BOARD_MISMATCH);
            listener.onBoardMismatch(initialDiff);
            return MoveDetectionResult.boardMismatch(initialDiff);
        }

        // 3. Candidate matching: legal moves whose occupancy equals the board.
        List<Move> candidates = new ArrayList<>();
        for (int i = 0; i < legalMoves.size(); i++) {
            if (legalOccupancies.get(i).equals(physical)) {
                candidates.add(legalMoves.get(i));
            }
        }
        BoardMismatch diff = BoardMismatch.between(expected, physical);
        if (!candidates.isEmpty()) {
            // The same occupancy can also be a plausible intermediate of a
            // DIFFERENT legal move (e.g. lifting the capturing piece equals
            // the capture's final occupancy - a capture is genuinely
            // unproven until the destination is observed to change, since
            // the destination stays "occupied" throughout). Confirmation
            // must then wait for the host's stabilization window
            // (commitPending()). This does NOT apply to castling: a rook
            // moved to its post-castling square with the king still
            // unmoved is a complete, legal, standalone rook move in its
            // own right (touch-move: castling requires touching the king
            // first) and confirms immediately, not "maybe castling still
            // coming" - see isPlausibleIntermediate's excludeExactMatches
            // handling.
            if (isPlausibleIntermediate(diff, physical, true)) {
                pendingCandidates = candidates;
                changeState(MoveDetectionState.MOVE_IN_PROGRESS);
                listener.onMoveInProgress(diff);
                return MoveDetectionResult.inProgress(diff);
            }
            return resolveExactCandidates(candidates);
        }

        // 4. No final match: plausible intermediate state of a legal move?
        if (isPlausibleIntermediate(diff, physical, false)) {
            pendingCandidates = new ArrayList<>();
            changeState(MoveDetectionState.MOVE_IN_PROGRESS);
            listener.onMoveInProgress(diff);
            return MoveDetectionResult.inProgress(diff);
        }

        // 5. Otherwise: real board mismatch.
        pendingCandidates = new ArrayList<>();
        changeState(MoveDetectionState.BOARD_MISMATCH);
        listener.onBoardMismatch(diff);
        return MoveDetectionResult.boardMismatch(diff);
    }

    /** Confirms unique candidates or raises promotion/ambiguity. */
    private MoveDetectionResult resolveExactCandidates(List<Move> candidates) {
        if (candidates.size() == 1) {
            return confirm(candidates.get(0));
        }
        pendingCandidates = candidates;
        if (isPromotionChoice(candidates)) {
            changeState(MoveDetectionState.PROMOTION_PENDING);
            listener.onPromotionRequired(pendingCandidates());
            return MoveDetectionResult.promotionRequired(pendingCandidates());
        }
        changeState(MoveDetectionState.AMBIGUOUS);
        listener.onAmbiguousMove(pendingCandidates());
        return MoveDetectionResult.ambiguous(pendingCandidates());
    }

    /**
     * Commits a pending exact match that was deferred because the physical state could also have
     * been an intermediate of another legal move. The host calls this after a short stabilization
     * window with no further physical changes (injectable scheduling — no sleeps in core logic).
     * Returns NO_CHANGE if nothing is pending.
     */
    public MoveDetectionResult commitPending() {
        if (state != MoveDetectionState.MOVE_IN_PROGRESS || pendingCandidates.isEmpty()) {
            return MoveDetectionResult.noChange();
        }
        return resolveExactCandidates(new ArrayList<>(pendingCandidates));
    }

    /**
     * Resolves an explicit, non-empty subset of the current {@link #pendingCandidates()} - same
     * single/promotion/ambiguous resolution as {@link #commitPending()}, just scoped to {@code
     * candidates} rather than the full pending set. For a host that independently narrowed the
     * ambiguity down further than "still pending" - e.g. multiple candidates target different
     * capture destinations (a pawn diagonally adjacent to two enemy pieces) and only one
     * destination was actually observed vacated, ruling the sibling(s) targeting the other
     * destination out even though occupancy alone still matches all of them. Every entry must
     * currently be pending; throws {@link IllegalArgumentException} otherwise, so a host can never
     * accidentally confirm a move that was never a real candidate.
     */
    public MoveDetectionResult resolvePendingSubset(List<Move> candidates) {
        if (state != MoveDetectionState.MOVE_IN_PROGRESS
                || candidates == null
                || candidates.isEmpty()
                || !pendingCandidates.containsAll(candidates)) {
            throw new IllegalArgumentException(
                    "candidates must be a non-empty subset of the current pendingCandidates");
        }
        return resolveExactCandidates(new ArrayList<>(candidates));
    }

    /**
     * Side-effect-free check for hosts: if {@code pendingCandidate} (a single-candidate capture
     * left unconfirmed by {@link #evaluate}, e.g. because {@link #onPhysicalBoard} just reported
     * BOARD_MISMATCH for {@code physical} against the still-uncommitted current position) were
     * already committed, would {@code physical} instead resolve to something other than
     * BOARD_MISMATCH from the resulting position?
     *
     * <p>A capture's destination stays "occupied" throughout (first by the captured piece, then by
     * the capturing one), so occupancy alone can never prove it is done - that ambiguity is
     * fundamental and stays (see {@link #commitPending()}'s doc, and the "positive proof" pattern
     * of observing the destination vacated). But a <em>subsequent</em> move that only makes sense
     * once that capture is treated as finished - typically the opponent's reply - is itself proof
     * of exactly that, without ever needing an explicit confirmation. This lets a host recognize
     * that case safely: it never commits to a guess it cannot take back, since nothing is mutated
     * unless the resulting position genuinely explains {@code physical}.
     *
     * <p>Use: on a fresh BOARD_MISMATCH, check this with the candidate that was pending immediately
     * before (see {@link #pendingCandidates()} captured prior to the {@link #onPhysicalBoard} call
     * - evaluate() already clears the real pendingCandidates on its way to BOARD_MISMATCH, which is
     * exactly why this takes the candidate as a parameter instead of reading it back off this
     * detector); if it returns {@code true}, call {@link #commitRecoveredCapture} with the same
     * candidate and then {@link #onPhysicalBoard} again for real.
     */
    public boolean wouldResolveIfCommitted(Move pendingCandidate, BoardState physical) {
        if (pendingCandidate == null || physical == null) {
            return false;
        }
        ChessPosition hypothetical = position.apply(pendingCandidate);
        MoveDetector trial = new MoveDetector(hypothetical, null);
        trial.onPhysicalBoard(OccupancyProjection.occupancyOf(hypothetical));
        BoardState normalized = OccupancyProjection.normalize(physical);
        return trial.onPhysicalBoard(normalized).kind() != MoveDetectionResult.Kind.BOARD_MISMATCH;
    }

    /**
     * Commits {@code move} directly against the current position, for a host recovering a capture
     * per {@link #wouldResolveIfCommitted} after evaluate() already cleared pendingCandidates on
     * its way to BOARD_MISMATCH (so {@link #commitPending()} would be a no-op). {@code move} must
     * be the same candidate {@link #wouldResolveIfCommitted} was just called with - this performs
     * no re-validation of its own. Also clears the idempotency guard ({@link #onPhysicalBoard}
     * normally skips a state it already saw): the physical state that triggered this recovery was
     * already recorded verbatim as unchanged/mismatched against the <em>old</em> position, so the
     * next {@link #onPhysicalBoard} call - typically with that very same state, now evaluated
     * against the newly-committed position - must not be short-circuited as a no-op duplicate.
     */
    public MoveDetectionResult commitRecoveredCapture(Move move) {
        MoveDetectionResult result = confirm(move);
        lastPhysical = null;
        return result;
    }

    /**
     * A state is a plausible intermediate if it can be explained as the current position with up to
     * {@link #MAX_LIFTED_PIECES} pieces lifted, or as progress towards one legal move (its
     * freed/gained squares) with up to {@link #MAX_UNRELATED_LIFTS} additional unrelated lifts.
     *
     * @param excludeExactMatches if true, an exact candidate was already found for {@code physical}
     *     (see evaluate(), step 3): only the "still in hand" ambiguity (unexpected empty -
     *     occupancy can't tell a completed capture from the attacker merely being lifted) can still
     *     defer it. A non-empty {@code unexpected} means a square was observably newly occupied,
     *     i.e. something was actually placed down - the found candidate is complete on its own
     *     terms, so a different legal move's unrelated intermediate state (e.g. castling's
     *     rook-first step) is not grounds to withhold confirmation.
     */
    private boolean isPlausibleIntermediate(
            BoardMismatch diff, BoardState physical, boolean excludeExactMatches) {
        List<Integer> missing = diff.missingOccupied();
        List<Integer> unexpected = diff.unexpectedOccupied();
        if (unexpected.isEmpty()) {
            return !missing.isEmpty() && missing.size() <= MAX_LIFTED_PIECES;
        }
        if (excludeExactMatches) {
            return false;
        }
        for (int i = 0; i < legalMoves.size(); i++) {
            BoardState after = legalOccupancies.get(i);
            if (excludeExactMatches && after.equals(physical)) {
                continue;
            }
            boolean unexpectedExplained = true;
            for (int square : unexpected) {
                if (!after.isOccupied(square)) {
                    unexpectedExplained = false;
                    break;
                }
            }
            if (!unexpectedExplained) {
                continue;
            }
            int unrelatedLifts = 0;
            for (int square : missing) {
                if (after.isOccupied(square)) {
                    unrelatedLifts++; // lifted although the move keeps it occupied
                }
            }
            // Physical constraint: pieces placed on the move's target squares
            // must come from squares the move actually frees (already lifted).
            int coveredLifts = missing.size() - unrelatedLifts;
            if (unrelatedLifts <= MAX_UNRELATED_LIFTS && unexpected.size() <= coveredLifts) {
                return true;
            }
        }
        return false;
    }

    /** True if all candidates share from/to and differ only in promotion. */
    private static boolean isPromotionChoice(List<Move> candidates) {
        Move first = candidates.get(0);
        for (Move candidate : candidates) {
            if (candidate.promotion() == null
                    || candidate.from() != first.from()
                    || candidate.to() != first.to()) {
                return false;
            }
        }
        return true;
    }

    /** Atomic confirmation: position update first, then event. */
    private MoveDetectionResult confirm(Move move) {
        ChessPosition newPosition = position.apply(move);
        setPosition(newPosition);
        pendingCandidates = new ArrayList<>();
        changeState(MoveDetectionState.SYNCHRONIZED);
        listener.onMoveConfirmed(move, newPosition);
        return MoveDetectionResult.confirmed(move, newPosition);
    }

    private void setPosition(ChessPosition newPosition) {
        position = newPosition;
        expected = OccupancyProjection.occupancyOf(newPosition);
        legalMoves = newPosition.legalMoves();
        legalOccupancies = new ArrayList<>(legalMoves.size());
        for (Move move : legalMoves) {
            legalOccupancies.add(OccupancyProjection.occupancyOf(newPosition.apply(move)));
        }
    }

    private void changeState(MoveDetectionState newState) {
        if (state != newState) {
            state = newState;
            listener.onStateChanged(newState);
        }
    }
}
