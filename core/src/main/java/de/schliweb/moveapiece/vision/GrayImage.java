/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.vision;

/** A greyscale picture, brightness 0..255 per pixel, row by row. */
final class GrayImage {

    final int width;
    final int height;
    final float[] data;

    GrayImage(int width, int height, float[] data) {
        this.width = width;
        this.height = height;
        this.data = data;
    }

    /**
     * The photo in grey, shrunk so that its longer side is {@code maxSide} pixels (never enlarged).
     * Every pixel of the result is the average of the photo pixels it covers, so fine patterns do
     * not turn into false ones.
     */
    static GrayImage shrunk(Photo photo, int maxSide) {
        int w = photo.width;
        int h = photo.height;
        double scale = Math.min(1.0, (double) maxSide / Math.max(w, h));
        int dw = Math.max(1, (int) Math.round(w * scale));
        int dh = Math.max(1, (int) Math.round(h * scale));
        // Horizontal pass on the fly from the packed colours, then the vertical pass.
        float[] rows = new float[dw * h];
        double sx = (double) w / dw;
        for (int y = 0; y < h; y++) {
            int base = y * w;
            for (int x = 0; x < dw; x++) {
                double x0 = x * sx;
                double x1 = x0 + sx;
                int first = (int) x0;
                int last = Math.min(w - 1, (int) Math.ceil(x1) - 1);
                double sum = 0;
                for (int i = first; i <= last; i++) {
                    double weight = Math.min(x1, i + 1) - Math.max(x0, i);
                    sum += weight * gray(photo.rgb[base + i]);
                }
                rows[y * dw + x] = (float) (sum / sx);
            }
        }
        float[] out = new float[dw * dh];
        double sy = (double) h / dh;
        for (int y = 0; y < dh; y++) {
            double y0 = y * sy;
            double y1 = y0 + sy;
            int first = (int) y0;
            int last = Math.min(h - 1, (int) Math.ceil(y1) - 1);
            for (int x = 0; x < dw; x++) {
                double sum = 0;
                for (int i = first; i <= last; i++) {
                    double weight = Math.min(y1, i + 1) - Math.max(y0, i);
                    sum += weight * rows[i * dw + x];
                }
                out[y * dw + x] = (float) (sum / sy);
            }
        }
        return new GrayImage(dw, dh, out);
    }

    private static double gray(int rgb) {
        return 0.299 * ((rgb >> 16) & 0xFF) + 0.587 * ((rgb >> 8) & 0xFF) + 0.114 * (rgb & 0xFF);
    }

    /** The picture at half the size; every pixel is the average of the four it covers. */
    GrayImage halved() {
        int w = width / 2;
        int h = height / 2;
        float[] out = new float[w * h];
        for (int y = 0; y < h; y++) {
            int row = 2 * y * width;
            for (int x = 0; x < w; x++) {
                int p = row + 2 * x;
                out[y * w + x] =
                        (data[p] + data[p + 1] + data[p + width] + data[p + width + 1]) / 4;
            }
        }
        return new GrayImage(w, h, out);
    }

    /** Gaussian blur; the border is mirrored. */
    GrayImage blurred(double sigma) {
        int radius = (int) Math.round(sigma * 3);
        float[] kernel = new float[2 * radius + 1];
        float total = 0;
        for (int i = -radius; i <= radius; i++) {
            kernel[i + radius] = (float) Math.exp(-(i * i) / (2 * sigma * sigma));
            total += kernel[i + radius];
        }
        for (int i = 0; i < kernel.length; i++) {
            kernel[i] /= total;
        }
        float[] tmp = new float[data.length];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                float sum = 0;
                for (int k = -radius; k <= radius; k++) {
                    sum += kernel[k + radius] * data[y * width + mirror(x + k, width)];
                }
                tmp[y * width + x] = sum;
            }
        }
        float[] out = new float[data.length];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                float sum = 0;
                for (int k = -radius; k <= radius; k++) {
                    sum += kernel[k + radius] * tmp[mirror(y + k, height) * width + x];
                }
                out[y * width + x] = sum;
            }
        }
        return new GrayImage(width, height, out);
    }

    private static int mirror(int i, int n) {
        if (n == 1) {
            return 0;
        }
        while (i < 0 || i >= n) {
            i = i < 0 ? -i : 2 * (n - 1) - i;
        }
        return i;
    }

    /** Brightness at the pixel nearest to (x, y); outside the picture, the nearest border pixel. */
    double at(double x, double y) {
        int xi = (int) Math.min(width - 1, Math.max(0, Math.round(x)));
        int yi = (int) Math.min(height - 1, Math.max(0, Math.round(y)));
        return data[yi * width + xi];
    }
}
