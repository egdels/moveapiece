#!/usr/bin/env python3
# Copyright (C) 2026 Christian Kierdorf
# SPDX-License-Identifier: GPL-3.0-or-later
"""Step 3b, preparation: turns photos of a known position into entries of a board manifest.

Nothing is labelled by hand. The position is known - the starting position,
a Chess960 starting position by its number, or any position as a FEN (from a
game MoveAPiece exported) - and everything else is found:

- the playing area, from the frame of the board (find_board_frame.py);
  photos in which it is not found, or in which the squares do not fit it
  well, are left out;
- where White sits in the photo: the side for which the known position
  explains best what the recogniser sees. The recogniser pretrained on the
  piece videos is good enough for that.

The entries are appended to the manifest (see manifest.py), so one call per
position builds the list up.

Usage:
  python label_photos.py --photos samples/start --series start --model runs/pretrain/checkpoints/best.pt \
      --manifest samples/boards.jsonl
  python label_photos.py --photos samples/sp165 --chess960 165 --model ... --manifest samples/boards.jsonl
  python label_photos.py --photos samples/game1 --fen "r1bq1rk1/..." --series game1 --model ... --manifest ...

The photos of the collector app (collector/) come with their positions: each
photo has a .json next to it that names the position. --collector takes the
folder of one board (collection/boards/<board>) and goes through all of it; the series are named after the
surroundings and the game, e.g. c1_opera, so that whole surroundings or games
can be kept for the test:

  python label_photos.py --collector samples/collector/collection/boards/Pegasus --model ... --manifest ...
"""

from __future__ import annotations

import argparse
import json
from collections import Counter
from pathlib import Path

import numpy as np

from find_board_frame import INSET, find_frame, inset_quad
from piece_model import PieceRecogniser
from piece_windows import board_windows
from quad import IMAGE_SUFFIXES, canonicalize_quad
from squares import WHITE_SIDES, board_to_view, fen_to_board, load_image

START = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR"
# How well the 8x8 squares have to fit the found quad (find_board_frame._grid_score) for a photo
# to be used for training: wrong quads seen so far were all below this.
MIN_FIT = 13.0


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    source = ap.add_mutually_exclusive_group(required=True)
    source.add_argument("--photos", type=Path, help="folder with photos of one position")
    source.add_argument("--collector", type=Path, help="the folder of one board of the collector app (boards/<board>), with all its positions")
    position = ap.add_mutually_exclusive_group()
    position.add_argument("--fen", help="the position (default: the starting position)")
    position.add_argument("--chess960", type=int, help="number of a Chess960 starting position (0-959)")
    ap.add_argument("--series", help="name of the series (default: sp<number> or the folder name)")
    ap.add_argument("--model", type=Path, required=True, help="piece recogniser (.pt, .onnx)")
    ap.add_argument("--manifest", type=Path, required=True, help="manifest to append to")
    args = ap.parse_args(argv)

    recogniser = PieceRecogniser(args.model)
    if args.collector is not None:
        added = 0
        for surroundings in sorted(p for p in args.collector.iterdir() if p.is_dir()):
            number = surroundings.name.rsplit("_", 1)[-1]
            for folder in sorted(p for p in surroundings.iterdir() if p.is_dir()):
                # Photos the collector found not to show their position are named *_differs.
                notes = sorted(n for n in folder.glob("*.json") if not n.stem.endswith("_differs"))
                if not notes:
                    continue
                fens = {json.loads(n.read_text(encoding="utf-8"))["fen"] for n in notes}
                if len(fens) != 1:
                    raise SystemExit(f"{folder}: the photos name different positions")
                if not any(c.isalpha() for c in next(iter(fens))):
                    continue  # photos of the empty board: nothing to label
                game = folder.name.rsplit("_", 1)[0]
                added += label_folder(folder, fens.pop(), f"c{number}_{game}", recogniser, args.manifest)
        return 0 if added else 1
    if args.chess960 is not None:
        import chess

        fen = chess.Board.from_chess960_pos(args.chess960).board_fen()
        series = args.series or f"sp{args.chess960}"
    else:
        fen = (args.fen or START).split()[0]
        series = args.series or args.photos.name
    return 0 if label_folder(args.photos, fen, series, recogniser, args.manifest) else 1


def label_folder(photos: Path, fen: str, series: str, recogniser: PieceRecogniser, manifest: Path) -> int:
    """Appends the photos of one position to the manifest; returns how many."""
    truth = fen_to_board(fen)
    root = manifest.parent

    lines, sides, left_out = [], Counter(), []
    for path in sorted(p for p in photos.iterdir() if p.suffix.lower() in IMAGE_SUFFIXES):
        if path.stem.endswith("_differs"):
            continue
        image = load_image(path)
        frame, fit = find_frame(image, return_score=True)
        if frame is None or fit < MIN_FIT:
            left_out.append(path.name)
            continue
        corners = canonicalize_quad(inset_quad(frame, INSET))
        windows = board_windows(image, corners)
        probs = np.clip(recogniser.predict(windows.reshape(64, *windows.shape[2:])).reshape(8, 8, -1), 1e-4, 1.0)
        fits = {
            side: float(np.log(np.take_along_axis(probs, board_to_view(truth, side)[..., None], axis=2)).sum())
            for side in WHITE_SIDES
        }
        side = max(fits, key=fits.get)
        sides[side] += 1
        try:
            name = str(path.resolve().relative_to(root.resolve()))
        except ValueError:
            name = str(path.resolve())
        lines.append(json.dumps({"image": name, "fen": fen, "white_side": side, "series": series, "corners": corners.round(1).tolist()}))

    with open(manifest, "a", encoding="utf-8") as f:
        f.writelines(line + "\n" for line in lines)
    print(f"{series}: {fen} - {len(lines)} photos added (White at: {dict(sides)}), {len(left_out)} left out")
    if left_out:
        print("  playing area not found or not certain:", " ".join(left_out))
    return len(lines)


if __name__ == "__main__":
    raise SystemExit(main())
