/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.analysis;

/**
 * One of the engine's best moves in a position, as the post-game analysis records them (see {@link
 * EngineSearchFlow#postGameCandidates()}).
 *
 * @param uci the move in UCI notation, as the engine writes it (a Chess960 castle is the king
 *     moving onto its own rook)
 * @param cp its score in centipawns from the mover's point of view; a mate is scaled by {@link
 *     MoveQuality#mateToCp}
 */
public record MoveCandidate(String uci, int cp) {}
