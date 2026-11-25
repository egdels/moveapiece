# Copyright (C) 2026 Christian Kierdorf
# SPDX-License-Identifier: GPL-3.0-or-later
"""The board manifest: which photo shows which position.

One JSON record per line and photo:

  {"image": "sp165/IMG_1.jpg", "fen": "nrbbnkqr/pppppppp/8/8/8/8/PPPPPPPP/NRBBNKQR",
   "white_side": "bottom", "series": "sp165", "corners": [[x,y],[x,y],[x,y],[x,y]]}

  image       photo, relative to the folder of the manifest
  fen         the position; only the piece placement is used
  white_side  where White sits as seen in the photo: bottom, right, top, left
  series      the position or game the photo belongs to. A series is either
              trained on or tested on, never both.
  corners     the four corners of the playing area in the upright photo;
              optional for photos that are only evaluated (they are found)

label_photos.py writes these records; nothing in them is entered by hand
except the position.
"""

from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path

import numpy as np

from quad import canonicalize_quad
from squares import WHITE_SIDES, fen_to_board


@dataclass
class BoardRecord:
    image: str
    fen: str
    white_side: str
    series: str
    corners: np.ndarray | None

    @property
    def board(self) -> np.ndarray:
        return fen_to_board(self.fen)


def load_manifest(path: Path) -> list[BoardRecord]:
    records = []
    for n, line in enumerate(Path(path).read_text(encoding="utf-8").splitlines(), start=1):
        if not line.strip():
            continue
        row = json.loads(line)
        try:
            white_side = row["white_side"]
            if white_side not in WHITE_SIDES:
                raise ValueError(f"white_side must be one of {WHITE_SIDES}")
            fen = row["fen"].split()[0]
            fen_to_board(fen)
            corners = canonicalize_quad(row["corners"]) if row.get("corners") else None
            records.append(BoardRecord(row["image"], fen, white_side, row["series"], corners))
        except (KeyError, ValueError) as e:
            raise SystemExit(f"{path}, line {n}: {e}")
    return records
