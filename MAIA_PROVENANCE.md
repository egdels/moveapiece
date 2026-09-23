# Maia model provenance

The nine `maia-<rating>.onnx` files bundled in `desktop/src/main/resources/de/schliweb/moveapiece/desktop/maia/`
and `app/src/main/assets/maia/` (byte-identical copies) are the original,
lc0-based Maia networks, converted to ONNX with lc0's own converter. This file
records where they come from and how the conversion was verified.

## Upstream source

- Project: <https://github.com/CSSLab/maia-chess> (Maia 1, **not** the
  AGPL-3.0-licensed Maia-3)
- Pinned revision: `749204cf5979ce7f8b0412e804a4ee7c83c49ff8`, directory
  `maia_weights/`. The repository's only tag (`v1.0`) predates the weight
  files, so the commit hash is the only usable pin. All nine files were
  re-downloaded from commit-bound raw URLs and matched the table below.
- License: GPL-3.0 (same as MoveAPiece)
- Training checkpoint per gzip header: `ckpt-40-400000.pb`, January 2020;
  `lc0 describenet` reports `Minimal Lc0 version: v0.21.0`,
  `Training steps: 400000` for every file.

| Rating | File | SHA-256 |
|--------|------|---------|
| 1100 | maia-1100.pb.gz | `e1cf1cd0c96b8a4fa6a275f4b9fd54ed1ffebf9fe44641b9fceded310e9619c4` |
| 1200 | maia-1200.pb.gz | `ead4ba953f233ae732999ebc1e2b675378148527ebcfad2f0acbc5e4c224d98e` |
| 1300 | maia-1300.pb.gz | `36195f87bf4761834baa0bf87472b18509a7261a9d7d6f1a8443261369a733f2` |
| 1400 | maia-1400.pb.gz | `d5353ea6766356dad2d28920c6692f37a5f30963767f1a3105d33b4d0af011e8` |
| 1500 | maia-1500.pb.gz | `35ab6f20421d59e1df3b17c5a5016947af4c6761368ef84044a9a9c7619a9a00` |
| 1600 | maia-1600.pb.gz | `d2c9e5948581acf4b9fc0b1e720c5dc0fe64ce80cfc4a239d3f8a42e1176c876` |
| 1700 | maia-1700.pb.gz | `d277eacd792d340a30abb464dc65127254e65cac57abca17facc469889b96478` |
| 1800 | maia-1800.pb.gz | `0031ad7c4256b1fd09fbebd28418d644d68b26cd2a45df4967ccf5c7ec9c4965` |
| 1900 | maia-1900.pb.gz | `e2f565f42d7cd9f122557e6dc4eb84e5bbaedceda1d404dc485d3611c7c97a12` |

## Declared network format

Read from each file's protobuf header with `lc0 describenet` (lc0 v0.32.1);
all nine are identical. The 1500 file was additionally parsed with
`net_pb2.py` compiled from `lczero-common/proto/net.proto`, with the same
result.

| Field | Value |
|-------|-------|
| `network` | `NETWORK_SE_WITH_HEADFORMAT` (6 blocks, 6 SE blocks, 64 filters) |
| `input` | `INPUT_CLASSICAL_112_PLANE` |
| `policy` | `POLICY_CONVOLUTION` |
| `value` | `VALUE_WDL` |
| `moves_left` | `MOVES_LEFT_NONE` |
| `PolicyTemperature` | `1.359` (network default, checked for 1500) |

These fields determine the encoder and decoder branches in `core`
(`MaiaPositionEncoder`, `MaiaMoveIndexer`, `MaiaPolicyIndex`). Because the
policy head is `POLICY_CONVOLUTION`, lc0's converter bakes the
80×8×8 → 1858 remapping into the ONNX graph; the Java side only indexes the
1858-entry policy vector.

## Conversion

- Tool: `lc0 leela2onnx`, lc0 v0.32.1 (Homebrew, GPL-3.0-or-later)
- Command: `lc0 leela2onnx --input=maia-<rating>.pb.gz --output=maia-<rating>.onnx`
  (defaults: opset 17, FLOAT)
- Interface, inspected with `onnxruntime.InferenceSession`:

```
Input:  /input/planes  [batch, 112, 8, 8]  float32
Output: /output/policy [batch, 1858]       float32
Output: /output/wdl    [batch, 3]          float32
```

| Rating | File | Size (bytes) | SHA-256 |
|--------|------|--------------|---------|
| 1100 | maia-1100.onnx | 3,483,882 | `a23f8e9af7a66d6a498a57e4b7aa6c4c4f716879fb2ab49da90c785433656ff0` |
| 1200 | maia-1200.onnx | 3,483,882 | `4e96d6162ba4145e6a05f885e14b8dfbba8bc895b7a2f0a44d19962798b305ca` |
| 1300 | maia-1300.onnx | 3,483,882 | `e609d53fb347be056515140ef6384a37348332baa94b36e2d61231ba03dcb157` |
| 1400 | maia-1400.onnx | 3,483,882 | `1ca1045cd98736d799a6d4c5bd33df5c0d8ea45f452bd3467cc2f6d0de3c77a1` |
| 1500 | maia-1500.onnx | 3,483,882 | `8b3ad11f20efaaf5d4c8f76e170b8bc5527378abea0be8ac847fe281899889f3` |
| 1600 | maia-1600.onnx | 3,483,882 | `491aec3a97f48c443576b0c26f913faa2c72e986749171ab9e82e5e7c529ff33` |
| 1700 | maia-1700.onnx | 3,483,882 | `44f7350f5964d4a044b1c59ec872ca8fd8b53083cd371da8a4be7f9f7154b57a` |
| 1800 | maia-1800.onnx | 3,483,882 | `c712d7764f2c4925fb96de321de323a762d0b57ba476597137badc82929008d0` |
| 1900 | maia-1900.onnx | 3,483,882 | `866e22cad1300492fb9927e361a848f6f51a9639cc9b44b783f7aa02b5711da5` |

## Policy index table

`core/src/main/resources/de/schliweb/moveapiece/engine/maia/policy_index_1858.txt`
maps the 1858 policy slots to moves. Source: `policy_index.py` from
<https://github.com/Rocketknight1/minimal_lczero>, commit
`dfccc33d4968d15922437a64608bcc7584a5ead6`, GPL-3.0. Cross-checked against
lc0's own `VerboseMoveStats` output for the starting position:
`e2e4 → 322`, `d2d4 → 293`. Castling uses lc0's king-takes-rook encoding
(`e1h1`, index 103).

## Verification

`desktop/src/test/.../MaiaEngineSmokeTest` and `MaiaEngineGoldenTest` run
`MaiaEngine` end to end through ONNX Runtime and compare against lc0's native
output (`eigen` backend, `VerboseMoveStats`) for the same move sequences with
the 1500 network:

| Case | Moves | lc0 native top move | `MaiaEngine` |
|------|-------|---------------------|--------------|
| Starting position | – | `e2e4` (50.2 %) | `e2e4` |
| Multi-ply history, Black to move | `e2e4 e7e5 g1f3` | `b8c6` (50.0 %) | `b8c6` |
| Castling rights changed by play | `e2e4 e7e5 g1f3 b8c6 f1c4 f8c5 e1g1` | `g8f6` (45.6 %) | `g8f6` |
| Repeated starting position | `g1f3 g8f6 f3g1 f6g8` | `g1f3` (15.1 %) | `g1f3` |
| Promotion available but not best | `a2a4 h7h5 a4a5 h5h4 a5a6 h4h3 a6b7 h3g2` | `f1g2` (46.0 %) | `f1g2` |

The golden test also runs the starting position through all nine networks;
each picks `e2e4`, with policy mass tapering from about 51 % at 1100 to
44 % at 1900.

One real bug was found by these tests: missing pre-game history must be
zero-filled once the reconstructed history reaches the standard starting
position (lc0 `encoder.cc`, `HistoryFill=fen_only`), not padded by repeating
the current position. Fixed in `MaiaPositionEncoder#isStartingPosition`.

## Not covered

- En passant has no input plane of its own in the classical 112-plane
  format, so no separate golden test exists for it.
- Underpromotion is unit-tested at the move-string level only
  (`MaiaMoveIndexerTest`), not in a live ONNX comparison.
- No automated Android instrumented test for the Maia UI; verified manually
  on a Pixel 7a.
