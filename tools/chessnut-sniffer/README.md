# Chessnut Air BLE sniffer

Hardware verification tool: records what a Chessnut Air actually sends over
BLE so the protocol module can be written against captured bytes.

```
python3 -m venv .venv && .venv/bin/pip install bleak
.venv/bin/python sniff.py
```

Captures land in `captures/` (ignored by git). See the docstring in
`sniff.py` for the interactive commands and which constants are still
ASSUMED rather than verified.
