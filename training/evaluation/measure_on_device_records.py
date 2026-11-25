"""How well a recogniser reads the photos the app left on the device (samples/device/board-photos).

The true position of a record is the one of the known positions (below) that has the occupancy
the board reported; "corrected" in info.json is no safe label, some were confirmed with mistakes.
The whole chain as in the app: frame, windows, occupancy from the board, usual pieces.

    python evaluation/measure_on_device_records.py . runs/boards_v2/checkpoints/best.pt runs/other/checkpoints/best.pt
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
from squares import EMPTY, fen_to_board, load_image  # noqa: E402

KNOWN = [
    "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR",
    "bnrkrbnq/pppppppp/8/8/8/8/PPPPPPPP/BNRKRBNQ",
    "bnrkrb1q/pppppppp/5n2/8/4P3/8/PPPP1PPP/BNRKRBNQ",
    "bnrkrbnq/pp1ppppp/2p5/8/4P3/8/PPPP1PPP/BNRKRBNQ",
    "rnbq1bnr/ppp1kppp/8/3pp3/3P4/4P3/PPPQ1PPP/RNB1KBNR",
    "8/3k4/5n2/8/PPPPPPPP/5B2/2N1K1N1/8",
    "8/8/3P1n2/1Pk5/P3PPPP/5B2/2N1K1N1/8",
]


def occupancy(board):
    return "".join("1" if board[7 - i // 8, i % 8] != EMPTY else "0" for i in range(64))


records = []
for folder in sorted((BV / "samples/device/board-photos").iterdir()):
    info = json.loads((folder / "info.json").read_text())
    truths = [fen_to_board(f) for f in KNOWN if occupancy(fen_to_board(f)) == info["occupancy"]]
    image = load_image(folder / "photo.jpg")
    corners = find_playing_area(image)
    if corners is None or not truths:
        continue
    windows = board_windows(image, corners)
    deg, share = info.get("camera_degrees_above_table"), info.get("board_share_of_photo")
    # Three kinds: in the camera window; with an angle recorded but outside the window; and taken
    # before the app showed and recorded the angle. Those were
    # looked at: nearly level over the board, far flatter than the window allows.
    kind = "before" if deg is None else "inside" if 50 <= deg <= 70 and share >= 0.25 else "outside"
    records.append((windows.reshape(64, *windows.shape[2:]), truths, kind))
print(f"{len(records)} photos with a frame and a known position, {sum(r[2] == 'inside' for r in records)} of them in the camera window")

for path in sys.argv[2:]:
    model = PieceRecogniser(Path(path))
    total = {k: [0, 0, 0, 0] for k in ("inside", "outside", "before")}  # pieces, right, photos, boards right
    for windows, truths, inside in records:
        probs = model.predict(windows).reshape(8, 8, -1)
        wrong = None
        for truth in truths:  # two known positions may share an occupancy: the nearer one counts
            occupied = truth != EMPTY
            side, _, _ = choose_white_side(probs, occupied)
            board, _ = decode_position(probs, side, occupied)
            w = int((board != truth).sum())
            if wrong is None or w < wrong:
                wrong, pieces = w, int(occupied.sum())
        t = total[inside]
        t[0] += pieces; t[1] += pieces - wrong; t[2] += 1; t[3] += wrong == 0
    a = [sum(t[i] for t in total.values()) for i in range(4)]
    print(f"\n{path}")
    print(f"  all photos:           {a[1]}/{a[0]} pieces = {100 * a[1] / a[0]:.1f} %, {a[3]}/{a[2]} boards right")
    for kind, name in (("inside", "in the camera window:"), ("outside", "angle known, outside: "), ("before", "before the angle was shown:")):
        i = total[kind]
        if i[2]:
            print(f"  {name} {i[1]}/{i[0]} pieces = {100 * i[1] / i[0]:.1f} %, {i[3]}/{i[2]} boards right")
