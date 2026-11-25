"""How well a recogniser reads the board photos of the collector app, whose positions are known.

The whole chain as in the app: frame, windows, occupancy as the board would report it, usual pieces.

    python evaluation/measure_on_collector_photos.py . samples/collector/collection/boards/Pegasus/surroundings_4 runs/boards_v2/checkpoints/best.pt
"""
import json
import sys
from pathlib import Path

BV = Path(sys.argv[1])
sys.path.insert(0, str(BV))
from decode import choose_white_side, decode_position  # noqa: E402
from find_board_frame import find_playing_area  # noqa: E402
from piece_model import PieceRecogniser  # noqa: E402
from piece_windows import board_windows  # noqa: E402
from squares import EMPTY, FEN_SYMBOLS, fen_to_board, load_image, square_name  # noqa: E402

records, not_found = [], []
for note in sorted(Path(sys.argv[2]).glob("*/*.json")):
    if note.stem.endswith("_differs"):
        continue
    info = json.loads(note.read_text())
    truth = fen_to_board(info["fen"])
    if not (truth != EMPTY).any():
        continue
    image = load_image(note.with_suffix(".jpg"))
    corners = find_playing_area(image)
    if corners is None:
        not_found.append(note.stem)
        continue
    windows = board_windows(image, corners)
    records.append((info["position"], note.stem, windows.reshape(64, *windows.shape[2:]), truth, info.get("camera_degrees_in_photo")))
print(f"{len(records)} photos with a frame, {len(not_found)} without: {not_found}")

for path in sys.argv[3:]:
    model = PieceRecogniser(Path(path))
    print(f"\n{path}")
    by_position, mistakes = {}, {}
    for position, name, windows, truth, degrees in records:
        probs = model.predict(windows).reshape(8, 8, -1)
        occupied = truth != EMPTY
        side, _, _ = choose_white_side(probs, occupied)
        board, _ = decode_position(probs, side, occupied)
        wrong = board != truth
        t = by_position.setdefault(position, [0, 0, 0, 0])
        t[0] += int(occupied.sum()); t[1] += int(occupied.sum() - wrong.sum()); t[2] += 1; t[3] += int(not wrong.any())
        for rank, file in zip(*wrong.nonzero()):
            key = f"{FEN_SYMBOLS[truth[rank, file]]} on {square_name(file, rank)} read as {FEN_SYMBOLS[board[rank, file]]}"
            mistakes.setdefault(position, {}).setdefault(key, 0)
            mistakes[position][key] += 1
    total = [sum(t[i] for t in by_position.values()) for i in range(4)]
    for position, t in sorted(by_position.items()):
        print(f"  {position:14s} {t[1]}/{t[0]} pieces, {t[3]}/{t[2]} boards right   {dict(sorted(mistakes.get(position, {}).items(), key=lambda kv: -kv[1]))}")
    print(f"  all            {total[1]}/{total[0]} pieces = {100 * total[1] / total[0]:.1f} %, {total[3]}/{total[2]} boards right")
