/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.desktop;

import de.schliweb.moveapiece.board.BoardScheduler;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;

/**
 * {@link BoardScheduler} on the JavaFX Application Thread; a daemon timer thread hands delayed
 * actions over to it.
 */
public final class FxBoardScheduler implements BoardScheduler {

    private final ScheduledExecutorService timer;

    /**
     * @param threadName name of the timer thread, to tell the boards apart in thread dumps
     */
    public FxBoardScheduler(String threadName) {
        this.timer =
                Executors.newSingleThreadScheduledExecutor(
                        r -> {
                            Thread t = new Thread(r, threadName);
                            t.setDaemon(true);
                            return t;
                        });
    }

    @Override
    public void post(Runnable action) {
        Platform.runLater(action);
    }

    @Override
    public Task postDelayed(Runnable action, long delayMs) {
        if (timer.isShutdown()) {
            return () -> {};
        }
        // The flag is only touched on the FX thread: it closes the gap between the timer firing
        // and the action actually running there, in which cancelling the future is too late.
        boolean[] cancelled = {false};
        ScheduledFuture<?> future =
                timer.schedule(
                        () ->
                                Platform.runLater(
                                        () -> {
                                            if (!cancelled[0]) {
                                                action.run();
                                            }
                                        }),
                        delayMs,
                        TimeUnit.MILLISECONDS);
        return () -> {
            cancelled[0] = true;
            future.cancel(false);
        };
    }

    @Override
    public void shutdown() {
        timer.shutdownNow();
    }
}
