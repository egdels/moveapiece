/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.ui;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;
import de.schliweb.moveapiece.R;

/**
 * Short UI sound effects for moves, backed by {@link SoundPool} (low latency, meant for exactly
 * this kind of one-shot playback). Survives configuration changes: create once in onCreate(),
 * release once in onDestroy(), independent of the (re-inflated on rotation) view hierarchy.
 */
public class MoveSoundPlayer {

    private final SoundPool soundPool;
    private final int moveSoundId;
    private final int captureSoundId;
    private final int checkSoundId;

    public MoveSoundPlayer(Context context) {
        AudioAttributes attributes =
                new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build();
        soundPool = new SoundPool.Builder().setMaxStreams(2).setAudioAttributes(attributes).build();
        moveSoundId = soundPool.load(context, R.raw.move, 1);
        captureSoundId = soundPool.load(context, R.raw.capture, 1);
        checkSoundId = soundPool.load(context, R.raw.check, 1);
    }

    public void playMove() {
        play(moveSoundId);
    }

    public void playCapture() {
        play(captureSoundId);
    }

    public void playCheck() {
        play(checkSoundId);
    }

    private void play(int soundId) {
        soundPool.play(soundId, 1f, 1f, 1, 0, 1f);
    }

    public void release() {
        soundPool.release();
    }
}
