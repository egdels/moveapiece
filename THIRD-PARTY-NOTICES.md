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
