#!/usr/bin/env python3
# Copyright (C) 2026 Christian Kierdorf
# SPDX-License-Identifier: GPL-3.0-or-later
"""Measures the whole chain on photos of known positions.

For every photo of the chosen series of a manifest (manifest.py) the
position is recognised exactly as recognise.py does it - playing area from
the frame, windows, recogniser, occupancy and usual pieces - and compared
with the known position. The occupancy is taken from the known position: it
is what the Pegasus would report.

Only evaluate series the recogniser was not fine-tuned on.

Usage:
  python evaluate.py --model runs/boards/export/piece_recogniser.onnx \
      --manifest samples/boards.jsonl --series desk_standard_start,desk_960_start,desk_after_5_moves
"""

from __future__ import annotations

import argparse
import json
from collections import Counter
from pathlib import Path

from manifest import load_manifest
from piece_model import PieceRecogniser
from recognise import recognise
from squares import EMPTY, FEN_SYMBOLS, board_to_fen, load_image, square_name

KINDS = "PNBRQK"


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument("--model", type=Path, required=True, help="piece recogniser (.onnx, .ort or .pt)")
    ap.add_argument("--manifest", type=Path, required=True)
    ap.add_argument("--series", required=True, help="comma-separated series to evaluate")
    ap.add_argument("--labelled-corners", action="store_true", help="use the corners of the manifest instead of finding them")
    ap.add_argument("--out", type=Path, help="folder for evaluation.json and boards.jsonl")
    args = ap.parse_args(argv)

    wanted = [s for s in args.series.split(",") if s]
    records = [r for r in load_manifest(args.manifest) if r.series in wanted]
    missing = sorted(set(wanted) - {r.series for r in records})
    if missing or not records:
        raise SystemExit(f"series not in the manifest: {missing or wanted}")
    recogniser = PieceRecogniser(args.model)
    images_dir = args.manifest.parent

    rows = []
    per_series: dict[str, Counter] = {}
    per_kind, confusions = Counter(), Counter()
    for rec in records:
        truth = rec.board
        occupancy = truth != EMPTY
        result = recognise(load_image(images_dir / rec.image), occupancy, recogniser, rec.corners if args.labelled_corners else None)
        stats = per_series.setdefault(rec.series, Counter())
        stats["photos"] += 1
        if result is None:
            stats["not_found"] += 1
            rows.append({"image": rec.image, "series": rec.series, "found": False})
            continue
        wrong = [(r, f) for r in range(8) for f in range(8) if result.board[r, f] != truth[r, f]]
        uncertain = set(result.uncertain_squares())
        stats["pieces"] += int(occupancy.sum())
        stats["pieces_right"] += int(occupancy.sum()) - len(wrong)
        stats["boards_right"] += not wrong
        stats["white_side_right"] += result.white_side == rec.white_side
        stats["wrong_flagged"] += sum(square_name(f, r) in uncertain for r, f in wrong)
        stats["flagged"] += len(uncertain)
        for r in range(8):
            for f in range(8):
                if occupancy[r, f]:
                    kind = KINDS[(truth[r, f] - 1) % 6]
                    per_kind[kind, "all"] += 1
                    per_kind[kind, "right"] += result.board[r, f] == truth[r, f]
        for r, f in wrong:
            confusions[FEN_SYMBOLS[truth[r, f]] + ">" + FEN_SYMBOLS[result.board[r, f]]] += 1
        rows.append(
            {
                "image": rec.image,
                "series": rec.series,
                "found": True,
                "fen": board_to_fen(truth),
                "recognised": result.fen,
                "white_side": result.white_side,
                "wrong": [square_name(f, r) for r, f in wrong],
                "uncertain": sorted(uncertain),
            }
        )

    total = Counter()
    for stats in per_series.values():
        total.update(stats)

    def summary(stats: Counter) -> dict:
        found = stats["photos"] - stats["not_found"]
        return {
            "photos": stats["photos"],
            "playing_area_found": found,
            "pieces_right": f"{stats['pieces_right']}/{stats['pieces']}",
            "pieces_right_share": round(stats["pieces_right"] / max(1, stats["pieces"]), 4),
            "boards_completely_right": f"{stats['boards_right']}/{found}",
            "white_side_right": f"{stats['white_side_right']}/{found}",
            "wrong_pieces_flagged_uncertain": f"{stats['wrong_flagged']}/{stats['pieces'] - stats['pieces_right']}",
            "flagged_per_board": round(stats["flagged"] / max(1, found), 1),
        }

    report = {
        "model": str(args.model),
        "corners": "from the manifest" if args.labelled_corners else "frame search",
        "all": summary(total),
        "per_series": {name: summary(stats) for name, stats in per_series.items()},
        "per_piece": {k: f"{per_kind[k, 'right']}/{per_kind[k, 'all']}" for k in KINDS if per_kind[k, "all"]},
        "confusions (truth>recognised)": dict(confusions.most_common(8)),
    }
    print(json.dumps(report, indent=2))
    if args.out:
        args.out.mkdir(parents=True, exist_ok=True)
        (args.out / "evaluation.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
        (args.out / "boards.jsonl").write_text("".join(json.dumps(r) + "\n" for r in rows), encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
