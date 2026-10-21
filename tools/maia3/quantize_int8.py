#!/usr/bin/env python3
# Copyright (C) 2026 Christian Kierdorf
# SPDX-License-Identifier: GPL-3.0-or-later
"""Quantizes an exported Maia-3 ONNX file to int8 weights.

Only for the measurements in tools/maia960-eval/REPORT.md ("Experiment: Maia-3
79M and int8 quantization"); the app bundles the float file.

    python quantize_int8.py maia3-79m.onnx maia3-79m-int8.onnx

Dynamic quantization of the MatMul and Gemm weights, one scale per tensor,
with the onnxruntime version from requirements.txt.
"""

import argparse
import hashlib

from onnxruntime.quantization import QuantType, quantize_dynamic


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("source")
    parser.add_argument("target")
    args = parser.parse_args()
    quantize_dynamic(
        args.source,
        args.target,
        weight_type=QuantType.QInt8,
        op_types_to_quantize=["MatMul", "Gemm"],
    )
    with open(args.target, "rb") as handle:
        print(f"wrote {args.target}\n  sha256 {hashlib.sha256(handle.read()).hexdigest()}")


if __name__ == "__main__":
    main()
