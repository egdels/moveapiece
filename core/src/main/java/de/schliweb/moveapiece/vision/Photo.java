/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.vision;

/**
 * A photo as plain pixels, so the recognition does not depend on the picture classes of Android or
 * the desktop. The photo has to be upright, i.e. with the orientation the camera recorded already
 * applied.
 */
public final class Photo {

    public final int width;
    public final int height;

    /** Pixels row by row, each as 0xRRGGBB (anything above the lowest 24 bits is ignored). */
    public final int[] rgb;

    public Photo(int width, int height, int[] rgb) {
        if (width <= 0 || height <= 0 || rgb.length != width * height) {
            throw new IllegalArgumentException("pixels do not match " + width + " x " + height);
        }
        this.width = width;
        this.height = height;
        this.rgb = rgb;
    }

    /**
     * The upright photo for pixels as a camera stored them together with an EXIF orientation (1-8;
     * 1 or anything unknown means the pixels are upright already).
     */
    public static Photo upright(int width, int height, int[] rgb, int exifOrientation) {
        if (exifOrientation < 2 || exifOrientation > 8) {
            return new Photo(width, height, rgb);
        }
        boolean swap = exifOrientation >= 5;
        int w = swap ? height : width;
        int h = swap ? width : height;
        int[] out = new int[rgb.length];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int sx;
                int sy;
                switch (exifOrientation) {
                    case 2: // mirrored
                        sx = width - 1 - x;
                        sy = y;
                        break;
                    case 3: // upside down
                        sx = width - 1 - x;
                        sy = height - 1 - y;
                        break;
                    case 4: // flipped
                        sx = x;
                        sy = height - 1 - y;
                        break;
                    case 5: // mirrored, then a quarter turn
                        sx = y;
                        sy = x;
                        break;
                    case 6: // needs a quarter turn clockwise
                        sx = y;
                        sy = height - 1 - x;
                        break;
                    case 7:
                        sx = width - 1 - y;
                        sy = height - 1 - x;
                        break;
                    default: // 8: needs a quarter turn counter-clockwise
                        sx = width - 1 - y;
                        sy = x;
                        break;
                }
                out[y * w + x] = rgb[sy * width + sx];
            }
        }
        return new Photo(w, h, out);
    }

    /**
     * The EXIF orientation (1-8) recorded in a JPEG file, or 1 if there is none. Reads only the
     * header of the file.
     */
    public static int jpegOrientation(byte[] jpeg) {
        int pos = 2; // after the start-of-image marker
        while (pos + 4 <= jpeg.length && (jpeg[pos] & 0xFF) == 0xFF) {
            int marker = jpeg[pos + 1] & 0xFF;
            int length = ((jpeg[pos + 2] & 0xFF) << 8) | (jpeg[pos + 3] & 0xFF);
            if (marker == 0xDA) {
                break; // picture data begins
            }
            if (marker == 0xE1
                    && pos + 10 <= jpeg.length
                    && jpeg[pos + 4] == 'E'
                    && jpeg[pos + 5] == 'x'
                    && jpeg[pos + 6] == 'i'
                    && jpeg[pos + 7] == 'f') {
                int tiff = pos + 10;
                if (tiff + 8 > jpeg.length) {
                    return 1;
                }
                boolean little = jpeg[tiff] == 'I';
                int dir = tiff + read(jpeg, tiff + 4, 4, little);
                if (dir + 2 > jpeg.length) {
                    return 1;
                }
                int entries = read(jpeg, dir, 2, little);
                for (int i = 0; i < entries; i++) {
                    int entry = dir + 2 + 12 * i;
                    if (entry + 12 > jpeg.length) {
                        return 1;
                    }
                    if (read(jpeg, entry, 2, little) == 0x0112) {
                        return read(jpeg, entry + 8, 2, little);
                    }
                }
                return 1;
            }
            pos += 2 + length;
        }
        return 1;
    }

    private static int read(byte[] bytes, int pos, int count, boolean little) {
        int value = 0;
        for (int i = 0; i < count; i++) {
            int b = bytes[little ? pos + count - 1 - i : pos + i] & 0xFF;
            value = (value << 8) | b;
        }
        return value;
    }
}
