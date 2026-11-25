#!/usr/bin/env python3
# Copyright (C) 2026 Christian Kierdorf
# SPDX-License-Identifier: GPL-3.0-or-later
"""Cuts single chess pieces out of videos, as reference views for the piece classifier.

The Pegasus comes with its own pieces, so every piece looks the same on every
board. Instead of labelling board photos, each piece is filmed once, alone,
from all around and from straight above down to the side. The video itself
is the label.

Input: one video per piece type showing a dark piece on a plain light
surface, and a CSV that says which video shows what:

  video,piece
  king.mp4,king
  queen.mp4,queen

Output: <out>/<piece>/<video>_<frame>.png - RGBA cut-outs, cropped to the
piece, with the alpha channel as its mask. Frames that are blurred, or in
which the piece touches the edge of the picture, are skipped.

Usage:
  python extract_piece_cutouts.py --videos samples/pieces --map samples/pieces/videos.csv \
      --out data/cutouts --sheet out/cutouts.jpg
"""

from __future__ import annotations

import argparse
import csv
from pathlib import Path

import cv2
import numpy as np

PIECES = ("pawn", "knight", "bishop", "rook", "queen", "king")
WORK_SIZE = 960  # frames are shrunk to this (longest side) before segmenting


def segment(frame: np.ndarray) -> np.ndarray | None:
    """Mask (uint8 0/255) of the dark piece on the light surface, or None if there is no clean one.

    A brightness threshold alone bites pieces off where the light hits the plastic, and takes
    part of the shadow if it is loosened. So the threshold only seeds GrabCut: clearly dark
    pixels are piece, fairly dark ones probably, and the colour model and the edges decide the
    rest inside a box around them.
    """
    gray = cv2.GaussianBlur(cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY), (5, 5), 0)
    background = float(np.median(gray))
    height, width = gray.shape
    sure = (gray < 0.42 * background).astype(np.uint8)
    n, labels, stats, _ = cv2.connectedComponentsWithStats(sure)
    if n < 2:
        return None
    biggest = 1 + int(np.argmax(stats[1:, cv2.CC_STAT_AREA]))
    x, y, w, h, area = stats[biggest]
    if area < 0.002 * width * height or area > 0.35 * width * height:
        return None
    margin = int(0.2 * max(w, h)) + 8
    x0, y0 = max(0, x - margin), max(0, y - margin)
    x1, y1 = min(width, x + w + margin), min(height, y + h + margin)

    init = np.full(gray.shape, cv2.GC_BGD, np.uint8)
    box = init[y0:y1, x0:x1]
    box[:] = cv2.GC_PR_BGD
    box[gray[y0:y1, x0:x1] < 0.62 * background] = cv2.GC_PR_FGD
    box[labels[y0:y1, x0:x1] == biggest] = cv2.GC_FGD
    try:
        cv2.grabCut(frame, init, None, np.zeros((1, 65)), np.zeros((1, 65)), 4, cv2.GC_INIT_WITH_MASK)
    except cv2.error:
        return None
    mask = np.isin(init, (cv2.GC_FGD, cv2.GC_PR_FGD)).astype(np.uint8)
    mask = cv2.morphologyEx(mask, cv2.MORPH_OPEN, cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (5, 5)))
    n, labels, stats, _ = cv2.connectedComponentsWithStats(mask)
    if n < 2:
        return None
    biggest = 1 + int(np.argmax(stats[1:, cv2.CC_STAT_AREA]))
    x, y, w, h, area = stats[biggest]
    if x <= 2 or y <= 2 or x + w >= width - 2 or y + h >= height - 2:
        return None  # cut off by the edge of the picture
    piece = np.zeros_like(mask)
    contours, _ = cv2.findContours((labels == biggest).astype(np.uint8), cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    cv2.drawContours(piece, contours, -1, 255, cv2.FILLED)  # highlights on the plastic leave holes
    if (gray[piece > 0] > 0.85 * background).mean() > 0.04:
        return None  # a patch of the surface or the shadow came along
    return piece


def sharpness(gray: np.ndarray, mask: np.ndarray) -> float:
    """Edge strength along the outline of the piece; motion blur makes it small."""
    edge = cv2.morphologyEx(mask, cv2.MORPH_GRADIENT, np.ones((7, 7), np.uint8)) > 0
    gx = cv2.Sobel(gray, cv2.CV_32F, 1, 0)
    gy = cv2.Sobel(gray, cv2.CV_32F, 0, 1)
    return float(np.sqrt(gx * gx + gy * gy)[edge].mean())


def cutout(frame: np.ndarray, mask: np.ndarray) -> np.ndarray:
    alpha = cv2.GaussianBlur(mask, (5, 5), 0)
    x, y, w, h = cv2.boundingRect(mask)
    pad = 4
    x0, y0 = max(0, x - pad), max(0, y - pad)
    x1, y1 = min(frame.shape[1], x + w + pad), min(frame.shape[0], y + h + pad)
    return np.dstack([frame[y0:y1, x0:x1], alpha[y0:y1, x0:x1]])


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument("--videos", type=Path, required=True, help="folder with the videos")
    ap.add_argument("--map", type=Path, required=True, help="CSV with the columns video,piece")
    ap.add_argument("--out", type=Path, required=True)
    ap.add_argument("--step", type=int, default=5, help="use every n-th frame")
    ap.add_argument("--min-sharpness", type=float, default=12.0)
    ap.add_argument("--sheet", type=Path, help="write an overview of the cut-outs here")
    args = ap.parse_args(argv)

    with open(args.map, newline="", encoding="utf-8") as f:
        rows = list(csv.DictReader(f))
    sheet_rows = []
    for row in rows:
        piece = row["piece"].strip()
        if piece not in PIECES:
            raise SystemExit(f"{args.map}: piece must be one of {PIECES}, got {piece!r}")
        path = args.videos / row["video"].strip()
        capture = cv2.VideoCapture(str(path))
        if not capture.isOpened():
            raise SystemExit(f"cannot open {path}")
        folder = args.out / piece
        folder.mkdir(parents=True, exist_ok=True)
        kept, skipped, index, tiles = 0, 0, -1, []
        while True:
            ok, frame = capture.read()
            if not ok:
                break
            index += 1
            if index % args.step:
                continue
            scale = WORK_SIZE / max(frame.shape[:2])
            frame = cv2.resize(frame, None, fx=scale, fy=scale, interpolation=cv2.INTER_AREA)
            mask = segment(frame)
            if mask is None or sharpness(cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY), mask) < args.min_sharpness:
                skipped += 1
                continue
            rgba = cutout(frame, mask)
            cv2.imwrite(str(folder / f"{path.stem}_{index:04d}.png"), rgba)
            kept += 1
            if kept % 6 == 1 and len(tiles) < 12:
                tile = np.full((160, 160, 3), (60, 140, 60), np.uint8)
                s = 150 / max(rgba.shape[:2])
                small = cv2.resize(rgba, None, fx=s, fy=s, interpolation=cv2.INTER_AREA)
                a = small[..., 3:4] / 255.0
                region = tile[: small.shape[0], : small.shape[1]]
                region[:] = (small[..., :3] * a + region * (1 - a)).astype(np.uint8)
                tiles.append(tile)
        print(f"{path.name}: {piece:7s} {kept} cut-outs, {skipped} frames skipped")
        if tiles:
            tiles += [np.zeros_like(tiles[0])] * (12 - len(tiles))
            sheet_rows.append(np.hstack(tiles))
    if args.sheet and sheet_rows:
        args.sheet.parent.mkdir(parents=True, exist_ok=True)
        cv2.imwrite(str(args.sheet), np.vstack(sheet_rows))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
