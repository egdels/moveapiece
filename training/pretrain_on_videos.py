#!/usr/bin/env python3
# Copyright (C) 2026 Christian Kierdorf
# SPDX-License-Identifier: GPL-3.0-or-later
"""Step 3a: trains the piece recogniser on the artificial windows made from the piece videos.

Input is the output of synthesize_pieces.py: <data>/synth_train/<class>/ and
<data>/synth_val/<class>/. Two phases: first only the new head on top of the
frozen ImageNet backbone, then the whole network at a lower learning rate.
The windows are varied geometrically when they are generated; here only the
colours are jittered.

The result knows the shapes of the pieces but not yet how they look in real
photos; finetune_on_boards.py does that part.

Outputs in --out:
  metrics.jsonl         one record per epoch
  checkpoints/best.pt   best validation accuracy
  checkpoints/last.pt

Usage:
  python pretrain_on_videos.py --data data/synth --out runs/pretrain
"""

from __future__ import annotations

import argparse
import json
import time
from pathlib import Path

import cv2
import numpy as np
import torch
from torch import nn
from torch.utils.data import DataLoader, Dataset
from torchvision.transforms import v2

from piece_model import build_model, pick_device
from squares import CLASSES, EMPTY


def list_windows(folder: Path) -> list[tuple[Path, int]]:
    items = [(p, i) for i, cls in enumerate(CLASSES) for p in sorted((folder / cls).glob("*.png"))]
    if not items:
        raise SystemExit(f"no windows in {folder}")
    return items


class WindowDataset(Dataset):
    def __init__(self, items, augment: bool):
        self.items = items
        self.jitter = v2.ColorJitter(brightness=0.2, contrast=0.3, saturation=0.2, hue=0.02) if augment else None

    def __len__(self):
        return len(self.items)

    def __getitem__(self, i):
        path, label = self.items[i]
        img = cv2.cvtColor(cv2.imread(str(path), cv2.IMREAD_COLOR), cv2.COLOR_BGR2RGB)
        x = torch.from_numpy(np.ascontiguousarray(img.transpose(2, 0, 1))).float() / 255.0
        return (self.jitter(x) if self.jitter else x), label


@torch.no_grad()
def validate(model, loader, device) -> dict:
    model.eval()
    right = pieces = pieces_right = n = 0
    loss_sum = 0.0
    for x, y in loader:
        logits = model(x.to(device)).cpu()
        loss_sum += float(nn.functional.cross_entropy(logits, y, reduction="sum"))
        pred = logits.argmax(dim=1)
        right += int((pred == y).sum())
        occupied = y != EMPTY
        pieces += int(occupied.sum())
        pieces_right += int((pred[occupied] == y[occupied]).sum())
        n += len(y)
    return {"loss": loss_sum / max(1, n), "accuracy": right / max(1, n), "accuracy_pieces": pieces_right / max(1, pieces)}


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument("--data", type=Path, required=True, help="output of synthesize_pieces.py")
    ap.add_argument("--out", type=Path, required=True, help="run folder")
    ap.add_argument("--head-epochs", type=int, default=2, help="phase 1: only the head")
    ap.add_argument("--epochs", type=int, default=14, help="phase 2: the whole network")
    ap.add_argument("--head-lr", type=float, default=1e-3)
    ap.add_argument("--lr", type=float, default=1e-4)
    ap.add_argument("--batch", type=int, default=64)
    ap.add_argument("--workers", type=int, default=4)
    ap.add_argument("--device", default="auto", help="auto (MPS, else CUDA, else CPU), mps, cuda or cpu")
    ap.add_argument("--seed", type=int, default=42)
    ap.add_argument("--no-pretrained", action="store_true", help="start from random weights (no download)")
    args = ap.parse_args(argv)

    torch.manual_seed(args.seed)
    train_items = list_windows(args.data / "synth_train")
    val_items = list_windows(args.data / "synth_val")
    (args.out / "checkpoints").mkdir(parents=True, exist_ok=True)
    print(f"windows: {len(train_items)} train, {len(val_items)} val")

    device = pick_device(args.device)
    print(f"device: {device}")
    loader_args = dict(batch_size=args.batch, num_workers=args.workers, persistent_workers=args.workers > 0)
    train_loader = DataLoader(WindowDataset(train_items, augment=True), shuffle=True, **loader_args)
    val_loader = DataLoader(WindowDataset(val_items, augment=False), **loader_args)

    model = build_model(pretrained=not args.no_pretrained).to(device)
    criterion = nn.CrossEntropyLoss()
    phases = [
        ("head", args.head_epochs, torch.optim.Adam(model.head_parameters(), lr=args.head_lr)),
        ("all", args.epochs, torch.optim.Adam(model.parameters(), lr=args.lr)),
    ]

    best, epoch = -1.0, 0
    with open(args.out / "metrics.jsonl", "w", encoding="utf-8") as log:
        for phase, epochs, optimizer in phases:
            for p in model.parameters():
                p.requires_grad = phase == "all"
            for p in model.head_parameters():
                p.requires_grad = True
            for _ in range(epochs):
                epoch += 1
                start = time.time()
                model.train()
                if phase == "head":
                    model.net.features.eval()  # frozen backbone: keep its batch-norm statistics
                loss_sum, n = 0.0, 0
                for x, y in train_loader:
                    x, y = x.to(device), y.to(device)
                    optimizer.zero_grad()
                    loss = criterion(model(x), y)
                    loss.backward()
                    optimizer.step()
                    loss_sum += loss.item() * len(y)
                    n += len(y)
                val = validate(model, val_loader, device)
                rec = {"epoch": epoch, "phase": phase, "train_loss": loss_sum / max(1, n), "seconds": round(time.time() - start, 1)}
                rec.update({f"val_{k}": v for k, v in val.items()})
                log.write(json.dumps(rec) + "\n")
                log.flush()
                print(
                    f"epoch {epoch:3d} [{phase}] train loss {rec['train_loss']:.4f} | val loss {val['loss']:.4f} "
                    f"accuracy {val['accuracy']:.4f} pieces {val['accuracy_pieces']:.4f} ({rec['seconds']} s)"
                )
                state = {"model": model.state_dict(), "classes": CLASSES, "epoch": epoch, "val": val}
                torch.save(state, args.out / "checkpoints" / "last.pt")
                if val["accuracy"] > best:
                    best = val["accuracy"]
                    torch.save(state, args.out / "checkpoints" / "best.pt")
    print(f"best validation accuracy: {best:.4f}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
