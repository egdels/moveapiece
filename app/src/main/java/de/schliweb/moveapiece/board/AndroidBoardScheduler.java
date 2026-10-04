/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.board;

import android.os.Handler;
import android.os.Looper;

/** {@link BoardScheduler} on Android's main thread. */
public final class AndroidBoardScheduler implements BoardScheduler {

    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public void post(Runnable action) {
        handler.post(action);
    }

    @Override
    public Task postDelayed(Runnable action, long delayMs) {
        // A Runnable of its own per call, so that cancelling removes this one posting only.
        Runnable posted = action::run;
        handler.postDelayed(posted, delayMs);
        return () -> handler.removeCallbacks(posted);
    }

    @Override
    public void shutdown() {
        handler.removeCallbacksAndMessages(null);
    }
}
