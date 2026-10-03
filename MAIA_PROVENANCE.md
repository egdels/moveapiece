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

## Not covered

- The model plays the reference presets' way: the highest-scoring legal move,
  no sampling.
- ONNX Runtime 1.16.3, which the desktop build uses on Intel Macs, loads the
  file and returns the same result as 1.29.0, but that was checked with its
  Apple Silicon natives; on Intel hardware only CI exercises it.
- No automated Android instrumented test for the Maia UI.
