# Copyright (C) 2026 Christian Kierdorf
# SPDX-License-Identifier: GPL-3.0-or-later
"""From 64 class probabilities and the Pegasus occupancy to a position.

The Pegasus reports which squares are occupied, reliably, but not by what.
That turns the task from "13 classes on 64 squares" into "12 classes on the
occupied squares", and it also tells how the board lies in the photo: of
the four possible places for White, the right one is the one where the
squares the recogniser sees as occupied match the squares the Pegasus
reports. On the occupied squares each side gets at most the pieces a game
starts with, handed out so that it fits all squares at once.
"""

from __future__ import annotations

import numpy as np

from squares import (
    BLACK_KING,
    BLACK_PAWN,
    CLASSES,
    EMPTY,
    WHITE_KING,
    WHITE_PAWN,
    WHITE_SIDES,
    board_to_fen,
    square_name,
    view_to_board,
)

WHITE_CLASSES = slice(1, 7)
BLACK_CLASSES = slice(7, 13)


def choose_white_side(view_probs: np.ndarray, occupancy: np.ndarray) -> tuple[str, dict[str, float], bool]:
    """Finds where White sits by matching predicted against reported occupancy.

    `view_probs` is (8, 8, 13) in view order, `occupancy` is bool board[rank][file] from the
    Pegasus. Returns (white_side, log-likelihood per side, ambiguous).

    The occupancy cannot tell a position from its 180 degree turn when the occupancy pattern is
    point-symmetric (the starting position, for one). Such ties are broken by colour: White's
    pieces are, on average, nearer to the first rank than Black's. `ambiguous` reports that this
    weaker rule decided, so the app can ask instead of guessing.
    """
    eps = 1e-6
    scores, colour_scores = {}, {}
    for side in WHITE_SIDES:
        probs = view_to_board(view_probs, side)
        p_occupied = np.clip(1.0 - probs[..., EMPTY], eps, 1 - eps)
        scores[side] = float(np.where(occupancy, np.log(p_occupied), np.log(1 - p_occupied)).sum())
        white = probs[..., WHITE_CLASSES].sum(axis=-1)
        black = probs[..., BLACK_CLASSES].sum(axis=-1)
        towards_first_rank = (3.5 - np.arange(8))[:, None]
        colour_scores[side] = float(((white - black) * towards_first_rank).sum())
    best = max(scores.values())
    tied = [s for s in WHITE_SIDES if best - scores[s] < 1e-3 * max(1.0, abs(best))]
    side = max(tied, key=lambda s: colour_scores[s])
    return side, scores, len(tied) > 1


def plausibility_issues(board: np.ndarray) -> list[str]:
    """What is wrong with the position; empty if it could occur in a game."""
    issues = []
    for king, name in ((WHITE_KING, "white"), (BLACK_KING, "black")):
        n = int((board == king).sum())
        if n != 1:
            issues.append(f"{n} {name} kings")
    for rank in (0, 7):
        for file in range(8):
            if board[rank, file] in (WHITE_PAWN, BLACK_PAWN):
                issues.append(f"pawn on {square_name(file, rank)}")
    for colour, name in ((WHITE_CLASSES, "white"), (BLACK_CLASSES, "black")):
        ids = range(colour.start, colour.stop)
        if sum(int((board == i).sum()) for i in ids) > 16:
            issues.append(f"more than 16 {name} pieces")
        if int((board == ids[0]).sum()) > 8:
            issues.append(f"more than 8 {name} pawns")
    if not issues:
        import chess

        fen = board_to_fen(board)
        # Legal if it is a valid position with either side to move (castling rights ignored).
        if not any(chess.Board(f"{fen} {turn} - - 0 1").is_valid() for turn in "wb"):
            issues.append("not a legal position (both kings in check, or the side not to move is in check)")
    return issues


# The pieces of one side in a normal game, as class kinds 1..6 (pawn, knight, bishop, rook, queen, king).
USUAL_PIECES = (1,) * 8 + (2,) * 2 + (3,) * 2 + (4,) * 2 + (5,) + (6,)


def _assign(cost: np.ndarray) -> list[int]:
    """Hungarian method: for each row of `cost` (rows <= columns) a different column, at minimal total cost."""
    n, m = cost.shape
    u, v = np.zeros(n + 1), np.zeros(m + 1)
    match = np.zeros(m + 1, dtype=int)  # row matched to each column (1-based, 0 = none)
    way = np.zeros(m + 1, dtype=int)
    for i in range(1, n + 1):
        match[0] = i
        j0 = 0
        minv = np.full(m + 1, np.inf)
        used = np.zeros(m + 1, dtype=bool)
        while True:
            used[j0] = True
            i0, delta, j1 = match[j0], np.inf, 0
            for j in range(1, m + 1):
                if not used[j]:
                    cur = cost[i0 - 1, j - 1] - u[i0] - v[j]
                    if cur < minv[j]:
                        minv[j], way[j] = cur, j0
                    if minv[j] < delta:
                        delta, j1 = minv[j], j
            for j in range(m + 1):
                if used[j]:
                    u[match[j]] += delta
                    v[j] -= delta
                else:
                    minv[j] -= delta
            j0 = j1
            if match[j0] == 0:
                break
        while j0:
            match[j0] = match[way[j0]]
            j0 = way[j0]
    rows = [0] * n
    for j in range(1, m + 1):
        if match[j]:
            rows[match[j] - 1] = j - 1
    return rows


def decode_position(view_probs: np.ndarray, white_side: str, occupancy: np.ndarray) -> tuple[np.ndarray, np.ndarray]:
    """Class id and confidence per square, both as board[rank][file].

    Empty squares come from the board. On the occupied ones each side gets at most the pieces a
    game starts with. Two pieces that look alike from where the photo was taken (rook and queen, say) are often
    mixed up square by square. Over the whole board there is only one way to hand out one queen
    and two rooks that fits what is seen on all squares at once; that assignment is taken. A
    side with more than 16 pieces, or a position after a promotion to a second queen, does not
    fit this; such squares come out wrong and with low confidence, so the caller should still
    let the user correct them.
    """
    probs = np.clip(view_to_board(view_probs, white_side).astype(np.float64), 1e-9, 1.0)
    occupancy = np.asarray(occupancy, dtype=bool)
    board = np.zeros((8, 8), dtype=np.int64)
    confidence = np.ones((8, 8))
    white = probs[..., WHITE_CLASSES].sum(axis=-1) >= probs[..., BLACK_CLASSES].sum(axis=-1)
    for is_white, classes in ((True, WHITE_CLASSES), (False, BLACK_CLASSES)):
        squares = np.argwhere(occupancy & (white == is_white))
        if len(squares) == 0:
            continue
        block = probs[squares[:, 0], squares[:, 1]][:, classes]
        block = block / block.sum(axis=1, keepdims=True)
        if len(squares) > len(USUAL_PIECES):
            kinds = block.argmax(axis=1) + 1
        else:
            # Pawns cannot stand on the first or last rank.
            cost = -np.log(block[:, [k - 1 for k in USUAL_PIECES]])
            on_back_rank = np.isin(squares[:, 0], (0, 7))
            cost[np.ix_(on_back_rank, [i for i, k in enumerate(USUAL_PIECES) if k == 1])] = 1e6
            kinds = np.array([USUAL_PIECES[j] for j in _assign(cost)])
        for (rank, file), kind, row in zip(squares, kinds, block):
            board[rank, file] = kind + classes.start - 1
            confidence[rank, file] = row[kind - 1]
    return board, confidence
