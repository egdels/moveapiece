#!/usr/bin/env python3
# Copyright (C) 2026 Christian Kierdorf
# SPDX-License-Identifier: GPL-3.0-or-later
"""Step 3b: fine-tunes the piece recogniser on photos of known positions.

This is the recipe of chesscog (Wölflein and Arandjelović, "Determining
Chess Game State From an Image", 2021): a recogniser trained on artificial
images is adapted to a particular chess set with a few photos of positions
that are known and so need no labelling. There two photos of the starting
position took the per-square error on an unseen set to 0.17 %.

Here the recogniser comes from the piece videos (pretrain_on_videos.py) and
the photos from a manifest built by label_photos.py. Three things keep it
from learning where a piece usually stands instead of what it looks like:

- the photos show the pieces on changing squares (Chess960 starting
  positions next to the standard one);
- in part of the windows, what stands left and right of the piece is greyed
  out (--hide-neighbours);
- for every photo window an artificial window from the videos is mixed in
  (--replay), in which every piece appears in every surrounding.

Series named in --hold-out are not trained on; they are for evaluate.py.

Usage:
  python finetune_on_boards.py --manifest samples/boards.jsonl \
      --checkpoint runs/pretrain/checkpoints/best.pt --replay data/synth/synth_train \
      --hold-out desk_standard_start,desk_960_start,desk_after_5_moves --out runs/boards
"""

from __future__ import annotations

import argparse
import json
import shutil
import time
from pathlib import Path

import cv2
import numpy as np
import torch
from torch import nn
from torchvision.transforms import v2

from manifest import load_manifest
from piece_model import build_model, load_checkpoint, pick_device
from piece_windows import board_windows
from pretrain_on_videos import list_windows
from squares import CLASSES, EMPTY, board_to_view, load_image


def load_photo_windows(records, images_dir: Path) -> tuple[np.ndarray, np.ndarray]:
    """All 64 windows of every photo (N, 224, 224, 3) uint8, and their classes."""
    xs, ys = [], []
    for rec in records:
        if rec.corners is None:
            raise SystemExit(f"{rec.image}: no corners in the manifest (label_photos.py writes them)")
        windows = board_windows(load_image(images_dir / rec.image), rec.corners)
        xs.append(windows.reshape(64, *windows.shape[2:]))
        ys.append(board_to_view(rec.board, rec.white_side).reshape(64))
    return np.concatenate(xs), np.concatenate(ys)


def to_tensor(windows: np.ndarray) -> torch.Tensor:
    return torch.from_numpy(np.ascontiguousarray(windows.transpose(0, 3, 1, 2))).float() / 255.0


def hide_neighbours(img: torch.Tensor, rng) -> torch.Tensor:
    """Greys out what stands left and right of the piece in a window (C, H, W).

    The piece stands upright in the middle of its window; what is beside it are its neighbours.
    In photos of starting positions the neighbours give the piece away (next to a knight in the
    corner stands a rook). Hiding them at random during training takes that hint away.
    """
    width = img.shape[2]
    half = int(width * rng.uniform(0.2, 0.32))  # the window is 2.6 squares wide: about 0.5 to 0.8 squares
    out = img.clone()
    fill = img.mean(dim=(1, 2), keepdim=True)
    if rng.random() < 0.85:
        out[:, :, : width // 2 - half] = fill
    if rng.random() < 0.85:
        out[:, :, width // 2 + half :] = fill
    return out


def finetune(model, windows: np.ndarray, labels: np.ndarray, epochs: int, lr: float, device, replay=(), hide: float = 0.0, seed: int = 0, after_epoch=None, lr_falls: bool = False, even: float = 0.5):
    torch.manual_seed(seed)
    rng = np.random.default_rng(seed)
    model.to(device)
    # The windows stay bytes and become numbers batch by batch: as numbers all at once they are
    # four times as large (20 GB for 520 photos), and the system ended two runs for that without a word.
    x, y = torch.from_numpy(windows).permute(0, 3, 1, 2), torch.from_numpy(labels)
    # Half of all squares are empty and pawns outnumber queens: weight the loss so rare pieces count.
    counts = np.bincount(labels, minlength=len(CLASSES)).astype(np.float64)
    # even 0.5 (the root) leaves pawns counting about twice as much as bishops all in all, 1.0
    # makes every kind of piece count the same (recognisers trained with photos of
    # the starting position, half of whose pieces are pawns, read officers as pawns).
    weights = 1.0 / np.maximum(counts, 1.0) ** even
    weights[counts == 0] = 0.0
    criterion = nn.CrossEntropyLoss(weight=torch.tensor(weights / weights[weights > 0].mean(), dtype=torch.float32).to(device))
    augment = v2.Compose(
        [
            v2.RandomHorizontalFlip(),
            v2.RandomAffine(degrees=8, translate=(0.04, 0.04), scale=(0.9, 1.1)),
            v2.ColorJitter(brightness=0.3, contrast=0.3, saturation=0.3, hue=0.03),
            v2.RandomApply([v2.GaussianBlur(9, sigma=(0.3, 3.0))], p=0.5),
        ]
    )
    optimizer = torch.optim.Adam(model.parameters(), lr=lr)
    # With the step as large in the last epoch as in the first, the recogniser still changes from
    # epoch to epoch at the end (up to eight boards of 45 between two epochs of
    # one run), and where it stops is luck. Falling to nothing along a cosine, it settles.
    steps = epochs * ((len(y) + 31) // 32)
    schedule = torch.optim.lr_scheduler.CosineAnnealingLR(optimizer, T_max=steps) if lr_falls else None
    for epoch in range(1, epochs + 1):
        model.train()
        order = torch.randperm(len(y))
        loss_sum, batches = 0.0, 0
        for start in range(0, len(y), 32):
            idx = order[start : start + 32]
            if len(idx) < 2:
                continue
            batch = torch.stack([hide_neighbours(augment(img), rng) if rng.random() < hide else augment(img) for img in x[idx].float() / 255.0])
            target = y[idx]
            if replay:
                picks = [replay[i] for i in rng.integers(len(replay), size=len(idx))]
                extra = np.stack([cv2.cvtColor(cv2.imread(str(p)), cv2.COLOR_BGR2RGB) for p, _ in picks])
                batch = torch.cat([batch, to_tensor(extra)])
                target = torch.cat([target, torch.tensor([c for _, c in picks])])
            optimizer.zero_grad()
            loss = criterion(model(batch.to(device)), target.to(device))
            loss.backward()
            optimizer.step()
            if schedule:
                schedule.step()
            loss_sum += loss.item()
            batches += 1
        print(f"epoch {epoch:3d} loss {loss_sum / max(1, batches):.4f}", flush=True)
        if after_epoch:
            after_epoch(model, epoch, loss_sum / max(1, batches))
    return model


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument("--manifest", type=Path, required=True, help="photos of known positions (label_photos.py)")
    ap.add_argument("--checkpoint", type=Path, help="recogniser to start from (default: ImageNet weights)")
    ap.add_argument("--hold-out", default="", help="comma-separated series that are not trained on")
    ap.add_argument("--replay", type=Path, help="artificial windows to mix in, one per photo window (data/synth/synth_train)")
    ap.add_argument("--hide-neighbours", type=float, default=0.6, help="share of photo windows with the surroundings greyed out")
    ap.add_argument("--epochs", type=int, default=15)
    ap.add_argument("--lr", type=float, default=1e-4)
    ap.add_argument("--lr-falls", action="store_true", help="let the step fall to nothing over the run instead of keeping it")
    ap.add_argument("--even", type=float, default=0.5, help="how far the kinds of pieces are made to count the same in the loss: 0 not at all, 0.5 by the root of their number (default), 1 fully")
    ap.add_argument("--keep-epochs", action="store_true", help="keep the state after every epoch (checkpoints/epoch_NN.pt), not only the last")
    ap.add_argument("--device", default="auto")
    ap.add_argument("--out", type=Path, required=True, help="run folder")
    args = ap.parse_args(argv)

    held_out = {s for s in args.hold_out.split(",") if s}
    records = load_manifest(args.manifest)
    unknown = held_out - {r.series for r in records}
    if unknown:
        raise SystemExit(f"series not in the manifest: {sorted(unknown)}")
    records = [r for r in records if r.series not in held_out]
    series = sorted({r.series for r in records})
    windows, labels = load_photo_windows(records, args.manifest.parent)
    print(f"{len(records)} photos of {len(series)} series: {len(labels)} windows, {(labels != EMPTY).sum()} with a piece")

    model = load_checkpoint(args.checkpoint) if args.checkpoint else build_model(pretrained=True)
    replay = list_windows(args.replay) if args.replay else ()
    (args.out / "checkpoints").mkdir(parents=True, exist_ok=True)

    def keep(model, epoch, loss):
        """The state after each epoch: a run of an hour that breaks off is not lost, and how far
        it is can be read in progress.txt."""
        torch.save(
            {"model": model.state_dict(), "classes": CLASSES, "trained_on": series, "epoch": epoch},
            args.out / "checkpoints" / "last.pt",
        )
        if args.keep_epochs:
            shutil.copy(args.out / "checkpoints" / "last.pt", args.out / "checkpoints" / f"epoch_{epoch:02d}.pt")
        with open(args.out / "progress.txt", "a", encoding="utf-8") as f:
            f.write(f"{time.strftime('%Y-%m-%d %H:%M:%S')} epoch {epoch} of {args.epochs} loss {loss:.4f}\n")

    model = finetune(
        model, windows, labels, args.epochs, args.lr, pick_device(args.device), replay, args.hide_neighbours, after_epoch=keep, lr_falls=args.lr_falls, even=args.even
    )

    torch.save({"model": model.state_dict(), "classes": CLASSES, "trained_on": series}, args.out / "checkpoints" / "best.pt")
    (args.out / "finetune.json").write_text(
        json.dumps(
            {
                "trained_on": series,
                "held_out": sorted(held_out),
                "photos": len(records),
                "epochs": args.epochs,
                # how the run was started, so that it can be told later what a recogniser is made of
                "manifest": str(args.manifest),
                "started_from": str(args.checkpoint) if args.checkpoint else "ImageNet weights",
                "replay": str(args.replay) if args.replay else None,
                "hide_neighbours": args.hide_neighbours,
                "lr": args.lr,
                "lr_falls": args.lr_falls,
                "even": args.even,
            },
            indent=2,
        )
        + "\n",
        encoding="utf-8",
    )
    print(f"saved {args.out / 'checkpoints' / 'best.pt'}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
