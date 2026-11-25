#!/usr/bin/env python3
# Copyright (C) 2026 Christian Kierdorf
# SPDX-License-Identifier: GPL-3.0-or-later
"""Exports a recogniser checkpoint for ONNX Runtime, which the apps already ship.

  piece_recogniser.onnx        FP32, opset 17 (6.1 MB)
  piece_recogniser.fp16.onnx   weights as float16, float32 in and out (3.1 MB)
  piece_recogniser.ort         ORT format of the FP32 model

With --check, FP32 and FP16 are compared on the windows of the photos of a
manifest: how often they name the same piece and how long a board takes.
(INT8 static quantisation was tried and collapses this network; it is not
offered.)

Usage:
  python export_models.py --checkpoint runs/boards/checkpoints/best.pt --out runs/boards/export \
      --check samples/boards.jsonl
"""

from __future__ import annotations

import argparse
import json
import shutil
import tempfile
import time
from pathlib import Path

import numpy as np

from piece_model import PieceRecogniser, load_checkpoint
from piece_windows import WINDOW_SIZE

OPSET = 17
NAME = "piece_recogniser"


def spell_out_hard_activations(model) -> None:
    """Replaces Hardswish and Hardsigmoid by the same functions written with basic operations.

    The JVM build of ONNX Runtime 1.29.0 for macOS on Apple silicon returns zeros for the
    HardSwish operator (the Python build does not), so the network then gives the same answer
    for every picture. x * relu6(x + 3) / 6 is the same function and exports as Add, Clip, Mul
    and Div, which every build gets right.
    """
    import torch
    from torch import nn

    class HardSigmoid(nn.Module):
        def forward(self, x):
            return torch.clamp(x + 3.0, 0.0, 6.0) / 6.0

    class HardSwish(nn.Module):
        def forward(self, x):
            return x * torch.clamp(x + 3.0, 0.0, 6.0) / 6.0

    for name, child in model.named_children():
        if isinstance(child, nn.Hardswish):
            setattr(model, name, HardSwish())
        elif isinstance(child, nn.Hardsigmoid):
            setattr(model, name, HardSigmoid())
        else:
            spell_out_hard_activations(child)


def export_onnx(checkpoint: Path, out_path: Path) -> None:
    import torch

    model = load_checkpoint(checkpoint).eval()
    spell_out_hard_activations(model)
    torch.onnx.export(
        model,
        torch.zeros(1, 3, WINDOW_SIZE, WINDOW_SIZE),
        str(out_path),
        input_names=["input"],
        output_names=["logits"],
        dynamic_axes={"input": {0: "batch"}, "logits": {0: "batch"}},
        opset_version=OPSET,
        dynamo=False,
    )


def convert_fp16(onnx_path: Path, out_path: Path) -> None:
    import onnx
    from onnxruntime.transformers import float16

    onnx.save(float16.convert_float_to_float16(onnx.load(str(onnx_path)), keep_io_types=True), str(out_path))


def convert_ort(onnx_path: Path) -> Path | None:
    """ONNX -> ORT format next to the model (onnxruntime's converter, "Fixed" optimisation style)."""
    from onnxruntime.tools import convert_onnx_models_to_ort
    from onnxruntime.tools.convert_onnx_models_to_ort import OptimizationStyle

    with tempfile.TemporaryDirectory() as tmp:
        shutil.copy2(onnx_path, Path(tmp) / onnx_path.name)
        convert_onnx_models_to_ort.convert_onnx_models_to_ort(
            model_path_or_dir=Path(tmp),
            output_dir=None,
            optimization_styles=[OptimizationStyle.Fixed],
            custom_op_library_path=None,
        )
        generated = list(Path(tmp).glob("*.ort"))
        if not generated:
            return None
        ort_path = onnx_path.with_suffix(".ort")
        shutil.move(str(generated[0]), str(ort_path))
        return ort_path


def check(paths: list[Path], manifest: Path, photos: int) -> list[dict]:
    from manifest import load_manifest
    from piece_windows import board_windows
    from squares import load_image

    records = [r for r in load_manifest(manifest) if r.corners is not None]
    step = max(1, len(records) // photos)
    windows = np.concatenate(
        [board_windows(load_image(manifest.parent / r.image), r.corners).reshape(64, WINDOW_SIZE, WINDOW_SIZE, 3) for r in records[::step][:photos]]
    )
    rows, reference = [], None
    for path in paths:
        recogniser = PieceRecogniser(path)
        probs = recogniser.predict(windows)
        recogniser.predict(windows[:64])
        start = time.perf_counter()
        for _ in range(5):
            recogniser.predict(windows[:64])
        row = {"file": path.name, "bytes": path.stat().st_size, "ms_per_board": round((time.perf_counter() - start) / 5 * 1000, 1)}
        if reference is None:
            reference = probs
        else:
            row["same_piece_as_fp32"] = float((probs.argmax(axis=1) == reference.argmax(axis=1)).mean())
            row["max_probability_difference"] = float(np.abs(probs - reference).max())
        rows.append(row)
    return rows


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument("--checkpoint", type=Path, required=True)
    ap.add_argument("--out", type=Path, required=True)
    ap.add_argument("--check", type=Path, help="manifest whose photos are used to compare the exported models")
    ap.add_argument("--check-photos", type=int, default=20)
    args = ap.parse_args(argv)

    args.out.mkdir(parents=True, exist_ok=True)
    fp32 = args.out / f"{NAME}.onnx"
    fp16 = args.out / f"{NAME}.fp16.onnx"
    export_onnx(args.checkpoint, fp32)
    convert_fp16(fp32, fp16)
    ort_path = convert_ort(fp32)
    paths = [p for p in (fp32, fp16, ort_path) if p is not None]
    for path in paths:
        print(f"{path.name}: {path.stat().st_size / 1e6:.2f} MB")
    if args.check:
        rows = check(paths, args.check, args.check_photos)
        (args.out / "export_report.json").write_text(json.dumps(rows, indent=2) + "\n", encoding="utf-8")
        print(json.dumps(rows, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
