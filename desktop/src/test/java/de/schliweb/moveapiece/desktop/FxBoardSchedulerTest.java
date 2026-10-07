/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.desktop;

import de.schliweb.moveapiece.board.BoardScheduler;
import org.junit.Test;

public class FxBoardSchedulerTest {

    /**
     * A board event already queued for the FX thread when its bridge is shut down still reaches the
     * bridge and asks for a timer; that must be dropped, not thrown back at the FX thread.
     */
    @Test
    public void postDelayed_afterShutdownIsDropped() {
        FxBoardScheduler scheduler = new FxBoardScheduler("test-timer");
        scheduler.shutdown();

        BoardScheduler.Task task =
                scheduler.postDelayed(
                        () -> {
                            throw new AssertionError("ran after shutdown");
                        },
                        0);

        task.cancel();
    }
}
