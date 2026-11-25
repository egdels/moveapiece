#!/usr/bin/env python3
# Copyright (C) 2026 Christian Kierdorf
# SPDX-License-Identifier: GPL-3.0-or-later
"""Builds training images for the piece recogniser from single-piece cut-outs.

The pieces of the Pegasus always look the same, so the recogniser does not
need labelled board photos: it is shown the cut-outs of
extract_piece_cutouts.py in front of ever-changing surroundings, and so
learns the shapes and to ignore what is around them.

Each image is a window as piece_windows.py cuts it from a photo: 224 x 224
pixels, 2.6 squares across, the piece in question standing upright with its
foot at a fixed place in the window. Around it: a board (from photos of the empty board, or
drawn) or an arbitrary surface, other pieces on neighbouring squares, a soft
shadow, varying light.

Only dark pieces were filmed. Light pieces are made by inverting the
cut-outs (--white invert); whether that is close enough to the real ones is
what the test on real photos has to show.

It was not: the inverted pieces are flat light shapes, a bishop
among them is hardly told from a pawn, and white stayed the colour read
worse. With --photo-cutouts the windows are made from the photos of the
single pieces instead (cut_out_piece_photos.py): real light pieces, from four
sides, each with the angle it was seen from. All pieces of a window are then
seen from about the same angle, as in a photo, and the windows are blurred
less (--max-blur).

Classes are those of squares.CLASSES; `empty` is a window with nothing on
the middle square. Output: <out>/synth_train/<class>/ and
<out>/synth_val/<class>/, split by stretches of the videos so that
neighbouring frames do not end up on both sides.

Usage:
  python synthesize_pieces.py --cutouts data/cutouts --boards samples \
      --empty-boards IMG_a.jpg,IMG_b.jpg --out data/synth
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path

import cv2
import numpy as np

from extract_piece_cutouts import PIECES
from piece_windows import BASE_X, BASE_Y, WINDOW_EXTENT, WINDOW_SIZE, board_to_image, cut_window, square_window_geometry
from squares import CLASSES, load_image

SQUARE = WINDOW_SIZE / WINDOW_EXTENT  # pixels per square in a window
PX = WINDOW_SIZE / 128.0  # blur radii and shadow offsets are given for 128-pixel windows

# The DGT Pegasus and its pieces, measured with a ruler: edge of a square and
# diameter of the foot of each piece, in millimetres. A video has no scale, so this is what
# puts the pieces into the right proportion to the board and to each other.
SQUARE_MM = 40.0
FOOT_MM = {"king": 30.0, "queen": 27.0, "rook": 24.0, "bishop": 24.0, "knight": 24.0, "pawn": 20.0}


class Cutout:
    """A piece cut-out, turned upright (base down) and measured."""

    def __init__(self, path: Path):
        rgba = cv2.imread(str(path), cv2.IMREAD_UNCHANGED)
        mask = (rgba[..., 3] > 127).astype(np.uint8)
        pts = cv2.findNonZero(mask).reshape(-1, 2).astype(np.float32)
        mean, vecs, vals = cv2.PCACompute2(pts, mean=None)
        axis = vecs[0]
        along = (pts - mean) @ axis
        across = (pts - mean) @ vecs[1]
        length, width = np.ptp(along), np.ptp(across)
        self.elongation = float(length / max(width, 1.0))
        if self.elongation < 1.25:
            # Seen from above: the base is under the middle of the blob.
            base = mean[0]
            self.base_width = float(width)
            angle = 0.0
        else:
            # The base is the wider end.
            lo, hi = along.min(), along.max()
            near_lo = across[along < lo + 0.22 * length]
            near_hi = across[along > hi - 0.22 * length]
            base_at_hi = np.ptp(near_hi) > np.ptp(near_lo)
            end_width = float(np.ptp(near_hi if base_at_hi else near_lo))
            direction = axis if base_at_hi else -axis  # points from the top to the base
            base = mean[0] + direction * ((hi if base_at_hi else -lo) - 0.5 * end_width * 0.6)
            self.base_width = end_width
            # Rotate so the base points down (+y).
            angle = float(np.degrees(np.arctan2(direction[0], direction[1])))
        self.rgba = rgba
        self.base = (float(base[0]), float(base[1]))
        self.upright_angle = angle  # rotating the image by -angle (cv2, counter-clockwise) puts the base down

    def render(self, base_width_px: float, angle: float, white: bool, rng) -> tuple[np.ndarray, np.ndarray]:
        """Piece as (rgb float 0..255, alpha 0..1) on a WINDOW_SIZE canvas with its foot at (BASE_X, BASE_Y)."""
        size = WINDOW_SIZE
        scale = base_width_px / max(self.base_width, 1.0)
        src = self.rgba
        if scale < 0.5:
            pre = scale * 2
            src = cv2.resize(src, None, fx=pre, fy=pre, interpolation=cv2.INTER_AREA)
            base = (self.base[0] * pre, self.base[1] * pre)
            scale /= pre
        else:
            base = self.base
        matrix = cv2.getRotationMatrix2D(base, -self.upright_angle + angle, scale)
        matrix[0, 2] += BASE_X * size - base[0]
        matrix[1, 2] += BASE_Y * size - base[1]
        out = cv2.warpAffine(src, matrix, (size, size), flags=cv2.INTER_LINEAR, borderMode=cv2.BORDER_CONSTANT, borderValue=0)
        rgb = out[..., 2::-1].astype(np.float32)
        alpha = out[..., 3].astype(np.float32) / 255.0
        if white:
            rgb = to_white(rgb, alpha, rng)
        else:
            rgb = rgb * rng.uniform(0.7, 1.5) + rng.uniform(-8, 14)
        return np.clip(rgb, 0, 255), alpha


class PhotoCutout:
    """A cut-out of cut_out_piece_photos.py: upright already, with the angle it was seen from."""

    elongation = 2.0  # never taken for a piece seen from above

    def __init__(self, rec: dict):
        # shrunk once to about twice the size it has in a window
        shrink = min(1.0, 2.0 * SQUARE * rec["foot_mm"] / SQUARE_MM / (rec["foot_mm"] * rec["px_per_mm"]))
        img = rec["img"].astype(np.float32)
        img[..., :3] *= img[..., 3:4] / 255.0  # the colour of the mat outside the piece must not bleed in
        self.rgba = cv2.resize(img, None, fx=shrink, fy=shrink, interpolation=cv2.INTER_AREA)
        self.foot_px = rec["foot_mm"] * rec["px_per_mm"] * shrink
        # the middle of the round foot: half its short axis above the lowest point of the piece
        self.base = (rec["middle"] * shrink, (rec["bottom"] - rec["foot_mm"] * rec["px_per_mm"] / 2 * np.sin(np.radians(rec["seen_from"]))) * shrink)
        self.seen_from = rec["seen_from"]
        self.tone = np.float32(PHOTO_TONE[rec["colour"]])

    def render(self, base_width_px: float, angle: float, white: bool, rng) -> tuple[np.ndarray, np.ndarray]:
        size = WINDOW_SIZE
        matrix = cv2.getRotationMatrix2D(self.base, angle, base_width_px / self.foot_px)
        matrix[0, 2] += BASE_X * size - self.base[0]
        matrix[1, 2] += BASE_Y * size - self.base[1]
        out = cv2.warpAffine(self.rgba, matrix, (size, size), flags=cv2.INTER_LINEAR, borderMode=cv2.BORDER_CONSTANT, borderValue=0)
        alpha = out[..., 3] / 255.0
        rgb = out[..., :3] / np.maximum(alpha, 1e-3)[..., None] * self.tone * rng.uniform(0.75, 1.15)
        return np.clip(rgb, 0, 255), alpha


# What brings the photo cut-outs to the tone the pieces have in photos of the board, as in compose_boards.py.
PHOTO_TONE = {"white": (1.29, 1.35, 1.28), "black": (1.30, 1.09, 1.09)}
MAX_BLUR = 3.5  # in pixels of a 128-pixel window
NEAREST = 4


def to_white(rgb: np.ndarray, alpha: np.ndarray, rng) -> np.ndarray:
    """A light piece from a dark one: invert, then stretch to the tonal range of the real pieces.

    Dark plastic spans little brightness, so a plain inversion gives a flat cream shape. The
    real light pieces go from a clear grey in the shade to almost white; the inverted
    brightness is stretched to that range, which also brings the edges and grooves out.
    """
    inverted = 255.0 - rgb
    inside = alpha > 0.5
    if inside.sum() < 10:
        return inverted
    grey = inverted.mean(axis=2)
    low, high = np.percentile(grey[inside], (4, 96))
    level = np.clip((grey - low) / max(high - low, 1.0), 0.0, 1.15) ** rng.uniform(0.7, 1.4)
    shade, light = rng.uniform(95, 165), rng.uniform(225, 255)
    tint = np.array([1.0, rng.uniform(0.96, 1.0), rng.uniform(0.88, 0.98)], dtype=np.float32)
    out = (shade + (light - shade) * level)[..., None] * tint
    if rng.random() < 0.5:
        out = out * rng.uniform(1.1, 1.6)  # burnt out, as light pieces often are in phone photos
    return out


def drawn_board(rng) -> np.ndarray:
    """A window-sized piece of a drawn chessboard, randomly placed and turned."""
    canvas = 3 * WINDOW_SIZE
    light = np.array([rng.uniform(170, 250)] * 3) * [1.0, rng.uniform(0.94, 1.02), rng.uniform(0.85, 1.02)]
    dark = light * rng.uniform(0.45, 0.85) * [rng.uniform(0.9, 1.05), rng.uniform(0.9, 1.05), rng.uniform(0.9, 1.05)]
    square = SQUARE * rng.uniform(0.85, 1.2)
    ys, xs = np.mgrid[0:canvas, 0:canvas]
    ox, oy = rng.uniform(0, 2 * square, 2)
    cells = (np.floor((xs + ox) / square) + np.floor((ys + oy) / square)) % 2
    img = np.where(cells[..., None] > 0, light, dark).astype(np.float32)
    if rng.random() < 0.3:  # the frame of the board somewhere in view
        edge = int(rng.uniform(0.3, 0.7) * canvas)
        img[:, :edge] = np.array([rng.uniform(15, 60)] * 3)
        img[:, : edge - int(rng.uniform(0.2, 0.5) * square)] = rng.uniform(40, 220, 3)
    img = cv2.GaussianBlur(img, (0, 0), rng.uniform(0.4, 1.2) * PX)
    return cut_window(img, (canvas / 2, canvas / 2), WINDOW_SIZE, WINDOW_SIZE, angle=rng.uniform(0, 360))


def plain_surface(rng) -> np.ndarray:
    base = rng.uniform(30, 235, 3)
    noise = rng.normal(0, rng.uniform(2, 25), (WINDOW_SIZE // 4, WINDOW_SIZE // 4, 3))
    img = base + cv2.resize(noise.astype(np.float32), (WINDOW_SIZE, WINDOW_SIZE), interpolation=cv2.INTER_CUBIC)
    gradient = np.linspace(-1, 1, WINDOW_SIZE, dtype=np.float32)[None, :, None] * rng.uniform(-30, 30)
    return (img + gradient).astype(np.float32)


class EmptyBoards:
    """Windows from photos of the empty board (those whose label sits in `empty.txt`, or all given)."""

    def __init__(self, folder: Path | None, names: list[str]):
        self.items = []
        if folder is None:
            return
        labels = {}
        labels_file = folder / "labels.jsonl"
        if labels_file.is_file():
            for line in labels_file.read_text(encoding="utf-8").splitlines():
                if line.strip():
                    rec = json.loads(line)
                    labels[rec["image"]] = rec["corners"]
        for name in names:
            if name in labels:
                img = np.asarray(load_image(folder / name))
                img = cv2.resize(img, None, fx=0.5, fy=0.5, interpolation=cv2.INTER_AREA)
                h = np.diag([0.5, 0.5, 1.0]) @ board_to_image(labels[name])
                self.items.append((img, h))

    def sample(self, rng) -> np.ndarray:
        img, h = self.items[rng.integers(len(self.items))]
        row, col = rng.integers(0, 8, 2)
        centre, square = square_window_geometry(h, int(row), int(col))
        centre = centre + rng.uniform(-0.25, 0.25, 2) * square
        side = WINDOW_EXTENT * square * rng.uniform(0.85, 1.2)
        return cut_window(img, centre, side, WINDOW_SIZE, angle=rng.uniform(0, 360)).astype(np.float32)


def paste(canvas: np.ndarray, rgb: np.ndarray, alpha: np.ndarray, dx: float, dy: float, shadow: float, rng) -> None:
    """Pastes a rendered piece shifted by (dx, dy), with a soft shadow of the given strength."""
    shift = np.float32([[1, 0, dx], [0, 1, dy]])
    size = (WINDOW_SIZE, WINDOW_SIZE)
    alpha = cv2.warpAffine(alpha, shift, size)
    rgb = cv2.warpAffine(rgb, shift, size)
    if shadow > 0:
        offset = np.float32([[1, 0, rng.uniform(-6, 6) * PX], [0, 1, rng.uniform(-6, 6) * PX]])
        soft = cv2.GaussianBlur(cv2.warpAffine(alpha, offset, size), (0, 0), rng.uniform(2, 6) * PX)
        canvas *= 1.0 - shadow * soft[..., None]
    canvas[:] = rgb * alpha[..., None] + canvas * (1 - alpha[..., None])


def make_window(cls: int, cutouts: dict, boards: EmptyBoards, rng, white_mode: str) -> np.ndarray:
    kind = rng.random()
    if boards.items and kind < 0.45:
        canvas = boards.sample(rng)
    elif kind < 0.85:
        canvas = drawn_board(rng).astype(np.float32)
    else:
        canvas = plain_surface(rng)
    canvas = canvas * rng.uniform(0.6, 1.25)

    # with photo cut-outs: the angle the middle square is seen from, the squares around it nearly the same
    seen_from = rng.uniform(42, 86)

    def random_piece(piece: str, white: bool):
        if "white" in cutouts:
            wanted = seen_from + rng.uniform(-3, 3)
            pool = sorted(cutouts["white" if white else "black"][piece], key=lambda c: abs(c.seen_from - wanted))[:NEAREST]
            cutout = pool[rng.integers(len(pool))]
        else:
            cutout = cutouts[piece][rng.integers(len(cutouts[piece]))]
        # The foot at its real size; the spread covers the camera distance and measuring the
        # foot in the cut-out.
        width = SQUARE * FOOT_MM[piece] / SQUARE_MM * rng.uniform(0.9, 1.12)
        if "white" in cutouts:
            # A window of a photo is 2.6 squares across by the mean of how wide and how deep a square
            # looks there; seen from the side a square is less deep, so the window shows less and the
            # piece is larger in it.
            width *= 2.0 / (1.0 + np.sin(np.radians(seen_from)))
        # Upright as in the windows of a photo; a piece seen from above has no "up".
        angle = rng.uniform(0, 360) if cutout.elongation < 1.25 else rng.normal(0, 5 if "white" in cutouts else 9)
        rgb, alpha = cutout.render(width, angle, white, rng)
        if rng.random() < 0.5:
            # A mirrored piece is a valid view of it (the knight then looks the other way); the
            # foot is in the middle of the window, so it stays where it is.
            rgb, alpha = np.ascontiguousarray(rgb[:, ::-1]), np.ascontiguousarray(alpha[:, ::-1])
        return rgb, alpha

    layers = []
    # Other pieces on the squares around; they may reach across the piece in question. All
    # pieces in a photo lean the same way, so the neighbours stand upright too.
    for _ in range(rng.integers(0, 6)):
        angle, distance = rng.uniform(0, 2 * np.pi), SQUARE * rng.uniform(0.85, 1.7)
        piece = PIECES[rng.integers(len(PIECES))]
        rgb, alpha = random_piece(piece, white_mode != "none" and rng.random() < 0.5)
        layers.append((rgb, alpha, distance * np.cos(angle), distance * np.sin(angle)))
    if cls != 0:
        white = cls <= 6
        piece = PIECES[(cls - 1) % 6]
        rgb, alpha = random_piece(piece, white)
        jitter = rng.uniform(-0.2, 0.2, 2) * SQUARE
        layers.append((rgb, alpha, jitter[0], jitter[1]))
    # Pieces further down in the window are nearer to the camera and cover those behind.
    layers.sort(key=lambda layer: layer[3])
    for rgb, alpha, dx, dy in layers:
        paste(canvas, rgb, alpha, dx, dy, rng.uniform(0.0, 0.45), rng)

    canvas = canvas * rng.uniform(0.75, 1.2) + rng.uniform(-15, 15)
    canvas = canvas * np.array([rng.uniform(0.92, 1.08), 1.0, rng.uniform(0.92, 1.08)])
    if rng.random() < 0.75:
        # Up to clearly out of focus: photos taken in passing are.
        canvas = cv2.GaussianBlur(canvas, (0, 0), rng.uniform(0.3, MAX_BLUR) * PX)
    canvas += rng.normal(0, rng.uniform(0, 6), canvas.shape)
    return np.clip(canvas, 0, 255).astype(np.uint8)


def main(argv=None) -> int:
    global WINDOW_SIZE, SQUARE, PX, MAX_BLUR
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument("--cutouts", type=Path, help="output of extract_piece_cutouts.py")
    ap.add_argument("--photo-cutouts", type=Path, help="cut-outs of cut_out_piece_photos.py, both colours, instead of --cutouts")
    ap.add_argument("--pieces", type=Path, help="the folder of the set of pieces, with set.json (with --photo-cutouts)")
    ap.add_argument("--max-blur", type=float, default=MAX_BLUR, help="strongest blur, in pixels of a 128-pixel window (default 3.5)")
    ap.add_argument("--boards", type=Path, help="folder with photos of the empty board and labels.jsonl")
    ap.add_argument("--empty-boards", default="", help="comma-separated photo names in --boards that show the empty board")
    ap.add_argument("--out", type=Path, required=True)
    ap.add_argument("--count", type=int, default=1500, help="training images per class")
    ap.add_argument("--val-count", type=int, default=250, help="validation images per class")
    ap.add_argument("--white", choices=("invert", "none"), default="invert")
    ap.add_argument("--seed", type=int, default=0)
    ap.add_argument("--size", type=int, default=WINDOW_SIZE, help="window size in pixels")
    args = ap.parse_args(argv)

    WINDOW_SIZE = args.size
    SQUARE = WINDOW_SIZE / WINDOW_EXTENT
    PX = WINDOW_SIZE / 128.0
    MAX_BLUR = args.max_blur
    if (args.cutouts is None) == (args.photo_cutouts is None):
        raise SystemExit("give either --cutouts or --photo-cutouts")

    rng = np.random.default_rng(args.seed)
    # Split every video into stretches of 10 cut-outs; every fifth stretch is validation.
    cutouts = {"train": {}, "val": {}}
    if args.photo_cutouts:
        from compose_boards import load_cutouts

        foot_mm = json.loads((args.pieces / "set.json").read_text(encoding="utf-8"))["foot_mm"]
        both = {"white": {}, "black": {}}
        for recs in load_cutouts(args.photo_cutouts, foot_mm).values():
            both[recs[0]["colour"]][recs[0]["piece"]] = [PhotoCutout(r) for r in recs]
        # 44 photos of each piece: too few to keep some back, the same for both parts
        cutouts = {"train": both, "val": both}
    for piece in [] if args.photo_cutouts else PIECES:
        files = sorted((args.cutouts / piece).glob("*.png"))
        if not files:
            raise SystemExit(f"no cut-outs for {piece} in {args.cutouts}")
        cutouts["train"][piece] = [Cutout(f) for i, f in enumerate(files) if (i // 10) % 5 != 4]
        cutouts["val"][piece] = [Cutout(f) for i, f in enumerate(files) if (i // 10) % 5 == 4]
    boards = EmptyBoards(args.boards, [n for n in args.empty_boards.split(",") if n])
    classes = range(len(CLASSES)) if args.white == "invert" else [0] + list(range(7, 13))

    for subset, count in (("train", args.count), ("val", args.val_count)):
        for cls in classes:
            folder = args.out / f"synth_{subset}" / CLASSES[cls]
            folder.mkdir(parents=True, exist_ok=True)
            n = count * 2 if cls == 0 else count
            for i in range(n):
                window = make_window(cls, cutouts[subset], boards, rng, args.white)
                cv2.imwrite(str(folder / f"{i:05d}.png"), cv2.cvtColor(window, cv2.COLOR_RGB2BGR))
        print(f"{subset}: {count} images per class in {args.out / ('synth_' + subset)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
