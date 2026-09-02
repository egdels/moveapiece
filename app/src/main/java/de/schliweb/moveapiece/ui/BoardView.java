/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.Square;
import de.schliweb.moveapiece.R;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

/**
 * Renders an 8x8 chess board and turns taps into move requests. Holds no chess rules itself: it
 * asks {@link MoveSource} for legal destinations and reports chosen moves to {@link
 * OnMoveListener}, both driven by whoever owns the {@link de.schliweb.moveapiece.logic.ChessGame}
 * (MainActivity).
 */
public class BoardView extends View {

    /** Supplies legal destination squares for a given origin, so the view can highlight them. */
    public interface MoveSource {
        List<Square> legalDestinationsFrom(Square from);

        boolean hasOwnPieceOn(Square square);
    }

    public interface OnMoveListener {
        void onMoveChosen(Square from, Square to);
    }

    private final Paint lightPaint = new Paint();
    private final Paint darkPaint = new Paint();
    private final Paint selectedPaint = new Paint();
    private final Paint legalMovePaint = new Paint();
    private final Paint legalCaptureRingPaint = new Paint();
    private final Paint lastMovePaint = new Paint();
    private final Paint checkPaint = new Paint();
    private final Paint trainingHintPaint = new Paint();
    private final Paint piecePaint = new Paint();
    private final RectF reusablePieceRect = new RectF();

    private final EnumMap<Piece, Integer> pieceDrawableIds = new EnumMap<>(Piece.class);
    private final EnumMap<Piece, Bitmap> pieceBitmaps = new EnumMap<>(Piece.class);

    private Piece[] boardState = new Piece[64];
    private boolean flipped = false;
    private Square selected = null;
    private List<Square> legalDestinations = new ArrayList<>();
    private Square lastMoveFrom = null;
    private Square lastMoveTo = null;
    private Square checkedKingSquare = null;
    private Square trainingHintFrom = null;
    private Square trainingHintTo = null;

    private MoveSource moveSource;
    private OnMoveListener onMoveListener;
    private boolean interactive = true;

    private float squareSize;

    public BoardView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        lightPaint.setColor(getResources().getColor(R.color.board_light, null));
        darkPaint.setColor(getResources().getColor(R.color.board_dark, null));
        selectedPaint.setColor(getResources().getColor(R.color.board_selected, null));
        legalMovePaint.setColor(getResources().getColor(R.color.board_legal_move, null));
        legalMovePaint.setAntiAlias(true);
        legalCaptureRingPaint.setColor(getResources().getColor(R.color.board_legal_move, null));
        legalCaptureRingPaint.setAntiAlias(true);
        legalCaptureRingPaint.setStyle(Paint.Style.STROKE);
        lastMovePaint.setColor(getResources().getColor(R.color.board_last_move, null));
        checkPaint.setColor(getResources().getColor(R.color.board_check, null));
        trainingHintPaint.setColor(getResources().getColor(R.color.board_training_hint, null));
        // Piece bitmaps are fixed 256x256 sources scaled to whatever the board's
        // current square size is; without filtering, that scale (almost never an
        // exact match) produces visibly jagged/pixelated edges on curves like the
        // knight's mane.
        piecePaint.setFilterBitmap(true);
        piecePaint.setAntiAlias(true);

        pieceDrawableIds.put(Piece.WHITE_KING, R.drawable.piece_wk);
        pieceDrawableIds.put(Piece.WHITE_QUEEN, R.drawable.piece_wq);
        pieceDrawableIds.put(Piece.WHITE_ROOK, R.drawable.piece_wr);
        pieceDrawableIds.put(Piece.WHITE_BISHOP, R.drawable.piece_wb);
        pieceDrawableIds.put(Piece.WHITE_KNIGHT, R.drawable.piece_wn);
        pieceDrawableIds.put(Piece.WHITE_PAWN, R.drawable.piece_wp);
        pieceDrawableIds.put(Piece.BLACK_KING, R.drawable.piece_bk);
        pieceDrawableIds.put(Piece.BLACK_QUEEN, R.drawable.piece_bq);
        pieceDrawableIds.put(Piece.BLACK_ROOK, R.drawable.piece_br);
        pieceDrawableIds.put(Piece.BLACK_BISHOP, R.drawable.piece_bb);
        pieceDrawableIds.put(Piece.BLACK_KNIGHT, R.drawable.piece_bn);
        pieceDrawableIds.put(Piece.BLACK_PAWN, R.drawable.piece_bp);

        for (EnumMap.Entry<Piece, Integer> entry : pieceDrawableIds.entrySet()) {
            Bitmap bmp = BitmapFactory.decodeResource(getResources(), entry.getValue());
            pieceBitmaps.put(entry.getKey(), bmp);
        }
    }

    public void setMoveSource(MoveSource moveSource) {
        this.moveSource = moveSource;
    }

    public void setOnMoveListener(OnMoveListener listener) {
        this.onMoveListener = listener;
    }

    public void setInteractive(boolean interactive) {
        this.interactive = interactive;
        if (!interactive) {
            clearSelection();
        }
    }

    public void setFlipped(boolean flipped) {
        this.flipped = flipped;
        invalidate();
    }

    public void setLastMove(Square from, Square to) {
        this.lastMoveFrom = from;
        this.lastMoveTo = to;
        invalidate();
    }

    public void setCheckedKingSquare(Square square) {
        this.checkedKingSquare = square;
        invalidate();
    }

    /** The opening trainer's next expected move, or {@code null,null} to clear it. */
    public void setTrainingHint(Square from, Square to) {
        this.trainingHintFrom = from;
        this.trainingHintTo = to;
        invalidate();
    }

    /**
     * @param pieces indexed by {@link Square#ordinal()}, {@link Piece#NONE} for empty squares
     */
    public void setBoard(Piece[] pieces) {
        this.boardState = pieces;
        invalidate();
    }

    public void clearSelection() {
        selected = null;
        legalDestinations = new ArrayList<>();
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        squareSize = Math.min(w, h) / 8f;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        // Inside a ScrollView the height spec is UNSPECIFIED (size 0), since
        // the container lets its content size itself; fall back to the
        // width alone so the board still renders as a square there.
        int size;
        if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) {
            size = width;
        } else {
            size = Math.min(width, MeasureSpec.getSize(heightMeasureSpec));
        }
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (squareSize <= 0) {
            return;
        }
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                Square square = squareAt(row, col);
                boolean isLight = (row + col) % 2 == 0;
                Paint base = isLight ? lightPaint : darkPaint;
                float left = col * squareSize;
                float top = row * squareSize;
                canvas.drawRect(left, top, left + squareSize, top + squareSize, base);

                if (square == lastMoveFrom || square == lastMoveTo) {
                    canvas.drawRect(left, top, left + squareSize, top + squareSize, lastMovePaint);
                }
                if (square == trainingHintFrom || square == trainingHintTo) {
                    canvas.drawRect(
                            left, top, left + squareSize, top + squareSize, trainingHintPaint);
                }
                if (square == checkedKingSquare) {
                    canvas.drawRect(left, top, left + squareSize, top + squareSize, checkPaint);
                }
                if (square == selected) {
                    canvas.drawRect(left, top, left + squareSize, top + squareSize, selectedPaint);
                }

                Piece piece = boardState[square.ordinal()];
                if (piece != null && piece != Piece.NONE) {
                    Bitmap bmp = pieceBitmaps.get(piece);
                    if (bmp != null) {
                        reusablePieceRect.set(left, top, left + squareSize, top + squareSize);
                        canvas.drawBitmap(bmp, null, reusablePieceRect, piecePaint);
                    }
                }

                if (legalDestinations.contains(square)) {
                    float cx = left + squareSize / 2f;
                    float cy = top + squareSize / 2f;
                    boolean capture = boardState[square.ordinal()] != Piece.NONE;
                    float radius = capture ? squareSize * 0.46f : squareSize * 0.16f;
                    if (capture) {
                        legalCaptureRingPaint.setStrokeWidth(squareSize * 0.08f);
                        canvas.drawCircle(cx, cy, radius, legalCaptureRingPaint);
                    } else {
                        canvas.drawCircle(cx, cy, radius, legalMovePaint);
                    }
                }
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!interactive || event.getAction() != MotionEvent.ACTION_DOWN || squareSize <= 0) {
            return interactive;
        }
        int col = (int) (event.getX() / squareSize);
        int row = (int) (event.getY() / squareSize);
        if (col < 0 || col > 7 || row < 0 || row > 7) {
            return true;
        }
        Square tapped = squareAt(row, col);
        handleTap(tapped);
        performClick();
        return true;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private void handleTap(Square tapped) {
        if (selected != null && legalDestinations.contains(tapped)) {
            Square from = selected;
            clearSelection();
            if (onMoveListener != null) {
                onMoveListener.onMoveChosen(from, tapped);
            }
            return;
        }

        if (moveSource != null && moveSource.hasOwnPieceOn(tapped)) {
            selected = tapped;
            legalDestinations = moveSource.legalDestinationsFrom(tapped);
        } else {
            clearSelection();
        }
        invalidate();
    }

    private Square squareAt(int row, int col) {
        int file = flipped ? 7 - col : col;
        int rank = flipped ? row : 7 - row;
        int ordinal = rank * 8 + file;
        return Square.squareAt(ordinal);
    }
}
