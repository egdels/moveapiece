# Puzzle set provenance

`puzzles/puzzles.csv` is the set of tactics puzzles MoveAPiece ships. It is a
selection from the Lichess puzzle database. This file records where it comes
from and how it was built.

## Upstream source

- Database: <https://database.lichess.org/#puzzles>, file
  `lichess_db_puzzle.csv.zst`
- Dump used: published 2026-11-01 (HTTP `Last-Modified: Sun, 01 Nov 2026
  07:57:40 GMT`), 309,751,173 bytes, SHA-256
  `7b0d235fa40cfd7fdb7df1fec56a96b3b6ee1496b32ed2eb05be90d0ebf94253`,
  6,193,530 puzzles. Lichess replaces the file regularly; older dumps are not
  kept online.
- License: CC0 (public domain dedication), as stated on the database page

## Selection

Built with `tools/puzzle-export/export_puzzles.py` (Python standard library
only):

```
python3 tools/puzzle-export/export_puzzles.py lichess_db_puzzle.csv.zst puzzles/puzzles.csv
```

A puzzle is kept if

- its rating is between 400 and 2799,
- its rating deviation is at most 80, it was played at least 500 times and its
  popularity (Lichess users' votes, -100 to 100) is at least 85, so the rating
  is settled and the puzzle is well liked,
- the trainee has to find at most 4 moves.

The candidates are grouped into rating bands of 100 points. Within each band
they are ordered by the SHA-256 of their id, not by their place in the dump,
and the first 210 are taken. The result is sorted by rating, then id.

Result: 4,616 puzzles, rated 502 to 2765. Every band from 600 to 2699 is full;
there are only 194 candidates in 500-599, 12 in 2700-2799 and none below 500.
1,326 end in checkmate. 597 have a one-move solution, 2,095 two moves, 1,505
three, 419 four. File size 593,855 bytes, SHA-256
`bf3afc6a11c2b6b0856e6ff5523faad6aa626231565c04cb3fb4b3a9a354fb06`. Two runs
on the same dump produced the same bytes.

## Format

A header line `id,fen,moves,rating,themes`, then one puzzle per line. No field
contains a comma, so there is no quoting.

- `id`: the Lichess puzzle id (`https://lichess.org/training/<id>`)
- `fen`: the position *before* the opponent's move
- `moves`: the line in UCI notation, separated by spaces. The first move is
  the opponent's; then the trainee and the opponent alternate, and the line
  ends on the trainee's move. When the line ends in checkmate, any mating last
  move counts as solved, as on Lichess.
- `rating`: the Lichess puzzle rating at the time of the dump
- `themes`: the Lichess theme tags, separated by spaces

The other columns of the dump (rating deviation, popularity, play count, game
URL, opening tags, daily date) are not kept.

## Verification

`PuzzleRepositoryTest` in `:core` loads the file and plays every puzzle
through MoveAPiece's own chess model: the FEN must load, every move must be
legal, the trainee must be the side to move after the opponent's move, and a
puzzle tagged `mate` must end in checkmate.
