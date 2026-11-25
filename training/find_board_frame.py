#!/usr/bin/env python3
# Copyright (C) 2026 Christian Kierdorf
# SPDX-License-Identifier: GPL-3.0-or-later
"""Finds the playing area of a DGT Pegasus from its frame, without a model.

The Pegasus has a black frame, and between frame and playing area a thin
white line. Where that line meets the frame is the strongest straight edge
of the whole board, and it runs right along the playing area.

1. The inside of the frame is a light area enclosed by dark: threshold at
   several levels, take light regions that look like a quadrilateral and
   are surrounded by a dark band.
2. For each side of that rough quadrilateral, walk along it and look across
   it for the step from light to dark; find the straight lines with many
   such steps on them.
3. The board is a square. With the focal length of a phone camera, a
   quadrilateral in the photo is the picture of a square or it is not, and
   that is checked. In a photo taken at an angle the far edge is hidden
   behind the pieces standing in front of it; then the other three edges and
   "it is a square" give the fourth.
4. Among the remaining possibilities the 8x8 squares decide: they have to
   fit inside.
5. Move in by the fixed width of the white line (INSET): the playing area.

Corner order of the result: TL, TR, BR, BL, geometric, like everywhere here.

Usage:
  python find_board_frame.py --images samples --out out/frame        # with <images>/labels.jsonl: measures
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path

import cv2
import numpy as np

from piece_windows import DEFAULT_FOCAL
from quad import GREEN, IMAGE_SUFFIXES, RED, area_abs, canonicalize_quad, corner_error, draw_quad, is_convex
from squares import load_image

WORK_SIZE = 1000  # the photo is shrunk to this (longest side) for the search
# If nothing is found at that size, the search is repeated on smaller and smaller pictures. Where
# the sun glares on the far side of the frame, the frame is not dark there and the fine picture
# gives no closed outline; shrunk further, the glare and the pieces in front of the frame weigh
# less. At which size a board then shows differs from photo to photo (see
# BoardFrameFinder.java). The corners are less exact that way - a few pixels as a rule, a quarter
# of a square at worst - which is better than no board.
SMALLER_WORK_SIZES = (800, 640, 500)
# Distance from the inner edge of the frame to the playing area, as a fraction of the side of
# the inner frame edge (white line plus the gap behind it). Calibrated on labelled photos.
INSET = 0.009
# A quad counts as the board only if the squares fit into it at least this well (see _grid_score).
MIN_GRID_SCORE = 6.0
# ... and only if it can be the picture of a square (see squareness). Labelled playing areas
# are within 0.03.
MAX_OFF_SQUARE = 0.07


def _rough_candidates(gray: np.ndarray):
    """Light, quadrilateral regions, best first: (score, quad)."""
    h, w = gray.shape
    blurred = cv2.GaussianBlur(gray, (0, 0), 2.0)
    kernel = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (5, 5))
    out = []
    for level in range(70, 231, 20):
        light = cv2.morphologyEx((blurred > level).astype(np.uint8), cv2.MORPH_OPEN, kernel)
        contours, _ = cv2.findContours(light, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
        for contour in contours:
            area = cv2.contourArea(contour)
            if area < 0.04 * w * h:
                continue
            hull = cv2.convexHull(contour)
            quad = None
            perimeter = cv2.arcLength(hull, True)
            for eps in np.linspace(0.01, 0.08, 15):
                approx = cv2.approxPolyDP(hull, eps * perimeter, True)
                if len(approx) == 4:
                    quad = approx.reshape(4, 2).astype(np.float64)
                    break
            if quad is None or not is_convex(quad):
                continue
            quad_area = area_abs(quad)
            fill = area / max(quad_area, 1.0)  # pieces make holes at the rim; the hull covers them
            edges = [np.linalg.norm(quad[(i + 1) % 4] - quad[i]) for i in range(4)]
            if fill < 0.6 or min(edges) < 0.25 * max(edges):
                continue
            # The signature of the frame: just outside the quad it is much darker than just inside.
            contrast = _band_contrast(blurred, quad)
            if contrast < 25:
                continue
            out.append((contrast * min(1.0, fill / 0.85), quad))
    out.sort(key=lambda c: -c[0])
    return out


def _sample(gray: np.ndarray, pts: np.ndarray) -> np.ndarray:
    x = np.clip(np.round(pts[:, 0]).astype(int), 0, gray.shape[1] - 1)
    y = np.clip(np.round(pts[:, 1]).astype(int), 0, gray.shape[0] - 1)
    return gray[y, x].astype(np.float64)


def _band_contrast(gray: np.ndarray, quad: np.ndarray) -> float:
    centre = quad.mean(axis=0)
    inner, outer = [], []
    for i in range(4):
        a, b = quad[i], quad[(i + 1) % 4]
        normal = np.array([b[1] - a[1], a[0] - b[0]])
        normal /= np.linalg.norm(normal) + 1e-9
        if normal @ ((a + b) / 2 - centre) < 0:
            normal = -normal
        side = np.linalg.norm(b - a)
        ts = np.linspace(0.1, 0.9, 40)[:, None]
        pts = a + ts * (b - a)
        inner.append(_sample(gray, pts - normal * 0.02 * side))
        outer.append(_sample(gray, pts + normal * 0.02 * side))
    # Medians: pieces on the rim and whatever lies next to the board must not decide.
    return float(np.median(np.concatenate(inner)) - np.median(np.concatenate(outer)))


def _refine_side(gray: np.ndarray, a: np.ndarray, b: np.ndarray, centre: np.ndarray, reach_frac: float):
    """Up to three lines (point, direction, share of inliers) along the side a-b where the white
    line may meet the frame, best supported first.

    Across the side, every step from light to dark that is followed by a dark stretch as wide as
    the frame is a candidate. A row of pieces near the side produces such steps too, but not on
    one straight line; lines with many candidates on them are found by RANSAC. In a photo taken
    at an angle the far edge shows only between the pieces standing in front of it, so a line
    may have little support; which of the lines is the frame is decided by the squares (see
    _grid_score).
    """
    side = np.linalg.norm(b - a)
    direction = (b - a) / side
    normal = np.array([direction[1], -direction[0]])
    if normal @ ((a + b) / 2 - centre) < 0:
        normal = -normal
    reach = max(6, int(reach_frac * side))
    frame = max(3, int(0.018 * side))  # the frame is about 3 % of a side wide
    offsets = np.arange(-reach, reach + frame + 1)
    candidates, profiles = [], 0
    for t in np.linspace(0.04, 0.96, 110):
        base = a + t * (b - a)
        profile = _sample(gray, base + offsets[:, None] * normal)
        profile = np.convolve(profile, np.ones(3) / 3, mode="same")
        drop = profile[:-2] - profile[2:]  # positive where it gets darker going outwards
        profiles += 1
        order = np.argsort(-drop[1 : len(drop) - frame])[:6] + 1
        taken = []
        for k in order:
            if drop[k] < 28 or any(abs(k - j) < 3 for j in taken):
                continue
            inside = profile[max(0, k - 2) : k + 1].max()
            beyond = profile[k + 3 : k + 3 + frame]
            if beyond.mean() < 0.62 * inside and beyond.max() < 0.8 * inside:
                taken.append(k)
                candidates.append(base + offsets[k + 1] * normal)
            if len(taken) == 3:
                break
    if len(candidates) < 12:
        return []
    pts = np.array(candidates, dtype=np.float64)
    rng = np.random.default_rng(0)
    lines = []
    for _ in range(3):
        if len(pts) < 12:
            break
        best = None
        for _ in range(300):
            i, j = rng.choice(len(pts), 2, replace=False)
            d = pts[j] - pts[i]
            length = np.linalg.norm(d)
            if length < 0.2 * side:
                continue
            d /= length
            residual = np.abs((pts[:, 0] - pts[i, 0]) * d[1] - (pts[:, 1] - pts[i, 1]) * d[0])
            inliers = residual < 1.6
            if best is None or inliers.sum() > best.sum():
                best = inliers
        if best is None or best.sum() < max(12, 0.1 * profiles):
            break
        vx, vy, x0, y0 = cv2.fitLine(pts[best].astype(np.float32), cv2.DIST_L2, 0, 0.01, 0.01).ravel()
        lines.append((np.array([x0, y0], dtype=np.float64), np.array([vx, vy], dtype=np.float64), best.sum() / profiles))
        near = np.abs((pts[:, 0] - x0) * vy - (pts[:, 1] - y0) * vx) < 4.0
        pts = pts[~near]
    return lines


def _intersect(l1, l2) -> np.ndarray | None:
    (p, d), (q, e) = l1[:2], l2[:2]
    m = np.array([[d[0], -e[0]], [d[1], -e[1]]])
    if abs(np.linalg.det(m)) < 1e-9:
        return None
    t = np.linalg.solve(m, q - p)[0]
    return p + t * d


def inset_quad(quad: np.ndarray, margin: float) -> np.ndarray:
    """Moves every side of the quad inwards by `margin` of the side length (perspective-correct)."""
    unit = np.array([[0, 0], [1, 0], [1, 1], [0, 1]], dtype=np.float32)
    h = cv2.getPerspectiveTransform(unit, quad.astype(np.float32))
    inner = np.array([[margin, margin], [1 - margin, margin], [1 - margin, 1 - margin], [margin, 1 - margin]], np.float32)
    return cv2.perspectiveTransform(inner[None], h)[0].astype(np.float64)


def _dark_candidates(gray: np.ndarray):
    """Rough quads from the black frame itself: the outline of a dark, quadrilateral region, moved in."""
    h, w = gray.shape
    blurred = cv2.GaussianBlur(gray, (0, 0), 2.0)
    out = []
    for level in (50, 70, 90, 110):
        dark = (blurred < level).astype(np.uint8)
        contours, _ = cv2.findContours(dark, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
        for contour in contours:
            hull = cv2.convexHull(contour)
            if cv2.contourArea(hull) < 0.05 * w * h:
                continue
            perimeter = cv2.arcLength(hull, True)
            for eps in np.linspace(0.01, 0.08, 15):
                approx = cv2.approxPolyDP(hull, eps * perimeter, True)
                if len(approx) == 4:
                    quad = approx.reshape(4, 2).astype(np.float64)
                    edges = [np.linalg.norm(quad[(i + 1) % 4] - quad[i]) for i in range(4)]
                    if is_convex(quad) and min(edges) > 0.25 * max(edges):
                        out.append(inset_quad(quad, 0.03))
                    break
    return out


def _grid_score(gray: np.ndarray, frame_quad: np.ndarray) -> float:
    """How well the 8x8 squares fit inside the frame quad: contrast across the lines between
    squares, minus the contrast across lines through the middle of the squares.

    Neighbouring squares differ in brightness, so with the right quad there is an edge on every
    line between two squares and none in between. Pieces hide some of it, in both terms alike.
    """
    unit = np.array([[0, 0], [1, 0], [1, 1], [0, 1]], dtype=np.float32)
    h = cv2.getPerspectiveTransform(unit, inset_quad(frame_quad, INSET).astype(np.float32))
    along = (np.arange(8) + 0.5) / 8
    delta = 0.2 / 8

    def contrast(lines) -> float:
        pts = []
        for k in lines:
            for t in along:
                pts += [[k - delta, t], [k + delta, t], [t, k - delta], [t, k + delta]]
        values = _sample(gray, cv2.perspectiveTransform(np.array([pts], dtype=np.float32), h)[0])
        return float(np.minimum(np.abs(values[0::2] - values[1::2]), 80).mean())

    return contrast(np.arange(1, 8) / 8) - contrast((np.arange(8) + 0.5) / 8)


def squareness(quad: np.ndarray, width: int, height: int, focal: float | None = None) -> float:
    """0 if the quad can be the picture of a square, larger the less it can.

    With the camera matrix K = diag(f, f, 1) about the image centre, K^-1 H maps the board plane
    into camera coordinates; for a square its first two columns have the same length and are
    perpendicular. The result is |log(length ratio)| + |cosine of the angle|.
    """
    f = focal or DEFAULT_FOCAL * float(np.hypot(width, height))
    unit = np.array([[0, 0], [1, 0], [1, 1], [0, 1]], dtype=np.float32)
    h = cv2.getPerspectiveTransform(unit, np.asarray(quad, dtype=np.float32))
    k_inv = np.array([[1 / f, 0, -width / 2 / f], [0, 1 / f, -height / 2 / f], [0, 0, 1]])
    m = k_inv @ h
    a, b = m[:, 0], m[:, 1]
    na, nb = np.linalg.norm(a), np.linalg.norm(b)
    return float(abs(np.log(na / nb)) + abs(a @ b) / (na * nb))


def _fourth_side(lines, missing: int, shape) -> tuple | None:
    """The line of side `missing` that makes a square with the other three, or None.

    The two neighbouring sides and the opposite side are given. The two unknown corners lie on
    the neighbouring sides; they are moved along them until the quad is the picture of a square.
    """
    opposite, left, right = lines[(missing + 2) % 4], lines[(missing + 3) % 4], lines[(missing + 1) % 4]
    # Sides run corner i -> corner i+1; `left` ends in the first corner of `missing`, `right` starts
    # at its second one.
    a = _intersect(left, opposite)   # corner missing+3, shared by `opposite` and `left`
    b = _intersect(opposite, right)  # corner missing+2
    if a is None or b is None:
        return None
    base = np.linalg.norm(b - a)
    centre = (a + b) / 2

    def away(line, corner):
        d = line[1] / np.linalg.norm(line[1])
        other = _intersect(line, lines[missing]) if lines[missing] is not None else None
        if other is not None and np.linalg.norm(other - corner) > 1e-6:
            return d if d @ (other - corner) > 0 else -d
        # Without a hint: away from the opposite side, towards where the quad has to be.
        normal = np.array([-(b - a)[1], (b - a)[0]])
        return d if abs(d @ normal) > 0 else d

    da, db = away(left, a), away(right, b)
    if lines[missing] is None:
        # Both possible directions along each side are tried through the sign of the step.
        signs = [(1, 1), (-1, -1)]
    else:
        signs = [(1, 1)]
    best = None
    h, w = shape
    for sa, sb in signs:
        lo, hi = np.array([0.2, 0.2]) * base, np.array([4.0, 4.0]) * base
        for _ in range(4):
            ss = np.linspace(lo[0], hi[0], 13)
            ts = np.linspace(lo[1], hi[1], 13)
            local = None
            for s_ in ss:
                for t_ in ts:
                    d_corner, c_corner = a + sa * s_ * da, b + sb * t_ * db
                    quad = np.array([a, b, c_corner, d_corner])
                    if not is_convex(quad):
                        continue
                    cost = squareness(quad, w, h)
                    if local is None or cost < local[0]:
                        local = (cost, s_, t_, c_corner, d_corner)
            if local is None:
                break
            step = np.array([ss[1] - ss[0], ts[1] - ts[0]])
            lo = np.maximum(np.array([local[1], local[2]]) - step, 0.05 * base)
            hi = np.array([local[1], local[2]]) + step
            if best is None or local[0] < best[0]:
                best = local
    if best is None or best[0] > 0.02:
        return None
    c_corner, d_corner = best[3], best[4]
    direction = c_corner - d_corner
    return d_corner, direction / np.linalg.norm(direction), 0.0


def _best_quad(gray: np.ndarray, quad: np.ndarray, reach: float):
    """The best combination of one line per side: (quad, grid score) or None.

    A combination has to be the picture of a square; among those, the squares and the support
    of the lines decide.
    """
    centre = quad.mean(axis=0)
    sides = [_refine_side(gray, quad[i], quad[(i + 1) % 4], centre, reach) for i in range(4)]
    # A side that is hidden (the far edge behind a row of pieces) can be worked out from the
    # other three when those are well supported.
    firsts = [lines[0] if lines else None for lines in sides]
    for i in range(4):
        others = [firsts[(i + k) % 4] for k in (1, 2, 3)]
        if all(line is not None and line[2] >= 0.25 for line in others):
            inferred = _fourth_side(firsts, i, gray.shape)
            if inferred is not None:
                sides[i] = sides[i] + [inferred]
    if any(not lines for lines in sides):
        return None
    best = None
    for l0 in sides[0]:
        for l1 in sides[1]:
            for l2 in sides[2]:
                for l3 in sides[3]:
                    lines = (l0, l1, l2, l3)
                    corners = [_intersect(lines[i - 1], lines[i]) for i in range(4)]
                    if any(c is None for c in corners):
                        continue
                    candidate = np.array(corners)
                    edges = [np.linalg.norm(candidate[(i + 1) % 4] - candidate[i]) for i in range(4)]
                    if not is_convex(candidate) or min(edges) < 0.2 * max(edges):
                        continue
                    if area_abs(candidate) < 0.03 * gray.shape[0] * gray.shape[1]:
                        continue
                    off_square = squareness(candidate, gray.shape[1], gray.shape[0])
                    if off_square > MAX_OFF_SQUARE:
                        continue
                    grid = _grid_score(gray, candidate)
                    # The squares decide between clearly different lines (the frame or a row of
                    # pieces); between lines a few pixels apart they cannot, there the better
                    # supported one is the frame edge.
                    value = grid + 20.0 * sum(line[2] for line in lines) - 150.0 * off_square
                    if best is None or value > best[2]:
                        best = (candidate, grid, value)
    return None if best is None else best[:2]


def _refine(gray: np.ndarray, rough: np.ndarray):
    """Refined inner frame edge and its score, or None."""
    result = _best_quad(gray, rough, 0.2)  # wide first: the rough side may sit on a row of pieces
    if result is None:
        return None
    return _best_quad(gray, result[0], 0.05) or result


def find_frame(image, return_score: bool = False):
    """The inner edge of the black frame as four corners in photo pixels, or None."""
    img = np.asarray(image.convert("RGB"))
    for size in (WORK_SIZE, *SMALLER_WORK_SIZES):
        scale = size / max(img.shape[:2])
        gray = cv2.cvtColor(cv2.resize(img, None, fx=scale, fy=scale, interpolation=cv2.INTER_AREA), cv2.COLOR_RGB2GRAY)
        roughs = [quad for _, quad in _rough_candidates(gray)[:6]] + _dark_candidates(gray)[:6]
        best, best_score = None, MIN_GRID_SCORE
        for rough in roughs:
            result = _refine(gray, rough)
            if result is not None and result[1] > best_score:
                best, best_score = result
        if best is not None:
            break
    quad = None if best is None else canonicalize_quad(best) / scale
    return (quad, best_score if best is not None else None) if return_score else quad


def find_playing_area(image, inset: float = INSET) -> np.ndarray | None:
    frame = find_frame(image)
    return None if frame is None else canonicalize_quad(inset_quad(frame, inset))


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument("--images", type=Path, required=True)
    ap.add_argument("--labels", type=Path, help="default: <images>/labels.jsonl")
    ap.add_argument("--out", type=Path, required=True)
    ap.add_argument("--inset", type=float, default=INSET)
    args = ap.parse_args(argv)

    labels = {}
    labels_file = args.labels or args.images / "labels.jsonl"
    if labels_file.is_file():
        for line in labels_file.read_text(encoding="utf-8").splitlines():
            if line.strip():
                rec = json.loads(line)
                labels[rec["image"]] = canonicalize_quad(rec["corners"])
    args.out.mkdir(parents=True, exist_ok=True)
    errors, missed, margins = [], [], []
    paths = sorted(p for p in args.images.iterdir() if p.suffix.lower() in IMAGE_SUFFIXES)
    for path in paths:
        image = load_image(path)
        frame = find_frame(image)
        truth = labels.get(path.name)
        canvas = cv2.cvtColor(np.asarray(image), cv2.COLOR_RGB2BGR)
        if truth is not None:
            draw_quad(canvas, truth, GREEN, 4)
        if frame is None:
            missed.append(path.name)
            print(f"{path.name}: not found")
        else:
            area = canonicalize_quad(inset_quad(frame, args.inset))
            draw_quad(canvas, area, RED, 4)
            line = f"{path.name}: found"
            if truth is not None:
                e = corner_error(area, truth)
                errors.append((path.name, e))
                line += f", error {e['mean_squares']:.3f} squares (max {e['max_squares']:.3f})"
                # Where the labelled corners lie inside the frame quad: the inset that would fit.
                unit = np.array([[0, 0], [1, 0], [1, 1], [0, 1]], dtype=np.float32)
                h = cv2.getPerspectiveTransform(np.roll(frame, -e["shift"], axis=0).astype(np.float32), unit)
                p = cv2.perspectiveTransform(truth.astype(np.float32)[None], h)[0]
                margins.append(np.mean([p[0, 0], p[0, 1], 1 - p[2, 0], 1 - p[2, 1], p[1, 1], 1 - p[1, 0], p[3, 0], 1 - p[3, 1]]))
            print(line)
        small = cv2.resize(canvas, None, fx=0.25, fy=0.25, interpolation=cv2.INTER_AREA)
        cv2.imwrite(str(args.out / f"{path.stem}_frame.jpg"), small)

    labelled = [p for p in paths if p.name in labels]
    if labelled:
        worst = np.array([e["max_squares"] for _, e in errors])
        mean = np.array([e["mean_squares"] for _, e in errors])
        summary = {
            "labelled": len(labelled),
            "found": len(errors),
            "not_found": [n for n in missed if n in labels],
            "all_corners_within_0.25_squares": int((worst <= 0.25).sum()),
            "all_corners_within_0.10_squares": int((worst <= 0.10).sum()),
            "median_error_squares": float(np.median(mean)) if len(mean) else None,
            "fitting_inset_median": float(np.median(margins)) if margins else None,
            "found_but_off": [(n[13:19], round(e["max_squares"], 2)) for n, e in errors if e["max_squares"] > 0.25],
        }
        print(json.dumps(summary, indent=2))
        (args.out / "summary.json").write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
