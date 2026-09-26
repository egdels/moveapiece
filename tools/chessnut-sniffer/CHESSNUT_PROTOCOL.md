# Chessnut Air BLE protocol (hardware-verified)

Verified on a Chessnut Air on 2026-09-26 with `sniff.py` from a Mac
(bleak / CoreBluetooth). Raw captures live in `captures/` (git-ignored);
the frames quoted below are copied from them and are the fixtures for the
protocol unit tests. Nothing here is confidential: the protocol was
observed on our own hardware and matches public reverse-engineering notes.

## Advertising

- Name `Chessnut Air\n` (note the trailing newline in the local name).
- No service UUIDs in the advertisement, so scan by name prefix
  `Chessnut` and confirm by the GATT services after connecting.
- MTU negotiated to 185 on macOS.

## GATT layout

| Service                                | Characteristic                         | Props                        | Role |
|----------------------------------------|----------------------------------------|------------------------------|------|
| `1b7e8261-2877-41c3-b46e-cf057c562023` | `1b7e8262-2877-41c3-b46e-cf057c562023` | notify                       | board reports (streamed) |
| `1b7e8271-2877-41c3-b46e-cf057c562023` | `1b7e8272-2877-41c3-b46e-cf057c562023` | write, write-without-response | commands (app -> board) |
|                                        | `1b7e8273-2877-41c3-b46e-cf057c562023` | notify                       | command replies (acks, battery) |
| `1b7e8281-2877-41c3-b46e-cf057c562023` | `1b7e8282` write / `1b7e8283` notify   |                              | unused so far (nothing observed) |
| `9e5d1e47-5c13-43a0-8635-82ad38a1386f` | three characteristics                  |                              | unknown, probably OTA/vendor; untouched |

Subscribe to `…8262` and `…8273`, write to `…8272`.

## Commands (write to `…8272`)

| Bytes                          | Meaning                              | Reply on `…8273` |
|--------------------------------|--------------------------------------|------------------|
| `21 01 00`                     | enable real-time board reports       | `23 01 00` |
| `29 01 00`                     | battery request                      | `2a 02 <level> <flag>` e.g. `2a 02 64 00` = 100 %, flag 0 |
| `0a 08 b0 b1 b2 b3 b4 b5 b6 b7` | set LEDs, one bit per square         | `23 01 00` |
| `0b 04 f1 f0 d1 d0`            | beep: frequency Hz, duration ms, both 16-bit **big-endian** | `23 01 00` |

`23 01 00` is a generic ack, it followed the enable, LED and beep
commands.

Beep, verified 2026-09-26: `0b 04 07 d0 01 f4` plays 2000 Hz for 500 ms,
`0b 04 01 90 01 f4` 400 Hz for 500 ms. Note the byte order differs from
the little-endian uptime counter: sent little-endian, 2000 Hz becomes
53 kHz and the piezo only clicks. The board also plays a short tone of
its own when the BLE connection drops. The battery flag's meaning is not verified yet (0 while on
battery; check while charging).

## Button events (notify on `…8273`)

A short press of the NEW GAME button sends `0f 01 02`. A long press
(about three seconds) sends the same `0f 01 02` twice, 3.2 s apart,
apparently once on press and once on release, so the board does not
distinguish short from long. The board itself does not change anything:
the board report stream continues with the same position, no LEDs light
up and the board stays on, so "new game" is purely an event for the app
to act on. Debounce repeated events within a few seconds.

The board switched itself off once after roughly ten minutes without a
connection or piece movement, so expect an idle auto power-off.

## Board report (notify on `…8262`)

Sent about ten times per second as long as reporting is enabled, whether
or not anything changed. Layout, 38 bytes:

```
01 24 <32 bytes board> <4 bytes counter>
```

- `01 24` header.
- 32 board bytes, two squares per byte, **low nibble first**. Nibble index
  0 = h8, 1 = g8, … 7 = a8, 8 = h7, … 63 = a1. In other words
  `index = (8 - rank) * 8 + (7 - file)` with file a = 0.
- 4-byte little-endian counter, seconds since power-on (it ran 0x43,
  0x80, 0x0100 across three sessions 60 s and 125 s apart).

Piece codes (nibble values):

| Code | Piece | Code | Piece |
|------|-------|------|-------|
| 0    | empty | 7    | P white pawn |
| 1    | q black queen | 8 | r black rook |
| 2    | k black king  | 9 | B white bishop |
| 3    | b black bishop | A | N white knight |
| 4    | p black pawn  | B | Q white queen |
| 5    | n black knight | C | K white king |
| 6    | R white rook  |   | |

Reference frame, start position, from the capture at 05:37:28:

```
01 24 58 23 31 85 44 44 44 44 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 77 77 77 77 a6 c9 9b 6a cb 01 00 00
```

Reading byte 0 = `58`: low nibble 8 = r on h8, high nibble 5 = n on g8.
Byte 30 = `a6`: low nibble 6 = R on h1, high nibble A = N on g1.

## Move as seen by the board (e2-e4)

Two reports, 0.36 s apart:

1. e2 becomes empty (nibble 51 -> 0), everything else unchanged.
2. e4 becomes P (nibble 35 -> 7).

So a move arrives as a lift followed by a placement, each as a full
board snapshot. Unlike the DGT Pegasus the board reports piece identity,
so the occupancy-based `MoveDetector` can be fed with a projection and
piece identity is available on top for stricter checks and for
promotion without a UI prompt.

## LEDs

`0a 08` followed by 8 bytes. Byte i bit j lights the square with nibble
index 8*i + j, the same numbering as the board report. `0a 08 01 00 00 00
00 00 00 00` lit h8. All zeros switches everything off.

## Still open

- Battery flag semantics while charging.
- Whether the board keeps streaming after a reconnect without a new
  `21 01 00` (assume not; always re-send after connect).
- Behaviour of service `…8281` and the vendor service; not needed for
  MoveAPiece.
