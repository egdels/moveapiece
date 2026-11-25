"""A training manifest of the collector app's photos of played positions, films included.

Real photos in the training halve what a recogniser reads wrong, but nearly all real photos so far
show the starting position or a Chess960 start. These are photos of positions out of games and of
endings (surroundings 5 and 6 of the Pegasus), each with the position in its note.
Where White sits in a photo is taken from a recogniser and the occupancy of the position.

    python manifest_of_played_positions.py . runs/x/checkpoints/best.pt data/played.jsonl 5 6
"""
import json
import sys
from pathlib import Path

import numpy as np

BV = Path(sys.argv[1]).resolve()
sys.path.insert(0, str(BV))
from decode import choose_white_side  # noqa: E402
from find_board_frame import find_playing_area  # noqa: E402
from piece_model import PieceRecogniser  # noqa: E402
from piece_windows import board_windows  # noqa: E402
from squares import EMPTY, fen_to_board, load_image  # noqa: E402

model = PieceRecogniser(Path(sys.argv[2]))
out = Path(sys.argv[3]).resolve()
boards = BV / "samples/collector/collection/boards/Pegasus"
lines, skipped = [], 0
for number in sys.argv[4:]:
    folder = boards / f"surroundings_{number}"
    for note in sorted(list(folder.glob("*/*.json")) + list(folder.glob("*/film_*/*.json"))):
        if note.stem.endswith("_differs"):
            continue
        info = json.loads(note.read_text())
        truth = fen_to_board(info["fen"])
        if not (truth != EMPTY).any():
            continue
        image = load_image(note.with_suffix(".jpg"))
        corners = find_playing_area(image)
        if corners is None:
            skipped += 1
            continue
        w = board_windows(image, corners)
        side, _, _ = choose_white_side(model.predict(w.reshape(64, *w.shape[2:])).reshape(8, 8, -1), truth != EMPTY)
        lines.append(json.dumps({
            "image": str(Path("..") / note.with_suffix(".jpg").relative_to(BV)) if out.parent == BV / "data" else str(note.with_suffix(".jpg")),
            "fen": info["fen"],
            "white_side": side,
            "series": f"played_{number}_{info['position']}",
            "corners": np.asarray(corners).round(1).tolist(),
        }))
out.write_text("\n".join(lines) + "\n", encoding="utf-8")
print(f"{len(lines)} photos in {out}, the board not found in {skipped}")
