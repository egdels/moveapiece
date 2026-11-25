"""Recognisers compared more finely: every photo is read several times, with the frame moved a little.

A network that is sure of a piece and wrong often reads it right when the frame lies a twentieth
of a square elsewhere. So two or three boards between two networks say little on
these few photos. Here every photo is read with the frame as found and with eight frames moved
by up to 0.06 squares (the same for every network); counted are the wrong squares of every
reading, not of their mean.

Counted too is what the network reads wrong square by square, before the usual numbers of pieces
are kept: of the squares with a piece, those where the piece it holds most likely is not the one
standing there. With all pieces on the board the numbers repair nearly every such mistake, in a
played position with pieces taken they often cannot, so this share says most
about the network itself and does not hang on the position. It is given by piece as well. For every network after the first the difference to the first is
given with the range it has when the photos are drawn anew (bootstrap, 90 in a hundred): if the
range holds zero, the photos do not tell the two apart.

Photos: surroundings 4 of the collector app and the device records, in their three groups.

    python evaluation/measure_with_moved_frames.py . runs/composed_800/checkpoints/best.pt runs/other/checkpoints/best.pt
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
from squares import EMPTY, FEN_SYMBOLS, fen_to_board, load_image, view_cell  # noqa: E402

FRAMES = 8  # moved frames beside the one found
MOVED = 0.06  # of a square, the whole frame
BENT = 0.02  # of a square, each corner on its own

src = (BV / "evaluation/measure_on_device_records.py").read_text()
KNOWN = eval(src[src.index("KNOWN = [") + 8: src.index("]\n", src.index("KNOWN = [")) + 1])
occ = lambda b: "".join("1" if b[7 - i // 8, i % 8] != EMPTY else "0" for i in range(64))

photos = []  # (group, path, possible positions)
for s in (4,):  # surroundings 5 and 6 are training data
    for note in sorted((BV / f"samples/collector/collection/boards/Pegasus/surroundings_{s}").glob("*/*.json")):
        if note.stem.endswith("_differs"):
            continue
        truth = fen_to_board(json.loads(note.read_text())["fen"])
        if (truth != EMPTY).any():
            photos.append((f"surroundings {s}", note.with_suffix(".jpg"), [truth]))
for folder in sorted((BV / "samples/device/board-photos").iterdir()):
    info = json.loads((folder / "info.json").read_text())
    truths = [fen_to_board(f) for f in KNOWN if occ(fen_to_board(f)) == info["occupancy"]]
    if not truths:
        continue
    deg, share = info.get("camera_degrees_above_table"), info.get("board_share_of_photo")
    group = "before the angle was shown" if deg is None else ("in the camera window" if 50 <= deg <= 70 and share >= 0.25 else "angle known, outside")
    photos.append((group, folder / "photo.jpg", truths))

paths = sys.argv[2:]
models = [PieceRecogniser(Path(p)) for p in paths]
rng = np.random.default_rng(0)
wrong = {}  # group -> list over photos of an array (models, readings)
alone = {}  # the same for the squares read wrong before the numbers of pieces are kept, and the squares with a piece
by_piece = {}  # (group, model, piece) -> [wrong alone, squares]
for group, path, truths in photos:
    image = load_image(path)
    found = find_playing_area(image)
    if found is None:
        continue
    corners = np.asarray(found, dtype=np.float64)
    square = np.linalg.norm(corners[1] - corners[0]) / 8
    frames = [corners] + [corners + rng.uniform(-MOVED, MOVED, (1, 2)) * square + rng.uniform(-BENT, BENT, (4, 2)) * square for _ in range(FRAMES)]
    counts = np.zeros((len(models), len(frames)))
    raw = np.zeros((len(models), len(frames)))
    pieces = 0
    for j, frame in enumerate(frames):
        w = board_windows(image, frame.astype(np.float32))
        flat = w.reshape(64, *w.shape[2:])
        for i, model in enumerate(models):
            probs = model.predict(flat).reshape(8, 8, -1)
            best = None
            for truth in truths:
                o = truth != EMPTY
                side, _, _ = choose_white_side(probs, o)
                board, _ = decode_position(probs, side, o)
                n = int((board != truth).sum())
                if best is None or n < best[0]:
                    best = (n, truth, side)
            counts[i, j], truth, side = best
            pieces = int((truth != EMPTY).sum())
            for rank, file in zip(*(truth != EMPTY).nonzero()):
                vr, vc = view_cell(file, rank, side)
                miss = int(probs[vr, vc].argmax() != truth[rank, file])
                raw[i, j] += miss
                t = by_piece.setdefault((group, i, FEN_SYMBOLS[truth[rank, file]]), [0, 0])
                t[0] += miss
                t[1] += 1
    wrong.setdefault(group, []).append(counts)
    alone.setdefault(group, []).append((raw, pieces))

draw = np.random.default_rng(1)
for group in ["in the camera window", "surroundings 4", "angle known, outside", "before the angle was shown"]:
    if group not in wrong:
        continue
    c = np.stack(wrong[group])  # photos, models, readings
    print(f"\n{group}: {len(c)} photos, {c.shape[2]} readings each")
    per_photo = c.mean(axis=2)  # photos, models
    for i, p in enumerate(paths):
        line = (f"  {Path(p).parts[-3]:28s} as found {int((c[:, i, 0] == 0).sum()):2d} boards right | "
                f"readings right {100 * (c[:, i] == 0).mean():5.1f} %, wrong squares per reading {per_photo[:, i].mean():.2f}")
        if i:
            d = per_photo[:, i] - per_photo[:, 0]
            means = [d[draw.integers(len(d), size=len(d))].mean() for _ in range(2000)]
            lo, hi = np.percentile(means, [5, 95])
            line += f" | to the first {d.mean():+.2f} ({lo:+.2f} to {hi:+.2f}){'' if lo <= 0 <= hi else '  <- told apart'}"
        print(line)
    print("  squares read wrong alone, before the usual numbers of pieces are kept, in a hundred with a piece:")
    share = np.stack([100 * raw.mean(axis=1) / pieces for raw, pieces in alone[group]])  # photos, models
    for i, p in enumerate(paths):
        line = f"  {Path(p).parts[-3]:28s} {share[:, i].mean():5.2f}"
        if i:
            d = share[:, i] - share[:, 0]
            means = [d[draw.integers(len(d), size=len(d))].mean() for _ in range(2000)]
            lo, hi = np.percentile(means, [5, 95])
            line += f" | to the first {d.mean():+.2f} ({lo:+.2f} to {hi:+.2f}){'' if lo <= 0 <= hi else '  <- told apart'}"
        print(line + "   " + " ".join(f"{s} {100 * by_piece[(group, i, s)][0] / by_piece[(group, i, s)][1]:.0f}" for s in "KQRBNPkqrbnp" if (group, i, s) in by_piece))
