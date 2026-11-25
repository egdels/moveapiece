"""Recognisers compared on the records of the dev app whose position the user confirmed.

Every record holds one or more photos of a position set up on the board, what the board reported
and the position as the user confirmed it (info.json: "corrected", outcome "accepted"). Read as the
app reads: every window as it is and with its neighbours hidden, averaged; the photos of a record
laid on each other. Given per recogniser: photos read right alone, positions read right from all
their photos, wrong squares, and the squares read wrong before the usual numbers of pieces are
kept, with the range the photos allow against the first recogniser (photos drawn anew).

Confirmed is not proven: look at the photos of a record before trusting it (one was
confirmed wrong and is marked in its info.json, "corrected": null).

    python evaluation/measure_on_app_records.py . samples/device/board-photos <how the names begin> runs/a/checkpoints/best.pt runs/b/checkpoints/best.pt
"""
import json
import sys
from pathlib import Path

import numpy as np

BV = Path(sys.argv[1])
sys.path.insert(0, str(BV))
from decode import choose_white_side, decode_position  # noqa: E402
from find_board_frame import find_playing_area  # noqa: E402
from piece_model import PieceRecogniser  # noqa: E402
from piece_windows import board_windows  # noqa: E402
from squares import EMPTY, fen_to_board, load_image, view_cell  # noqa: E402


def hidden(flat: np.ndarray, share: float = 0.26) -> np.ndarray:
    out = flat.copy()
    width = flat.shape[2]
    half = int(width * share)
    fill = flat.mean(axis=(1, 2), keepdims=True).astype(np.uint8)
    out[:, :, : width // 2 - half] = fill
    out[:, :, width // 2 + half :] = fill
    return out


records = []  # (name, truth, [windows of each photo the board is found in])
for folder in sorted(Path(sys.argv[2]).glob(sys.argv[3] + "*")):
    info = json.loads((folder / "info.json").read_text())
    if info.get("outcome") != "accepted" or not info.get("corrected"):
        continue
    photos = []
    for photo in sorted(folder.glob("photo*.jpg")):
        image = load_image(photo)
        corners = find_playing_area(image)
        if corners is not None:
            w = board_windows(image, corners)
            photos.append(w.reshape(64, *w.shape[2:]))
    if photos:
        records.append((folder.name, fen_to_board(info["corrected"]), photos))
print(f"{len(records)} confirmed positions with {sum(len(p) for _, _, p in records)} photos")

draw = np.random.default_rng(1)
first = None
for path in sys.argv[4:]:
    model = PieceRecogniser(Path(path))
    alone_right = positions_right = wrong_squares = 0
    share = []  # per photo: squares read wrong alone, in a hundred with a piece
    for name, truth, photos in records:
        occupied = truth != EMPTY
        views = []
        for flat in photos:
            probs = ((model.predict(flat) + model.predict(hidden(flat))) / 2).reshape(8, 8, -1)
            side, _, _ = choose_white_side(probs, occupied)
            view = np.zeros_like(probs)
            for rank in range(8):
                for file in range(8):
                    a, b = view_cell(file, rank, side), view_cell(file, rank, "bottom")
                    view[b[0], b[1]] = probs[a[0], a[1]]
            views.append(view)
            board, _ = decode_position(view, "bottom", occupied)
            alone_right += int((board == truth).all())
            wrong_squares += int((board != truth).sum())
            on_board = np.zeros((8, 8), dtype=int)
            for rank in range(8):
                for file in range(8):
                    vr, vc = view_cell(file, rank, "bottom")
                    on_board[rank, file] = view[vr, vc].argmax()
            share.append(100 * (on_board[occupied] != truth[occupied]).mean())
        board, _ = decode_position(np.mean(views, axis=0), "bottom", occupied)
        positions_right += int((board == truth).all())
    share = np.array(share)
    line = (f"{Path(path).parts[-3]:38s} photos right alone {alone_right:2d}/{len(share)}, wrong squares {wrong_squares:3d} | "
            f"positions right together {positions_right:2d}/{len(records)} | read wrong alone {share.mean():5.2f} %")
    if first is None:
        first = share
    else:
        d = share - first
        means = [d[draw.integers(len(d), size=len(d))].mean() for _ in range(2000)]
        lo, hi = np.percentile(means, [5, 95])
        line += f" | to the first {d.mean():+.2f} ({lo:+.2f} to {hi:+.2f}){'' if lo <= 0 <= hi else '  <- told apart'}"
    print(line, flush=True)
