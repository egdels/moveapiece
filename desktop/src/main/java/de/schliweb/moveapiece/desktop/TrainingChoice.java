/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.desktop;

import com.github.bhlangonijr.chesslib.Side;
import de.schliweb.moveapiece.training.OpeningLine;

/** Result of {@link TrainingSetupDialog}: which line to drill, as which side, with hints or not. */
record TrainingChoice(OpeningLine opening, Side side, boolean hintsEnabled) {}
