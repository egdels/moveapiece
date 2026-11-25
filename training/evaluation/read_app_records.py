"""A record of the dev app: every photo read alone and all together, as the app does it, against the corrected position."""
import json, sys
from pathlib import Path
import numpy as np
BV = Path(".").resolve(); sys.path.insert(0, str(BV))
from decode import choose_white_side, decode_position
from find_board_frame import find_playing_area
from piece_model import PieceRecogniser
from piece_windows import board_windows
from squares import EMPTY, FEN_SYMBOLS, fen_to_board, load_image, square_name, view_cell
from compose_boards import Camera
m = PieceRecogniser(Path(sys.argv[2]))
def hidden(flat, share=0.26):
    out = flat.copy(); w = flat.shape[2]; half = int(w * share)
    fill = flat.mean(axis=(1, 2), keepdims=True).astype(np.uint8)
    out[:, :, : w // 2 - half] = fill; out[:, :, w // 2 + half:] = fill
    return out
for folder in sys.argv[3:]:
    folder = Path(sys.argv[1]) / folder
    info = json.loads((folder / "info.json").read_text())
    if not info.get("corrected"): print(folder.name, "not confirmed"); continue
    truth = fen_to_board(info["corrected"]); o = truth != EMPTY
    views = []
    print(folder.name, info["outcome"], "| app read:", "right" if info["recognised"] == info["corrected"] else "wrong", "| uncertain:", info.get("uncertain"))
    for photo in sorted(folder.glob("photo*.jpg")):
        image = load_image(photo); c = find_playing_area(image)
        if c is None: print("  ", photo.name, "no board"); continue
        cam = Camera(np.asarray(c, dtype=np.float64), *image.size)
        w = board_windows(image, c); flat = w.reshape(64, *w.shape[2:])
        probs = ((m.predict(flat) + m.predict(hidden(flat))) / 2).reshape(8, 8, -1)
        side, _, _ = choose_white_side(probs, o); board, _ = decode_position(probs, side, o)
        wrong = [f"{FEN_SYMBOLS[truth[r, f]]}{square_name(f, r)}>{FEN_SYMBOLS[board[r, f]]}" for r, f in zip(*(board != truth).nonzero())]
        view = np.zeros_like(probs)
        for r in range(8):
            for f in range(8):
                a, b = view_cell(f, r, side), view_cell(f, r, "bottom"); view[b[0], b[1]] = probs[a[0], a[1]]
        views.append(view)
        print(f"   {photo.name}: {cam.seen_from(cam.point(4, 4)):.0f} degrees, white {side}, alone wrong: {wrong or 'nothing'}")
    board, _ = decode_position(np.mean(views, axis=0), "bottom", o)
    wrong = [f"{FEN_SYMBOLS[truth[r, f]]}{square_name(f, r)}>{FEN_SYMBOLS[board[r, f]]}" for r, f in zip(*(board != truth).nonzero())]
    print(f"   all {len(views)} together wrong: {wrong or 'nothing'}")
