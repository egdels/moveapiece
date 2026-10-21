# Maia model provenance

`maia3-5m.onnx`, bundled in
`desktop/src/main/resources/de/schliweb/moveapiece/desktop/maia/` and
`app/src/main/assets/maia/` (byte-identical copies), is the Maia-3 5M network
converted to ONNX. This file records where it comes from and how the
conversion was verified.

## Upstream source

- Project: <https://github.com/CSSLab/maia3> (Maia-3, the successor of the
  lc0-based Maia 1 networks MoveAPiece shipped up to 1.5.2)
- Code revision used for loading and as the reference implementation:
  `1e13597c42d4858b7cfd7cfdae01e297263364b2`
- Weights: <https://huggingface.co/UofTCSSLab/Maia3-5M>, revision
  `b6559de2398d7140b985f28fd2c19fb5e47ddabe`, file `maia3-5m.pt`
  (20,968,049 bytes, SHA-256
  `ba14208b2992d85502f5fb501934abf6aaaeb355e9f3fdf90e326911f562524f`)
- License: AGPL-3.0 (`LICENSE` of the `maia3` repository; the model card
  refers to the repository for the license of code and weights)
- Paper: Monroe et al., "Chessformer: A Unified Architecture for Chess
  Modeling", ICLR 2026, <https://arxiv.org/abs/2605.19091>

## Model

An encoder-only transformer with one token per square (5,230,084 parameters,
8 blocks, width 256, 8 heads, 8 positions of history). Configuration
`maia3-5m` in the reference implementation's `model_registry.py`.

## Conversion

- Tool: `tools/maia3/export_onnx.py` in this repository, run with Python 3.11
  and the package versions in `tools/maia3/requirements.txt` (PyTorch 2.14.1,
  legacy TorchScript exporter, opset 17)
- Command:
  `python export_onnx.py --maia3-repo <maia3 checkout> --out maia3-5m.onnx --golden maia3_golden.txt`
- One change to the graph: PyTorch's ONNX exporter cannot export
  `torch.nn.RMSNorm`, so the script replaces the 16 `RMSNorm` modules by the
  same arithmetic written out (`x * rsqrt(mean(x², last dim) + eps) * weight`).
  The weights are untouched.
- Only the policy and the win/draw/loss output are exported; the model's
  third output (a thinking-time estimate) is not used.
- Result: `maia3-5m.onnx`, 20,990,437 bytes, SHA-256
  `f72550295eaaf66d8d8c94c5554cf5c3742dfd07f153d758b0d7baf0206ec589`.
  Two runs on the same machine (macOS, Apple Silicon) produced the same
  bytes; other platforms or package versions were not tried.

Interface:

```
Input:  tokens    [1, 64, 97]  float32
Input:  self_elo  [1]          float32
Input:  oppo_elo  [1]          float32
Output: policy    [1, 4352]    float32
Output: wdl       [1, 3]       float32   (logits: loss, draw, win)
```

- `tokens`: one row per square (a1 = 0 … h8 = 63). 8 history steps of 12
  channels each, oldest first, the current position last: own pawn, knight,
  bishop, rook, queen, king, then the opponent's. Each position is taken from
  its own side to move's point of view (Black to move: board flipped top to
  bottom, colours swapped). A history shorter than 8 is filled up at the front
  with its oldest position. The last channel is a clock value the reference
  engine sets to 0.
- `self_elo` / `oppo_elo`: ratings of the side to move and of the opponent,
  0–5000. MoveAPiece passes the chosen rating for both, like the reference
  engine's `Elo` option.
- `policy`: `from * 64 + to` for the 4096 square pairs, then 256 promotion
  slots (`a7a8q, a7a8r, a7a8b, a7a8n, a7b8q, …`), from the side to move's
  point of view. Castling is the plain king move.

These rules are implemented in `core` (`MaiaPositionEncoder`,
`MaiaMoveIndexer`, `MaiaEngine`), following `dataset.py`, `utils.py` and
`uci.py` of the reference implementation.

## Verification

`export_onnx.py` runs every test position through the PyTorch original and
through the exported file and stops if they differ. Over 131 positions at five
ratings each (800, 1100, 1500, 1900, 2400) the largest difference between any
two logits was 2.6e-05.

The same run writes
`desktop/src/test/resources/de/schliweb/moveapiece/desktop/maia3_golden.txt`:
the PyTorch model's top move wherever it leads the runner-up by at least five
percentage points, 529 cases. `MaiaEngineGoldenTest` plays each of them through
`MaiaEngine` and ONNX Runtime and expects the same move. The positions are
eleven hand-picked lines (openings, castling, a repeated position, promotions
that are and are not the best move, an en passant capture on offer) and
positions from twelve random move sequences.

`MaiaEngineSmokeTest` checks the starting position (e2e4, 64 % at 1500);
`MaiaMoveIndexerTest` and `MaiaPositionEncoderTest` cover the move slots and
the input layout without the model.

## Chess960

Maia-3 was trained on standard games only. MoveAPiece still offers it as a
Chess960 opponent, based on this measurement (2026-10-18): move-matching
accuracy (the network's top legal move against the move actually played) on
500 rated Lichess games each from August 2026, blitz and slower, with both
players' ratings as input.

| Plies | Standard | Chess960 |
|---|---|---|
| 0-9 | 49.9 % | 31.1 % |
| 10-29 | 52.4 % | 45.1 % |
| 30+ | 56.5 % | 54.8 % |
| All | 54.4 % | 48.3 % |

Castling is the weak spot. The network only knows castling as the king's
move to the g- or c-file. `MaiaMoveIndexer.indexOf(Board, Move)` therefore
scores a Chess960 castle (the king onto its own rook) by that slot, or by the
rook's move when the king already stands on its castling square. With it,
the network's top move is the castle in 19.9 % of the positions where the
player castled (top 3: 34.8 %); scored by the king-onto-rook slot it was
2.8 %.

Even so, Maia castled far less often than human players, so
`MaiaMoveIndexer.logitOf` adds `CHESS960_CASTLE_BONUS` (1.0) to a Chess960
castle's logit. The value was calibrated on 1000 rated Chess960 games
(Lichess, August 2026, blitz and slower) and checked on 1000 further games:

| Bonus | Accuracy | Castle top when the player castled | Maia's castles / players' castles |
|---|---|---|---|
| 0 | 50.97 % | 17.2 % | 448 / 1220 |
| 1 | 51.03 % | 34.7 % | 1205 / 1220 |

(held-out games, counted over positions where castling was legal). Scoring
the castle by the standard castling slot (e1g1/e1c1) instead was worse (11 %
castle hits). In play against Stockfish, Maia now castles in 35–39 % of its
Chess960 games instead of 17–19 %, at unchanged strength. **The bonus belongs
to this mapping and to `maia3-5m.onnx`: it has to be measured again whenever
either changes.** Harness, data and numbers: `tools/maia960-eval/REPORT.md`.

Where the castle and the king's plain step to its castling square share a
slot (king next to that square, e.g. king b1, rook a1, castling square c1),
the step gets no bonus, so the castle always ranks first. Human players
castled in 286 of the 291 such positions in which they played one of the two.

Strength: against Stockfish at fixed strength, Maia plays about 200 Elo
(Stockfish's scale) weaker in Chess960 than in standard chess at every
rating, with the order of the ratings kept. The castling bonus does not
change that. Move-matching accuracy from ply 20 on is close to standard chess
(54.9 % against 56.4 %), so the gap lies in the opening.

Known limitation: the network itself still cannot tell the castle and the
plain king step on a shared slot apart. Only training on Chess960 games
would fix this and the weaker opening play. That is not
done: the `maia3` repository has inference code only, no training code, so
data preparation and training would have to be written from scratch, though
the data exists (about 29.8 million rated Chess960 games in the Lichess
database). A fine-tuned network would be a derivative under AGPL-3.0 and
need its own provenance record.

## Opening sampling

The highest-scoring legal move alone opens every standard game the same way
(Maia against Maia: two lines in 100 games). In a standard game started with
New Game from the start position, Maia therefore samples its first two own
moves from the network's own distribution (temperature 1.0) and plays its
top move from then on (`MaiaOpeningSampling`). Never in Chess960, in a game
continued from the Opening Trainer, from a position taken from the board or
from an import.

Measured in `tools/maia960-eval/REPORT.md` ("Study: more variety in Maia's
standard openings") against 11 settings and 1000 Lichess games per rating
band: White's first move is as distributed as in human games of the same
rating (total variation distance 0.08–0.09, argmax 0.39–0.41), 24 opening
families instead of two, 0.06 % of the sampled moves lose more than 200 cp,
and the strength against Stockfish shifts by −18 Elo (95 % CI −57 to +20).

## Not covered

- Apart from the first two moves above, the model plays the reference
  presets' way: the highest-scoring legal move. The opening sampling and the
  Chess960 castling bonus are MoveAPiece's own additions.
- ONNX Runtime 1.16.3, which the desktop build uses on Intel Macs, loads the
  file and returns the same result as 1.29.0, but that was checked with its
  Apple Silicon natives; on Intel hardware only CI exercises it.
- No automated Android instrumented test for the Maia UI.
