# MoveAPiece

A native chess app for Android and desktop (Windows/macOS/Linux), written in
Java, playing against a locally compiled Stockfish engine or against Maia, a
human-like neural-network opponent that runs on-device — fully offline, no
accounts, no network access, no proprietary services. The Android app is
built for eventual distribution via [F-Droid](https://f-droid.org); the
desktop app is packaged as a native, double-clickable application image via
the JDK's own `jpackage`.

Optionally connects to a physical chess board over Bluetooth LE — a
[DGT Pegasus](https://digitalgametechnology.com/) or a
[Chessnut Air](https://www.chessnutech.com/) (Android and all three desktop
OSes), so you can play Stockfish on a real board instead of tapping the screen.

## Features

- Local play: human vs. human, human vs. Stockfish, or human vs. Maia — a
  human-like opponent that predicts what a player of a chosen rating would
  play, instead of searching for the objectively strongest move (see the
  Tech stack table)
- Opening trainer: drill a fixed line from a built-in library of 20
  well-known openings (Ruy Lopez, Italian, Sicilian Najdorf, Queen's Gambit
  Declined, King's Indian, Catalan, Trompowsky, ...). The app plays out the
  book side's moves and only accepts the trainee's correct move — matched
  on-screen by tap/click, or (with a connected board) guided
  physically via the same LED mechanism used for Stockfish's replies
- Searchable opening library: a read-only, step-through reference viewer
  over the same 20 lines, separate from the trainer
- Adjustable Stockfish playing strength (UCI_LimitStrength / UCI_Elo,
  1320–3190). Maia's rating (1100–1900 in steps of 100 - one of 9 separately
  trained models, not a single tunable engine) is live-adjustable mid-game
  on desktop; a one-time choice per game on Android, matching how Stockfish's
  own strength already worked there
- Move history in SAN notation, undo
- Check / checkmate / stalemate / draw detection (repetition, 50-move rule,
  insufficient material)
- Evaluation display: optional live Stockfish evaluation (+/- score, or
  mate-in-N) next to the board; off by default during opening-trainer
  drills so it doesn't spoil the line
- PGN import/export, including multi-game files (a picker lists each game
  by White/Black/date so you can choose which one to import)
- Move/capture/check sound effects
- Localized UI: English (default), German, French, Spanish, Italian, Dutch
- **Android and all three desktop OSes:** physical board support over BLE
  for two boards, selectable in the connect dialog — physical moves are
  detected and applied to the game; the opponent's replies are shown via
  LEDs on the board. On-screen tap-to-move stays fully usable at the same
  time — the board is a second, redundant input, not a replacement — and
  picks back up correctly after a disconnect or a screen move made while
  it was away. Portrait and landscape layouts on Android.
  - **DGT Pegasus** senses occupancy only: moves are inferred from which
    squares emptied and filled, promotions are asked on screen, and a
    capture swapped too quickly on its destination is resolved with a
    banner hint (lift the piece once). Hardware-verified on Android, macOS
    (Apple Silicon and Intel) and Windows; the Linux transport is
    implementation-complete but not yet hardware-verified.
  - **Chessnut Air** identifies every piece, so captures and promotions
    are recognised directly (set the promoted piece down, no dialog), a
    position set up on the board can be taken over into the game (tap the
    mismatch banner), move sounds play on the board's own speaker, and its
    NEW GAME button restarts what was last played (the same training line,
    or a fresh game against the same opponent). Hardware-verified on Android
    and macOS; Windows and Linux share the same transports and are
    expected to work but are not yet verified.

## Tech stack

| Area | Choice |
|---|---|
| Language | Java (no Kotlin) |
| Android UI | Material 3 Components, View Binding, ConstraintLayout |
| Desktop UI | JavaFX, styled with a custom stylesheet using the Android app's own Material 3 colors (`desktop/.../app.css`) |
| Chess rules | [chesslib](https://github.com/bhlangonijr/chesslib) (MIT) |
| Engine | [Stockfish](https://github.com/official-stockfish/Stockfish) (GPLv3), built from source, driven over UCI through `ProcessBuilder` — via the NDK on Android, via the host's native toolchain (Makefile `COMP=gcc`/`clang`/`mingw`) on desktop |
| Human-like opponent | [Maia](https://github.com/CSSLab/maia-chess) (GPLv3, original lc0-based weights, not the newer AGPL-3.0 Maia-3), 9 bundled rating levels (1100–1900), run in-process via [ONNX Runtime](https://github.com/microsoft/onnxruntime) (MIT) — a single forward pass per move, no subprocess/UCI involved unlike Stockfish |
| Physical boards | DGT Pegasus: vendored from a companion project's `core`/BLE-transport modules (GPLv3, own code — see [Third-Party Notices](THIRD-PARTY-NOTICES.md)). Chessnut Air: `chessnut-core`, own implementation of the board's BLE protocol, verified byte by byte against real hardware with the capture tool in `tools/chessnut-sniffer/` (protocol notes there), plus piece-identity-based move detection on top of `pegasus-core`'s chess model. Transport implementations (shared by both boards through a per-board GATT profile): Android (`android.bluetooth.*`), desktop/macOS (CoreBluetooth via a small in-house Objective-C/JNI bridge, `desktop/src/main/native/macos/`), desktop/Windows (Windows Runtime `Windows.Devices.Bluetooth` via a small in-house C++/WinRT/JNI bridge, `desktop/src/main/native/windows/`, MSVC-built), desktop/Linux ([bluez-dbus](https://github.com/hypfvieh/bluez-dbus)/[dbus-java](https://github.com/hypfvieh/dbus-java), both MIT — pure Java, no native code, since BlueZ's GATT client API is fully reachable over D-Bus). No third-party dependency for macOS/Windows: the one mature cross-platform BLE library (SimpleBLE) is BUSL-1.1-licensed, not GPL/FOSS |
| License | GPLv3 (required by the Stockfish dependency) |

## Building

Stockfish is pinned as a git submodule, so clone with submodules (or
initialize them afterwards):

```sh
git clone --recurse-submodules <repo-url>
# or, in an existing checkout:
git submodule update --init
```

### Android

```sh
./gradlew :app:assembleDebug
```

NNUE evaluation networks (~112 MB) are downloaded automatically at build
time and shipped as APK assets rather than embedded per-ABI in the native
binary, to keep the APK size down — see `app/stockfish.gradle`.

#### Reproducible builds

Bit-identical, reproducible output is a hard requirement of the Android
build: NDK/build-tools/compileSdk/targetSdk are pinned exactly, the native
build uses fixed parallelism and disables the linker build-id, and no
native library is stripped. Verify locally with two independent clean
builds:

```sh
./gradlew clean :app:assembleDebug
shasum -a 256 app/build/outputs/apk/debug/*.apk
./gradlew clean :app:assembleDebug
shasum -a 256 app/build/outputs/apk/debug/*.apk
# hashes must match per file
```

`assembleDebug` produces one APK per ABI plus a universal one (see
`app/build.gradle`'s `splits.abi` - each ABI split gets a distinct
`versionCode` so an F-Droid client always resolves the one matching the
device). `.github/workflows/build.yml` builds all of them on two
independent runners (`build-a`, `build-b`) and a third job (`compare`)
fails the workflow if any file's SHA-256 hash differs between the two.
Both jobs sign with the same fixed, committed `app/debug.keystore` (see
`app/build.gradle`'s `signingConfigs.debug`) instead of AGP's
auto-generated per-machine keystore, which is what makes a whole-file
hash comparison meaningful - without it, the signing block alone would
differ between runners even when the app content is identical. This
reproducibility requirement is Android-specific (driven by F-Droid's
build process) and does not apply to the desktop app.

### Desktop

```sh
./gradlew :desktop:run                 # run directly
./gradlew :desktop:jpackageAppImage    # build a native app image (desktop/build/jpackage)
./gradlew :desktop:jpackageDmg         # macOS installer (DMG)
./gradlew :desktop:jpackageDeb         # Linux installer (DEB, needs dpkg-deb + fakeroot)
./gradlew :desktop:jpackageMsi         # Windows installer (MSI, needs the WiX Toolset v3)
```

Stockfish is built from the same pinned submodule and the same committed
NNUE networks as Android, but for the host OS/architecture directly (no
NDK) — see `desktop/stockfish.gradle`. `jpackageAppImage` bundles that
binary plus a full JRE into a double-clickable `.app`/`.exe`/Linux binary
via the JDK's `jpackage` tool (`desktop/packaging.gradle`), using the same
launcher icon as the Android app. The app image (`--type app-image`) is the
plain, no-prerequisites output for local testing; the actual release
artifacts are one installer per OS (DMG, DEB, MSI — see the `jpackageDmg`/
`jpackageDeb`/`jpackageMsi` tasks in `desktop/packaging.gradle` for why not
a raw app image). None of them are code-signed, so the OS shows its usual
"unidentified developer" style warning on first launch/install.

`.github/workflows/desktop.yml` builds and packages the desktop app across
Linux (x86-64 and arm64), macOS (Apple Silicon and Intel), and Windows on
every push; `.github/workflows/release.yml` builds the same legs on a version
tag and attaches the installers (plus the signed Android APKs) to the GitHub
Release.

On macOS, the build additionally compiles a small Objective-C/JNI bridge to
CoreBluetooth for the physical-board feature (`desktop/pegasus-ble-macos.gradle`,
`desktop/src/main/native/macos/`) — needs Xcode's Command Line Tools (`clang`)
installed, which `desktop.yml`'s `macos-latest`/`macos-15-intel` runners
already have preinstalled. The packaged app's `Info.plist` declares
`NSBluetoothAlwaysUsageDescription` (macOS kills any process outright that
touches CoreBluetooth without it - see `desktop/src/main/jpackage-resources/Info.plist`).

On Windows, the build likewise compiles a small C++/WinRT/JNI bridge to
`Windows.Devices.Bluetooth` (`desktop/pegasus-ble-windows.gradle`,
`desktop/src/main/native/windows/`) — needs MSVC (`cl.exe`) and the Windows
SDK's `cppwinrt.exe` on `PATH` (a "Developer Command Prompt for VS" locally;
`ilammy/msvc-dev-cmd` in CI, alongside the MinGW toolchain
`desktop/stockfish.gradle` already needs for Stockfish itself - two
toolchains in the same job, see `desktop.yml`'s comments on that step).

## Testing

```sh
./gradlew :core:test :pegasus-core:test :chessnut-core:test :desktop:test :app:testDebugUnitTest  # JVM unit tests
./gradlew :app:connectedDebugAndroidTest                          # instrumented tests, needs a device/emulator
```

`:core:test` covers the chess logic, Stockfish engine wrapper, opening
trainer library, and Maia's ONNX position encoding/policy decoding shared by
both apps; `:chessnut-core:test` covers the Chessnut protocol (against frames
captured from the real board) and the identity-based move detection; `:desktop:test` additionally golden-tests `MaiaEngine`'s actual
ONNX Runtime output against lc0's own native `eigen` backend for several
positions (multi-ply history, castling, repetition, promotion) across all 9
bundled rating levels; `:app:connectedDebugAndroidTest` covers the
Android-only pieces (`StockfishEngine` against a real subprocess, `BoardView`
real measure/layout/touch) that can't run on the plain JVM.

## Project structure

```
core/                    Platform-agnostic chess logic, shared by :app and
                          :desktop (plain java-library, no Android dependency)
├── src/main/java/de/schliweb/moveapiece/
│   ├── engine/           Stockfish process wrapper (UCI over stdin/stdout);
│   │                     Maia (human-like opponent): ONNX position encoding/
│   │                     policy decoding/engine + its bundled rating list
│   ├── logic/             chesslib integration (ChessGame), PGN helpers
│   └── training/          Opening trainer: curated line library + session progress

app/                    Android application module
├── src/main/java/de/schliweb/moveapiece/
│   ├── ui/               Board view, sound effects, opening library/preview screens
│   ├── board/            PhysicalBoardBridge: one interface over both boards, adapters
│   ├── pegasus/          Bridge between the DGT Pegasus and ChessGame
│   ├── chessnut/         Bridge between the Chessnut Air and ChessGame (thin, logic in chessnut-core)
│   └── MainActivity.java
├── src/main/java/de/schliweb/pegasus/bluetooth/  BLE transport (vendored, profile-aware)
├── src/main/cpp/stockfish/                       Stockfish, pinned git submodule
└── stockfish.gradle                              Drives Stockfish's own Makefile via the NDK

desktop/                JavaFX desktop application module
├── src/main/java/de/schliweb/moveapiece/desktop/
│   ├── GameController.java    Wires ChessGame + StockfishEngine + MaiaEngine + the board together
│   ├── BoardCanvas.java       Board rendering + click-to-move (Canvas/GraphicsContext)
│   ├── GameSetupDialog.java, BoardConnectDialog.java, OpeningLibraryWindow.java,
│   │   OpeningPreviewWindow.java
│   ├── board/            PhysicalBoardBridge: one interface over both boards, adapters
│   ├── pegasus/          Bridge between the DGT Pegasus and ChessGame, plus the
│   │                     per-OS BLE transports (macOS/Windows/Linux, profile-aware)
│   ├── chessnut/         Bridge between the Chessnut Air and ChessGame
│   ├── Messages.java          Localized strings (i18n/Messages*.properties)
│   └── DesktopApp.java, Launcher.java, Styles.java, MoveSoundPlayer.java, ...
├── src/main/native/macos/, src/main/native/windows/   Objective-C/JNI and C++/WinRT/JNI
│                         bridges to CoreBluetooth / Windows.Devices.Bluetooth (no native
│                         code needed for Linux - see the Tech stack table)
├── src/main/resources/de/schliweb/moveapiece/desktop/
│   ├── app.css            Visual theme (Android's own Material 3 colors)
│   ├── i18n/               Messages.properties + _de/_fr/_es/_it/_nl
│   ├── pieces/, sounds/, icon.png   Same artwork/audio as the Android app
├── stockfish.gradle       Builds Stockfish for the host OS/arch (no NDK)
├── pegasus-ble-macos.gradle, pegasus-ble-windows.gradle   Compile the native BLE bridges
└── packaging.gradle       jpackage app-image + per-OS icon generation

pegasus-core/            DGT Pegasus protocol + chess-rules/move-detection
                         module (plain java-library, zero dependencies,
                         vendored — see Third-Party Notices); used by
                         :app and :desktop (Pegasus support), by
                         :chessnut-core (chess model, BoardState, BLE
                         profile) and by :core's own tests

chessnut-core/           Chessnut Air protocol (frames, board reports with
                         piece identity, LEDs, beep, battery, button) and
                         the identity-based move detection/game flow shared
                         by :app and :desktop

tools/chessnut-sniffer/  Python/bleak capture tool used to verify the
                         Chessnut protocol on hardware, with the protocol
                         notes (CHESSNUT_PROTOCOL.md); not part of the build
```

## License

GPLv3 (see `LICENSE`), required by the Stockfish dependency. Third-party
components and their licenses are listed in
[THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).

## AI-assisted development

Most of this codebase is written by an AI coding agent (Claude Code) under a
human developer's direction and review. See [AI_POLICY.md](AI_POLICY.md) for
what that means in practice.

## Status

Android: core app and physical-board support (DGT Pegasus and Chessnut
Air) are feature-complete and verified (automated tests + real-hardware
testing). Desktop: covers the same feature set, including both boards on
macOS, Windows, and Linux. Hardware-verified on macOS (Apple Silicon and
Intel, both boards) and Windows (Pegasus); Linux is implementation-complete
but not yet hardware-verified - see the Features section above. All five desktop CI
legs (Linux x86-64/arm64, macOS Apple Silicon/Intel, Windows) build and
package in CI and ship installers with every GitHub Release.

Maia (human-like opponent) is feature-complete on both platforms, all 9
bundled rating levels, hardware-verified (Android: real device via adb;
desktop: `:desktop:test`'s golden tests against lc0's own native output).
