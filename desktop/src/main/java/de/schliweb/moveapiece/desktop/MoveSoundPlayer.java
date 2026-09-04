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
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;

/**
 * Short one-shot move/capture/check sound effects. Same audio files as the Android app's {@code
 * ui.MoveSoundPlayer} (SoundPool there).
 *
 * <p>TEMPORARY DIAGNOSTIC (see conversation/commit history): normally backed by {@code
 * javafx.scene.media.AudioClip} (low latency, meant for exactly this kind of playback), but that
 * API has no error-reporting surface at all - on the one Windows machine this has been tested on so
 * far, move sounds are completely silent (confirmed via the OS volume mixer: the per-app level
 * meter never moves) with zero output anywhere, console included, so there is no way to find out
 * *why* through AudioClip. Swapped to {@link MediaPlayer}, which does expose {@code setOnError}/
 * {@code setOnReady}, purely to see what it reports. Revert to AudioClip once the cause is known
 * (unless MediaPlayer turns out to actually work correctly here, in which case just keep it).
 *
 * <p>Each clip is extracted to a temp file and loaded from a plain {@code file:} URL rather than
 * played directly from a {@code jar:} URL: loading straight out of a jar (as happens once the app
 * is packaged - {@code :desktop:run} runs from exploded classes/resources directories, so this
 * never showed up there) made JavaFX's GStreamer-backed media engine double-play every clip, one
 * real play() call producing two audible, overlapping playbacks. Extracting to a real file
 * sidesteps whatever jar-URL handling caused that.
 */
final class MoveSoundPlayer {

    private final MediaPlayer moveClip = load("move.mp3");
    private final MediaPlayer captureClip = load("capture.mp3");
    private final MediaPlayer checkClip = load("check.mp3");

    void playMove() {
        replay(moveClip, "move.mp3");
    }

    void playCapture() {
        replay(captureClip, "capture.mp3");
    }

    void playCheck() {
        replay(checkClip, "check.mp3");
    }

    private static void replay(MediaPlayer player, String resourceName) {
        System.out.println("[sound] play " + resourceName + ", status=" + player.getStatus());
        player.stop();
        player.play();
    }

    private static MediaPlayer load(String resourceName) {
        try (InputStream in = MoveSoundPlayer.class.getResourceAsStream("sounds/" + resourceName)) {
            Path tempFile = Files.createTempFile("moveapiece-", "-" + resourceName);
            tempFile.toFile().deleteOnExit();
            Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
            Media media = new Media(tempFile.toUri().toString());
            MediaPlayer player = new MediaPlayer(media);
            player.setOnError(
                    () ->
                            System.out.println(
                                    "[sound] " + resourceName + " ERROR: " + player.getError()));
            player.setOnReady(() -> System.out.println("[sound] " + resourceName + " ready"));
            System.out.println("[sound] " + resourceName + " extracted to " + tempFile);
            return player;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to extract sound resource " + resourceName, e);
        }
    }
}
