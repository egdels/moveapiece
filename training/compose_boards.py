# Copyright (C) 2026 Christian Kierdorf
# SPDX-License-Identifier: GPL-3.0-or-later
"""Photos of positions that nobody set up: cut-out pieces put on photos of the empty board.

Setting up positions is the work; photographing a piece alone and the empty
board is not. So the two are photographed apart (the collector app) and put
together here, as often as wanted and with the position known:

- the camera pose follows from the four corners of the playing area (as in
  piece_windows.py), and with it from which angle each square is seen;
- for every piece a cut-out of that piece seen from about that angle is
  taken (cut_out_piece_photos.py writes the angle), brought to the size its
  foot has on that square, leaned the way "up from the board" points there
  and pasted, far pieces first;
- a soft shadow under the foot, and the light of the board photo on the
  piece (its brightness and its tint).

Output: <out>/<name>.jpg and <out>/boards.jsonl, a manifest as label_photos.py
writes it, for finetune_on_boards.py.

Usage:
  python compose_boards.py --cutouts data/photo_cutouts \
      --boards samples/collector/collection/boards/Pegasus \
      --pieces samples/collector/collection/pieces/Pegasus --out data/composed --count 400
"""

from __future__ import annotations

import argparse
import json
import math
import random
import re
from pathlib import Path

import cv2
import numpy as np

from find_board_frame import find_playing_area
from piece_windows import DEFAULT_FOCAL, PIECE_HEIGHT, board_to_image
from squares import CLASSES, EMPTY, FEN_SYMBOLS, board_to_fen, board_to_view, fen_to_board, load_image, view_to_board

SCALE = 0.5  # the photos are put together at this share of their size: a square is still wider than in a window
START = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR"
KIND_LETTER = {"king": "K", "queen": "Q", "rook": "R", "bishop": "B", "knight": "N", "pawn": "P"}
# Below this angle the foot of a piece is seen from the side and can be measured in its cut-out;
# above it the head covers the foot.
FOOT_SEEN_BELOW = 62.0
# A piece is taken from the cut-outs whose angle is nearest to the one wanted, one of this many.
NEAREST = 4
# Brightness of the light squares at which a piece keeps the brightness of its cut-out.
LIGHT_SQUARES_USUAL = 195.0
# What brings the cut-outs to the tone the pieces have in photos of the board (red, green, blue).
# Measured as the colour of the body of a piece divided by that of the light squares: in 40 real
# photos of positions white pieces had 1.22, 1.18, 1.15 and black ones 0.27, 0.28, 0.29; in 45
# photos put together without this, 0.94, 0.92, 0.96 and 0.25, 0.25, 0.27. Where the light
# squares are nearly white already, a white piece cannot be that much lighter and ends at white.
# (Those were measured with red and blue of the pieces swapped, see load_cutouts; the values here
# are the same with the two swapped back, not measured anew.)
TONE = {"white": (1.27, 1.28, 1.22), "black": (1.00, 1.12, 1.16)}


class Camera:
    """Pose of the camera from the playing area (as piece_windows.up_direction does it)."""

    def __init__(self, corners, width, height):
        self.f = DEFAULT_FOCAL * math.hypot(width, height)
        self.c = np.array([width / 2, height / 2])
        h = board_to_image(corners).astype(np.float64)
        k_inv = np.array([[1 / self.f, 0, -width / 2 / self.f], [0, 1 / self.f, -height / 2 / self.f], [0, 0, 1]])
        m = k_inv @ h
        s = 2.0 / (np.linalg.norm(m[:, 0]) + np.linalg.norm(m[:, 1]))
        self.r1, self.r2, self.t = m[:, 0] * s, m[:, 1] * s, m[:, 2] * s
        if self.t[2] < 0:
            self.r1, self.r2, self.t = -self.r1, -self.r2, -self.t
        n = np.cross(self.r1, self.r2)
        n /= np.linalg.norm(n)
        self.n = -n if n @ self.t > 0 else n  # towards the camera

    def point(self, u, v):
        return self.r1 * u + self.r2 * v + self.t

    def pixel(self, p):
        return p[:2] / p[2] * self.f + self.c

    def seen_from(self, p):
        return math.degrees(math.asin(float(self.n @ -p) / np.linalg.norm(p)))


# --- the cut-outs ---------------------------------------------------------------------------


def measure(alpha: np.ndarray) -> dict:
    """Where the foot is in a cut-out and how wide the piece is: all in pixels of the cut-out."""
    rows = np.where(alpha.any(axis=1))[0]
    top, bottom = int(rows[0]), int(rows[-1])
    widths = alpha.sum(axis=1)
    foot = 0
    for y in range(int(bottom - 0.35 * (bottom - top)), bottom + 1):
        xs = np.where(alpha[y])[0]
        if len(xs):
            foot = max(foot, int(xs[-1] - xs[0]))
    low = np.where(alpha[int(bottom - 0.06 * (bottom - top)) : bottom + 1].any(axis=0))[0]
    return {"bottom": bottom, "foot": foot, "widest": int(widths.max()), "middle": (low[0] + low[-1]) / 2}


def load_cutouts(folder: Path, foot_mm: dict) -> dict:
    """The cut-outs by class id, each with its angle and its pixels per millimetre."""
    by_class: dict[int, list] = {}
    for line in (folder / "cutouts.jsonl").read_text(encoding="utf-8").splitlines():
        rec = json.loads(line)
        img = cv2.imread(str(folder / rec["file"]), cv2.IMREAD_UNCHANGED)
        if img is None:
            continue
        # OpenCV reads blue first, the photos of the board are red first. At first this was
        # left out and every piece was pasted with red and blue swapped; TONE made up for it on
        # the whole (its red and blue were swapped too), but a bluish gleam on a black piece came
        # out brown.
        img = cv2.cvtColor(img, cv2.COLOR_BGRA2RGBA)
        rec.update(measure(img[..., 3] > 127))
        side = re.search(r"_s(\d+)_", rec["file"])
        rec["side"] = int(side.group(1)) if side else 0
        rec["img"] = img
        by_class.setdefault(CLASSES.index(f"{rec['colour']}_{rec['piece']}"), []).append(rec)
    for cls, recs in by_class.items():
        mm = foot_mm[KIND_LETTER[recs[0]["piece"]]]
        # How much wider than its foot the piece is, as seen from this side of it: 1 for a piece
        # that is round and widest at the foot, more for a knight seen from the side.
        flat = [r for r in recs if r["seen_from"] < FOOT_SEEN_BELOW and r["foot"] > 0]
        for r in recs:
            same = [f["widest"] / f["foot"] for f in flat if f["side"] == r["side"]] or [f["widest"] / f["foot"] for f in flat] or [1.0]
            r["wider"] = float(np.median(same))
            if r["seen_from"] < FOOT_SEEN_BELOW and r["foot"] > 0:
                r["px_per_mm"] = r["foot"] / mm
            else:
                r["px_per_mm"] = r["widest"] / (mm * r["wider"])
            r["foot_mm"] = mm
        recs.sort(key=lambda r: r["seen_from"])
    return by_class


# --- positions ------------------------------------------------------------------------------


def random_position(rng: random.Random) -> np.ndarray:
    """A position as board[rank][file]: no real game, but with what a game has - pawn chains,
    pieces at home and away, crowded and nearly empty boards - and never more of a kind than a
    game starts with (as decode.py assumes)."""
    kind = rng.choices(("start", "opening", "middle", "ending"), (0.15, 0.3, 0.35, 0.2))[0]
    if kind == "ending":
        board = np.zeros((8, 8), dtype=np.int64)
        pool = [c for c in fen_to_board(START).reshape(64) if c != EMPTY and FEN_SYMBOLS[c] not in "Kk"]
        chosen = [FEN_SYMBOLS.index("K"), FEN_SYMBOLS.index("k")] + rng.sample(pool, rng.randint(1, 10))
        for cls in chosen:
            while True:
                rank, file = rng.randrange(8), rng.randrange(8)
                if board[rank, file] == EMPTY and not (FEN_SYMBOLS[cls] in "Pp" and rank in (0, 7)):
                    board[rank, file] = cls
                    break
        return board
    back = list("rnbqkbnr")
    if rng.random() < 0.6:
        rng.shuffle(back)
    board = fen_to_board("".join(back) + "/pppppppp/8/8/8/8/PPPPPPPP/" + "".join(back).upper())
    if kind == "start":
        return board
    if kind == "middle":
        pieces = [(r, f) for r in range(8) for f in range(8) if board[r, f] != EMPTY and FEN_SYMBOLS[board[r, f]] not in "Kk"]
        for r, f in rng.sample(pieces, rng.randint(2, 14)):
            board[r, f] = EMPTY
    for _ in range(rng.randint(2, 10) if kind == "opening" else rng.randint(10, 30)):
        pieces = [(r, f) for r in range(8) for f in range(8) if board[r, f] != EMPTY]
        r, f = rng.choice(pieces)
        cls = board[r, f]
        if FEN_SYMBOLS[cls] in "Pp":
            # a pawn goes forward, sometimes sideways as after a capture
            step = (1 if FEN_SYMBOLS[cls] == "P" else -1) * rng.choice((1, 1, 2))
            r2, f2 = r + step, f + rng.choice((0, 0, 0, -1, 1))
            if not (1 <= r2 <= 6 and 0 <= f2 <= 7):
                continue
        else:
            r2, f2 = rng.randrange(8), rng.randrange(8)
        if board[r2, f2] == EMPTY:
            board[r2, f2], board[r, f] = cls, EMPTY
    return board


# --- putting together -----------------------------------------------------------------------


def light_of(canvas: np.ndarray, corners: np.ndarray) -> tuple[np.ndarray, bool]:
    """Colour of the light squares of the empty board, and whether its square at the bottom
    right of the view is a light one (then White sits at the bottom or at the top)."""
    flat = cv2.warpPerspective(canvas, cv2.getPerspectiveTransform(corners.astype(np.float32), np.float32([[0, 0], [64, 0], [64, 64], [0, 64]])), (64, 64))
    cells = flat.reshape(8, 8, 8, 8, 3)[:, 1:7, :, 1:7].mean(axis=(1, 3))
    brightness = cells.mean(axis=2)
    parity = np.indices((8, 8)).sum(axis=0) % 2
    light_parity = int(brightness[parity == 1].mean() > brightness[parity == 0].mean())
    return cells[parity == light_parity].mean(axis=0), light_parity == 0  # the cell (7, 7) has parity 0


def paste(canvas: np.ndarray, cam: Camera, cut: dict, u: float, v: float, square_mm: float, light: dict, rng: random.Random, plain: float = 0.0) -> bool:
    """Puts one cut-out with its foot on (u, v) of the board. False if it would leave the photo.

    plain: if not 0, what is seen inside the piece is smoothed away over this share of the width
    of its foot; its outline and how light and shade lie on it as a whole stay.
    """
    height, width = canvas.shape[:2]
    p = cam.point(u, v)
    angle = cam.seen_from(p)
    across = np.cross(cam.n, p)
    across /= np.linalg.norm(across)
    r = cut["foot_mm"] / square_mm / 2
    foot_px = float(np.linalg.norm(cam.pixel(p + across * r) - cam.pixel(p - across * r)))
    base, top = cam.pixel(p), cam.pixel(p + cam.n * PIECE_HEIGHT)
    up = (top - base) / np.linalg.norm(top - base)
    turn = math.degrees(math.atan2(up[0], -up[1]))  # leaning to the right is positive

    scale = foot_px / (cut["foot_mm"] * cut["px_per_mm"])
    img = cut["img"].astype(np.float32)
    img[..., :3] *= img[..., 3:4] / 255.0  # the colour of the mat outside the piece must not bleed in
    # The middle of the round foot: half its short axis above the lowest point of the piece.
    fx = cut["middle"]
    fy = cut["bottom"] - cut["foot_mm"] * cut["px_per_mm"] / 2 * math.sin(math.radians(cut["seen_from"]))
    if scale < 0.7:
        img = cv2.resize(img, None, fx=scale, fy=scale, interpolation=cv2.INTER_AREA)
        fx, fy, scale = fx * scale, fy * scale, 1.0
    m = cv2.getRotationMatrix2D((fx, fy), -turn, scale)
    m[0, 2] += base[0] - fx
    m[1, 2] += base[1] - fy
    h, w = img.shape[:2]
    box = (m @ np.array([[0, 0, 1], [w, 0, 1], [w, h, 1], [0, h, 1]], dtype=np.float64).T).T
    x0, y0 = np.floor(box.min(axis=0)).astype(int) - 2
    x1, y1 = np.ceil(box.max(axis=0)).astype(int) + 2
    if x0 < 0 or y0 < 0 or x1 > width or y1 > height:
        return False
    m[0, 2] -= x0
    m[1, 2] -= y0
    warped = cv2.warpAffine(img, m, (int(x1 - x0), int(y1 - y0)), flags=cv2.INTER_LINEAR, borderMode=cv2.BORDER_CONSTANT, borderValue=0)
    alpha = warped[..., 3:4] / 255.0
    colour = warped[..., :3] * light["tint"] * light["gain"] * np.float32(TONE[cut["colour"]])
    colour = np.minimum(colour, 255.0 * alpha)  # premultiplied: nothing brighter than white
    if plain:
        # Smoothed inside the piece only: what lies outside it does not count (both are divided
        # by the smoothed outline), and the outline itself stays as sharp as it was.
        sigma = max(1.0, foot_px * plain)
        smooth = cv2.GaussianBlur(colour, (0, 0), sigma) / np.maximum(cv2.GaussianBlur(alpha, (0, 0), sigma)[..., None], 1e-3)
        colour = np.minimum(smooth, 255.0) * alpha

    # The shadow: an ellipse as wide as the foot, pushed away from the light, soft at the edge.
    reach = int(foot_px * 1.6) + 2
    sx0, sy0 = max(0, int(base[0]) - reach), max(0, int(base[1]) - reach)
    sx1, sy1 = min(width, int(base[0]) + reach), min(height, int(base[1]) + reach)
    shadow = np.zeros((sy1 - sy0, sx1 - sx0), np.float32)
    centre = base + light["away"] * foot_px * light["length"] - (sx0, sy0)
    axes = (max(1, int(foot_px * (0.5 + light["length"] * 0.4))), max(1, int(foot_px * 0.5 * math.sin(math.radians(angle)))))
    cv2.ellipse(shadow, (int(centre[0]), int(centre[1])), axes, math.degrees(math.atan2(light["away"][1], light["away"][0])), 0, 360, 1.0, -1)
    shadow = cv2.GaussianBlur(shadow, (0, 0), max(1.0, foot_px * light["soft"]))[..., None] * light["dark"]
    canvas[sy0:sy1, sx0:sx1] *= 1 - shadow

    part = canvas[y0:y1, x0:x1]
    part[:] = part * (1 - alpha) + colour
    return True


def with_officers_mixed(board: np.ndarray, rng: random.Random) -> np.ndarray:
    """The position with the pieces that are not pawns changing places among those of their colour.

    In played games a rook stands in the corner and a king behind his pawns, and a recogniser
    that sees the corner of the frame in its window learns to read a rook there whatever stands
    on the square (a white bishop on a1, plain to see, read as a rook with certainty
    in eight photos; of the pieces on corner squares 63 in a hundred were rooks). Mixed, the
    position keeps its look - where pieces stand, how crowded it is - but not what stands where.
    """
    board = board.copy()
    for white in (True, False):
        squares = [(r, f) for r in range(8) for f in range(8) if board[r, f] != EMPTY and FEN_SYMBOLS[board[r, f]] not in "Pp" and FEN_SYMBOLS[board[r, f]].isupper() == white]
        kinds = [board[r, f] for r, f in squares]
        rng.shuffle(kinds)
        for (r, f), kind in zip(squares, kinds):
            board[r, f] = kind
    return board


def compose(photo: np.ndarray, corners: np.ndarray, cutouts: dict, square_mm: float, rng: random.Random, played=(), played_share: float = 0.0, mix: random.Random | None = None, mix_share: float = 0.0, plain: random.Random | None = None, plain_share: float = 0.0):
    """One photo of a position: (picture, board[rank][file] of what was put on it, white side)."""
    canvas = photo.astype(np.float32)
    height, width = canvas.shape[:2]
    cam = Camera(corners, width, height)
    light_rgb, light_at_right = light_of(canvas, corners)
    white_side = rng.choice(("bottom", "top") if light_at_right else ("left", "right"))
    # a position of a played game, or one made up (which also mixes the back rank, so that the
    # recogniser does not learn which piece to expect on which square)
    if played and rng.random() < played_share:
        board = fen_to_board(rng.choice(played))
        if mix and mix.random() < mix_share:
            board = with_officers_mixed(board, mix)
    else:
        board = random_position(rng)
    view = board_to_view(board, white_side)
    direction = rng.uniform(0, 2 * math.pi)
    light = {
        "tint": np.clip(light_rgb / light_rgb.mean(), 0.9, 1.1).astype(np.float32),
        "gain": float(np.clip(light_rgb.mean() / LIGHT_SQUARES_USUAL, 0.75, 1.12)) * rng.uniform(0.93, 1.07),
        "away": np.array([math.cos(direction), math.sin(direction)]),
        "length": rng.uniform(0.0, 0.5),
        "soft": rng.uniform(0.12, 0.3),
        "dark": rng.uniform(0.2, 0.5),
    }
    cells = [(row, col) for row in range(8) for col in range(8) if view[row, col] != EMPTY]
    cells.sort(key=lambda c: -np.linalg.norm(cam.point(c[1] + 0.5, c[0] + 0.5)))  # far ones first
    for row, col in cells:
        u, v = col + 0.5 + rng.uniform(-0.13, 0.13), row + 0.5 + rng.uniform(-0.13, 0.13)
        wanted = cam.seen_from(cam.point(u, v))
        near = sorted(cutouts[int(view[row, col])], key=lambda c: abs(c["seen_from"] - wanted))[:NEAREST]
        # A piece now and then with nothing but its outline and its shading: a bishop is a bishop
        # by its shape, also from behind, where its slit is not seen (every network
        # trained on well exposed white pieces read a white bishop seen from behind as a pawn in
        # 40 of 48 photos, the two trained on pieces without detail - overexposed, or made from
        # the black ones - read it right).
        smoothed = plain.uniform(0.08, 0.25) if plain and plain.random() < plain_share else 0.0
        if not paste(canvas, cam, rng.choice(near), u, v, square_mm, light, rng, smoothed):
            view[row, col] = EMPTY  # the piece would be cut off by the edge of the photo: the square stays empty
    return np.clip(canvas, 0, 255).astype(np.uint8), view_to_board(view, white_side), white_side


def on_other_surroundings(photo: np.ndarray, outline: np.ndarray, background: np.ndarray, rng: random.Random) -> np.ndarray:
    """The board of a photo (RGB) on another background: what lies around the board is replaced.

    The network sees of the surroundings only what is beside and behind the squares at the edge,
    but there stand the pieces it confuses most, and all photos of the empty board come from a
    few places. `outline` are the four corners of the whole board, frame included;
    `background` is any picture, of which a part is taken, turned, mirrored, made lighter or
    darker and now and then a little out of focus. The board throws a soft shadow on it.
    """
    height, width = photo.shape[:2]
    # a part of the background, between half and the whole of its shorter side (of less, the
    # small pictures of a collection of textures become a blur)
    bh, bw = background.shape[:2]
    side = rng.uniform(0.5, 1.0) * min(bh, bw)
    middle = (rng.uniform(side / 2, bw - side / 2), rng.uniform(side / 2, bh - side / 2))
    m = cv2.getRotationMatrix2D(middle, rng.uniform(0, 360), max(height, width) * 1.45 / side)
    m[0, 2] += width / 2 - middle[0]
    m[1, 2] += height / 2 - middle[1]
    ground = cv2.warpAffine(background, m, (width, height), flags=cv2.INTER_LINEAR, borderMode=cv2.BORDER_REFLECT_101).astype(np.float32)
    if rng.random() < 0.5:
        ground = ground[:, ::-1]
    # about as light as the surroundings were, so that the board does not lie in another light
    mask = np.zeros((height, width), np.float32)
    cv2.fillPoly(mask, [outline.round().astype(np.int32)], 1.0, lineType=cv2.LINE_AA)
    around = cv2.dilate(mask, np.ones((3, 3), np.uint8), iterations=max(1, width // 12)) - mask > 0.5
    if around.any():
        was = float(photo[around].mean())
        ground *= np.clip(was / max(float(ground.mean()), 1.0), 0.4, 2.5) * rng.uniform(0.8, 1.2)
    if rng.random() < 0.5:
        ground = cv2.GaussianBlur(ground, (0, 0), rng.uniform(0.5, 2.5))
    # the shadow of the board: its outline pushed a little to one side, soft, on the ground only
    reach = rng.uniform(0.004, 0.02) * width
    turn = rng.uniform(0, 2 * math.pi)
    moved = cv2.warpAffine(mask, np.float32([[1, 0, reach * math.cos(turn)], [0, 1, reach * math.sin(turn)]]), (width, height))
    shadow = cv2.GaussianBlur(moved, (0, 0), max(1.0, reach * rng.uniform(0.6, 1.5))) * rng.uniform(0.2, 0.6)
    ground *= (1.0 - shadow)[..., None]
    soft = cv2.GaussianBlur(mask, (0, 0), 0.8)[..., None]
    return np.clip(photo * soft + ground * (1.0 - soft), 0, 255).astype(np.uint8)


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument("--cutouts", type=Path, required=True, help="cut-outs of cut_out_piece_photos.py")
    ap.add_argument("--boards", type=Path, required=True, help="the folder of one board of the collector app, with board.json")
    ap.add_argument("--pieces", type=Path, required=True, help="the folder of the set of pieces, with set.json")
    ap.add_argument("--out", type=Path, required=True)
    ap.add_argument("--count", type=int, default=400)
    ap.add_argument("--seed", type=int, default=1)
    ap.add_argument(
        "--tone-white",
        help="red,green,blue instead of TONE for white pieces: other photos of the pieces, another "
        "tone (the colour of the body of a piece divided by that of the light squares, see TONE)",
    )
    ap.add_argument("--tone-black", help="the same for black pieces")
    ap.add_argument("--positions", type=Path, help="positions of played games, a text file with one piece placement per line")
    ap.add_argument("--positions-share", type=float, default=0.5, help="share of the photos with a position of --positions (default 0.5)")
    ap.add_argument(
        "--mix-officers",
        type=float,
        default=0.5,
        help="share of the positions of --positions in which the pieces that are not pawns change places among those of their colour (default 0.5)",
    )
    ap.add_argument(
        "--plain-pieces",
        type=float,
        default=0.0,
        help="share of the pieces pasted with what is seen inside them smoothed away, so that the recogniser learns their outline (default 0)",
    )
    ap.add_argument("--board-outlines", type=Path, help="outlines of the whole board, a JSON file: photo -> its four corners; needed for --backgrounds")
    ap.add_argument("--backgrounds", type=Path, help="folder of pictures (also in folders below it) to put the board on instead of its own surroundings")
    ap.add_argument("--background-share", type=float, default=0.5, help="share of the photos that get another background (default 0.5)")
    ap.add_argument("--background-skip", default="chequered,grid", help="folders of --backgrounds left out, comma separated: what looks like a chessboard")
    args = ap.parse_args(argv)
    for colour, given in (("white", args.tone_white), ("black", args.tone_black)):
        if given:
            TONE[colour] = tuple(float(v) for v in given.split(","))

    rng = random.Random(args.seed)
    square_mm = json.loads((args.boards / "board.json").read_text(encoding="utf-8"))["square_mm"]
    foot_mm = json.loads((args.pieces / "set.json").read_text(encoding="utf-8"))["foot_mm"]
    cutouts = load_cutouts(args.cutouts, foot_mm)
    missing = [CLASSES[c] for c in range(1, 13) if c not in cutouts]
    if missing:
        raise SystemExit(f"no cut-outs of {missing}")
    print(f"{sum(len(v) for v in cutouts.values())} cut-outs of {len(cutouts)} pieces")

    args.out.mkdir(parents=True, exist_ok=True)
    photos = sorted(p for p in args.boards.glob("surroundings_*/empty_00/*.jpg") if not p.stem.endswith("_differs"))
    cache_file = args.out / "corners.json"
    cache = json.loads(cache_file.read_text(encoding="utf-8")) if cache_file.exists() else {}
    usable = []
    for path in photos:
        key = str(path.relative_to(args.boards))
        if key not in cache:
            found = find_playing_area(load_image(path))
            cache[key] = None if found is None else np.asarray(found).tolist()
            cache_file.write_text(json.dumps(cache), encoding="utf-8")
        if cache[key] is not None:
            usable.append((path, np.asarray(cache[key], dtype=np.float64)))
    print(f"{len(usable)} photos of the empty board ({len(photos) - len(usable)} without a playing area found)")

    rng.shuffle(usable)  # every surroundings from the first photos on
    played = args.positions.read_text(encoding="utf-8").split() if args.positions else []
    outlines, grounds = {}, []
    if args.backgrounds:
        if not args.board_outlines:
            raise SystemExit("--backgrounds needs --board-outlines")
        outlines = {k: np.asarray(v, dtype=np.float64) for k, v in json.loads(args.board_outlines.read_text(encoding="utf-8")).items() if v and k != "frame"}
        skip = {name for name in args.background_skip.split(",") if name}
        grounds = sorted(p for p in args.backgrounds.rglob("*") if p.suffix.lower() in (".jpg", ".jpeg", ".png") and not skip & set(p.relative_to(args.backgrounds).parts))
        if not grounds:
            raise SystemExit(f"no pictures in {args.backgrounds}")
        print(f"{len(grounds)} backgrounds, {len(outlines)} photos with the outline of the board")
    mixing = random.Random(args.seed + 2)  # its own dice as well
    plain = random.Random(args.seed + 3)
    other = random.Random(args.seed + 1)  # its own dice: the positions stay those of a run without --backgrounds
    replaced = 0
    lines, pieces = [], 0
    for n in range(args.count):
        path, corners = usable[n % len(usable)] if n < len(usable) else rng.choice(usable)
        photo = np.asarray(load_image(path))
        photo = cv2.resize(photo, None, fx=SCALE, fy=SCALE, interpolation=cv2.INTER_AREA)
        key = str(path.relative_to(args.boards))
        ground = None
        if key in outlines and other.random() < args.background_share:
            ground = other.choice(grounds)
            picture_of_ground = cv2.imread(str(ground))
            if picture_of_ground is not None:
                photo = on_other_surroundings(photo, outlines[key] * SCALE, cv2.cvtColor(picture_of_ground, cv2.COLOR_BGR2RGB), other)
                replaced += 1
            else:
                ground = None
        picture, board, white_side = compose(photo, corners * SCALE, cutouts, square_mm, rng, played, args.positions_share, mixing, args.mix_officers, plain, args.plain_pieces)
        name = f"composed_{n:05d}.jpg"
        cv2.imwrite(str(args.out / name), cv2.cvtColor(picture, cv2.COLOR_RGB2BGR), [cv2.IMWRITE_JPEG_QUALITY, rng.randint(86, 95)])
        lines.append(
            json.dumps(
                {
                    "image": name,
                    "fen": board_to_fen(board),
                    "white_side": white_side,
                    "series": "composed_" + path.parent.parent.name,
                    "corners": (corners * SCALE).round(1).tolist(),
                    "empty_board": str(path.relative_to(args.boards)),
                    **({"background": str(ground.relative_to(args.backgrounds))} if ground else {}),
                }
            )
        )
        pieces += int((board != EMPTY).sum())
        if (n + 1) % 50 == 0:
            print(f"{n + 1} photos")
    (args.out / "boards.jsonl").write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"{args.count} photos with {pieces} pieces in {args.out}" + (f", {replaced} of them on another background" if args.backgrounds else ""))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
