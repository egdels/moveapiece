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
 *
 * <p>The bundled sounds/*.mp3 files must not carry an ID3v2 tag: Windows' bundled GStreamer build
 * rejected these clips outright (ERROR_MEDIA_INVALID, confirmed via a temporary MediaPlayer-based
 * diagnostic - AudioClip itself has no error-reporting API at all) because of a malformed ID3
 * comment frame in the original encode, while macOS's build tolerated it - see the commit that
 * stripped the tags (`ffmpeg -map_metadata -1 -id3v2_version 0 -write_id3v1 0 -c copy`) for
 * details. Re-strip any replacement/new sound file's ID3 tag the same way before adding it here.
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
