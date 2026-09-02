/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.Square;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Runs on-device (needs a real Context for resources/BitmapFactory, and real View
 * measure/layout/touch dispatch). Covers the square-sizing fallback for ScrollView's UNSPECIFIED
 * height spec and the tap-to-move input handling (selection, legal-destination gating,
 * interactivity, board flip) that are otherwise only exercised by hand on the emulator.
 */
@RunWith(AndroidJUnit4.class)
public class BoardViewTest {

    private static final int SIZE = 800; // px; divisible by 8 -> 100px/square

    private BoardView boardView;

    @Before
    public void setUp() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        boardView = new BoardView(context, null);
        Piece[] pieces = new Piece[64];
        Arrays.fill(pieces, Piece.NONE);
        pieces[Square.E2.ordinal()] = Piece.WHITE_PAWN;
        pieces[Square.E7.ordinal()] = Piece.BLACK_PAWN;
        boardView.setBoard(pieces);
        layout(SIZE, SIZE);
    }

    private void layout(int width, int height) {
        int widthSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY);
        boardView.measure(widthSpec, heightSpec);
        boardView.layout(0, 0, boardView.getMeasuredWidth(), boardView.getMeasuredHeight());
    }

    private void tapDown(float x, float y) {
        long time = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, x, y, 0);
        try {
            boardView.onTouchEvent(down);
        } finally {
            down.recycle();
        }
    }

    // Matches BoardView's own non-flipped mapping: col = file (a=0..h=7),
    // row = 7 - (rank - 1), i.e. row 0 is the top of the screen (rank 8).
    private float xFor(int col) {
        return (col + 0.5f) * (SIZE / 8f);
    }

    private float yFor(int row) {
        return (row + 0.5f) * (SIZE / 8f);
    }

    private static class RecordingMoveSource implements BoardView.MoveSource {
        final List<Square> queriedFrom = new ArrayList<>();
        List<Square> legalDestinations = Collections.emptyList();
        Square ownPieceSquare;

        @Override
        public List<Square> legalDestinationsFrom(Square from) {
            queriedFrom.add(from);
            return legalDestinations;
        }

        @Override
        public boolean hasOwnPieceOn(Square square) {
            return square == ownPieceSquare;
        }
    }

    private static class RecordingMoveListener implements BoardView.OnMoveListener {
        final List<Square[]> moves = new ArrayList<>();

        @Override
        public void onMoveChosen(Square from, Square to) {
            moves.add(new Square[] {from, to});
        }
    }

    @Test
    public void measure_producesSquareDimensions_smallerOfWidthAndHeight() {
        layout(600, 1000);
        assertEquals(600, boardView.getMeasuredWidth());
        assertEquals(600, boardView.getMeasuredHeight());
    }

    @Test
    public void measure_withUnspecifiedHeight_fallsBackToWidth() {
        // What a ScrollView gives its content: bounded width, UNSPECIFIED height.
        int widthSpec = View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        boardView.measure(widthSpec, heightSpec);
        assertEquals(500, boardView.getMeasuredWidth());
        assertEquals(500, boardView.getMeasuredHeight());
    }

    @Test
    public void tapOwnPiece_thenLegalDestination_firesMoveListener() {
        RecordingMoveSource moveSource = new RecordingMoveSource();
        moveSource.ownPieceSquare = Square.E2;
        moveSource.legalDestinations = Arrays.asList(Square.E3, Square.E4);
        RecordingMoveListener listener = new RecordingMoveListener();
        boardView.setMoveSource(moveSource);
        boardView.setOnMoveListener(listener);

        tapDown(xFor(4), yFor(6)); // e2
        tapDown(xFor(4), yFor(4)); // e4

        assertEquals(1, listener.moves.size());
        assertEquals(Square.E2, listener.moves.get(0)[0]);
        assertEquals(Square.E4, listener.moves.get(0)[1]);
    }

    @Test
    public void tapNonOwnedSquare_doesNotSelectOrQueryDestinations() {
        RecordingMoveSource moveSource = new RecordingMoveSource();
        moveSource.ownPieceSquare = Square.E2; // e7 (black pawn) is not "own"
        RecordingMoveListener listener = new RecordingMoveListener();
        boardView.setMoveSource(moveSource);
        boardView.setOnMoveListener(listener);

        tapDown(xFor(4), yFor(1)); // e7

        assertTrue(moveSource.queriedFrom.isEmpty());
        assertTrue(listener.moves.isEmpty());
    }

    @Test
    public void tapOutsideLegalDestinations_doesNotFireMove() {
        RecordingMoveSource moveSource = new RecordingMoveSource();
        moveSource.ownPieceSquare = Square.E2;
        moveSource.legalDestinations = Collections.singletonList(Square.E4);
        RecordingMoveListener listener = new RecordingMoveListener();
        boardView.setMoveSource(moveSource);
        boardView.setOnMoveListener(listener);

        tapDown(xFor(4), yFor(6)); // select e2
        tapDown(xFor(0), yFor(0)); // a8: not a legal destination, not "own" either

        assertTrue(listener.moves.isEmpty());
    }

    @Test
    public void setInteractiveFalse_ignoresTouches() {
        RecordingMoveSource moveSource = new RecordingMoveSource();
        moveSource.ownPieceSquare = Square.E2;
        moveSource.legalDestinations = Collections.singletonList(Square.E4);
        RecordingMoveListener listener = new RecordingMoveListener();
        boardView.setMoveSource(moveSource);
        boardView.setOnMoveListener(listener);
        boardView.setInteractive(false);

        tapDown(xFor(4), yFor(6));
        tapDown(xFor(4), yFor(4));

        assertTrue(listener.moves.isEmpty());
    }

    @Test
    public void clearSelection_afterSelecting_dropsLegalDestinationState() {
        RecordingMoveSource moveSource = new RecordingMoveSource();
        moveSource.ownPieceSquare = Square.E2;
        moveSource.legalDestinations = Collections.singletonList(Square.E4);
        RecordingMoveListener listener = new RecordingMoveListener();
        boardView.setMoveSource(moveSource);
        boardView.setOnMoveListener(listener);

        tapDown(xFor(4), yFor(6)); // select e2
        boardView.clearSelection();
        tapDown(xFor(4), yFor(4)); // e4: no longer a "chosen" destination, and not an own piece

        assertTrue(listener.moves.isEmpty());
    }

    @Test
    public void flipped_mapsTopLeftTapToH1() {
        // A 180-degree flip moves h1 (bottom-right in the normal White-at-
        // bottom view) to the top-left corner of the screen.
        boardView.setFlipped(true);
        RecordingMoveSource moveSource = new RecordingMoveSource();
        moveSource.ownPieceSquare = Square.H1;
        boardView.setMoveSource(moveSource);
        boardView.setOnMoveListener(new RecordingMoveListener());

        tapDown(xFor(0), yFor(0));

        assertEquals(1, moveSource.queriedFrom.size());
        assertEquals(Square.H1, moveSource.queriedFrom.get(0));
    }
}
