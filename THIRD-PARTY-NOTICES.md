# Third-Party Notices

This project (MoveAPiece) is licensed under the GNU General Public License v3.0
(see `LICENSE`), as required by its use of Stockfish.

## Source code dependencies

| Component | License | Source |
|---|---|---|
| Stockfish (`app/src/main/cpp/stockfish`, git submodule, pinned to tag `sf_18`, commit `cb3d4ee9b47d0c5aae855b12379378ea1439675c`) | GPLv3 | https://github.com/official-stockfish/Stockfish |
| chesslib 1.3.7 | Apache 2.0 | https://github.com/bhlangonijr/chesslib |
| Apache Commons Lang3 3.18.0 (transitive dependency of chesslib above, both `app` and `desktop`) | Apache 2.0 | https://github.com/apache/commons-lang |
| AndroidX (appcompat, constraintlayout) | Apache 2.0 | https://developer.android.com/jetpack/androidx |
| Material Components for Android | Apache 2.0 | https://github.com/material-components/material-components-android |
| JavaFX (OpenJFX) 24.0.2 (`desktop` UI toolkit: javafx-controls/-graphics/-base/-media) | GPLv2 + Classpath Exception | https://github.com/openjdk/jfx |
| bluez-dbus 0.3.5 (`desktop`, Linux Pegasus BLE transport) | MIT | https://github.com/hypfvieh/bluez-dbus |
| dbus-java-core / dbus-java-transport-native-unixsocket 5.2.0 (`desktop`, transitive/direct deps of bluez-dbus above) | MIT | https://github.com/hypfvieh/dbus-java |
| SLF4J API 2.0.18 (`desktop`, transitive dependency of dbus-java-core above) | MIT | https://www.slf4j.org/ |
| ONNX Runtime (runs the Maia neural network - see below; `core` only compiles against its API, `compileOnly` - `desktop` supplies the `onnxruntime` JVM artifact, 1.29.0 on Apple Silicon/Linux/Windows hosts and 1.16.3 when built on an Intel Mac (`ext.onnxruntimeVersion` in the root `build.gradle`; osx-x64 natives were dropped from 1.24.0 onward and 1.17.0+ requires macOS 13.3), and `app` the `onnxruntime-android` AAR at 1.29.0, since the two platforms need genuinely different native binaries) | MIT | https://github.com/microsoft/onnxruntime |

### ONNX Runtime's bundled telemetry (disabled on both platforms)

`onnxruntime-android`'s own `AndroidManifest.xml` declares `INTERNET` and
`ACCESS_NETWORK_STATE`, plus an auto-init `ContentProvider`
(`ai.onnxruntime.TelemetryInitializer`) that unconditionally builds an
`HttpClient` for Microsoft's proprietary "1DS" telemetry pipeline at process
start - collecting `Settings.Secure.ANDROID_ID`, manufacturer, model, app
version, OS version, and timezone. MoveAPiece has no use for this (it stays
fully offline on every platform) and doesn't want it running regardless, so
`app/src/main/AndroidManifest.xml` strips both permissions and the provider
via `tools:node="remove"`. Verified this doesn't affect Maia itself: ONNX
Runtime's own native log confirms a graceful fallback
(`telemetry.cc:453 Initialize: Android telemetry is unavailable because the
1DS Java HttpClient was not initialized`), and inference otherwise runs
unchanged - `OrtEnvironment`/`OrtSession` load the native library themselves
on demand, independent of the removed provider.

The same "1DS"/OneCollector telemetry system (`onnxruntime/core/platform/
posix/telemetry.cc` on macOS/Linux) is compiled into the shared native
`libonnxruntime` core, so desktop's plain JVM `onnxruntime` artifact - no
Android manifest, no `INTERNET` permission concept - has it too, just
reached differently: `OrtEnvironment.getEnvironment()` starts it
unconditionally, evidenced by a `Failed to persist telemetry device ID`
warning and a per-install UUID it tries to write to disk on every run that
loads a Maia model (see the now-removed `:memory:.ses` `.gitignore` entry).
Two changes address this:

- `MaiaEngine.start()` (`:core`, shared by desktop and Android) calls the
  official `OrtEnvironment.setTelemetry(false)` right after
  `OrtEnvironment.getEnvironment()` - the documented API for disabling
  telemetry *event collection/sending*. It doesn't suppress the device-ID
  bootstrap above (that already runs by the time this call executes), but
  it is defense-in-depth on Android too, in case the native telemetry
  system ever starts there independently of the removed
  `TelemetryInitializer` provider.
- Desktop's jpackage entry point (`desktop/.../Launcher.java`, not
  `DesktopApp` - see its Javadoc) actually prevents the bootstrap: it
  re-execs itself once with the `ORT_DISABLE_TELEMETRY=1` environment
  variable set (a real OS env var the native library reads via `getenv()`
  before any Java code runs, so it can't be set from within the same
  process after the fact) before continuing into `DesktopApp.main()`.
  Verified against a real `jpackageAppImage` build: neither the warning nor
  the device-ID file appear, the app starts and plays normally, and the
  relaunch is invisible - it only affects the packaged app, not
  `:desktop:run` dev mode, which goes through `DesktopApp` directly.

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

## Maia neural network (human-like opponent, desktop and Android)

Nine files each (ratings 1100–1900 in steps of 100, byte-identical between the
two copies), run via ONNX Runtime (see above) as an alternative, human-like
opponent to Stockfish - see `GameController#startMaiaGame` (desktop) and
`MainActivity#loadMaiaEngine` (Android):

- `desktop/src/main/resources/de/schliweb/moveapiece/desktop/maia/maia-<rating>.onnx`
- `app/src/main/assets/maia/maia-<rating>.onnx`

- Source: original Maia weights from
  https://github.com/CSSLab/maia-chess (**not** the newer, AGPL-3.0-licensed
  Maia-3), converted to ONNX with lc0's own official `leela2onnx` converter
  (https://github.com/LeelaChessZero/lc0), no third-party conversion tool
  involved
- License: GPLv3 (same as MoveAPiece itself, no additional obligations)
- Full provenance (exact upstream commit, per-file SHA-256 of both the
  original `.pb.gz` weights and the converted `.onnx` files, declared
  network-format fields, and how the conversion was verified against lc0's
  own native output): see `MAIA_PROVENANCE.md`

`core/src/main/resources/de/schliweb/moveapiece/engine/maia/policy_index_1858.txt`
is a separate, small artifact: the fixed lookup table mapping the network's
1858-slot policy output to actual chess moves, used unmodified.

- Source: `policy_index.py` from
  https://github.com/Rocketknight1/minimal_lczero, commit
  `dfccc33d4968d15922437a64608bcc7584a5ead6`
- License: GPLv3
- Independently cross-checked against lc0's own live `VerboseMoveStats`
  debug output (see `MAIA_PROVENANCE.md`) rather than trusted as-is

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
