# Piece recogniser training

This document describes how the **piece recogniser** was trained: the network
that says which piece stands on a square in a photo of a DGT Pegasus. MoveAPiece
uses it to take a position over from a board that senses only which squares are
occupied, by photographing it.

Only the exported **ONNX** network is in the repository
(`app/src/main/assets/vision/piece_recogniser.onnx`). Photos, videos, generated
images and checkpoints are **not redistributed**; where a step cannot be
repeated from the repository alone, this document says so.

Related: [EVALUATION.md](EVALUATION.md) (how the chain is measured and what it
scores), [BOARD_VISION_PROVENANCE.md](../BOARD_VISION_PROVENANCE.md) (everything
that went into the shipped network, with checksums).

---

## 1) The chain the network is part of

A photo is read in four steps; only the third uses a network.

1. **Playing area from the frame of the board** (`find_board_frame.py`, in the
   apps `BoardFrameFinder`). The Pegasus has a black frame and a thin white line
   between frame and playing area. The edge where the two meet is found as four
   straight lines; the result has to be the picture of a square, and the 8×8
   squares have to fit inside. No network.
2. **One window per square** (`piece_windows.py`, `PieceWindows`): 224 × 224
   pixels, 2.6 squares across, turned so that a piece on the square stands
   upright with its foot at a fixed place (half way across, 70 % down). The
   direction comes from the camera pose, which the four corners give.
3. **The piece recogniser** (`piece_model.py`, `BoardPhotoRecognizer`): 13
   classes for every window - empty, and the six kinds of pieces in white and
   black.
4. **Occupancy from the board and the usual numbers of pieces** (`decode.py`,
   `PositionDecoder`). Empty squares come from the board. Where White sits
   follows from comparing the occupancy seen in the photo with the reported one.
   On the occupied squares each side gets at most the pieces a game starts with,
   handed out so that it fits all squares at once.

In the app every window is read twice, as it is and with what stands left and
right of the piece greyed out, and the two answers are averaged; several photos
of one position can be laid on each other before step 4.

---

## 2) Model

- **Architecture**: MobileNetV3-Small (torchvision), the last layer replaced by
  one with 13 outputs. 1,531,181 parameters.
- **Input** `input`: float32 `[N, 3, 224, 224]`, RGB, 0..1. The ImageNet
  mean/std normalisation is part of the network.
- **Output** `logits`: float32 `[N, 13]`, classes in the order of
  `squares.CLASSES` (`empty`, `white_pawn`, `white_knight`, `white_bishop`,
  `white_rook`, `white_queen`, `white_king`, then the same in black).
- **Start weights**: torchvision's ImageNet weights `IMAGENET1K_V1`.
- **Shipped form**: ONNX opset 17, weights as float16 with float32 in and out,
  3.15 MB. Hardswish and Hardsigmoid are written out with basic operations (see
  section 8).

The network knows the pieces the Pegasus comes with. Another set of pieces needs
its own photos and its own training.

---

## 3) Training strategy

Nothing is labelled by hand. The network is trained in three stages:

```
pretraining on artificial windows
   -> fine-tuning on composed boards + real photos of starting positions
      -> continued with real photos of played positions
```

What was learned on the way, and is the reason for how it is done:

1. **Real photos of whole boards matter most.** A recogniser trained on composed
   boards alone reads about three times as many squares wrong as one that also
   saw real photos (10 % against 3-5 % on the photos of section 3 of
   EVALUATION.md).
2. **Photos of played positions matter.** Starting positions alone teach the
   network where a piece usually stands and which pieces stand beside which;
   adding 234 real photos of positions out of games and of endings took the
   squares read wrong from 3.6 % to 2.9 % on photos it had never seen.
3. **Artificial single-piece windows are mixed into every training step**
   ("replay"). Leaving them out makes the network worse wherever a photo is not
   like the training photos (14 % against 6 % outside the camera window). What
   they are made of matters: made from photos of the real pieces instead of
   inverted video frames they gave the clearest single gain.
4. **The learning rate falls to nothing along a cosine.** With a constant rate
   the result changed from epoch to epoch by more than the differences that were
   to be measured.
5. **Not everything helps.** Tried without a proven gain: more composed boards,
   positions of played games and other backgrounds for them, pieces changing
   places, pieces with their inside smoothed away, evening out the kinds of
   pieces in the loss, and pretraining on the new artificial windows.

---

## 4) Training data

All of it our own, taken with a phone. No
public dataset is used. The pieces are those the DGT Pegasus comes with; the
measures (square 40 mm; foot of king 30, queen 27, rook, bishop and knight 24,
pawn 20 mm) put the pieces into the right proportion to the board.

| What | Amount | Used for |
|------|--------|----------|
| Videos of the six black pieces, one each, on a light surface (`samples/pieces/`) | 6 × about 14 s, 4K | cut-outs for the artificial windows of the pretraining |
| Photos with hand-labelled corners (`samples/frame_test/`) | 57 (55 labelled) | measuring the frame search; two of them show the empty board and are backgrounds of the artificial windows |
| The starting position, from all four sides, on a floor, a hob and a table (`samples/start/`) | 83, of which 17 in the training | fine-tuning |
| Chess960 starting positions 165, 872, 274, 270, 490, 295, from both sides (`samples/sp*/`) | 95 | fine-tuning |
| On a desk: the starting position, Chess960 position `BNRKRBNQ`, the same game after five moves (`samples/desk_*/`) | 11 | fine-tuning |
| Photos of the single pieces with the collector app, each from four sides at eleven angles (`samples/collector/collection/pieces/`) | 528 per set of photos: 44 of each of the 12 pieces | cut-outs for the composed boards and for the artificial windows mixed into fine-tuning |
| Photos of the empty board with the collector app, in three surroundings (`.../boards/Pegasus/surroundings_1..3/empty_00/`) | 135, of which 117 usable | backgrounds of the composed boards |
| Photos and short films of positions out of games and of endings with the collector app, in two surroundings (`.../surroundings_5`, `_6`) | 234 photos of 7 positions | the last stage |

Positions come from the Chess960 number, from the diagram the collector app
shows, or are made up; where White sits and the playing area are found by the
scripts.

### The collector app

`collector/` is a small Android app that takes these photos (`./gradlew
-Pcollector :board-photo-collector:assembleRelease`; never part of a release).
For single pieces it files each photo under the tilt of the phone and takes a
series by itself, one photo per angle, side by side. For boards it shows a
position to set up, finds the board in the live picture, tells how to hold the
phone and files the photo with the position; it can also take a short film,
twelve photos one after the other.

White pieces are photographed three stops darker than the camera would choose:
on the dark mat it otherwise burns them out.

---

## 5) Data preparation

Paths are relative to `training/`; `samples/`, `data/` and `runs/` are not in
the repository.

### Artificial windows for the pretraining

```bash
python extract_piece_cutouts.py --videos samples/pieces --map samples/pieces/videos.csv \
    --out data/cutouts --step 2
python synthesize_pieces.py --cutouts data/cutouts --boards samples/frame_test \
    --empty-boards "$EMPTY_BOARDS" --out data/synth
```

1,221 cut-outs of the six black pieces; white pieces are made by inverting
them. 1,500 windows per class and 3,000 empty ones, each with a piece standing
upright in front of a board (photographed or drawn) or a plain surface, other
pieces around it, a soft shadow, varying light and blur.

### Cut-outs of the piece photos

```bash
python cut_out_piece_photos.py --photos samples/collector/collection/pieces/Pegasus \
    --out data/photo_cutouts_45cm_v2 --model birefnet-general-lite --window 0.5 --work-size 4032
python match_cutout_tone.py --cutouts data/photo_cutouts_45cm_v2 --like data/photo_cutouts \
    --out data/photo_cutouts_45cm_v2_toned --each-photo
```

The pieces are cut out of their background by a network for that purpose
([rembg](https://github.com/danielgatis/rembg) with the BiRefNet model
`birefnet-general-lite`; `pip install rembg onnxruntime`, not in
`requirements.txt`). Each cut-out keeps the angle it was seen from, corrected by
where the foot stood in the picture. `match_cutout_tone.py` brings brightness
and colour cast of one series of photos to those of another; `data/photo_cutouts`
are the cut-outs of the very first series, taken from 12 cm.

### Artificial windows mixed into fine-tuning

```bash
python synthesize_pieces.py --photo-cutouts data/photo_cutouts_45cm_v2_toned \
    --pieces samples/collector/collection/pieces/Pegasus --boards samples/frame_test \
    --empty-boards "$EMPTY_BOARDS" \
    --out data/synth_photo --max-blur 2.0
```

The same kind of windows, but from the photos of the real pieces of both
colours, all pieces of a window seen from about the same angle, blurred less.

### Composed boards

```bash
python compose_boards.py --cutouts data/photo_cutouts_45cm_toned \
    --boards samples/collector/collection/boards/Pegasus \
    --pieces samples/collector/collection/pieces/Pegasus --out data/composed_45cm --count 400 \
    --tone-white 1.31,1.30,1.34 --tone-black 0.94,1.05,1.35
```

Cut-outs pasted onto photos of the empty board: for every square the cut-out
whose angle is nearest to the one the square is seen from, at the right size,
leaning as the camera sees it, far pieces first, with a soft shadow. 400 boards
with made-up positions (the back rank mixed, so that the network does not learn
which piece to expect on which square).

**The 400 composed boards of the shipped network cannot be made again from the
repository.** They were made with the first series of piece
photos, whose white pieces were overexposed, and by a version of
`compose_boards.py` that pasted the pieces with red and blue swapped (found and
fixed later). Later sets made without these flaws did not give a better
network once real photos were part of the training, so the set was kept. The
images are part of the saved data (section 10).

### Manifests

A manifest is a text file with one line per photo: image, position, where White
sits, the four corners of the playing area, the series it belongs to.

```bash
# real photos of starting positions: position known, the rest found
python label_photos.py --photos samples/start --series start \
    --model runs/pretrain/checkpoints/best.pt --manifest samples/boards_v2.jsonl
python label_photos.py --photos samples/sp165 --chess960 165 --model ... --manifest ...

# real photos of played positions from the collector app, films included
python manifest_of_played_positions.py . <a recogniser> data/played.jsonl 5 6
```

The training manifests are the lines of several of these one after the other:
`data/composed_45cm_and_real.jsonl` (the 400 composed boards and 126 real
photos, 3 of them held out) and `data/composed_45cm_real_and_played.jsonl` (the
same and the 234 photos of played positions). Which of the 83 photos of the
starting position went into `boards_v2.jsonl` was not recorded; the file is part
of the saved data.

---

## 6) Training

Environment: Python 3.12, `pip install -r requirements.txt` (the shipped network
was trained with torch 2.15.0 and torchvision 0.29.2 on a Mac, device
`mps`).

### Stage 1: pretraining on artificial windows

```bash
python pretrain_on_videos.py --data data/synth --out runs/pretrain
```

Two epochs for the new last layer on the frozen ImageNet backbone (Adam,
learning rate 1e-3), then 14 epochs for the whole network (1e-4), batches of 64.
About 15 minutes. The result knows the shapes of the pieces but reads real
photos badly (28 % of the squares wrong); it is only where the next stage
starts.

### Stage 2: composed boards and real photos of starting positions

```bash
python finetune_on_boards.py --manifest data/composed_45cm_and_real.jsonl \
    --hold-out device_960_start,device_after_moves \
    --checkpoint runs/pretrain/checkpoints/best.pt \
    --replay data/synth_photo/synth_train --lr-falls --keep-epochs \
    --out runs/composed_45cm_and_real_falling_new_replay
```

523 photos, 33,472 windows. 15 epochs, Adam, learning rate 1e-4 falling to
nothing along a cosine; batches of 32 windows of photos and 32 artificial
windows. About 85 minutes.

### Stage 3: continued with real photos of played positions

```bash
python finetune_on_boards.py --manifest data/composed_45cm_real_and_played.jsonl \
    --hold-out device_960_start,device_after_moves \
    --checkpoint runs/composed_45cm_and_real_falling_new_replay/checkpoints/best.pt \
    --replay data/synth_photo/synth_train --epochs 5 --lr-falls --keep-epochs \
    --out runs/new_replay_plus_played
```

757 photos, 5 epochs, otherwise as stage 2. About 40 minutes. This is the
shipped network.

### What the fine-tuning does to the windows

- mirrored at random, turned by up to 8°, moved by up to 4 % and scaled by
  ±10 %, colours jittered, blurred in half of the cases;
- in 60 % of the windows what stands left and right of the piece is greyed out,
  so that the neighbours do not give the piece away;
- the loss weighs a class by one over the root of how often it occurs: half of
  all squares are empty, and pawns outnumber queens.

### Outputs

`runs/<name>/checkpoints/best.pt` (the state after the last epoch),
`epoch_NN.pt` with `--keep-epochs`, `progress.txt`, and `finetune.json`, which
records what the run was made of.

---

## 7) Evaluation

See [EVALUATION.md](EVALUATION.md). In short, on 50 photos of a game photographed
move by move that the network had never seen: every photo read right by itself,
1.5 % of the squares read wrong before the usual numbers of pieces repair them.

---

## 8) Export

```bash
python export_models.py --checkpoint runs/new_replay_plus_played/checkpoints/best.pt \
    --out runs/new_replay_plus_played/export
```

Writes `piece_recogniser.onnx` (FP32, opset 17, 6.2 MB), `piece_recogniser.fp16.onnx`
(weights as float16, 3.15 MB) and `piece_recogniser.ort`, and compares their
answers with the checkpoint's on photos of a manifest (`--check`).

- **Hardswish and Hardsigmoid are written out with basic operations.** ONNX
  Runtime 1.29.0 for the JVM on macOS arm64 returned zeros for the Hardswish
  operator, and the network then answered the same for every picture.
- **ONNX Runtime version**: `requirements.txt` pins the version the apps ship
  (1.29.0), so the exported network is checked against the runtime it runs on.

---

## 9) Integration into MoveAPiece

```bash
cp runs/new_replay_plus_played/export/piece_recogniser.fp16.onnx \
    ../app/src/main/assets/vision/piece_recogniser.onnx
```

- `core`, package `de.schliweb.moveapiece.vision`: the chain in Java
  (`BoardFrameFinder`, `PieceWindows`, `BoardPhotoRecognizer`,
  `PositionDecoder`, `PhotoGuide`), run with ONNX Runtime, which the apps
  already ship for Maia.
- `app`: `BoardCameraActivity` (live picture, guidance, automatic photo) and
  `BoardPhotoFlow` (check dialog). A photo is read on the device and not kept;
  only debug builds keep the photos with what was read from them, as material
  for measuring.
- The Java chain and the Python one agree on average, not photo by photo: a
  square the network is torn about can fall either way through small
  differences in decoding and resampling.
- Tests: `ShippedRecogniserTest` runs the network in the repository on a drawn
  board and needs no photos; `BoardPhotoRecognizerTest`, `CollectorPhotosTest`
  and parts of `BoardFrameFinderTest` run only where `training/samples` holds
  the photos.

---

## 10) Reproducibility

- [`reproduce.sh`](reproduce.sh) runs the stages from the saved data to the
  exported network.
- **Not repeatable from the repository**: everything that needs the photos and
  videos; the 400 composed boards (section 5); the choice of the starting
  position photos in `boards_v2.jsonl`.
- **Bit for bit from the saved data, on the same computer**: the two
  fine-tuning stages and the export, repeated (torch 2.15.0,
  device `mps`), gave the shipped network with the same checksum. The
  pretraining has not been repeated; on other hardware or library versions
  expect a network that reads about as well, not the same file.
- The photos, videos, generated images, manifests and all runs with their
  checkpoints are kept outside the repository; the checksum of the shipped
  network and of the files it was made from are in
  [BOARD_VISION_PROVENANCE.md](../BOARD_VISION_PROVENANCE.md).

---

## 11) Scripts

| Script | What it does |
|--------|--------------|
| `find_board_frame.py` | playing area from the frame of the board; measures itself against hand-made labels |
| `piece_windows.py` | the windows around the squares |
| `piece_model.py` | the network and its use with ONNX Runtime |
| `decode.py` | position from what the network sees, the occupancy and the usual pieces |
| `recognise.py` | the whole chain for one photo: photo and occupancy in, position out |
| `extract_piece_cutouts.py` | cut-outs from the videos of the single pieces |
| `cut_out_piece_photos.py` | cut-outs from the collector app's photos of the single pieces |
| `match_cutout_tone.py` | brings the tone of one series of cut-outs to that of another |
| `synthesize_pieces.py` | artificial windows from cut-outs |
| `compose_boards.py` | composed boards from cut-outs and photos of the empty board |
| `label_photos.py` | manifest of photos whose position is known |
| `manifest_of_played_positions.py` | manifest of the collector app's photos of played positions |
| `pretrain_on_videos.py` | stage 1 |
| `finetune_on_boards.py` | stages 2 and 3 |
| `export_models.py` | export for ONNX Runtime |
| `evaluate.py` | the chain against a manifest, with pictures of what went wrong |
| `evaluation/*.py` | the measurements of EVALUATION.md |
| `squares.py`, `quad.py`, `manifest.py` | shared: classes and squares, quadrilaterals, manifests |

---

## 12) Notes

- **A sleeping computer stops the training.** Wrap long runs in `caffeinate -i`
  on macOS.
- **Keep test photos out of the training.** Surroundings 5 and 6 were test
  photos until they went into stage 3; since then only the records of the app
  and surroundings 4 are tests (EVALUATION.md).
- **Look at the pictures.** Three flaws of the training material - swapped
  colours, burnt-out white pieces, white pieces that were inverted black ones -
  were each found by looking at the windows beside real ones, not by a number.
