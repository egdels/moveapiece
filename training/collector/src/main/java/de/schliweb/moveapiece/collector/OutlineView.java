/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.collector;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

/**
 * Draws the playing area found in the camera picture over it. It lies exactly over a preview that
 * shows the whole picture, centred, so a point of the picture is found here by the same fitting.
 * (The same as BoardOutlineView of the app, which this tool does not depend on.)
 */
public final class OutlineView extends View {

    private static final int GOOD = 0xFF4CAF50;
    private static final int NOT_YET = 0xFFFFC107;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    /** Corners as x, y pairs in fractions of the picture, or {@code null} if there is no board. */
    private float[] corners;

    /** Width of the picture divided by its height. */
    private float aspect = 0.75f;

    private boolean target;

    /** Where the line for the foot of a single piece is, as a share of the picture's height. */
    static final float TARGET_Y = 0.66f;

    /**
     * The frame around the piece: half its width and its height above the line, in widths. It is
     * small on purpose: a piece that just fits is about 45 cm away, nearly as far as the pieces of
     * a board photographed whole. Measured with the black king, 30 mm at the foot: a frame 0.4 wide
     * and 0.62 high was filled from 12 cm, and from there the king is 2.66 times as high as its
     * foot is wide where it should be about 2.2; a frame 0.18 by 0.28 was filled from 26 cm (2.37
     * times).
     */
    static final float FRAME_HALF_WIDTH = 0.055f;

    static final float FRAME_HEIGHT = 0.17f;

    private boolean targetGood = true;

    public OutlineView(Context context, AttributeSet attrs) {
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
    /**
     * Shows or hides a mark in the middle of the picture, where a single piece should stand: only
     * there the tilt of the phone is the angle the piece is seen from.
     */
    public void showTarget(boolean target) {
        this.target = target;
        invalidate();
    }

    /** Whether the frame around the piece shows nothing but the surface: green, else amber. */
    public void targetGood(boolean good) {
        if (good != targetGood) {
            targetGood = good;
            invalidate();
        }
    }

    public void show(float[] corners, float aspect, boolean good) {
        this.corners = corners;
        this.aspect = aspect;
        paint.setColor(good ? GOOD : NOT_YET);
        invalidate();
    }

    /** The middle of the frame around a single piece, in pixels of this view. */
    float[] middleOfFrame() {
        float width = Math.min(getWidth(), getHeight() * aspect);
        float height = width / aspect;
        float top = (getHeight() - height) / 2;
        return new float[] {
            getWidth() / 2f, top + TARGET_Y * height - (FRAME_HEIGHT - 0.02f) / 2 * width
        };
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // Lines stay as thick as they are when the view is shown enlarged.
        paint.setStrokeWidth(4 * getResources().getDisplayMetrics().density / getScaleX());
        float width = Math.min(getWidth(), getHeight() * aspect);
        float height = width / aspect;
        float left = (getWidth() - width) / 2;
        float top = (getHeight() - height) / 2;
        if (target) {
            // The piece stands on the short line, below the middle so that all of it is in the
            // picture, and inside the frame above it.
            float x = left + width / 2;
            float y = top + TARGET_Y * height;
            int before = paint.getColor();
            paint.setColor(targetGood ? GOOD : NOT_YET);
            canvas.drawLine(x - 0.025f * width, y, x + 0.025f * width, y, paint);
            canvas.drawRoundRect(
                    x - FRAME_HALF_WIDTH * width,
                    y - FRAME_HEIGHT * width,
                    x + FRAME_HALF_WIDTH * width,
                    y + 0.02f * width,
                    0.012f * width,
                    0.012f * width,
                    paint);
            paint.setColor(before);
        }
        if (corners == null) {
            return;
        }
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
