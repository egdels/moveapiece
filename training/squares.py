# Copyright (C) 2026 Christian Kierdorf
# SPDX-License-Identifier: GPL-3.0-or-later
"""Positions and how they lie in a photo: classes, FEN, and the view of the board.

Two coordinate systems:

- view: (row, col) as the board lies in the photo, row 0 = far side, col 0 =
  left. The corners of the playing area are geometric (TL, TR, BR, BL as
  seen in the photo), so the view says nothing about where a1 is.
- board: chess squares, board[rank][file] with rank 0 = first rank. Which
  view cell is a1 depends on where White sits as seen in the photo:
  WHITE_SIDES = bottom, right, top, left.

The windows cut for the squares (piece_windows.py) are in view order; only
the mapping from view cells to squares depends on the orientation, and that
is found from the occupancy the board reports (decode.py).
"""

from __future__ import annotations

import numpy as np
from PIL import Image, ImageOps


CLASSES = (
    "empty",
    "white_pawn",
    "white_knight",
    "white_bishop",
    "white_rook",
    "white_queen",
    "white_king",
    "black_pawn",
    "black_knight",
    "black_bishop",
    "black_rook",
    "black_queen",
    "black_king",
)
FEN_SYMBOLS = ".PNBRQKpnbrqk"  # index = class id
EMPTY = 0
WHITE_KING, BLACK_KING = 6, 12
WHITE_PAWN, BLACK_PAWN = 1, 7

WHITE_SIDES = ("bottom", "right", "top", "left")


def load_image(path, apply_exif: bool = True) -> Image.Image:
    """The photo as RGB; upright by default (EXIF orientation applied), as the app would see it."""
    image = Image.open(path)
    if apply_exif:
        image = ImageOps.exif_transpose(image)
    return image.convert("RGB")


# --- board <-> view ---------------------------------------------------------


def view_cell(file: int, rank: int, white_side: str) -> tuple[int, int]:
    """(row, col) in the warped view of the square with 0-based file (a=0) and rank (1 -> 0)."""
    if white_side == "bottom":
        return 7 - rank, file
    if white_side == "top":
        return rank, 7 - file
    if white_side == "left":
        return file, rank
    if white_side == "right":
        return 7 - file, 7 - rank
    raise ValueError(f"white_side must be one of {WHITE_SIDES}, got {white_side!r}")


def square_name(file: int, rank: int) -> str:
    return "abcdefgh"[file] + str(rank + 1)


def fen_to_board(fen: str) -> np.ndarray:
    """Class ids as board[rank][file] (rank 0 = first rank) from the placement field of a FEN."""
    rows = fen.split()[0].split("/")
    if len(rows) != 8:
        raise ValueError(f"not a FEN placement: {fen!r}")
    board = np.zeros((8, 8), dtype=np.int64)
    for i, row in enumerate(rows):
        file = 0
        for ch in row:
            if ch.isdigit():
                file += int(ch)
            else:
                board[7 - i, file] = FEN_SYMBOLS.index(ch)
                file += 1
        if file != 8:
            raise ValueError(f"not a FEN placement: {fen!r}")
    return board


def board_to_fen(board: np.ndarray) -> str:
    rows = []
    for rank in range(7, -1, -1):
        row, gap = "", 0
        for file in range(8):
            cls = int(board[rank, file])
            if cls == EMPTY:
                gap += 1
            else:
                row += (str(gap) if gap else "") + FEN_SYMBOLS[cls]
                gap = 0
        rows.append(row + (str(gap) if gap else ""))
    return "/".join(rows)


def board_to_view(board: np.ndarray, white_side: str) -> np.ndarray:
    """Rearranges board[rank][file] to view[row][col]."""
    view = np.empty_like(board)
    for rank in range(8):
        for file in range(8):
            view[view_cell(file, rank, white_side)] = board[rank, file]
    return view


def view_to_board(view: np.ndarray, white_side: str) -> np.ndarray:
    """Rearranges view[row][col] (any trailing dimensions) to board[rank][file]."""
    board = np.empty_like(view)
    for rank in range(8):
        for file in range(8):
            board[rank, file] = view[view_cell(file, rank, white_side)]
    return board
