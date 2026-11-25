/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.vision;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import javax.imageio.ImageIO;

/** Loads photo files for tests, upright. */
final class TestPhotos {

    private TestPhotos() {}

    static Photo load(File file) throws IOException {
        byte[] bytes = Files.readAllBytes(file.toPath());
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        if (image == null) {
            throw new IOException("not a picture: " + file);
        }
        int w = image.getWidth();
        int h = image.getHeight();
        int[] rgb = image.getRGB(0, 0, w, h, null, 0, w);
        return Photo.upright(w, h, rgb, Photo.jpegOrientation(bytes));
    }
}
