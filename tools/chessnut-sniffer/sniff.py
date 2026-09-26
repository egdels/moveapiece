#!/usr/bin/env python3
# Copyright (C) 2026 Christian Kierdorf
# SPDX-License-Identifier: GPL-3.0-or-later
"""
Chessnut Air BLE sniffer — hardware verification tool for MoveAPiece.

Purpose: capture the real bytes a Chessnut Air sends over BLE so the
protocol module can be written against recorded data rather than
assumptions. Everything marked ASSUMED below comes from public
reverse-engineering notes and must be confirmed on the board.

Usage:
    .venv/bin/python sniff.py                # scan, pick the Chessnut, connect
    .venv/bin/python sniff.py --list         # just list nearby BLE devices
    .venv/bin/python sniff.py --address ID   # connect to a specific device

Interactive commands once connected (type + Enter):
    board          re-render the last board report
    bat            send the ASSUMED battery request (29 01 00)
    led N          light the LED for bit N (0..63) to map bit -> square
    led a1 e4 ..   light LEDs for squares under the ASSUMED bit mapping
    ledoff         clear all LEDs
    beep HZ MS     play a tone, e.g. beep 2000 500
    raw 21 01 00   send arbitrary hex bytes to the write characteristic
    q              disconnect and quit

Every packet (in and out) is appended to captures/chessnut-<timestamp>.log
as timestamped hex, ready to be turned into unit test fixtures.
"""

import argparse
import asyncio
import datetime as dt
import pathlib
import sys

from bleak import BleakClient, BleakScanner

# --- protocol constants -----------------------------------------------------
# VERIFIED on hardware 2026-09-26 (see captures/): write char 1b7e8272 in
# service 1b7e8271; command acks and battery replies come on 1b7e8273;
# board reports (01 24 + 32 bytes + 4-byte seconds counter) stream at ~10 Hz
# on 1b7e8262 in service 1b7e8261 regardless of changes. Battery reply
# 2a 02 <level 0-100> <flag>. Every command is acked with 23 01 00.
# LED frame 0a 08 + 8 bytes VERIFIED: byte 0 bit 0 lights h8. Beep 0b 04 + Hz + ms,
# both 16-bit BIG-endian, VERIFIED (2000 Hz / 400 Hz / 500 ms audibly distinct).
# Square order and all twelve piece codes VERIFIED against the start
# position and e2-e4 (captures/chessnut-20260926-053406.log). The 4-byte
# trailer is a little-endian seconds-since-power-on counter.
# Full write-up: CHESSNUT_PROTOCOL.md next to this script.

ASSUMED_SERVICE = "1b7e8261-2877-41c3-b46e-cf057c562023"
ASSUMED_WRITE = "1b7e8272-2877-41c3-b46e-cf057c562023"
ASSUMED_NOTIFY = "1b7e8273-2877-41c3-b46e-cf057c562023"

CMD_ENABLE_REALTIME = bytes([0x21, 0x01, 0x00])
CMD_BATTERY = bytes([0x29, 0x01, 0x00])
LED_HEADER = bytes([0x0A, 0x08])
BEEP_HEADER = bytes([0x0B, 0x04])  # + frequency Hz, duration ms, both 16-bit big-endian

# Board report: 01 24 + 32 bytes (two squares per byte) + trailing bytes.
BOARD_HEADER = bytes([0x01, 0x24])
BOARD_BYTES = 32

# VERIFIED nibble -> piece code.
PIECE_CODES = {
    0x0: ".",
    0x1: "q", 0x2: "k", 0x3: "b", 0x4: "p", 0x5: "n", 0x6: "R",
    0x7: "P", 0x8: "r", 0x9: "B", 0xA: "N", 0xB: "Q", 0xC: "K",
}

# VERIFIED square order of the 64 nibbles: low nibble of byte 0 first,
# starting at h8 and running h8,g8,...,a8,h7,...,a1. The LED bitmask uses
# the same index (byte i bit j = nibble 8*i+j).
def assumed_square_name(nibble_index: int) -> str:
    rank = 8 - nibble_index // 8
    file_ = 7 - nibble_index % 8
    return "abcdefgh"[file_] + str(rank)


def assumed_nibble_index(square: str) -> int:
    file_ = "abcdefgh".index(square[0])
    rank = int(square[1])
    return (8 - rank) * 8 + (7 - file_)


# --- logging ---------------------------------------------------------------

CAPTURE_DIR = pathlib.Path(__file__).parent / "captures"


class Capture:
    def __init__(self):
        CAPTURE_DIR.mkdir(exist_ok=True)
        stamp = dt.datetime.now().strftime("%Y%m%d-%H%M%S")
        self.path = CAPTURE_DIR / f"chessnut-{stamp}.log"
        self.file = self.path.open("a")

    def log(self, direction: str, uuid: str, data: bytes, note: str = ""):
        ts = dt.datetime.now().strftime("%H:%M:%S.%f")[:-3]
        line = f"{ts} {direction} {uuid[:8]} {data.hex(' ')}"
        if note:
            line += f"   # {note}"
        print(line)
        self.file.write(line + "\n")
        self.file.flush()

    def text(self, msg: str):
        print(msg)
        self.file.write("# " + msg.replace("\n", "\n# ") + "\n")
        self.file.flush()

    def close(self):
        self.file.close()


# --- board decoding --------------------------------------------------------

def nibbles(board_bytes: bytes) -> list[int]:
    out = []
    for b in board_bytes:
        out.append(b & 0x0F)
        out.append(b >> 4)
    return out


def render_board(nibs: list[int], changed: set[int]) -> str:
    lines = []
    for rank in range(8, 0, -1):
        cells = []
        for file_ in "abcdefgh":
            idx = assumed_nibble_index(file_ + str(rank))
            code = PIECE_CODES.get(nibs[idx], f"?{nibs[idx]:X}")
            cells.append(f"[{code}]" if idx in changed else f" {code} ")
        lines.append(f"{rank} " + "".join(cells))
    lines.append("   " + "  ".join(" " + f for f in "abcdefgh"))
    return "\n".join(lines)


class BoardTracker:
    def __init__(self, cap: Capture):
        self.cap = cap
        self.last: list[int] | None = None

    def feed(self, data: bytes):
        body = data[len(BOARD_HEADER):len(BOARD_HEADER) + BOARD_BYTES]
        if len(body) != BOARD_BYTES:
            self.cap.text(f"board report too short: {len(data)} bytes")
            return
        nibs = nibbles(body)
        first = self.last is None
        changed = set() if first else {i for i in range(64) if nibs[i] != self.last[i]}
        self.last = nibs
        if first:
            self.cap.text("first board report:")
            self.cap.text(render_board(nibs, set()))
        elif changed:
            desc = ", ".join(
                f"nibble {i} ({assumed_square_name(i)}): "
                f"{PIECE_CODES.get(self.last[i], hex(self.last[i]))}"
                for i in sorted(changed)
            )
            self.cap.text(f"changed: {desc}")
            self.cap.text(render_board(nibs, changed))

    def show(self):
        if self.last is None:
            self.cap.text("no board report received yet")
        else:
            self.cap.text(render_board(self.last, set()))


# --- BLE -------------------------------------------------------------------

async def scan(seconds: float):
    print(f"scanning {seconds:.0f}s ...")
    found = await BleakScanner.discover(timeout=seconds, return_adv=True)
    rows = []
    for address, (device, adv) in found.items():
        rows.append((adv.rssi, address, device.name or adv.local_name or "", adv.service_uuids))
    rows.sort(reverse=True)
    for rssi, address, name, uuids in rows:
        print(f"{rssi:5d}  {address}  {name!r:30}  {' '.join(uuids)}")
    return rows


def pick_chessnut(rows, name_prefix=None):
    if name_prefix:
        return next((r for r in rows if r[2].lower().startswith(name_prefix.lower())), None)
    candidates = [r for r in rows if "chessnut" in r[2].lower()]
    if not candidates:
        candidates = [r for r in rows if ASSUMED_SERVICE in [u.lower() for u in r[3]]]
    return candidates[0] if candidates else None


async def dump_services(client: BleakClient, cap: Capture):
    cap.text("GATT services:")
    for service in client.services:
        cap.text(f"  service {service.uuid}  {service.description}")
        for ch in service.characteristics:
            props = ",".join(ch.properties)
            cap.text(f"    char {ch.uuid}  [{props}]  {ch.description}")
            for d in ch.descriptors:
                cap.text(f"      desc {d.uuid}")


def led_frame(bits: set[int]) -> bytes:
    payload = bytearray(8)
    for bit in bits:
        payload[bit // 8] |= 1 << (bit % 8)
    return LED_HEADER + bytes(payload)


async def read_stdin(queue: asyncio.Queue):
    loop = asyncio.get_running_loop()
    while True:
        line = await loop.run_in_executor(None, sys.stdin.readline)
        if not line:
            await queue.put("q")
            return
        await queue.put(line.strip())


async def session(address: str, cap: Capture, listen_seconds: float | None = None, extra_sends: list[bytes] = (), send_delay: float = 0.5, init: bool = True):
    tracker = BoardTracker(cap)
    write_uuid = None
    disconnected = asyncio.Event()

    def on_disconnect(_):
        cap.text("disconnected by peer")
        disconnected.set()

    dup = {"body": None, "count": 0}

    def on_notify(char, data: bytearray):
        data = bytes(data)
        if data.startswith(BOARD_HEADER):
            body = data[:len(BOARD_HEADER) + BOARD_BYTES]
            if body == dup["body"]:
                dup["count"] += 1
                return
            if dup["count"]:
                cap.text(f"({dup['count']} identical board reports suppressed)")
            dup["body"], dup["count"] = body, 0
            cap.log("<-", str(char.uuid), data, "board report (trailer = seconds counter)")
            tracker.feed(data)
            return
        note = ""
        if data and data[0] == 0x2A:
            note = "battery (2A 02 <level%> <flag>)"
        elif data == bytes([0x0F, 0x01, 0x02]):
            note = "NEW GAME button pressed"
        elif data == bytes([0x23, 0x01, 0x00]):
            note = "generic ack (seen for 21 01 00 and for 0a 08 led)"
        cap.log("<-", str(char.uuid), data, note)

    async with BleakClient(address, disconnected_callback=on_disconnect) as client:
        cap.text(f"connected to {address}  mtu={client.mtu_size}")
        await dump_services(client, cap)

        notify_chars = []
        for service in client.services:
            for ch in service.characteristics:
                if "notify" in ch.properties or "indicate" in ch.properties:
                    notify_chars.append(ch)
                if str(ch.uuid).lower() == ASSUMED_WRITE:
                    write_uuid = ch.uuid
        if write_uuid is None:
            for service in client.services:
                for ch in service.characteristics:
                    if "write" in ch.properties or "write-without-response" in ch.properties:
                        write_uuid = ch.uuid
                        cap.text(f"ASSUMED write char not found, falling back to {write_uuid}")
                        break
                if write_uuid:
                    break
        if write_uuid is None:
            cap.text("no writable characteristic found, cannot send commands")

        for ch in notify_chars:
            try:
                await client.start_notify(ch, on_notify)
                cap.text(f"subscribed {ch.uuid}")
            except Exception as e:  # noqa: BLE001
                cap.text(f"subscribe {ch.uuid} failed: {e}")

        async def send(data: bytes, note: str):
            if write_uuid is None:
                cap.text("no write characteristic")
                return
            cap.log("->", str(write_uuid), data, note)
            await client.write_gatt_char(write_uuid, data, response=False)

        if init:
            await send(CMD_ENABLE_REALTIME, "enable real-time board reports")
        for extra in extra_sends:
            await asyncio.sleep(send_delay)
            await send(extra, "extra --send")

        if listen_seconds is not None:
            cap.text(f"listening {listen_seconds:.0f}s (non-interactive)")
            try:
                await asyncio.wait_for(disconnected.wait(), timeout=listen_seconds)
            except asyncio.TimeoutError:
                pass
            for ch in notify_chars:
                try:
                    await client.stop_notify(ch)
                except Exception:  # noqa: BLE001
                    pass
            cap.text("session closed")
            return

        cap.text("ready. commands: board | bat | led N | led a1 e4 | ledoff | beep HZ MS | raw .. | q")

        queue: asyncio.Queue = asyncio.Queue()
        stdin_task = asyncio.create_task(read_stdin(queue))
        try:
            while not disconnected.is_set():
                get = asyncio.create_task(queue.get())
                wait = asyncio.create_task(disconnected.wait())
                done, _ = await asyncio.wait({get, wait}, return_when=asyncio.FIRST_COMPLETED)
                wait.cancel()
                if get not in done:
                    get.cancel()
                    break
                cmd = get.result()
                parts = cmd.split()
                if not parts:
                    continue
                op = parts[0].lower()
                if op == "q":
                    break
                elif op == "board":
                    tracker.show()
                elif op == "bat":
                    await send(CMD_BATTERY, "battery request")
                elif op == "ledoff":
                    await send(led_frame(set()), "leds off")
                elif op == "led":
                    bits = set()
                    for arg in parts[1:]:
                        if arg.isdigit():
                            bits.add(int(arg))
                        elif len(arg) == 2 and arg[0] in "abcdefgh" and arg[1] in "12345678":
                            bits.add(assumed_nibble_index(arg))
                        else:
                            cap.text(f"bad led arg {arg!r}")
                    await send(led_frame(bits), f"led bits {sorted(bits)}")
                elif op == "beep" and len(parts) == 3 and parts[1].isdigit() and parts[2].isdigit():
                    hz, ms = int(parts[1]), int(parts[2])
                    await send(BEEP_HEADER + hz.to_bytes(2, "big") + ms.to_bytes(2, "big"), f"beep {hz} Hz {ms} ms")
                elif op == "raw":
                    try:
                        await send(bytes.fromhex("".join(parts[1:])), "raw")
                    except ValueError:
                        cap.text("raw: expected hex bytes, e.g. raw 21 01 00")
                else:
                    cap.text(f"unknown command {op!r}")
        finally:
            stdin_task.cancel()
            for ch in notify_chars:
                try:
                    await client.stop_notify(ch)
                except Exception:  # noqa: BLE001
                    pass
    cap.text("session closed")


async def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--list", action="store_true", help="only list nearby BLE devices")
    ap.add_argument("--address", help="connect to this device id instead of auto-picking")
    ap.add_argument("--scan-seconds", type=float, default=8.0)
    ap.add_argument("--listen", type=float, metavar="SECONDS",
                    help="non-interactive: connect, init, log for SECONDS, exit")
    ap.add_argument("--no-init", action="store_true",
                    help="do not send the Chessnut enable-reports command after connecting")
    ap.add_argument("--name", metavar="PREFIX",
                    help="auto-pick the device whose name starts with PREFIX instead of a Chessnut")
    ap.add_argument("--send-delay", type=float, default=0.5, metavar="SECONDS",
                    help="pause before each --send (default 0.5)")
    ap.add_argument("--send", action="append", default=[], metavar="HEX",
                    help="extra hex bytes to send after init (repeatable), e.g. --send '29 01 00'")
    args = ap.parse_args()

    address = args.address
    if args.list or address is None:
        rows = await scan(args.scan_seconds)
        if args.list:
            return
        pick = pick_chessnut(rows, args.name)
        if pick is None:
            print("no Chessnut found (by name or service uuid); use --address")
            return
        address = pick[1]
        print(f"picked {pick[2]!r} at {address}")

    cap = Capture()
    cap.text(f"capture file: {cap.path}")
    try:
        await session(address, cap, args.listen, [bytes.fromhex(h) for h in args.send], args.send_delay, not args.no_init)
    finally:
        cap.close()
        print(f"capture written to {cap.path}")


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        pass
