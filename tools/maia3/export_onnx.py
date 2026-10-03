#!/usr/bin/env python3
# Copyright (C) 2026 Christian Kierdorf
# SPDX-License-Identifier: GPL-3.0-or-later
"""Converts the Maia-3 5M PyTorch checkpoint to the ONNX file MoveAPiece bundles.

Needs a checkout of https://github.com/CSSLab/maia3 (pass it with --maia3-repo)
and the packages from requirements.txt next to this script. See
MAIA_PROVENANCE.md for the pinned revisions and checksums.

    python export_onnx.py --maia3-repo ../maia3 --out maia3-5m.onnx \
        --golden maia3_golden.txt

Besides writing the model it checks the export against the PyTorch original
and, with --golden, writes the reference moves the Java tests assert against.
Those come from the PyTorch model, not from the exported file.
"""

import argparse
import copy
import hashlib
import random
import sys
from collections import deque

import numpy as np

MODEL = "maia3-5m"
OPSET = 17
ELOS = (800, 1100, 1500, 1900, 2400)

# Hand-picked lines: openings, castling, a repetition, promotions (including
# one where promoting is best and one where it is not), en passant available.
FIXED_LINES = (
    "",
    "e2e4 e7e5 g1f3",
    "e2e4 e7e5 g1f3 b8c6 f1c4 f8c5 e1g1",
    "g1f3 g8f6 f3g1 f6g8",
    "a2a4 h7h5 a4a5 h5h4 a5a6 h4h3 a6b7 h3g2",
    "h2h4 b8c6 h4h5 c6b4 h5h6 b4c6 h6g7 c6e5",
    "a2a4 h7h5 a4a5 h5h4 a5a6 h4h3 a6b7 h3g2 b7a8q",
    "e2e4 c7c5 g1f3 d7d6 d2d4 c5d4 f3d4 g8f6 b1c3 a7a6",
    "d2d4 d7d5 c2c4 e7e6 b1c3 g8f6 c4d5 e6d5 c1g5 f8e7 e2e3 e8g8",
    "e2e4 a7a6 e4e5 d7d5",
    "e2e4 e7e5 g1f3 b8c6 f1b5 a7a6 b5a4 g8f6 e1g1 f8e7 f1e1 b7b5 a4b3 d7d6 c2c3 e8g8",
)
RANDOM_GAMES = 12
RANDOM_PLIES = 40
# A reference move only becomes a test case when it leads the runner-up by
# this much probability, so float noise can never flip the expected answer.
MIN_MARGIN = 0.05


def parse_args():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--maia3-repo", required=True, help="checkout of CSSLab/maia3")
    parser.add_argument("--out", default="maia3-5m.onnx")
    parser.add_argument("--golden", help="write reference moves for the Java tests here")
    return parser.parse_args()


def sha256(path):
    with open(path, "rb") as handle:
        return hashlib.sha256(handle.read()).hexdigest()


def main():
    args = parse_args()
    sys.path.insert(0, args.maia3_repo)

    import chess
    import onnx
    import onnxruntime
    import torch
    from maia3.dataset import get_historical_tokens, get_legal_moves_mask, tokenize_board
    from maia3.model_registry import resolve_checkpoint_path
    from maia3.uci import load_model, parse_args as maia3_args
    from maia3.utils import get_all_possible_moves, mirror_move

    cfg = maia3_args(["--model", MODEL, "--device", "cpu", "--no-use-amp"])
    cfg.checkpoint_path = resolve_checkpoint_path(
        cfg.model_spec,
        checkpoint_filename=cfg.checkpoint_filename,
        cache_dir=cfg.cache_dir,
        revision=cfg.revision,
        local_files_only=cfg.local_files_only,
        force_download=cfg.force_download,
        token=cfg.hf_token,
    )
    print(f"checkpoint {cfg.checkpoint_path}\n  sha256 {sha256(cfg.checkpoint_path)}")
    model = load_model(cfg)

    all_moves = get_all_possible_moves()
    move_index = {move: i for i, move in enumerate(all_moves)}

    def position(line):
        """Board and input tokens after `line`, with the real move history."""
        board = chess.Board()
        history = deque(maxlen=cfg.history)
        history.append(tokenize_board(board))
        for uci in line.split():
            board.push_uci(uci)
            history.append(tokenize_board(board))
        tokens = get_historical_tokens(
            history, cfg, base=0.0, inc=0.0, clk_left_before=0.0, clk_ponder=0.0
        )
        return board, tokens.unsqueeze(0)

    class ManualRMSNorm(torch.nn.Module):
        """torch.nn.RMSNorm spelled out; the ONNX exporter has no rule for the built-in."""

        def __init__(self, source):
            super().__init__()
            self.weight = source.weight
            self.eps = source.eps if source.eps is not None else torch.finfo(torch.float32).eps

        def forward(self, x):
            return x * torch.rsqrt(x.pow(2).mean(dim=-1, keepdim=True) + self.eps) * self.weight

    class PolicyAndValue(torch.nn.Module):
        def __init__(self, inner):
            super().__init__()
            self.inner = inner

        def forward(self, tokens, self_elo, oppo_elo):
            policy, wdl, _ = self.inner(tokens, self_elo, oppo_elo)
            return policy, wdl

    exportable = copy.deepcopy(model)
    for block in exportable.transformer.layers:
        for name in ("norm1", "norm2"):
            norm = getattr(block, name)
            if isinstance(norm, torch.nn.RMSNorm):
                setattr(block, name, ManualRMSNorm(norm))

    _, example = position("")
    torch.onnx.export(
        PolicyAndValue(exportable).eval(),
        (example, torch.tensor([1500.0]), torch.tensor([1500.0])),
        args.out,
        input_names=["tokens", "self_elo", "oppo_elo"],
        output_names=["policy", "wdl"],
        opset_version=OPSET,
        dynamo=False,
    )
    onnx.checker.check_model(onnx.load(args.out))
    session = onnxruntime.InferenceSession(args.out, providers=["CPUExecutionProvider"])

    def reference(line, elo):
        """Legal moves by probability from the PyTorch original, plus the ONNX logits."""
        board, tokens = position(line)
        elos = torch.tensor([elo], dtype=torch.long)
        with torch.no_grad():
            logits, _, _ = model(tokens, elos, elos)
        onnx_logits = session.run(
            None,
            {
                "tokens": tokens.numpy(),
                "self_elo": np.array([elo], np.float32),
                "oppo_elo": np.array([elo], np.float32),
            },
        )[0][0]
        logits = logits[0].numpy()
        if board.is_game_over():
            return board, [], logits, onnx_logits
        mask = get_legal_moves_mask(board, move_index).numpy()
        masked = np.where(mask, logits, -np.inf)
        probs = np.exp(masked - masked.max())
        probs /= probs.sum()
        ranked = []
        for i in np.argsort(-probs)[: int(mask.sum())]:
            uci = all_moves[i]
            ranked.append((mirror_move(uci) if board.turn == chess.BLACK else uci, float(probs[i])))
        return board, ranked, logits, onnx_logits

    rng = random.Random(20261003)
    lines = list(FIXED_LINES)
    for _ in range(RANDOM_GAMES):
        board = chess.Board()
        played = []
        for _ in range(RANDOM_PLIES):
            if board.is_game_over():
                break
            move = rng.choice(list(board.legal_moves))
            board.push(move)
            played.append(move.uci())
            if len(played) % 4 == 0:
                lines.append(" ".join(played))

    worst = 0.0
    cases = []
    for line in lines:
        for elo in ELOS:
            board, ranked, logits, onnx_logits = reference(line, elo)
            worst = max(worst, float(np.abs(logits - onnx_logits).max()))
            if not ranked:
                continue
            onnx_top = int(np.where(get_legal_moves_mask(board, move_index).numpy(), onnx_logits, -np.inf).argmax())
            onnx_move = all_moves[onnx_top]
            if board.turn == chess.BLACK:
                onnx_move = mirror_move(onnx_move)
            margin = ranked[0][1] - (ranked[1][1] if len(ranked) > 1 else 0.0)
            if margin >= MIN_MARGIN:
                if onnx_move != ranked[0][0]:
                    raise SystemExit(f"export disagrees with PyTorch: {line!r} elo {elo}")
                cases.append((elo, ranked[0][0], ranked[0][1], line))
    print(f"largest logit difference PyTorch vs ONNX: {worst:.2e}")
    if worst > 1e-3:
        raise SystemExit("export does not reproduce the PyTorch model")

    print(f"wrote {args.out}\n  sha256 {sha256(args.out)}")
    if args.golden:
        with open(args.golden, "w", encoding="utf-8") as handle:
            handle.write("# elo;expected move;probability;moves from the starting position\n")
            handle.write("# Generated by tools/maia3/export_onnx.py from the PyTorch model.\n")
            for elo, move, prob, line in cases:
                handle.write(f"{elo};{move};{prob:.3f};{line}\n")
        print(f"wrote {args.golden} ({len(cases)} cases)")


if __name__ == "__main__":
    main()
