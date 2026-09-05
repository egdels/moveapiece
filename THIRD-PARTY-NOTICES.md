# Third-Party Notices

This project (MoveAPiece) is licensed under the GNU General Public License v3.0
(see `LICENSE`), as required by its use of Stockfish.

## Source code dependencies

| Component | License | Source |
|---|---|---|
| Stockfish (`app/src/main/cpp/stockfish`, git submodule, pinned to tag `sf_18`, commit `cb3d4ee9b47d0c5aae855b12379378ea1439675c`) | GPLv3 | https://github.com/official-stockfish/Stockfish |
| chesslib 1.3.7 | Apache 2.0 | https://github.com/bhlangonijr/chesslib |
| AndroidX (appcompat, constraintlayout) | Apache 2.0 | https://developer.android.com/jetpack/androidx |
| Material Components for Android | Apache 2.0 | https://github.com/material-components/material-components-android |

## NNUE evaluation networks

Originally obtained from the Stockfish project's own network distribution
(`tests.stockfishchess.org` / `data.stockfishchess.org`), pinned by filename
(which itself encodes the SHA-256 prefix) and verified against a full SHA-256
checksum recorded in `app/stockfish.gradle`. Committed in this repo under
`app/nnue-nets/` (the big net xz-compressed) so the build needs no network
access — `app/stockfish.gradle` decompresses/verifies them from there,
falling back to the original download only if that copy is missing or fails
verification:

- `nn-c288c895ea92.nnue` (big net)
- `nn-37f18f62d772.nnue` (small net)

These are Stockfish project artifacts and fall under the same GPLv3 terms.
The engine binary is built with `NNUE_EMBEDDING_OFF` so the networks are not
duplicated into each of the three ABI binaries; instead they ship once as APK
assets (`app/build/generated/nnueAssets`, populated by the `installNnueAssets`
Gradle task) and are extracted to app-private storage at first launch by
`NnueAssets.java`, then pointed to via the standard UCI `EvalFile` /
`EvalFileSmall` options.

## Chess piece artwork (and app icon)

`app/src/main/res/drawable-nodpi/piece_*.png`, rasterized from the **cburnett**
SVG piece set bundled with Lichess's `lila` project:

- Source: https://github.com/lichess-org/lila/tree/master/public/piece/cburnett
- Original author: Colin M.L. Burnett
- License: GPL (see https://en.wikipedia.org/wiki/User:Cburnett/GFDL, the set
  is dual GPL/CC-BY-SA and is distributed under the GPL as part of `lila`)

Rasterized to 256x256 PNG via `rsvg-convert`; no other modifications made.

The app icon (`ic_launcher_foreground.png`, `mipmap/ic_launcher*.png`,
`fastlane/.../images/icon.png`) reuses this same `piece_wn.png` (white
knight) artwork, re-cropped and centered to fit Android's adaptive-icon
safe zone on the app's brand-green background — same source, same license,
no separate provenance.

## Sound effects

`app/src/main/res/raw/{move,capture,check}.mp3`, from the **sfx** sound set
bundled with Lichess's `lila` project:

- Source: https://github.com/lichess-org/lila/tree/master/public/sound/sfx
- Author: [Enigmahack](https://github.com/Enigmahack)
- License: AGPLv3+ (per `lila`'s own `COPYING.md`, table of per-directory
  exceptions to its default AGPLv3 code license)

Note: `lila`'s **default** sound set (`public/sound/standard`) is explicitly
listed in that same `COPYING.md` as a non-free, undocumented-license
exception ("The other sounds in public/sound") and was deliberately *not*
used here for that reason. `sfx` is one of the few sound directories `lila`
itself documents under a free license.

No modifications made beyond selecting three of the set's files.

## Pegasus board integration

`pegasus-core/` and `app/src/main/java/de/schliweb/pegasus/bluetooth/` are
copied verbatim from the user's own separate "pegasus" project (BLE
transport, DGT protocol parsing, occupancy-based move detection, LED
guidance for the physical DGT Pegasus chess board), not a third-party
dependency. License: GPLv3, same as MoveAPiece itself. Copied wholesale rather
than referenced as a git submodule so MoveAPiece's build and CI stay fully
reproducible from MoveAPiece's own git history alone; the source project's own
remote is a private, CI-unreachable NAS. `pegasus-core` is a plain
`java-library` module with zero runtime dependencies and includes that
project's own JUnit test suite (chess rules verified via perft, move
detection, protocol parsing).

### Protocol knowledge sources

The DGT/Pegasus wire protocol implementation in `pegasus-core`
(`PegasusCommands`, `PegasusUuids`, `PegasusFrameParser`,
`PegasusMessageType`, `FieldUpdate`, `BatteryStatus` and related classes) was
developed from protocol *knowledge* documented by two open-source projects —
BLE service/characteristic UUIDs, message framing, command bytes, and
message-type semantics. **No source code from either project was copied or
translated**; each fact was independently re-implemented in Java and, where
noted in the source Javadoc's `CONFIRMED_ON_HARDWARE` /
`CONFIRMED_BY_REFERENCE_IMPLEMENTATION` / `INFERRED` / `UNKNOWN` evidence
labels, separately verified against real Pegasus hardware:

- [DGTCentaurMods](https://github.com/DGTCentaurMods/DGTCentaurMods)
  (`DGTCentaurMods/opt/DGTCentaurMods/game/pegasus.py`, a Pegasus BLE
  emulation that interoperates with the official DGT app), GPL-3.0
- [picochess](https://github.com/ffalcinelli/picochess) (`dgt/board.py`,
  the classic DGT electronic-board serial protocol that the Pegasus BLE
  protocol extends), GPL-3.0

No binaries from either project are included in this repository or in the
built app; nothing beyond the protocol facts above was reused.
