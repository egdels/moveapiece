#!/usr/bin/env python3
# Copyright (C) 2026 Christian Kierdorf
# SPDX-License-Identifier: GPL-3.0-or-later
"""Gives cut-out pieces the light and dark of another set of cut-outs of the same pieces.

Pieces photographed from afar are a small part of the photo, and the camera sets its exposure by
the mat and the table around them. The white pieces come out too light: their shaded sides are
nearly as light as their lit ones, and the shape shows less than in photos taken from near, where
the piece fills the picture. What is left of the shading is still in the photo, only pressed into
a narrow range. This spreads it out again: per kind of piece, the brightness of the new cut-outs
is mapped so that it is spread like that of the reference cut-outs (as many pixels as dark, as
many as light). The outline stays as they are, and so does the grain: only the brightness
smoothed over a hundredth of the piece's height is mapped.
A colour cast of the light goes with it: each colour channel is scaled so that the pieces of a
kind have on the whole the colour of the reference.

Measured on the white king and bishop, views up to 65 degrees, brightness 0..255:
from 12 cm the pixels spread 122..232 (5th to 95th in a hundred), from 45 cm 193..255; the black pieces are alike in both (28..141 and 31..127) and are left alone by default.

Input: <cutouts>/<white|black>/<piece>/*.png and cutouts.jsonl, as cut_out_piece_photos.py
writes them, and the same of the reference. Output: the same under <out>.

With --white-from-black the white pieces are not taken from the white cut-outs at all but made
from the black ones, which the camera exposed well: white and black pieces of a set have the same
shape. Their brightness is mapped to that of the white reference as above (dark stays dark, light
stays light, so the light still comes from where it came), and their colour is replaced by that of
the white reference, as the black pieces mirror the blue of the sky and lifting them shows it.

Usage:
  python match_cutout_tone.py --cutouts data/photo_cutouts_45cm --like data/photo_cutouts \
      --out data/photo_cutouts_45cm_toned
"""

from __future__ import annotations

import argparse
import json
import re
import shutil
from pathlib import Path

import cv2
import numpy as np


def brightness(rgba: np.ndarray) -> np.ndarray:
    return cv2.cvtColor(rgba[..., :3], cv2.COLOR_BGR2GRAY).astype(np.float32)


def of_the_photo(path: Path) -> np.ndarray:
    """Brightness of all pixels well inside the piece of one cut-out, the soft edge left out."""
    rgba = cv2.imread(str(path), cv2.IMREAD_UNCHANGED)
    inside = cv2.erode((rgba[..., 3] > 200).astype(np.uint8), np.ones((5, 5), np.uint8)) > 0
    return brightness(rgba)[inside]


def of_the_piece(folder: Path) -> np.ndarray:
    """The same of all cut-outs of a folder."""
    values = [of_the_photo(path) for path in sorted(folder.glob("*.png"))]
    return np.concatenate(values) if values else np.zeros(0, np.float32)


def colour_of_the_piece(folder: Path, only: str = "*.png") -> np.ndarray:
    """Mean colour (B, G, R) of the pieces of a folder (or of the files named) for a brightness of one."""
    colour, light = np.zeros(3), 0.0
    for path in sorted(folder.glob(only)):
        rgba = cv2.imread(str(path), cv2.IMREAD_UNCHANGED)
        inside = cv2.erode((rgba[..., 3] > 200).astype(np.uint8), np.ones((5, 5), np.uint8)) > 0
        colour += rgba[..., :3][inside].sum(axis=0)
        light += brightness(rgba)[inside].sum()
    return colour / max(light, 1.0)


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument("--cutouts", type=Path, required=True, help="the cut-outs to change")
    ap.add_argument("--like", type=Path, required=True, help="the cut-outs whose light and dark they get")
    ap.add_argument("--out", type=Path, required=True)
    ap.add_argument("--colours", default="white", help="comma separated: white, black (default: white)")
    ap.add_argument(
        "--each-photo",
        action="store_true",
        help="match every photo by itself to the reference photos from the same height, not all of a piece together: "
        "for photos whose exposure and colour of light change from one to the next",
    )
    ap.add_argument("--white-from-black", action="store_true", help="make the white pieces from the black cut-outs")
    args = ap.parse_args(argv)

    matched = set(args.colours.split(","))
    args.out.mkdir(parents=True, exist_ok=True)
    notes = [json.loads(line) for line in (args.cutouts / "cutouts.jsonl").read_text(encoding="utf-8").splitlines() if line.strip()]
    todo = [(p, p.parent.name) for p in sorted(args.cutouts.glob("*/*")) if p.is_dir()]
    if args.white_from_black:
        todo = [(p, c) for p, c in todo if c == "black"]
        todo += [(p, "white") for p, _ in todo]
        notes = [n for n in notes if n["colour"] == "black"]
        notes += [{**n, "colour": "white", "file": "white" + n["file"][len("black"):]} for n in notes]
        matched = {"white"}
    (args.out / "cutouts.jsonl").write_text("".join(json.dumps(n) + "\n" for n in notes), encoding="utf-8")
    steps = np.linspace(0, 100, 101)
    for folder, colour in todo:
        piece = folder.name
        recoloured = colour != folder.parent.name
        target = args.out / colour / piece
        target.mkdir(parents=True, exist_ok=True)
        new, old = of_the_piece(folder), of_the_piece(args.like / colour / piece)
        if recoloured and not len(old):
            raise SystemExit(f"{args.like / colour / piece}: no reference cut-outs to make the white {piece} like")
        tint = colour_of_the_piece(args.like / colour / piece) if recoloured else None
        # The colour of the light, too: a white piece on a dark table under the sky comes out blue
        # (red 124, green 154, blue 185), the reference is nearly grey. Each colour
        # channel is scaled so that the pieces have on the whole the colour of the reference.
        cast = None
        if not recoloured and colour in matched and len(new) and len(old):
            cast = colour_of_the_piece(args.like / colour / piece) / np.maximum(colour_of_the_piece(folder), 1e-3)
        if colour not in matched or not len(new) or not len(old):
            for path in folder.glob("*.png"):
                shutil.copy(path, target / path.name)
            print(f"{colour} {piece}: copied as it is")
            continue
        source, wanted = np.percentile(new, steps), np.percentile(old, steps)
        # Rising all the way, or np.interp has nothing to go by where many pixels are plain white.
        source = np.maximum.accumulate(source + np.arange(len(source)) * 1e-3)
        for path in sorted(folder.glob("*.png")):
            rgba = cv2.imread(str(path), cv2.IMREAD_UNCHANGED)
            light = brightness(rgba)
            if recoloured or args.each_photo:
                # Black plastic mirrors, white plastic is matt: spread like all white photos together,
                # one black piece would get in itself the light and dark that the white ones have
                # between their lit and their shaded sides, and look like silver. So each photo gets
                # the light and dark of the white photos from the same height (and side, if there is).
                view = re.search(r"_s(\d+)_(\d+)\.png$", path.name)
                same = sorted((args.like / colour / piece).glob(f"*_s{view.group(1)}_{view.group(2)}.png")) if view else []
                same = same or (sorted((args.like / colour / piece).glob(f"*_{view.group(2)}.png")) if view else [])
                if args.each_photo and not recoloured and view:
                    # With the camera choosing anew for every photo, the same white piece is here
                    # grey and there blue. All sides of the reference from this height together:
                    # its sides were not lit as these are.
                    same = sorted((args.like / colour / piece).glob(f"*_{view.group(2)}.png"))
                    if same:
                        cast = colour_of_the_piece(args.like / colour / piece, f"*_{view.group(2)}.png") / np.maximum(
                            colour_of_the_piece(folder, path.name), 1e-3
                        )
                if same:
                    wanted = np.mean([np.percentile(of_the_photo(q), steps) for q in same], axis=0)
                    source = np.percentile(of_the_photo(path), steps)
                    source = np.maximum.accumulate(source + np.arange(len(source)) * 1e-3)
            # Only the shading is spread out, not the grain: spread to twice its range, the noise
            # and the blocks of the JPEG in the lightest parts would show as mottling. The shading
            # is the brightness smoothed over a hundredth of the piece's height, within the piece.
            inside = (rgba[..., 3] > 127).astype(np.float32)
            sigma = max(1.0, 0.01 * rgba.shape[0])
            smooth = cv2.GaussianBlur(light * inside, (0, 0), sigma) / np.maximum(
                cv2.GaussianBlur(inside, (0, 0), sigma), 1e-3
            )
            mapped = np.interp(smooth, source, wanted) + (light - smooth)
            if recoloured:
                rgba[..., :3] = np.clip(np.clip(mapped, 0, 255)[..., None] * tint, 0, 255).astype(np.uint8)
            else:
                factor = np.clip(mapped, 0, 255) / np.maximum(light, 1.0)
                rgba[..., :3] = np.clip(rgba[..., :3] * factor[..., None] * cast, 0, 255).astype(np.uint8)
            cv2.imwrite(str(target / path.name), rgba)
        print(
            f"{colour} {piece}{' from black' if recoloured else ''}: 5th..95th in a hundred {source[5]:.0f}..{source[95]:.0f}"
            f" -> {wanted[5]:.0f}..{wanted[95]:.0f}"
        )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
