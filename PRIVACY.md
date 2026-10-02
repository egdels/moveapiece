# Privacy Policy

*MoveAPiece, Android and desktop. Last updated 2026-10-02.*

MoveAPiece does not collect, store, transmit or share any personal data.
There is no account, no analytics, no crash reporting, no advertising and
no telemetry. The Android app does not request the Internet permission,
so it cannot send anything anywhere; the desktop app never opens a
network connection either.

## What stays on your device

- **Settings** such as the last game setup, the chosen engine strength
  and whether the evaluation display is on. They are stored in the app's
  private storage and are removed when you uninstall the app.
- **Games you export** as PGN files. They are written only where you
  choose to save them, and only when you ask for it.

Nothing else is written, and nothing is read from other apps.

## Permissions and what they are for

- **Bluetooth** (`BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT` on Android 12 and
  later; `BLUETOOTH`, `BLUETOOTH_ADMIN` on Android 8 to 11): to find and
  talk to a DGT Pegasus or Chessnut electronic chess board. Used only
  while you connect a board. The scan is declared as never being used
  for location.
- **Location** (`ACCESS_FINE_LOCATION`, Android 8 to 11 only): Android up
  to version 11 requires this permission before an app may see Bluetooth
  LE scan results. MoveAPiece does not determine, store or use your
  location; the permission is not requested on Android 12 and later.

If you never connect a board, no permission is requested at all.

## Chess engines

Stockfish and the Maia neural networks are bundled with the app and run
entirely on your device. The ONNX Runtime library that executes Maia
ships with its own telemetry; MoveAPiece disables it on every platform.

## Children

MoveAPiece is a chess app for everyone. It collects no data from anyone,
including children.

## Changes and contact

This policy changes only if the app's behaviour changes, and such a
change will be noted in the changelog. Questions go to the project's
issue tracker: https://github.com/egdels/moveapiece/issues
