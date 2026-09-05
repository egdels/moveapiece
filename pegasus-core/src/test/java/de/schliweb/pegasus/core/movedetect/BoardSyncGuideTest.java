/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.pegasus.core.movedetect;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import de.schliweb.pegasus.core.chess.ChessPosition;
import de.schliweb.pegasus.core.chess.Move;
import de.schliweb.pegasus.core.chess.OccupancyProjection;
import de.schliweb.pegasus.core.protocol.BoardState;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class BoardSyncGuideTest {

    private final List<List<Integer>> indications = new ArrayList<>();
    private int reachedCount;

    private final BoardSyncGuide guide =
            new BoardSyncGuide(
                    new BoardSyncGuide.Listener() {
                        @Override
                        public void onIndicate(List<Integer> squares) {
                            indications.add(new ArrayList<>(squares));
                        }

                        @Override
                        public void onTargetReached() {
                            reachedCount++;
                        }
                    });

    private static int sq(String name) {
        return BoardState.squareIndex(name);
    }

    private static BoardState lift(BoardState state, String square) {
        return state.withSquare(sq(square), OccupancyProjection.EMPTY);
    }

    private static BoardState place(BoardState state, String square) {
        return state.withSquare(sq(square), OccupancyProjection.OCCUPIED);
    }

    @Test
    public void guidesOpponentMoveToCompletion() {
        ChessPosition start = ChessPosition.starting();
        ChessPosition afterMove = start.apply(Move.fromUci("e2e4"));
        BoardState physical = OccupancyProjection.occupancyOf(start);
        guide.start(OccupancyProjection.occupancyOf(afterMove), physical);
        assertTrue(guide.isActive());
        // initial diff: e4 missing, e2 unexpected
        assertEquals(1, indications.size());
        assertEquals(Arrays.asList(sq("e4"), sq("e2")), indications.get(0));

        physical = lift(physical, "e2");
        guide.onPhysicalBoard(physical);
        assertEquals(2, indications.size());
        assertEquals(Arrays.asList(sq("e4")), indications.get(1));

        physical = place(physical, "e4");
        guide.onPhysicalBoard(physical);
        assertEquals(1, reachedCount);
        assertFalse(guide.isActive());
    }

    @Test
    public void indicatesDeviationOnWrongPlacement() {
        ChessPosition start = ChessPosition.starting().apply(Move.fromUci("e2e4"));
        ChessPosition afterMove = start.apply(Move.fromUci("g8f6"));
        BoardState physical = OccupancyProjection.occupancyOf(start);
        guide.start(OccupancyProjection.occupancyOf(afterMove), physical);

        physical = lift(physical, "g8");
        physical = place(physical, "e6"); // wrong square
        guide.onPhysicalBoard(physical);
        List<Integer> last = indications.get(indications.size() - 1);
        assertEquals(Arrays.asList(sq("f6"), sq("e6")), last);

        physical = lift(physical, "e6");
        physical = place(physical, "f6");
        guide.onPhysicalBoard(physical);
        assertEquals(1, reachedCount);
        assertFalse(guide.isActive());
    }

    @Test
    public void handlesCastlingTarget() {
        ChessPosition position =
                ChessPosition.fromFen(
                        "r1bqkbnr/pppp1ppp/2n5/4p3/2B1P3/5N2/PPPP1PPP/RNBQK2R w KQkq - 4 4");
        ChessPosition after = position.apply(Move.fromUci("e1g1"));
        BoardState physical = OccupancyProjection.occupancyOf(position);
        guide.start(OccupancyProjection.occupancyOf(after), physical);
        // diff: g1+f1 missing, e1+h1 unexpected
        assertEquals(Arrays.asList(sq("f1"), sq("g1"), sq("e1"), sq("h1")), indications.get(0));

        physical = lift(physical, "e1");
        physical = place(physical, "g1");
        physical = lift(physical, "h1");
        physical = place(physical, "f1");
        guide.onPhysicalBoard(physical);
        assertEquals(1, reachedCount);
    }

    @Test
    public void unchangedDiffIsNotReindicated() {
        ChessPosition start = ChessPosition.starting();
        ChessPosition afterMove = start.apply(Move.fromUci("e2e4"));
        BoardState physical = OccupancyProjection.occupancyOf(start);
        guide.start(OccupancyProjection.occupancyOf(afterMove), physical);
        guide.onPhysicalBoard(physical);
        guide.onPhysicalBoard(physical);
        assertEquals(1, indications.size());
    }

    @Test
    public void startWithMatchingBoardCompletesImmediately() {
        ChessPosition start = ChessPosition.starting();
        BoardState physical = OccupancyProjection.occupancyOf(start);
        guide.start(OccupancyProjection.occupancyOf(start), physical);
        assertEquals(1, reachedCount);
        assertTrue(indications.isEmpty());
        assertFalse(guide.isActive());
    }

    @Test
    public void cancelStopsGuidance() {
        ChessPosition start = ChessPosition.starting();
        ChessPosition afterMove = start.apply(Move.fromUci("e2e4"));
        BoardState physical = OccupancyProjection.occupancyOf(start);
        guide.start(OccupancyProjection.occupancyOf(afterMove), physical);
        guide.cancel();
        assertFalse(guide.isActive());
        guide.onPhysicalBoard(lift(physical, "e2"));
        assertEquals(1, indications.size()); // no further callbacks
        assertEquals(0, reachedCount);
    }
}
