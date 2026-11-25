/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.vision;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

/**
 * Draws the playing area found in the camera picture over it (see {@link BoardCameraActivity}). It
 * lies exactly over a preview that shows the whole picture, centred, so a point of the picture is
 * found here by the same fitting.
 */
public final class BoardOutlineView extends View {

    private static final int GOOD = 0xFF4CAF50;
    private static final int NOT_YET = 0xFFFFC107;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    /** Corners as x, y pairs in fractions of the picture, or {@code null} if there is no board. */
    private float[] corners;

    /** Width of the picture divided by its height. */
    private float aspect = 0.75f;

    public BoardOutlineView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeWidth(4 * getResources().getDisplayMetrics().density);
    }

    /**
     * @param corners the four corners as x, y pairs in fractions of the picture, or {@code null}
     * @param aspect width of the picture divided by its height
     * @param good whether the photo can be taken like this
     */
    public void show(float[] corners, float aspect, boolean good) {
        this.corners = corners;
        this.aspect = aspect;
        paint.setColor(good ? GOOD : NOT_YET);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (corners == null) {
            return;
        }
        float width = Math.min(getWidth(), getHeight() * aspect);
        float height = width / aspect;
        float left = (getWidth() - width) / 2;
        float top = (getHeight() - height) / 2;
        path.rewind();
        for (int i = 0; i < 4; i++) {
            float x = left + corners[2 * i] * width;
            float y = top + corners[2 * i + 1] * height;
            if (i == 0) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }
        }
        path.close();
        canvas.drawPath(path, paint);
    }
}
