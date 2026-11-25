# Piece recogniser provenance

`piece_recogniser.onnx` in `app/src/main/assets/vision/` is the network that
says which piece stands on a square in a photo of a DGT Pegasus (reading a
position from a photo, Android app). This file records
everything that went into it. How it was trained is described in
[training/README.md](training/README.md), how it is measured in
[training/EVALUATION.md](training/EVALUATION.md).

## The file

- `piece_recogniser.onnx`: 3,154,640 bytes, SHA-256
  `8855683ed1a969328ae3f1749012240480f300e86d6a415c0f1d6769dfb573a1`
- MobileNetV3-Small with 13 outputs, ONNX opset 17, weights as float16
- Exported from the run `new_replay_plus_played` (checkpoint
  SHA-256 `457d4f23a9e52d4c0938227404e642f0a66bd89fb95f50aaf65ef30ecbfb3f22`)
- Licence: GPL-3.0-or-later, like the rest of this repository; start weights
  from torchvision, see below

## Rules

- Weights that are in the repository may only be trained on data with a clear,
  free licence: CC0, CC BY 4.0, our own photos and videos, our own renderings.
- Non-commercial licences are not used at all - not as training data, not as a
  teacher model, not for pseudo-labels.
- A fine-tuned network inherits the terms of everything in its lineage,
  including the weights it was initialised from.
- Photos, videos, generated images and checkpoints are never committed; only
  scripts, documentation and the exported network are.

## Lineage

| Stage | Run | Started from | Trained on | SHA-256 of the checkpoint |
|-------|-----|--------------|------------|---------------------------|
| 0 | - | - | torchvision's ImageNet weights | `047dcff4addef86ea5bc2eff13c9614dc11f47ab1160d0a71a25e7db994f4e1f` |
| 1 | `pretrain` | stage 0 | artificial windows from videos of the pieces | `aa1123401d49598aae9b254da33ccd58342a1a1c538238fa2597382462b890da` |
| 2 | `composed_45cm_and_real_falling_new_replay` | stage 1 | 400 composed boards, 123 real photos of starting positions; artificial windows from photos of the pieces mixed in | `d2562bde0898ee9e0604e052d71f1ab87a785c49c747fbc80e399ec6dcc3246e` |
| 3 | `new_replay_plus_played` | stage 2 | the same and 234 real photos of played positions | `457d4f23a9e52d4c0938227404e642f0a66bd89fb95f50aaf65ef30ecbfb3f22` |

## Data

All of it our own: photos and videos of a DGT Pegasus and its pieces, taken with
a phone. No public dataset is used. The
amounts are listed in [training/README.md](training/README.md), section 4.

Manifests of the photos the network was trained on (SHA-256):

- `data/composed_45cm_and_real.jsonl` (stage 2):
  `ad629cc4d5e2c0819cb0a88520de4f9756259e3ae1025d995f6771ee46eb1b37`
- `data/played.jsonl` (the photos added in stage 3):
  `20f80c697003bf284f1ad6dcc5d28eb2fe11c1d175a21c62458f625704d259c0`
- `data/composed_45cm_real_and_played.jsonl` (stage 3):
  `aa37ff1441a30eabf7eee97a93c20dd6932650fb911b3f005aadf995def4fc54`

The positions of the composed boards are made up; no positions of games, no
puzzle database and no other backgrounds than our own photos of the empty board
went into this network.

## Models and tools from others

| What | Source | Licence | Used for |
|------|--------|---------|----------|
| MobileNetV3-Small, ImageNet weights `IMAGENET1K_V1` (`mobilenet_v3_small-047dcff4.pth`, 10.3 MB) | [torchvision](https://github.com/pytorch/vision) 0.29.2, `https://download.pytorch.org/models/mobilenet_v3_small-047dcff4.pth` | torchvision: BSD-3-Clause; the weights were trained on ImageNet-1k | start weights (stage 0) |
| BiRefNet, model `birefnet-general-lite`, run through [rembg](https://github.com/danielgatis/rembg) | <https://github.com/ZhengPeng7/BiRefNet> | MIT (both) | cutting the single pieces out of their photos; a tool, its weights are not part of the network |

**The start weights:** the torchvision code is BSD-3-Clause; its weights were
trained on ImageNet-1k, whose images are not freely licensed. Shipping networks
initialised from such weights is common practice, and this network is shipped
that way by decision; torchvision is named in
[THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md) and
[OPEN-SOURCE-LICENSES.md](OPEN-SOURCE-LICENSES.md). Training from random weights
(`pretrain_on_videos.py --no-pretrained`) has not been tried.

## Methods taken from others

| What | Source | Where |
|------|--------|-------|
| Adapting a recogniser to a chess set with photos of known positions that need no labelling | Wölflein and Arandjelović, "Determining Chess Game State From an Image", J. Imaging 7(6):94, 2021, <https://arxiv.org/abs/2104.14963> (idea only, no code taken) | `training/finetune_on_boards.py`, `training/label_photos.py` |

No third-party code is included. The playing area is found without a network.

## Not covered

- The exact 400 composed boards and the choice of starting-position photos
  cannot be made again from the repository (training/README.md, section 5); they
  are part of the data kept outside it.
- The two fine-tuning stages and the export were repeated from the
  saved data, on the computer of the first run (torch 2.15.0, device `mps`),
  and gave this file bit for bit. The pretraining has not been repeated, and on
  other hardware or library versions a run may give a network that reads about
  as well, not this file.
