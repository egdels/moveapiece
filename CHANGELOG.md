# Changelog

All notable changes to MoveAPiece, for the Android app and the desktop app
(macOS, Windows, Linux) alike. The Android store entry for each release is the
matching file in `fastlane/metadata/android/en-US/changelogs/`.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/);
versions follow [Semantic Versioning](https://semver.org/). Release
candidates (`-rc`) and test tags are not listed.

## [Unreleased]

### Changed

- Chessnut: the board is quieter. Ordinary moves and captures no longer
  beep, the LEDs show them. A tone remains for check, two for checkmate,
  and a low one when a piece is put where it cannot go.
- Chessnut: while a game is under way, the NEW GAME button has to be
  pressed twice in quick succession to start over, so a bumped button no
  longer throws the game away. Before the first move and after the game
  has ended, one press is enough as before.
- Engine: Stockfish 19 instead of 18. It plays stronger and needs one
  evaluation network instead of two; the networks of the previous version
  are removed from the device on first start.
- Maia: the opponent now runs on Maia-3, the current generation of the
  model, which predicts human moves more accurately. One network covers
  every rating instead of one network per rating, and the ratings on offer
  now run from 800 to 2400 instead of 1100 to 1900. The Maia-3 network is
  licensed under AGPL-3.0, see `THIRD-PARTY-NOTICES.md`.

### Fixed

- Chessnut, "load position from board": a board with more pieces of a kind
  than promotions could have produced (for example a second queen next to
  all eight pawns) is turned down as an invalid position instead of being
  handed to the engine.
- If the engine process ends unexpectedly, the app now says so instead of
  waiting for a move that never comes.

## [1.5.2] - 2026-10-02

### Changed

- Main screen (Android and desktop): six buttons for what you do every game
  (new game, undo, redo, flip, hint, board) and a "more" menu for the rest
  (opening library, PGN import/export, game analysis). The texts above the
  board are down to one line: training progress or engine strength, replaced
  briefly by the blunder warning when there is one. Android buttons show a
  tooltip on long-press; the board button is filled green while a board is
  connected.
- The evaluation display is now off until you switch it on; a stored choice
  still wins.
- Boards: there is no board type to choose any more. "Connect board" lists
  the devices in range, recognised boards first, and the app finds out
  whether the one you pick is a DGT Pegasus or a Chessnut by probing its
  Bluetooth services, trying the next candidate when the first does not
  match. On Android a Pegasus is recognised by its advertised UART service
  even after it has been renamed.
- Chessnut: messages name the model the board announces, e.g. "Chessnut Air
  connected". Air+, Pro and Go speak the same protocol as the Air and are
  listed alongside it; only the Air has been tested. The Move is not
  supported.
- Desktop: the "more" button is a plain button with a context menu, so its
  glyph sits centred.

### Fixed

- Android, landscape: the New Game dialog no longer hides the colour choice
  and the strength slider below the fold on short screens (phones, 7-inch
  tablets); it now lays out in two columns, opponent on the left and the
  rest on the right.
- Android, DGT Pegasus: disconnecting within the board's start-up sequence
  no longer leaves one "write failed" error per remaining init command
  behind, and a reconnect inside that sequence no longer starts a second,
  interleaved one.
- macOS: the app connects through a freshly created Bluetooth central and
  says why Bluetooth is unavailable instead of failing silently.
- Windows: a board that drops the link while its services are still being
  looked up no longer triggers a cascade of "service not found" / "connect
  failed" reports and a given-up reconnect; the next attempt starts clean.
  Boards advertising a random address now connect reliably. Error messages
  name the Bluetooth status. A second disconnect while already reconnecting
  no longer starts two reconnect timers.
- Linux: the same guard for the BlueZ transport, covered by a test against a
  fake BlueZ. Still unverified on real hardware.
- Linux, macOS: a second disconnect while already reconnecting no longer
  starts two reconnect attempts in parallel.

### Build

- Building from source on Windows: Visual Studio and MSYS2 are found
  automatically, so a plain terminal or IDE build works.
- Running the desktop app from Gradle no longer needs a separately installed
  JDK 23; the Foojay toolchain resolver provisions it.

## [1.5.1] - 2026-09-27

### Changed

- Android: the release build now removes unused library code (R8 shrinking),
  as requested in the F-Droid review. Smaller APK, no functional changes.

## [1.5.0] - 2026-09-26

### Added

- Chessnut Air support on Android and desktop, next to the DGT Pegasus
  (board type in the connect dialog). The board knows every piece, so
  captures and promotions are detected directly, a position set up on the
  board can be taken over, move sounds play on the board's speaker and a low
  battery is announced. On promotion the app tells you to swap the pawn for
  the promotion piece.
- The Chessnut's NEW GAME button restarts what was last played (same training
  line or same opponent) and closes any open dialog.
- The New Game dialog remembers your last choice (opponent, colour, training
  line, hints) and opens pre-filled with it.
- Pegasus: banner hints explain what the board cannot see: a capture swapped
  too quickly ("lift the piece on that square briefly") and a lifted piece
  without a legal move (naming its destinations, or why it has none).
- Desktop: a recording switch for board traffic.
- New `chessnut-core` module with the hardware-verified Chessnut protocol, a
  BLE sniffer under `tools/chessnut-sniffer/` and protocol notes.

### Fixed

- Pegasus: engine-move LEDs are re-asserted once, 800 ms after being shown
  (fixes guides that stayed dark).

### Build

- `appVersionCode` is spelled out in `gradle.properties` for F-Droid's update
  checker; the build fails if it disagrees with the value derived from
  `appVersion`.
- Documented DGT's answer on the developer key.

## [1.4.1] - 2026-09-25

### Fixed

- Pegasus, opening trainer: castling in a trainer line was reported as a
  board mismatch (the rook's squares lit up as wrong) as soon as it was
  shown.
- With a board connected, the move sounds for your move and the reply played
  at the same instant. The reply now sounds when it is actually played on the
  board, in the trainer and against the engine.
- Desktop: the "opening complete" dialog could not be closed with the
  window's close button.

### Internal

- The opening trainer's move flow lives in `core` as `TrainingFlow`, with
  tests for its board transitions.

## [1.4.0] - 2026-09-25

### Added

- Pegasus: if the board no longer matches the position, the app shows which
  squares differ, on screen and via LEDs, and waits until they are fixed
  before the engine or the trainer plays its next move.

### Fixed

- Pegasus: after the opponent's capture, the app could stop accepting moves
  if the captured and the capturing piece were swapped quickly.
- The opening trainer no longer stalls when a line starts with a piece in
  hand.

## [1.3.5] - 2026-09-23

### Fixed

- Windows: the Maia opponent could fail to start at all. The Java runtime
  bundled with the desktop app shipped a Microsoft C++ runtime too old for
  the ONNX Runtime library Maia runs on; the desktop app is now built with
  Temurin 25.
- If Maia's native library cannot be loaded, the app now reports the error
  instead of silently never making a move (Android and desktop).

### Changed

- Added `MAIA_PROVENANCE.md` describing where the Maia models come from;
  README, `AI_POLICY.md` and the F-Droid description brought up to date.
- Release workflow: re-pushing a tag updates the existing GitHub release
  instead of failing; desktop tests run in CI.

## [1.3.4] - 2026-09-19

### Added

- Maia now takes a brief, human-like pause before replying instead of moving
  instantly.

### Fixed

- Move-quality/blunder-check now also works during Maia games, not just
  against Stockfish.
- Android: in the New Game dialog, the White/Black colour choice was
  vertically misaligned.

## [1.3.3] - 2026-09-16

### Fixed

- Privacy: the bundled ONNX Runtime library (used for the Maia opponent)
  silently started its own telemetry on every launch, on Android requesting
  network access MoveAPiece itself never needed. Disabled on both Android
  and desktop; the app stays fully offline, no accounts, no telemetry.

## [1.3.2] - 2026-09-15

### Fixed

- Desktop: the Maia opponent still failed to start on Intel-based Macs
  running macOS 12; the 1.3.1 fix only covered macOS 13.3 and later.

## [1.3.1] - 2026-09-14

### Fixed

- Desktop: the Maia opponent failed to start on Intel-based Macs. ONNX
  Runtime is pinned to 1.23.2.

## [1.3.0] - 2026-09-13

### Added

- Maia, a human-like chess opponent that plays like a real person rather
  than a search engine, on desktop and Android. Choose its rating
  (1100 to 1900); Android uses the same slider style as the Stockfish Elo.

### Fixed

- Android: the New Game dialog could appear broken after rotating the
  device; open dialogs are now dismissed on rotation.
- Pegasus: disconnecting the board mid-setup could trigger spurious errors;
  pending init-sequence writes are cancelled on disconnect.
- `THIRD-PARTY-NOTICES.md` lists the dependencies that ship with Maia.

## [1.2.0] - 2026-09-12

### Added

- Redo button, mirroring undo's engine and training pairing.
- The move list is clickable: jump to any position.
- Game analysis can run anytime, not just after the game ends.
- The board can show file and rank coordinates (a-h, 1-8).
- The Pegasus board warns when its battery is critically low; battery status
  is only surfaced on connect or when newly critical, not on every 1 % drift.
- The move list auto-scrolls to the end on a new move or PGN import.

### Fixed

- An occasional incorrect evaluation after fast undo/redo (stale engine
  "info" lines from an abandoned search were discarded).
- Desktop: square macOS Dock icon while the app is running.

## [1.1.0] - 2026-09-08

### Added

- Desktop: DGT Pegasus board over Bluetooth LE on macOS, Windows and Linux,
  with a battery toast.
- Desktop: choose your colour against Stockfish; game mode, colour, opening
  and hints share one New Game dialog, as on Android.

### Changed

- Android: redesigned game screen. The board and action buttons stay fixed
  while only the move list scrolls, so a long game cannot push the buttons
  out of view; action buttons wrap to fit the screen width.

### Fixed

- Android: the board shifted when a move-quality hint appeared or
  disappeared.
- Desktop: the packaged app crashed at launch because the trimmed runtime
  was missing modules.
- Desktop: the opening trainer's "Continue freely" option never appeared
  after finishing a line as White.
- Windows: Pegasus discovery and a startup crash in the BLE bridge.

## [1.0.1] - 2026-09-06

### Fixed

- Android: the Pegasus board could fail to appear when scanning on
  Android 11 and earlier if the system location service was off (required
  by Android for Bluetooth LE scanning on these versions). The app now
  detects this and asks you to turn location on instead of scanning
  silently forever.

## [1.0.0] - 2026-09-05

Initial public release, for Android and for desktop (macOS DMG, Windows MSI,
Linux .deb).

### Added

- Local play against Stockfish or against a second human.
- Opening training with a fixed line library.
- Evaluation display, move-quality assessment, multi-line hints and
  post-game analysis.
- PGN import and export.
- Android: physical DGT Pegasus board via Bluetooth LE.
- Engine strength and evaluation display persist across restarts.
- Available in German, English, French, Spanish, Italian and Dutch.

### Build

- One source of truth for the app version; the Android `versionCode` is
  derived from it. The APK is split per ABI for smaller F-Droid downloads.
- Reproducible builds with a fixed debug keystore and a CI hash gate;
  release signing and a tag-triggered GitHub Release workflow that attaches
  the Android APKs and the desktop packages.
- Windows: move sounds were silent because of a malformed ID3 tag in the
  mp3 files.

[1.5.2]: https://github.com/egdels/moveapiece/compare/v1.5.1...v1.5.2
[1.5.1]: https://github.com/egdels/moveapiece/compare/v1.5.0...v1.5.1
[1.5.0]: https://github.com/egdels/moveapiece/compare/v1.4.1...v1.5.0
[1.4.1]: https://github.com/egdels/moveapiece/compare/v1.4.0...v1.4.1
[1.4.0]: https://github.com/egdels/moveapiece/compare/v1.3.5...v1.4.0
[1.3.5]: https://github.com/egdels/moveapiece/compare/v1.3.4...v1.3.5
[1.3.4]: https://github.com/egdels/moveapiece/compare/v1.3.3...v1.3.4
[1.3.3]: https://github.com/egdels/moveapiece/compare/v1.3.2...v1.3.3
[1.3.2]: https://github.com/egdels/moveapiece/compare/v1.3.1...v1.3.2
[1.3.1]: https://github.com/egdels/moveapiece/compare/v1.3.0...v1.3.1
[1.3.0]: https://github.com/egdels/moveapiece/compare/v1.2.0...v1.3.0
[1.2.0]: https://github.com/egdels/moveapiece/compare/v1.1.0...v1.2.0
[1.1.0]: https://github.com/egdels/moveapiece/compare/v1.0.1...v1.1.0
[1.0.1]: https://github.com/egdels/moveapiece/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/egdels/moveapiece/releases/tag/v1.0.0
