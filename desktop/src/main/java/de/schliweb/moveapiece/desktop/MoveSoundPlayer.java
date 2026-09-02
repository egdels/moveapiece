/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.desktop;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import javafx.scene.media.AudioClip;

/**
 * Short one-shot move/capture/check sound effects, backed by JavaFX's {@link AudioClip} (low
 * latency, meant for exactly this kind of playback). Same audio files as the Android app's {@code
 * ui.MoveSoundPlayer} (SoundPool there, AudioClip here - platform equivalents).
 *
 * <p>Each clip is extracted to a temp file and loaded from a plain {@code file:} URL rather than
 * played directly from a {@code jar:} URL: loading an {@code AudioClip} straight out of a jar (as
 * happens once the app is packaged - {@code :desktop:run} runs from exploded classes/ resources
 * directories, so this never showed up there) made JavaFX's GStreamer-backed media engine
 * double-play every clip, one real call to {@link AudioClip#play()} producing two audible,
 * overlapping playbacks. Extracting to a real file sidesteps whatever jar-URL handling caused that.
 */
final class MoveSoundPlayer {

    private final AudioClip moveClip = load("move.mp3");
    private final AudioClip captureClip = load("capture.mp3");
    private final AudioClip checkClip = load("check.mp3");

    void playMove() {
        moveClip.play();
    }

    void playCapture() {
        captureClip.play();
    }

    void playCheck() {
        checkClip.play();
    }

    private static AudioClip load(String resourceName) {
        try (InputStream in = MoveSoundPlayer.class.getResourceAsStream("sounds/" + resourceName)) {
            Path tempFile = Files.createTempFile("moveapiece-", "-" + resourceName);
            tempFile.toFile().deleteOnExit();
            Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
            return new AudioClip(tempFile.toUri().toString());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to extract sound resource " + resourceName, e);
        }
    }
}
