# MoveAPiece

A native chess app for Android and desktop (Windows/macOS/Linux), written in
Java, playing against a locally compiled Stockfish engine — fully offline,
no accounts, no network access, no proprietary services. The Android app is
built for eventual distribution via [F-Droid](https://f-droid.org); the
desktop app is packaged as a native, double-clickable application image via
the JDK's own `jpackage`.

Optionally connects to a physical [DGT Pegasus](https://digitalgametechnology.com/)
chess board over Bluetooth LE (Android only), so you can play Stockfish on a
real board instead of tapping the screen.

## Features

- Local play: human vs. human, or human vs. Stockfish
- Opening trainer: drill a fixed line from a built-in library of 20
  well-known openings (Ruy Lopez, Italian, Sicilian Najdorf, Queen's Gambit
  Declined, King's Indian, Catalan, Trompowsky, ...). The app plays out the
  book side's moves and only accepts the trainee's correct move — matched
  on-screen by tap/click, or (Android + a connected Pegasus board) guided
  physically via the same LED mechanism used for Stockfish's replies
- Searchable opening library: a read-only, step-through reference viewer
  over the same 20 lines, separate from the trainer
- Adjustable Stockfish playing strength (UCI_LimitStrength / UCI_Elo,
  1320–3190)
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
- **Android only:** physical board support (DGT Pegasus, BLE) — physical
  moves are detected from occupancy changes and applied to the game;
  Stockfish's replies are shown via LEDs on the board. On-screen tap-to-move
  stays fully usable at the same time — the board is a second, redundant
  input, not a replacement — and picks back up correctly after a disconnect
  or a screen move made while it was away. Portrait and landscape layouts.

## Tech stack

| Area | Choice |
|---|---|
| Language | Java (no Kotlin) |
| Android UI | Material 3 Components, View Binding, ConstraintLayout |
| Desktop UI | JavaFX, styled with a custom stylesheet using the Android app's own Material 3 colors (`desktop/.../app.css`) |
| Chess rules | [chesslib](https://github.com/bhlangonijr/chesslib) (MIT) |
| Engine | [Stockfish](https://github.com/official-stockfish/Stockfish) (GPLv3), built from source, driven over UCI through `ProcessBuilder` — via the NDK on Android, via the host's native toolchain (Makefile `COMP=gcc`/`clang`/`mingw`) on desktop |
| Physical board | Vendored from a companion project's `core`/BLE-transport modules (GPLv3, own code — see [Third-Party Notices](THIRD-PARTY-NOTICES.md)); Android only |
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
```

Stockfish is built from the same pinned submodule and the same committed
NNUE networks as Android, but for the host OS/architecture directly (no
NDK) — see `desktop/stockfish.gradle`. `jpackageAppImage` bundles that
binary plus a full JRE into a double-clickable `.app`/`.exe`/Linux binary
via the JDK's `jpackage` tool (`desktop/packaging.gradle`), using the same
launcher icon as the Android app. It's an unsigned app image
(`--type app-image`), not a signed installer — macOS will warn about an
unidentified developer on first launch.

`.github/workflows/desktop.yml` builds and packages the desktop app across
Linux (x86-64 and arm64), macOS (Apple Silicon and Intel), and Windows.

## Testing

```sh
./gradlew :core:test :pegasus-core:test :app:testDebugUnitTest    # JVM unit tests
./gradlew :app:connectedDebugAndroidTest                          # instrumented tests, needs a device/emulator
```

`:core:test` covers the chess logic, Stockfish engine wrapper, and opening
trainer library shared by both apps; `:app:connectedDebugAndroidTest` covers
the Android-only pieces (`StockfishEngine` against a real subprocess,
`BoardView` real measure/layout/touch) that can't run on the plain JVM.

## Project structure

```
core/                    Platform-agnostic chess logic, shared by :app and
                          :desktop (plain java-library, no Android dependency)
├── src/main/java/de/schliweb/moveapiece/
│   ├── engine/           Stockfish process wrapper (UCI over stdin/stdout)
│   ├── logic/             chesslib integration (ChessGame), PGN helpers
│   └── training/          Opening trainer: curated line library + session progress

app/                    Android application module
├── src/main/java/de/schliweb/moveapiece/
│   ├── ui/               Board view, sound effects, opening library/preview screens
│   ├── pegasus/          Bridge between the physical board and ChessGame
│   └── MainActivity.java
├── src/main/java/de/schliweb/pegasus/bluetooth/  BLE transport (vendored)
├── src/main/cpp/stockfish/                       Stockfish, pinned git submodule
└── stockfish.gradle                              Drives Stockfish's own Makefile via the NDK

desktop/                JavaFX desktop application module
├── src/main/java/de/schliweb/moveapiece/desktop/
│   ├── GameController.java    Wires ChessGame + StockfishEngine + the board together
│   ├── BoardCanvas.java       Board rendering + click-to-move (Canvas/GraphicsContext)
│   ├── OpeningLibraryWindow.java, OpeningPreviewWindow.java, TrainingSetupDialog.java
│   ├── Messages.java          Localized strings (i18n/Messages*.properties)
│   └── DesktopApp.java, Launcher.java, Styles.java, MoveSoundPlayer.java, ...
├── src/main/resources/de/schliweb/moveapiece/desktop/
│   ├── app.css            Visual theme (Android's own Material 3 colors)
│   ├── i18n/               Messages.properties + _de/_fr/_es/_it/_nl
│   ├── pieces/, sounds/, icon.png   Same artwork/audio as the Android app
├── stockfish.gradle       Builds Stockfish for the host OS/arch (no NDK)
└── packaging.gradle       jpackage app-image + per-OS icon generation

pegasus-core/            Physical-board protocol/chess-rules/move-detection
                         module (plain java-library, zero dependencies,
                         vendored — see Third-Party Notices); used by
                         :app (Pegasus support) and by :core's own tests
                         (cross-checking FEN output against it)
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

Android: core app and physical-board support are feature-complete and
verified (automated tests + real-hardware testing). Desktop: covers the
same feature set except physical-board support, verified locally on macOS
(Apple Silicon); the Linux/Windows/Intel-Mac legs of `desktop.yml` are new
and not yet verified against a real CI run.
