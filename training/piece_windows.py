# Copyright (C) 2026 Christian Kierdorf
# SPDX-License-Identifier: GPL-3.0-or-later
"""Square windows around the squares of a board, cut from the photo as it is.

Unlike squares.py, nothing is warped: the window around a square is a plain
piece of the photo, so a piece looks in it the way it looks in any photo of
it. That is what the recogniser trained on single-piece cut-outs
(synthesize_pieces.py) expects.

The window is turned so that the piece stands upright in it. Which way a
piece leans in the photo follows from the four corners: they give the
position of the camera relative to the board (with an assumed focal length),
and with it the direction in which "up from the board" points at every
square. The foot of the piece is always at the same place in the window
(BASE_X, BASE_Y), so there is no doubt which of several pieces is meant.

Windows are indexed in view order like the crops of squares.py: [row][col],
row 0 = far side, col 0 = left, as the corners TL, TR, BR, BL define it.
"""

from __future__ import annotations

import cv2
import numpy as np
from PIL import Image

WINDOW_SIZE = 224  # pixels; a square is 86 pixels wide
WINDOW_EXTENT = 2.6  # squares across
BASE_X, BASE_Y = 0.5, 0.7  # where the foot of the piece is in the window (fractions of its size)
# Focal length as a fraction of the image diagonal when the photo does not say: the main camera
# of a phone is equivalent to 24-27 mm, i.e. 0.55-0.62 of the diagonal.
DEFAULT_FOCAL = 0.6
PIECE_HEIGHT = 1.5  # squares; only the direction of the lean is taken from it


def board_to_image(corners) -> np.ndarray:
    """Homography from board coordinates (0..8 in both directions, view order) to photo pixels."""
    plane = np.array([[0, 0], [8, 0], [8, 8], [0, 8]], dtype=np.float32)
    return cv2.getPerspectiveTransform(plane, np.asarray(corners, dtype=np.float32).reshape(4, 2))


def project(h: np.ndarray, points) -> np.ndarray:
    pts = np.asarray(points, dtype=np.float32).reshape(1, -1, 2)
    return cv2.perspectiveTransform(pts, h)[0].astype(np.float64)


def cut_window(img: np.ndarray, centre, side: float, size: int = WINDOW_SIZE, angle: float = 0.0) -> np.ndarray:
    """A size x size window of `side` photo pixels around `centre`, optionally rotated (degrees)."""
    scale = size / side
    if scale < 0.5:
        # Shrink by area first; a plain bilinear resample would alias.
        pre = max(scale * 2.0, 1e-3)
        img = cv2.resize(img, None, fx=pre, fy=pre, interpolation=cv2.INTER_AREA)
        centre = (centre[0] * pre, centre[1] * pre)
        scale /= pre
    matrix = cv2.getRotationMatrix2D((float(centre[0]), float(centre[1])), angle, scale)
    matrix[0, 2] += size / 2 - centre[0]
    matrix[1, 2] += size / 2 - centre[1]
    return cv2.warpAffine(img, matrix, (size, size), flags=cv2.INTER_LINEAR, borderMode=cv2.BORDER_REPLICATE)


def square_window_geometry(h: np.ndarray, row: int, col: int) -> tuple[np.ndarray, float]:
    """Centre of the square in the photo and the length of one square there, in pixels."""
    u, v = col + 0.5, row + 0.5
    c, left, right, up, down = project(h, [[u, v], [u - 0.5, v], [u + 0.5, v], [u, v - 0.5], [u, v + 0.5]])
    square = (np.linalg.norm(right - left) + np.linalg.norm(down - up)) / 2.0
    return c, float(square)


def up_direction(h: np.ndarray, width: int, height: int, focal: float | None = None):
    """Returns a function (u, v) -> unit vector in the photo along which a piece on that spot rises.

    The homography of the board plane and the focal length give the camera pose: with
    K = diag(f, f, 1) about the image centre, K^-1 h1 and K^-1 h2 are the board axes in camera
    coordinates and K^-1 h3 the board origin. The board normal that points towards the camera is
    "up"; projecting a point above a square shows where the top of a piece on it ends up.
    """
    f = focal or DEFAULT_FOCAL * float(np.hypot(width, height))
    k_inv = np.array([[1 / f, 0, -width / 2 / f], [0, 1 / f, -height / 2 / f], [0, 0, 1]])
    m = k_inv @ h
    scale = 2.0 / (np.linalg.norm(m[:, 0]) + np.linalg.norm(m[:, 1]))
    r1, r2, t = m[:, 0] * scale, m[:, 1] * scale, m[:, 2] * scale
    if t[2] < 0:
        r1, r2, t = -r1, -r2, -t
    n = np.cross(r1, r2)
    n /= np.linalg.norm(n)
    if n @ t > 0:
        n = -n  # towards the camera

    def direction(u: float, v: float) -> np.ndarray:
        base = r1 * u + r2 * v + t
        top = base + n * PIECE_HEIGHT
        lean = (top[:2] / top[2] - base[:2] / base[2]) * f
        length = float(np.linalg.norm(lean))
        return lean / length if length > 1e-6 else np.array([0.0, -1.0])

    return direction


def board_windows(
    image: Image.Image,
    corners,
    size: int = WINDOW_SIZE,
    extent: float = WINDOW_EXTENT,
    focal: float | None = None,
) -> np.ndarray:
    """All 64 windows as (8, 8, size, size, 3), RGB, indexed [row][col]; pieces stand upright."""
    img = np.asarray(image.convert("RGB"))
    h = board_to_image(corners)
    up = up_direction(h, img.shape[1], img.shape[0], focal)
    # One shrink for the whole photo, so that a square is about as large as in the windows.
    _, square = square_window_geometry(h, 3, 3)
    pre = min(1.0, 2.0 * (size / extent) / square)
    if pre < 1.0:
        img = cv2.resize(img, None, fx=pre, fy=pre, interpolation=cv2.INTER_AREA)
        h = np.diag([pre, pre, 1.0]) @ h
    out = np.empty((8, 8, size, size, 3), dtype=np.uint8)
    for row in range(8):
        for col in range(8):
            base, square = square_window_geometry(h, row, col)
            ux, uy = up(col + 0.5, row + 0.5)
            side = extent * square
            # Window axes: "up" in the window is the lean direction; the foot sits at (BASE_X, BASE_Y).
            right = np.array([-uy, ux])
            centre = base + np.array([ux, uy]) * (BASE_Y - 0.5) * side - right * (BASE_X - 0.5) * side
            angle = float(np.degrees(np.arctan2(ux, -uy)))  # turn the photo so that (ux, uy) points up
            out[row, col] = cut_window(img, centre, side, size, angle=angle)
    return out
