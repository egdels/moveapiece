#!/usr/bin/env python3
# Copyright (C) 2026 Christian Kierdorf
# SPDX-License-Identifier: GPL-3.0-or-later
"""Builds MoveAPiece's bundled puzzle set from the Lichess puzzle database.

Input: lichess_db_puzzle.csv.zst (or the decompressed .csv) from
https://database.lichess.org/#puzzles, released under CC0.

Output: a small CSV with the columns id,fen,moves,rating,themes - see
PUZZLE_PROVENANCE.md for the format and the reasoning behind the filters.

The selection is deterministic for a given dump: every puzzle that passes the
quality filters is assigned to a rating band, and within each band the puzzles
are ordered by a hash of their id, not by their position in the dump, and the
first PER_BAND are taken. Standard library only (Python 3.9+; .zst input needs
Python 3.14's compression.zstd or the zstd command line tool).

Usage: export_puzzles.py <lichess_db_puzzle.csv[.zst]> <output.csv>
"""

import csv
import hashlib
import io
import shutil
import subprocess
import sys

# Rating bands [MIN_RATING, MAX_RATING) of BAND_WIDTH points each.
MIN_RATING = 400
MAX_RATING = 2800
BAND_WIDTH = 100
PER_BAND = 210

# Quality filters, see PUZZLE_PROVENANCE.md.
MAX_RATING_DEVIATION = 80  # the rating has settled
MIN_POPULARITY = 85  # Lichess users' up/down votes, -100..100
MIN_PLAYS = 500  # enough attempts for the rating to mean something
MAX_SOLUTION_MOVES = 4  # the trainee's own moves; longer lines are tiring on a board
EXCLUDED_THEMES = set()


def open_dump(path):
    if not path.endswith(".zst"):
        return open(path, newline="", encoding="utf-8")
    try:
        from compression import zstd  # Python 3.14+

        return io.TextIOWrapper(zstd.open(path, "rb"), newline="", encoding="utf-8")
    except ImportError:
        if shutil.which("zstd") is None:
            sys.exit("Neither compression.zstd nor the zstd tool is available.")
        proc = subprocess.Popen(["zstd", "-dc", path], stdout=subprocess.PIPE)
        return io.TextIOWrapper(proc.stdout, newline="", encoding="utf-8")


def stable_key(puzzle_id):
    return hashlib.sha256(puzzle_id.encode("ascii")).hexdigest()


def main(argv):
    if len(argv) != 3:
        sys.exit(__doc__)
    src, dst = argv[1], argv[2]

    bands = {}
    total = 0
    with open_dump(src) as f:
        for row in csv.DictReader(f):
            total += 1
            rating = int(row["Rating"])
            if not MIN_RATING <= rating < MAX_RATING:
                continue
            if int(row["RatingDeviation"]) > MAX_RATING_DEVIATION:
                continue
            if int(row["Popularity"]) < MIN_POPULARITY:
                continue
            if int(row["NbPlays"]) < MIN_PLAYS:
                continue
            moves = row["Moves"].split()
            # First move is the opponent's, then the trainee and the opponent alternate,
            # ending on the trainee's move.
            if len(moves) < 2 or len(moves) % 2 != 0:
                continue
            if len(moves) // 2 > MAX_SOLUTION_MOVES:
                continue
            themes = row["Themes"].split()
            if EXCLUDED_THEMES.intersection(themes):
                continue
            band = (rating - MIN_RATING) // BAND_WIDTH
            bands.setdefault(band, []).append(
                (stable_key(row["PuzzleId"]), row["PuzzleId"], row["FEN"],
                 row["Moves"], rating, " ".join(themes)))

    selected = []
    for band in sorted(bands):
        candidates = sorted(bands[band])
        low = MIN_RATING + band * BAND_WIDTH
        print(f"{low}-{low + BAND_WIDTH - 1}: {len(candidates)} candidates, "
              f"taking {min(PER_BAND, len(candidates))}", file=sys.stderr)
        selected.extend(candidates[:PER_BAND])
    selected.sort(key=lambda p: (p[4], p[1]))  # by rating, then id

    with open(dst, "w", newline="", encoding="utf-8") as out:
        writer = csv.writer(out, lineterminator="\n")
        writer.writerow(["id", "fen", "moves", "rating", "themes"])
        for _, puzzle_id, fen, moves, rating, themes in selected:
            writer.writerow([puzzle_id, fen, moves, rating, themes])
    print(f"{total} puzzles read, {len(selected)} written to {dst}", file=sys.stderr)


if __name__ == "__main__":
    main(sys.argv)
