# Chessnut BLE sniffer

Hardware verification tool: records what a Chessnut board actually sends
over BLE so the protocol module can be written against captured bytes.
Verified so far with a Chessnut Air; if you own an Air+, Pro or Go, a
capture of the start position, a few moves and each button press is what
is needed to confirm those models (see "Other models" in
`CHESSNUT_PROTOCOL.md`).

```
python3 -m venv .venv && .venv/bin/pip install bleak
.venv/bin/python sniff.py
```

Captures land in `captures/` (ignored by git). See the docstring in
`sniff.py` for the interactive commands and which constants are still
ASSUMED rather than verified.
