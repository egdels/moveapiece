# maia960-eval

Measurement harness for Maia: plays it through the app's own `MaiaEngine` and
`StockfishEngine` (`:core`) against Stockfish at fixed strengths and against
itself, and compares its moves with human games. Results and discussion:
[REPORT.md](REPORT.md). Development tool only, not part of the apps; Gradle
includes it only with `-Pmaia960eval`.

- `reproduce.sh`: the Chess960 study (strength shift, castling bonus).
- `opening-study.sh`: the study of sampled opening moves in standard chess.

## Data

Nothing of it is in the repository; the scripts download it into `data/`
(ignored by git):

- **Lichess game database**, https://database.lichess.org, released under
  CC0: the rated Chess960 games of August 2026 (116 MB, SHA-256 pinned in
  `reproduce.sh`) and the first games of the standard dump of the same month
  (streamed, only as many as needed).
- **lichess-org/chess-openings**, https://github.com/lichess-org/chess-openings,
  CC0, commit `a6189a30dc273ccb21fc2536a9a2fefd5592a67a`: the five `*.tsv`
  files of opening names, used only here to name the openings of the opening
  study (`OpeningNames`).

The raw output of a run (`games.csv`, PGNs, `events.tsv`,
`sampled-moves.tsv`) is not committed either; the summaries (`summary.md`,
`comparison.md`) are.
