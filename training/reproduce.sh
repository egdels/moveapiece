#!/bin/sh
# Copyright (C) 2026 Christian Kierdorf
# SPDX-License-Identifier: GPL-3.0-or-later
#
# The training of the shipped piece recogniser, from the saved photos to the exported network.
# Run from training/ with the environment of requirements.txt in .venv; see README.md.
#
# samples/ and data/ are not in the repository. Expected:
#   samples/pieces/        videos of the single black pieces and videos.csv (video,piece)
#   samples/frame_test/    photos with labels.jsonl; two of them show the empty board
#   samples/names.sh       the names that say which photos are meant: EMPTY_BOARDS (the two
#                          photos of the empty board), RECORDS_A and RECORDS_B (how the names
#                          of the app records of the two test sets begin, see EVALUATION.md)
#   samples/start/, sp*/, desk_*/, device_*/   photos of starting positions (boards_v2.jsonl)
#   samples/collector/collection/   the photos of the collector app: pieces/, boards/
#   data/photo_cutouts_45cm_v2_toned/   cut-outs of the piece photos (README.md, section 5;
#                          cutting them out needs rembg, which is not in requirements.txt)
#   data/composed_45cm/    the 400 composed boards, which cannot be made again
#   data/composed_45cm_and_real.jsonl   the composed boards and the photos of starting positions
#
# Stages 2 and 3 and the export, repeated from the saved data on the computer of
# the first run (torch 2.15.0, device mps), gave the shipped network bit for bit. On other
# hardware or library versions expect a network that reads about as well, not the same file;
# the pretraining has not been repeated. A sleeping computer stops the run (macOS:
# caffeinate -i sh reproduce.sh).
set -e
PY=.venv/bin/python
. samples/names.sh
HELD_OUT=device_960_start,device_after_moves

# The playing area: measure the frame search against the hand-made labels.
$PY find_board_frame.py --images samples/frame_test --out out/frame

# Stage 1: pretraining on artificial windows made from the videos of the pieces.
rm -rf data/cutouts data/synth runs/pretrain
$PY extract_piece_cutouts.py --videos samples/pieces --map samples/pieces/videos.csv --out data/cutouts --step 2
$PY synthesize_pieces.py --cutouts data/cutouts --boards samples/frame_test --empty-boards $EMPTY_BOARDS --out data/synth
$PY pretrain_on_videos.py --data data/synth --out runs/pretrain

# The artificial windows mixed into fine-tuning: from the photos of the real pieces.
rm -rf data/synth_photo
$PY synthesize_pieces.py --photo-cutouts data/photo_cutouts_45cm_v2_toned \
    --pieces samples/collector/collection/pieces/Pegasus \
    --boards samples/frame_test --empty-boards $EMPTY_BOARDS --out data/synth_photo --max-blur 2.0

# Stage 2: composed boards and real photos of starting positions.
rm -rf runs/composed_45cm_and_real_falling_new_replay
$PY finetune_on_boards.py --manifest data/composed_45cm_and_real.jsonl --hold-out $HELD_OUT \
    --checkpoint runs/pretrain/checkpoints/best.pt --replay data/synth_photo/synth_train \
    --lr-falls --keep-epochs --out runs/composed_45cm_and_real_falling_new_replay

# Stage 3: continued with the collector app's photos of played positions (surroundings 5 and 6).
$PY manifest_of_played_positions.py . runs/composed_45cm_and_real_falling_new_replay/checkpoints/best.pt data/played.jsonl 5 6
cat data/composed_45cm_and_real.jsonl data/played.jsonl > data/composed_45cm_real_and_played.jsonl
rm -rf runs/new_replay_plus_played
$PY finetune_on_boards.py --manifest data/composed_45cm_real_and_played.jsonl --hold-out $HELD_OUT \
    --checkpoint runs/composed_45cm_and_real_falling_new_replay/checkpoints/best.pt \
    --replay data/synth_photo/synth_train --epochs 5 --lr-falls --keep-epochs --out runs/new_replay_plus_played

# Export for ONNX Runtime, and the measurements of EVALUATION.md on photos that were not trained on.
$PY export_models.py --checkpoint runs/new_replay_plus_played/checkpoints/best.pt --out runs/new_replay_plus_played/export
$PY evaluation/measure_on_app_records.py . samples/device/board-photos $RECORDS_A runs/new_replay_plus_played/checkpoints/best.pt
$PY evaluation/measure_on_app_records.py . samples/device/board-photos $RECORDS_B runs/new_replay_plus_played/checkpoints/best.pt
$PY evaluation/measure_with_moved_frames.py . runs/new_replay_plus_played/checkpoints/best.pt

echo "the network for the app: runs/new_replay_plus_played/export/piece_recogniser.fp16.onnx"
echo "  -> ../app/src/main/assets/vision/piece_recogniser.onnx"
