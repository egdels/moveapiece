/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.collector;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/** A small diagram of the position to set up, seen from White's side. */
public final class DiagramView extends View {

    private static final String SYMBOLS = "KQRBNPkqrbnp";
    private static final String GLYPHS = "♚♛♜♝♞♟♚♛♜♝♞♟";

    private final Paint square = new Paint();
    private final Paint piece = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final char[] board = new char[64];

    public DiagramView(Context context, AttributeSet attrs) {
        super(context, attrs);
        piece.setTextAlign(Paint.Align.CENTER);
        edge.setTextAlign(Paint.Align.CENTER);
        edge.setStyle(Paint.Style.STROKE);
        edge.setColor(0xFF000000);
        show("8/8/8/8/8/8/8/8");
    }

    /** Shows a piece placement as in a FEN. */
    public void show(String placement) {
        java.util.Arrays.fill(board, ' ');
        String[] rows = placement.split("/");
        for (int row = 0; row < 8 && row < rows.length; row++) {
            int col = 0;
            for (char c : rows[row].toCharArray()) {
                if (Character.isDigit(c)) {
                    col += c - '0';
                } else if (col < 8) {
                    board[row * 8 + col++] = c;
                }
            }
        }
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int side =
                Math.min(
                        MeasureSpec.getSize(widthMeasureSpec),
                        MeasureSpec.getSize(heightMeasureSpec));
        setMeasuredDimension(side, side);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cell = Math.min(getWidth(), getHeight()) / 8f;
        piece.setTextSize(cell * 0.82f);
        edge.setTextSize(cell * 0.82f);
        edge.setStrokeWidth(cell * 0.05f);
        float baseline = cell * 0.5f - (piece.descent() + piece.ascent()) / 2;
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                square.setColor((row + col) % 2 == 0 ? 0xFFEEEED2 : 0xFF9AA5A0);
                canvas.drawRect(col * cell, row * cell, (col + 1) * cell, (row + 1) * cell, square);
                int index = SYMBOLS.indexOf(board[row * 8 + col]);
                if (index < 0) {
                    continue;
                }
                String glyph = String.valueOf(GLYPHS.charAt(index));
                float x = (col + 0.5f) * cell;
                float y = row * cell + baseline;
                boolean white = index < 6;
                piece.setColor(white ? 0xFFFFFFFF : 0xFF000000);
                if (white) {
                    canvas.drawText(glyph, x, y, edge);
                }
                canvas.drawText(glyph, x, y, piece);
            }
        }
    }
}
