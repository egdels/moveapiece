# Copyright (C) 2026 Christian Kierdorf
# SPDX-License-Identifier: GPL-3.0-or-later
"""The piece recogniser: network (PyTorch) and inference (ONNX Runtime).

Model contract:

  input   "input"   float32 [N, 3, 224, 224], RGB, 0..1 - windows of piece_windows.py
  output  "logits"  float32 [N, 13], classes in the order of squares.CLASSES

The ImageNet mean/std normalisation is part of the network.
"""

from __future__ import annotations

from pathlib import Path

import numpy as np

from piece_windows import WINDOW_SIZE
from squares import CLASSES

IMAGENET_MEAN = (0.485, 0.456, 0.406)
IMAGENET_STD = (0.229, 0.224, 0.225)


def build_model(pretrained: bool = True):
    """MobileNetV3-Small with a 13-class head. ImageNet weights come from torchvision (BSD-3)."""
    import torch
    from torch import nn
    from torchvision.models import MobileNet_V3_Small_Weights, mobilenet_v3_small

    class PieceNet(nn.Module):
        def __init__(self):
            super().__init__()
            weights = MobileNet_V3_Small_Weights.IMAGENET1K_V1 if pretrained else None
            self.net = mobilenet_v3_small(weights=weights)
            head = self.net.classifier[3]
            self.net.classifier[3] = nn.Linear(head.in_features, len(CLASSES))
            self.register_buffer("mean", torch.tensor(IMAGENET_MEAN).view(1, 3, 1, 1))
            self.register_buffer("std", torch.tensor(IMAGENET_STD).view(1, 3, 1, 1))

        def forward(self, x):
            return self.net((x - self.mean) / self.std)

        def head_parameters(self):
            return self.net.classifier.parameters()

    return PieceNet()


def load_checkpoint(path: Path):
    """The network with the weights of a checkpoint written by the training scripts."""
    import torch

    model = build_model(pretrained=False)
    model.load_state_dict(torch.load(path, map_location="cpu")["model"])
    return model


def pick_device(name: str = "auto"):
    """MPS on a Mac, else CUDA, else the CPU."""
    import torch

    if name != "auto":
        return torch.device(name)
    if torch.backends.mps.is_available():
        return torch.device("mps")
    return torch.device("cuda" if torch.cuda.is_available() else "cpu")


def windows_to_input(windows: np.ndarray) -> np.ndarray:
    """uint8 RGB windows (N, 224, 224, 3) to the model input."""
    windows = np.asarray(windows)
    if windows.shape[1:] != (WINDOW_SIZE, WINDOW_SIZE, 3):
        raise ValueError(f"windows must be (N, {WINDOW_SIZE}, {WINDOW_SIZE}, 3), got {windows.shape}")
    return np.ascontiguousarray(windows.transpose(0, 3, 1, 2), dtype=np.float32) / 255.0


def softmax(logits: np.ndarray) -> np.ndarray:
    logits = logits.astype(np.float64)
    e = np.exp(logits - logits.max(axis=1, keepdims=True))
    return e / e.sum(axis=1, keepdims=True)


class PieceRecogniser:
    """A recogniser for windows: an exported model (.onnx, .ort) on ONNX Runtime, as the apps
    will run it, or a checkpoint (.pt) on PyTorch."""

    def __init__(self, model_path: str | Path, threads: int = 2):
        model_path = Path(model_path)
        self.session = self.model = None
        if model_path.suffix == ".pt":
            self.device = pick_device()
            self.model = load_checkpoint(model_path).to(self.device).eval()
        else:
            import onnxruntime as ort

            opts = ort.SessionOptions()
            opts.intra_op_num_threads = threads
            self.session = ort.InferenceSession(str(model_path), sess_options=opts, providers=["CPUExecutionProvider"])

    def predict(self, windows: np.ndarray, batch: int = 64) -> np.ndarray:
        """Class probabilities (N, 13) for uint8 RGB windows (N, 224, 224, 3)."""
        out = []
        for start in range(0, len(windows), batch):
            x = windows_to_input(windows[start : start + batch])
            if self.session is not None:
                (logits,) = self.session.run(["logits"], {"input": x})
            else:
                import torch

                with torch.no_grad():
                    logits = self.model(torch.from_numpy(x).to(self.device)).cpu().numpy()
            out.append(softmax(logits))
        return np.concatenate(out) if out else np.zeros((0, len(CLASSES)))
