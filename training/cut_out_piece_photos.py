#!/usr/bin/env python3
# Copyright (C) 2026 Christian Kierdorf
# SPDX-License-Identifier: GPL-3.0-or-later
"""Cuts single chess pieces out of the photos of the collector app.

The collector (collector/) photographs one piece alone on a plain coloured
surface - a green desk mat does for light and dark pieces alike - and notes
next to each photo which piece it is and how the phone was tilted. Here the
piece is separated from the surface by colour: the surface is whatever colour
fills most of the picture, the piece is what is clearly less coloured (white,
grey or black plastic), and GrabCut settles the pixels in between, where the
plastic mirrors the surface.

The tilt of the phone is the angle the piece is seen from only if its foot is
in the middle of the picture; the angle written here is corrected for where
the foot really is.

Input: <photos>/<piece>/<name>.jpg with <name>.json, the piece folder being
wK, wQ, wR, wB, wN, wP or bK ... bP, as the collector writes them.

Output: <out>/<white|black>/<piece>/<name>.png - RGBA cut-outs cropped to the
piece, the alpha channel as its mask - and <out>/cutouts.jsonl with one line
per cut-out: file, colour, piece, the angle it is seen from (degrees above
the table), the tilt of the phone and the sharpness. Photos that are blurred,
or in which no piece is found clear of the edges, are skipped and counted.

Usage:
  python cut_out_piece_photos.py --photos samples/collector/collection/pieces \
      --out data/photo_cutouts --sheet out/photo_cutouts.jpg
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path

import cv2
import numpy as np

from extract_piece_cutouts import cutout, sharpness
from piece_windows import DEFAULT_FOCAL
from squares import load_image

PIECE_NAMES = {"K": "king", "Q": "queen", "R": "rook", "B": "bishop", "N": "knight", "P": "pawn"}
WORK_SIZE = 1600  # photos are shrunk to this (longest side) before segmenting


# Share of the ring around the upper half of a piece that may look like something else than the
# surface: another colour, or hardly any. Measured on 41 photos of the white king of 16.11.2026,
# of which 3 lost their head (0.05, 0.24, 0.24; the other 38: 0.00), and on 161 good photos of all
# white pieces of 18.11.2026 (at most 0.012). Asking instead how much of the ring looks just like
# the surface threw out 98 of those 161: in another light the mat itself varies too much.
MAX_OTHER_AROUND_HEAD = 0.03

# Darker than this share of the surface's brightness is dark: the body of a black piece, and the
# deep shadow at a foot. Black plastic that mirrors the green mat had a brightness of 40 to 70 of
# 255 in the middle on a mat of about 180; the mat in half shadow is lighter.
DARK = 0.42

# Hues within this many steps (of OpenCV's 180) of the surface's count as its cast on a piece, and
# this share of their colour is left.
CAST_REACH = 30
CAST_LEFT = 0.25


def segment(rgb: np.ndarray) -> np.ndarray | None:
    """Mask (uint8 0/255) of the piece nearest the middle of the picture, or None."""
    height, width = rgb.shape[:2]
    hsv = cv2.cvtColor(cv2.GaussianBlur(rgb, (5, 5), 0), cv2.COLOR_RGB2HSV).astype(np.int32)
    hue, sat, value = hsv[..., 0], hsv[..., 1], hsv[..., 2]
    surface_value = float(np.median(value))
    surface_hue = int(np.median(hue))
    surface_sat = float(np.median(sat))
    if surface_sat < 40:
        return None  # the surface has no colour to tell the piece from
    off_hue = np.minimum(np.abs(hue - surface_hue), 180 - np.abs(hue - surface_hue)) > 22
    # Clearly not the surface: hardly any colour, or another one.
    sure = ((sat < 0.42 * surface_sat) | (off_hue & (sat < 0.8 * surface_sat))).astype(np.uint8)
    sure = cv2.morphologyEx(sure, cv2.MORPH_OPEN, cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (7, 7)))
    # A dark piece mirrors the surface over much of its body: the surface's colour, only much
    # darker. That is not sure enough to be called piece (the deep shadow looks the same), but the
    # box around the piece has to reach around it.
    dark = (value < DARK * surface_value).astype(np.uint8)
    dark = cv2.morphologyEx(dark, cv2.MORPH_OPEN, cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (7, 7)))
    # ... but only of a dark piece: around a light one the dark parts are its shadow.
    for parts in (sure, sure | dark):
        n, labels, stats, centres = cv2.connectedComponentsWithStats(parts)
        best, best_distance = None, None
        for k in range(1, n):
            x, y, w, h, area = stats[k]
            if area < 0.0015 * width * height:
                continue
            if x <= 2 or y <= 2 or x + w >= width - 2 or y + h >= height - 2:
                continue  # other things at the edge of the picture
            distance = float(np.hypot(centres[k][0] - width / 2, centres[k][1] - height / 2))
            if best is None or distance < best_distance:
                best, best_distance = k, distance
        if best is not None and float(np.median(value[labels == best])) > surface_value:
            break  # a light piece
    if best is None:
        return None
    chosen = labels == best
    # A light piece mirrors the surface on its shaded side, so what is clearly not surface is often
    # only parts of it: the box around the piece is that of all such parts near the chosen one.
    x0, y0, x1, y1 = stats[best][0], stats[best][1], stats[best][0] + stats[best][2], stats[best][1] + stats[best][3]
    grown = True
    while grown:
        grown = False
        reach = 0.5 * max(x1 - x0, y1 - y0)
        for k in range(1, n):
            x, y, w, h, area = stats[k]
            if area < 0.0003 * width * height or (x >= x0 and y >= y0 and x + w <= x1 and y + h <= y1):
                continue
            if x <= 2 or y <= 2 or x + w >= width - 2 or y + h >= height - 2:
                continue
            if x > x1 + reach or x + w < x0 - reach or y > y1 + reach or y + h < y0 - reach:
                continue
            x0, y0, x1, y1 = min(x0, x), min(y0, y), max(x1, x + w), max(y1, y + h)
            grown = True
    margin = int(0.06 * max(x1 - x0, y1 - y0)) + 6
    x0, y0 = max(0, x0 - margin), max(0, y0 - margin)
    x1, y1 = min(width, x1 + margin), min(height, y1 + margin)

    # Inside the box everything is piece unless it looks just like the surface; GrabCut then learns
    # both from the picture and draws the line along the edges.
    init = np.full((height, width), cv2.GC_BGD, np.uint8)
    box = init[y0:y1, x0:x1]
    box[:] = cv2.GC_PR_FGD
    near_hue = np.minimum(np.abs(hue - surface_hue), 180 - np.abs(hue - surface_hue)) < 12
    like_surface = near_hue & (sat > 0.85 * surface_sat)
    box[like_surface[y0:y1, x0:x1]] = cv2.GC_PR_BGD
    box[sure[y0:y1, x0:x1] > 0] = cv2.GC_FGD
    try:
        cv2.grabCut(rgb, init, None, np.zeros((1, 65)), np.zeros((1, 65)), 4, cv2.GC_INIT_WITH_MASK)
    except cv2.error:
        return None
    mask = np.isin(init, (cv2.GC_FGD, cv2.GC_PR_FGD)).astype(np.uint8)
    if float(np.median(value[chosen])) > surface_value:
        # A light piece: the dark shadow where it touches the surface is not part of it. (For a
        # dark piece the same test would bite into the plastic.)
        # Only the deep shadow: the shaded side of the plastic is greenish too, but lighter.
        shadow = near_hue & (sat > 0.75 * surface_sat) & (value < 0.55 * surface_value)
        mask[shadow] = 0
    else:
        # A dark piece: its shadow cannot be told from the body where both are as dark, but the
        # half shadow and the lit surface around the foot are lighter than any plastic that
        # mirrors the surface.
        mask[near_hue & (sat > 0.6 * surface_sat) & (value > DARK * surface_value)] = 0
    mask = cv2.morphologyEx(mask, cv2.MORPH_OPEN, cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (5, 5)))
    n, labels, stats, _ = cv2.connectedComponentsWithStats(mask)
    if n < 2:
        return None
    # The part that holds the chosen blob.
    inside = labels[chosen]
    inside = inside[inside > 0]
    if inside.size == 0:
        return None
    piece_label = int(np.bincount(inside).argmax())
    x, y, w, h, _ = stats[piece_label]
    if x <= 2 or y <= 2 or x + w >= width - 2 or y + h >= height - 2:
        return None
    piece = np.zeros_like(mask)
    contours, _ = cv2.findContours((labels == piece_label).astype(np.uint8), cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    cv2.drawContours(piece, contours, -1, 255, cv2.FILLED)
    # Thin leftovers of the shadow hang on to the foot; nothing of a piece is that thin.
    piece = cv2.morphologyEx(piece, cv2.MORPH_OPEN, cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (13, 13)))
    n, labels, stats, _ = cv2.connectedComponentsWithStats(piece)
    if n < 2:
        return None
    piece = np.where(labels == 1 + int(np.argmax(stats[1:, cv2.CC_STAT_AREA])), 255, 0).astype(np.uint8)
    # The upper half of the piece must stand in front of the surface. Where its head reaches past
    # the surface into other things, the line around it cannot be found and the head goes missing.
    wide = cv2.dilate(piece, cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (25, 25)))
    close = cv2.dilate(piece, cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (7, 7)))
    around = (wide > 0) & (close == 0)
    rows = np.where(piece.any(axis=1))[0]
    around[(rows[0] + rows[-1]) // 2 :] = False
    other = off_hue | (sat < 0.5 * surface_sat)
    if around.any() and float(other[around].mean()) > MAX_OTHER_AROUND_HEAD:
        return None
    return piece


def segment_with_model(rgb: np.ndarray, session, window: float = 0.0, foot: float = 0.66) -> np.ndarray | None:
    """Soft mask (uint8, 0..255) of the piece from a ready-made network that tells an object from
    what is behind it (rembg; BiRefNet does well), or None.

    The colour rules of `segment` do for light pieces on the green mat but not for black ones:
    black plastic mirrors the mat and is as dark as its own shadow, so they bit into the body and
    kept the shadow. The network knows what an object looks like and leaves the shadow out.

    `window` is for photos taken from afar with the small frame of the app: the piece is a
    twentieth of the photo's width there, and shown the whole photo the network takes whatever
    else is in it for the object (the table's edge, the photographer's feet). The network then
    sees only a part that wide (as a share of the photo's width) around the frame, whose line for
    the foot is at `foot` of the photo's height.
    """
    from PIL import Image
    from rembg import remove

    height, width = rgb.shape[:2]
    # The middle of the photo, where the frame of the app is: more of the piece per pixel for the
    # network, and the edge of the table stays out.
    x0, x1, y0, y1 = int(0.12 * width), int(0.88 * width), int(0.12 * height), int(0.88 * height)
    middle = (width / 2, height / 2)
    if window > 0:
        middle = (width / 2, foot * height - 0.075 * width)
        x0, x1 = max(0, int((0.5 - window / 2) * width)), min(width, int((0.5 + window / 2) * width))
        y0 = max(0, int(foot * height - 0.9 * window * width))
        y1 = min(height, int(foot * height + 0.6 * window * width))
    part = np.asarray(remove(Image.fromarray(rgb[y0:y1, x0:x1]), session=session, only_mask=True))
    alpha = np.zeros((height, width), np.uint8)
    alpha[y0:y1, x0:x1] = part
    n, labels, stats, centres = cv2.connectedComponentsWithStats((alpha > 127).astype(np.uint8))
    best, best_distance = None, None
    for k in range(1, n):
        if stats[k][4] < 0.0015 * (x1 - x0) * (y1 - y0):
            continue
        distance = float(np.hypot(centres[k][0] - middle[0], centres[k][1] - middle[1]))
        if best is None or distance < best_distance:
            best, best_distance = k, distance
    if best is None:
        return None
    x, y, w, h, _ = stats[best]
    if x <= x0 + 2 or y <= y0 + 2 or x + w >= x1 - 2 or y + h >= y1 - 2:
        return None  # the piece reaches out of the part looked at
    near = cv2.dilate((labels == best).astype(np.uint8), cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (15, 15)))
    alpha[near == 0] = 0
    return alpha


def cutout_soft(bgr: np.ndarray, alpha: np.ndarray) -> np.ndarray:
    """The piece with the network's soft edge as its alpha channel, cropped to the piece."""
    x, y, w, h = cv2.boundingRect((alpha > 127).astype(np.uint8))
    pad = 4
    x0, y0 = max(0, x - pad), max(0, y - pad)
    x1, y1 = min(bgr.shape[1], x + w + pad), min(bgr.shape[0], y + h + pad)
    return np.dstack([bgr[y0:y1, x0:x1], alpha[y0:y1, x0:x1]])


def without_cast(rgb: np.ndarray, mask: np.ndarray) -> np.ndarray:
    """The photo with the colour of the surface taken out of the piece.

    A piece mirrors the surface it stands on - a white one on its shaded side, a black one over
    most of its body - and would carry that colour on to every board. The plastic itself has
    hardly any colour, so what has the surface's hue loses most of its colour.
    """
    hsv = cv2.cvtColor(rgb, cv2.COLOR_RGB2HSV).astype(np.float32)
    hue = hsv[..., 0]
    surface_hue = float(np.median(hue))
    away = np.minimum(np.abs(hue - surface_hue), 180 - np.abs(hue - surface_hue))
    # Fully at the surface's hue, fading out towards other hues.
    cast = np.clip((CAST_REACH - away) / (CAST_REACH / 2), 0, 1) * (mask > 0)
    hsv[..., 1] *= 1 - (1 - CAST_LEFT) * cast
    return cv2.cvtColor(hsv.astype(np.uint8), cv2.COLOR_HSV2RGB)


def sharpness_at_use(rgb: np.ndarray, mask: np.ndarray) -> float:
    """Edge strength along the outline with the piece shrunk to about the size it has in a window.

    A photo taken from close by can be soft at full size and still be perfectly good once the piece
    is as small as in a photo of the whole board; that is what counts.
    """
    x, y, w, h = cv2.boundingRect(mask)
    scale = min(1.0, 160.0 / h)
    pad = 12
    y0, y1, x0, x1 = max(0, y - pad), y + h + pad, max(0, x - pad), x + w + pad
    gray = cv2.cvtColor(rgb[y0:y1, x0:x1], cv2.COLOR_RGB2GRAY)
    gray = cv2.resize(gray, None, fx=scale, fy=scale, interpolation=cv2.INTER_AREA)
    small = cv2.resize(mask[y0:y1, x0:x1], (gray.shape[1], gray.shape[0]), interpolation=cv2.INTER_NEAREST)
    return sharpness(gray, small)


def seen_from(mask: np.ndarray, tilt: float) -> float:
    """Degrees above the table from which the piece is seen, given the tilt of the phone.

    The camera looks down by `tilt` along the middle of the picture; a foot above or below the
    middle is seen along a ray that much flatter or steeper. The foot is the middle of the round
    base, which appears as an ellipse at the bottom of the mask - how flat, depends on the angle
    again, hence the two rounds.
    """
    height, width = mask.shape
    focal = DEFAULT_FOCAL * float(np.hypot(width, height))
    x, y, w, h = cv2.boundingRect(mask)
    base_width = float(np.count_nonzero(mask[y + int(0.9 * h) : y + h].max(axis=0)))
    angle = tilt
    for _ in range(2):
        foot_y = y + h - 0.5 * base_width * np.sin(np.radians(angle))
        angle = tilt - float(np.degrees(np.arctan((height / 2 - foot_y) / focal)))
    return angle


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument("--photos", type=Path, required=True, help="the folder of one set of pieces of the collector (pieces/<set>)")
    ap.add_argument("--out", type=Path, required=True)
    ap.add_argument("--min-sharpness", type=float, default=25.0)
    ap.add_argument("--sheet", type=Path, help="write an overview of the cut-outs here")
    ap.add_argument(
        "--model",
        help="cut out with this network of rembg (needs `pip install rembg onnxruntime`; "
        "birefnet-general-lite does well) instead of the colour rules",
    )
    ap.add_argument(
        "--window",
        type=float,
        default=0.0,
        help="with --model: show the network only a part this wide around the frame of the app, "
        "as a share of the photo's width (0.5 for pieces photographed from afar with the small "
        "frame; 0, the default, for the middle of the whole photo)",
    )
    ap.add_argument(
        "--work-size",
        type=int,
        default=WORK_SIZE,
        help="photos are shrunk to this (longest side) before cutting out; larger for small pieces",
    )
    args = ap.parse_args(argv)
    session = None
    if args.model:
        from rembg import new_session

        session = new_session(args.model)

    args.out.mkdir(parents=True, exist_ok=True)
    records, sheet_rows = [], []
    for folder in sorted(p for p in args.photos.iterdir() if p.is_dir()):
        name = folder.name
        if len(name) != 2 or name[0] not in "wb" or name[1] not in PIECE_NAMES:
            raise SystemExit(f"{folder}: expected a folder like wK or bN")
        colour, piece = ("white" if name[0] == "w" else "black"), PIECE_NAMES[name[1]]
        target = args.out / colour / piece
        target.mkdir(parents=True, exist_ok=True)
        kept, blurred, not_found, tiles = 0, 0, 0, []
        for path in sorted(folder.glob("*.jpg")):
            note = json.loads(path.with_suffix(".json").read_text(encoding="utf-8"))
            rgb = np.asarray(load_image(path))
            scale = min(1.0, args.work_size / max(rgb.shape[:2]))
            rgb = cv2.resize(rgb, None, fx=scale, fy=scale, interpolation=cv2.INTER_AREA)
            foot = float(note.get("line_for_the_foot_at", 0.66))
            soft = segment_with_model(rgb, session, args.window, foot) if session else None
            mask = segment(rgb) if session is None else None if soft is None else np.where(soft > 127, 255, 0).astype(np.uint8)
            if mask is None:
                not_found += 1
                continue
            sharp = sharpness_at_use(rgb, mask)
            if sharp < args.min_sharpness:
                blurred += 1
                continue
            bgr = cv2.cvtColor(without_cast(rgb, mask), cv2.COLOR_RGB2BGR)
            rgba = cutout(bgr, mask) if soft is None else cutout_soft(bgr, soft)
            cv2.imwrite(str(target / f"{path.stem}.png"), rgba)
            tilt = float(note["tilt_of_phone"])
            angle = seen_from(mask, tilt)
            records.append(
                {
                    "file": f"{colour}/{piece}/{path.stem}.png",
                    "colour": colour,
                    "piece": piece,
                    "seen_from": round(angle, 1),
                    "tilt_of_phone": round(tilt, 1),
                    "sharpness": round(sharp, 1),
                }
            )
            kept += 1
            if len(tiles) < 16:
                tile = np.full((200, 160, 3), (150, 60, 150), np.uint8)
                s = min(190 / rgba.shape[0], 150 / rgba.shape[1])
                small = cv2.resize(rgba, None, fx=s, fy=s, interpolation=cv2.INTER_AREA)
                a = small[..., 3:4] / 255.0
                region = tile[: small.shape[0], : small.shape[1]]
                region[:] = (small[..., :3] * a + region * (1 - a)).astype(np.uint8)
                cv2.putText(tile, f"{angle:.0f}", (4, 196), cv2.FONT_HERSHEY_SIMPLEX, 0.5, (255, 255, 255), 1)
                tiles.append(tile)
        print(f"{name}: {colour} {piece:7s} {kept} cut-outs, {blurred} blurred, {not_found} without a piece or with the head not in front of the surface")
        if tiles:
            tiles += [np.zeros_like(tiles[0])] * (16 - len(tiles))
            sheet_rows.append(np.hstack(tiles))
    with open(args.out / "cutouts.jsonl", "w", encoding="utf-8") as f:
        for record in records:
            f.write(json.dumps(record) + "\n")
    if args.sheet and sheet_rows:
        args.sheet.parent.mkdir(parents=True, exist_ok=True)
        cv2.imwrite(str(args.sheet), np.vstack(sheet_rows))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
