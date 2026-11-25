# Copyright (C) 2026 Christian Kierdorf
# SPDX-License-Identifier: GPL-3.0-or-later
"""Quadrilaterals: corner order, checks, and the corner error against labels.

Corner order everywhere: TL, TR, BR, BL - geometric, as seen in the photo
(sorted by angle around the centroid, starting at min(x+y)). It says nothing
about where a1 is; that follows later from the occupancy the board reports.
"""

from __future__ import annotations

import math

import cv2
import numpy as np

IMAGE_SUFFIXES = {".jpg", ".jpeg", ".png", ".webp", ".bmp"}
CORNER_NAMES = ("TL", "TR", "BR", "BL")
RED, GREEN = (40, 40, 230), (60, 200, 60)  # BGR


def canonicalize_quad(pts) -> np.ndarray:
    """Four points as TL, TR, BR, BL: by angle around the centroid, starting at min(x+y)."""
    pts = np.asarray(pts, dtype=np.float64).reshape(4, 2)
    cx, cy = pts.mean(axis=0)
    order = sorted(range(4), key=lambda i: (math.atan2(pts[i][1] - cy, pts[i][0] - cx), i))
    start = int(np.argmin([pts[i][0] + pts[i][1] for i in order]))
    return np.array([pts[order[(start + k) % 4]] for k in range(4)])


def area_abs(quad) -> float:
    s = 0.0
    for i in range(4):
        j = (i + 1) % 4
        s += quad[i][0] * quad[j][1] - quad[j][0] * quad[i][1]
    return abs(0.5 * s)


def is_convex(quad, eps: float = 1e-9) -> bool:
    sign = 0
    for i in range(4):
        a, b, c = quad[i], quad[(i + 1) % 4], quad[(i + 2) % 4]
        cross = (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0])
        if abs(cross) <= eps:
            return False
        s = 1 if cross > 0 else -1
        if sign == 0:
            sign = s
        elif s != sign:
            return False
    return True


def corner_error(pred: np.ndarray, truth: np.ndarray) -> dict:
    """Corner error in pixels and relative to the playing area.

    `square` is one board square: the mean edge length of the labelled quad / 8. A quarter of a
    square is roughly where piece crops start to pick up the neighbouring square.

    "Top-left" is geometric, so for a board turned by about 45 degrees the prediction can be a
    cyclic shift of the label. `shift` is the shift with the smallest error.
    """
    edges = [np.linalg.norm(truth[(i + 1) % 4] - truth[i]) for i in range(4)]
    square = float(np.mean(edges)) / 8.0
    per_shift = [np.linalg.norm(np.roll(pred, -s, axis=0) - truth, axis=1) for s in range(4)]
    shift = int(np.argmin([d.mean() for d in per_shift]))
    best = per_shift[shift]
    return {
        "shift": shift,
        "mean_px": float(best.mean()),
        "max_px": float(best.max()),
        "square_px": square,
        "mean_squares": float(best.mean() / square),
        "max_squares": float(best.max() / square),
    }


def draw_quad(img, quad, color, thickness: int) -> None:
    pts = np.round(np.asarray(quad)).astype(np.int32)
    cv2.polylines(img, [pts.reshape(-1, 1, 2)], True, color, thickness, cv2.LINE_AA)
    for x, y in pts:
        cv2.circle(img, (int(x), int(y)), thickness * 3, color, -1, cv2.LINE_AA)
