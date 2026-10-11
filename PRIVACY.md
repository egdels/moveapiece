# Privacy Policy

*MoveAPiece for Android and desktop (Windows, macOS, Linux). Effective
2026-10-11. Both apps show this text in their menu.*

## Who is responsible

MoveAPiece is free software developed and published by Christian Kierdorf
as a private, non-commercial project. The source code is at
https://github.com/egdels/moveapiece. Questions about this policy go to the
project's issue tracker at https://github.com/egdels/moveapiece/issues or
to the contact e-mail address shown on the app's Google Play listing.

## The short version

MoveAPiece does not collect, store, transmit, sell or share any personal
data. There is no account, no analytics, no crash reporting, no
advertising, no tracking and no telemetry. The Android app does not have
the Internet permission, so it cannot send anything anywhere; the desktop
app never opens a network connection either.

## What the app stores on your device

- **Settings**: the last game setup (opponent, colour, strength, opening),
  whether the evaluation is shown and which type of board was connected
  last. On Android they are in the app's private storage, on the desktop
  in the operating system's user preferences.
- **The chess engine's network file**: on Android, Stockfish's evaluation
  network is unpacked from the app into its private storage on first
  start.
- **Games you export** in PGN, only when you ask for it: on the desktop
  as a file where you choose to save it, on Android handed to the app you
  pick in the system's share dialog.

Nothing else is written. Uninstalling the Android app removes its settings
and files; exported games stay where you put them. The Android system
may include the app's settings in a device backup you have set up with
Google, under the terms of that backup service, not of MoveAPiece.

## What the app reads

- **PGN files you import**, only the file you pick in the system's file
  picker.
- **Your electronic chess board**, if you connect one: the positions of
  the pieces, the board's buttons and its battery level, over Bluetooth.
  This is used only to play the game on screen.

## Android permissions

MoveAPiece declares these permissions and nothing else. They are requested
only when you connect a board; if you never do, no permission is
requested at all.

- `BLUETOOTH_SCAN` and `BLUETOOTH_CONNECT` (Android 12 and later): to find
  and talk to a DGT Pegasus or Chessnut electronic chess board. The scan is
  declared as never being used to derive location.
- `BLUETOOTH` and `BLUETOOTH_ADMIN` (Android 8 to 11): the same, for older
  Android versions.
- `ACCESS_FINE_LOCATION` (Android 8 to 11 only): Android up to version 11
  requires this permission before an app may see Bluetooth scan results.
  MoveAPiece does not determine, store or use your location.

There is no Internet, storage, camera, microphone, contacts or phone
permission. On macOS the system asks once for Bluetooth access, for the
same purpose.

## Third parties

The chess engines Stockfish and Maia are part of the app and run entirely
on your device. The ONNX Runtime library that runs Maia comes with
telemetry of its own; MoveAPiece switches it off on every platform, and on
Android the permissions it would need are removed. The app contains no
third-party SDKs for advertising, analytics or crash reporting, and no
data is shared with anyone.

## Children

MoveAPiece is a chess app for everyone. It collects no data from anyone,
of any age.

## Changes

This policy changes only if the app's behaviour changes. Each change is
noted in `CHANGELOG.md` and the effective date above is updated. The
current version is always at
https://github.com/egdels/moveapiece/blob/main/PRIVACY.md.
