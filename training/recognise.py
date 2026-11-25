#!/usr/bin/env python3
# Copyright (C) 2026 Christian Kierdorf
# SPDX-License-Identifier: GPL-3.0-or-later
"""The whole chain: a photo of the Pegasus and its occupancy in, the position out.

  1. playing area from the frame of the board        find_board_frame.py
  2. one upright window per square                   piece_windows.py
  3. the piece in every window                       piece_model.py
  4. occupancy + usual pieces -> position            decode.py

The occupancy is what the Pegasus reports: which squares are occupied. Here
it is given as a FEN (only "occupied or not" is taken from it) or as 64
characters of 0 and 1, a8 first, as in a FEN.

Usage:
  python recognise.py --model runs/boards/export/piece_recogniser.onnx \
      --photo photo.jpg --occupancy "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR"
"""

from __future__ import annotations

import argparse
from dataclasses import dataclass
from pathlib import Path

import numpy as np

from decode import choose_white_side, decode_position, plausibility_issues
from find_board_frame import find_playing_area
from piece_model import PieceRecogniser
from piece_windows import board_windows
from squares import EMPTY, board_to_fen, fen_to_board, load_image, square_name


@dataclass
class Recognition:
    board: np.ndarray  # class ids, board[rank][file]
    confidence: np.ndarray  # per square
    white_side: str  # where White sits as seen in the photo
    white_side_ambiguous: bool  # the occupancy could not tell; the piece colours decided
    corners: np.ndarray  # playing area in the photo

    @property
    def fen(self) -> str:
        return board_to_fen(self.board)

    def uncertain_squares(self, threshold: float = 0.9) -> list[str]:
        """Squares the user should look at."""
        return [
            square_name(f, r)
            for r in range(8)
            for f in range(8)
            if self.board[r, f] != EMPTY and self.confidence[r, f] < threshold
        ]


def parse_occupancy(text: str) -> np.ndarray:
    """Occupancy as bool board[rank][file] from a FEN or from 64 characters 0/1 (a8 first)."""
    text = text.strip()
    if set(text) <= {"0", "1"} and len(text) == 64:
        return np.array([[text[(7 - rank) * 8 + file] == "1" for file in range(8)] for rank in range(8)])
    return fen_to_board(text) != EMPTY


def recognise(image, occupancy: np.ndarray, recogniser: PieceRecogniser, corners=None) -> Recognition | None:
    """The position in the photo, or None if the playing area is not found."""
    if corners is None:
        corners = find_playing_area(image)
    if corners is None:
        return None
    windows = board_windows(image, corners)
    probs = recogniser.predict(windows.reshape(64, *windows.shape[2:])).reshape(8, 8, -1)
    side, _, ambiguous = choose_white_side(probs, occupancy)
    board, confidence = decode_position(probs, side, occupancy)
    return Recognition(board, confidence, side, ambiguous, np.asarray(corners))


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument("--model", type=Path, required=True, help="piece recogniser (.onnx, .ort or .pt)")
    ap.add_argument("--photo", type=Path, required=True)
    ap.add_argument("--occupancy", required=True, help="FEN or 64 characters 0/1, a8 first")
    args = ap.parse_args(argv)

    result = recognise(load_image(args.photo), parse_occupancy(args.occupancy), PieceRecogniser(args.model))
    if result is None:
        print("playing area not found - please take the photo again")
        return 1
    print(result.fen)
    print(f"White sits at the {result.white_side} of the photo" + (" (not certain)" if result.white_side_ambiguous else ""))
    uncertain = result.uncertain_squares()
    if uncertain:
        print("please check:", " ".join(uncertain))
    for issue in plausibility_issues(result.board):
        print("note:", issue)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
