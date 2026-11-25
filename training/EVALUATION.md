# Evaluation and the separation of test photos

How reading a position from a photo is measured, on which photos, and what the
shipped recogniser scores. For how it was trained see [README.md](README.md).

---

## 1) Core rule: test photos are never trained on

A recogniser is judged only on photos it has not seen in training - other
positions, and where possible other places and light.

- Photos that have gone into a training are no test any more, for this and for
  every later recogniser. Surroundings 5 and 6 of the collector app were test
  photos at first; since they are part of the training they only show
  that the chain works.
- A setting that is chosen by looking at a set of photos is too good on that
  set. Say so where it happens.
- "Confirmed by the user" is not "checked": a record of the app counts as right
  when the user accepted what was read. Look at the photos before trusting a set
  of records (one record of set A was confirmed wrong and is marked).

---

## 2) What is measured

### Squares read wrong alone

For every occupied square: is the piece the network holds most likely the one
standing there - **before** the usual numbers of pieces are kept. Given as a
share of the occupied squares. This is the number that describes the network.

### Boards right, wrong squares

After the usual numbers of pieces are kept: how many squares differ from the
position, and how many photos are read right altogether. This is what the user
sees, but it describes the network badly:

- With all 32 pieces on the board the numbers repair nearly every single
  mistake: a pawn read as a bishop would be a third bishop. On the starting
  position a recogniser that reads 9 % of the squares wrong still gets every
  board right.
- In a played position pieces are missing, "the pawn is a bishop" is allowed,
  and the mistake stays.
- Because the numbers are kept, one misread piece mostly shows as two wrong
  squares.

Compare recognisers by the squares read wrong alone; give boards right beside
it.

### How sure a difference is

The sets are small, and a recogniser that is sure of a piece and wrong often
reads it right when the frame lies a twentieth of a square elsewhere. Two or
three boards between two recognisers say nothing. The scripts therefore give,
for every recogniser after the first, the difference to the first with the range
it has when the photos are drawn anew (bootstrap, 90 in a hundred); if the range
holds zero, the photos do not tell the two apart.

---

## 3) Test photos

None of them is in the repository. Paths are relative to `training/`.

| Set | What | Photos |
|-----|------|--------|
| App records A (`samples/device/board-photos`, the records whose names begin with `$RECORDS_A`) | 12 confirmed positions - starting position, openings, a scattered position - at three places, one to nine photos each, part of them taken by button outside the camera window | 53 |
| App records B (the same folder, `$RECORDS_B`) | 25 confirmed positions: one game photographed move by move on a wooden table in daylight | 50 |
| Surroundings 4 of the collector app (`samples/collector/collection/boards/Pegasus/surroundings_4`) | the starting position and two Chess960 starting positions, eight photos each at 50-70° | 24 |
| Device records whose position is one of seven known ones (`samples/device/board-photos`) | records of the first versions of the app, in three groups: inside the camera window (50-70°, the board on a quarter of the picture or more), outside it, and taken before the app showed the angle (nearly level) | 47 |

A record is named after the moment it was taken; which records make up the
sets A and B, and which photos show the empty board, is written in
`samples/names.sh`, which like the photos is not in the repository.

An app record is a folder with the photos of one position (`photo.jpg`,
`photo_2.jpg`, ...) and `info.json`: what the board reported as occupied, what
was read, and the position as the user confirmed it.

---

## 4) Scripts

All take the folder of the scripts (`.`) first, then photos, then one or more
recognisers (`.pt` checkpoints or `.onnx`).

```bash
# the confirmed records of the app, read as the app reads them
python evaluation/measure_on_app_records.py . samples/device/board-photos $RECORDS_B \
    runs/new_replay_plus_played/checkpoints/best.pt runs/other/checkpoints/best.pt

# one record photo by photo, with the squares read wrong
python evaluation/read_app_records.py samples/device/board-photos \
    runs/new_replay_plus_played/checkpoints/best.pt <record>

# every photo read nine times with the frame moved a little: device records and collector photos
python evaluation/measure_with_moved_frames.py . runs/a/checkpoints/best.pt runs/b/checkpoints/best.pt

# boards right and the squares misread, by position
python evaluation/measure_on_collector_photos.py . \
    samples/collector/collection/boards/Pegasus/surroundings_4 runs/a/checkpoints/best.pt
python evaluation/measure_on_device_records.py . runs/a/checkpoints/best.pt
```

`measure_on_app_records.py` reads as the app does: every window as it is and
with its neighbours hidden, averaged; the photos of a record laid on each other.
The others read every photo once, as it is.

---

## 5) Results of the shipped recogniser

`runs/new_replay_plus_played`.

### App records

| Recogniser | A: photos right alone | wrong squares | positions right, photos together | squares read wrong alone | B: photos right alone | wrong squares | positions right | squares read wrong alone |
|---|---|---|---|---|---|---|---|---|
| **shipped** (`new_replay_plus_played`) | 45 / 53 | 12 | 10 / 12 | 2.9 % | 50 / 50 | 0 | 25 / 25 | 1.5 % |
| before its last stage (`..._falling_new_replay`) | 42 | 16 | 10 | 3.6 % | 41 | 24 | 22 | 3.6 % |
| first with real photos (`composed_and_real`) | 33 | 32 | 9 | 5.8 % | 39 | 31 | 19 | 4.4 % |
| best without real board photos (`composed_800`) | 40 | 30 | 10 | 10.0 % | 30 | 59 | 15 | 10.5 % |

Every difference to the shipped recogniser in the squares read wrong alone is
told apart by the photos. The numbers are those of the checkpoints; the exported
network with its weights as float16 reads the photos of set B the same
(50 of 50) with 1.7 % of the squares wrong alone.

In set B, 23 of the 25 positions were confirmed as read, so a mistake of
the shipped recogniser that the user overlooked would count as right there; two
of the positions were checked against the photos piece by piece and are right.
The other two were changed by the user, both times because the board reported a
square as occupied that a pawn had left - not a misread piece.

### Photos read nine times with the frame moved

Squares read wrong alone:

| Group | shipped | before its last stage |
|---|---|---|
| device records inside the camera window (17) | 1.6 % | 2.0 % |
| surroundings 4 (24) | 2.1 % | 3.3 % |
| device records with a known angle, outside the window (14) | 3.8 % | 5.6 % |
| device records before the angle was shown (16) | 9.5 % | 10.2 % |

The first three differences are told apart, the last is not. On surroundings 4
both read all 24 boards right. (Records of the app that
show one of the known positions join the three groups of device records, so
their counts grow as the app is used.)

### What the other stages are worth

Squares read wrong alone on the app records A, all runs measured
with the same script:

| Recogniser | |
|---|---|
| pretraining alone | 27.9 % |
| real photos of starting positions alone | 6.7 % |
| composed boards alone, at best | 10.0 % |
| composed boards and real photos of starting positions | 4.8 % |
| the same, learning rate falling | 3.9 % |
| the same, without the artificial windows mixed in | 4.9 % (14.2 % outside the camera window, against 5.8 %) |
| the same, without pretraining | 5.2 % |
| the same, artificial windows from photos of the real pieces | 3.6 % |
| continued with real photos of played positions (shipped) | 2.9 % |

---

## 6) The chain in the app

The numbers above come from the Python chain. The Java chain of the app is
checked against photos by tests of `core` that run where `training/samples`
holds the photos:

```bash
./gradlew :core:test --tests "de.schliweb.moveapiece.vision.*" -i
```

- `BoardFrameFinderTest`: the playing area of 55 hand-labelled photos, within a
  tenth of a square; boards whose frame glares.
- `BoardPhotoRecognizerTest`, `CollectorPhotosTest`: positions read from photos,
  one photo alone and several together.
- `ShippedRecogniserTest`: the network in the repository on a drawn board; needs
  no photos.

Java and Python agree on average, not photo by photo. On the 37 single photos
of surroundings 6 an earlier recogniser read 25 boards right in Python and 23
in Java; on one record the two differed in a square the network held at about
one half, which fell one way or the other with a corner of the frame found six
pixels apart, the decoder of the JPEG and the shrinking of the photo.

On the device a photo takes about two seconds on a mid-range phone in a build that is
not debuggable (`-PnotDebuggable`): playing area 0.3-0.5 s, windows 0.1 s, the
network 1.6 s for both readings of the 64 windows. In a debuggable build the
same code runs many times slower.

---

## 7) Known weaknesses

- **Outside the camera window.** Flatter than 50° pieces hide each other,
  steeper than about 65° their tops look alike, and a board that fills less than
  a quarter of the picture is too small. The camera screen guides into the
  window, but the button works outside it too.
- **Bishop, rook and king**, above all the white ones, are what is mixed up
  where anything is: a bishop seen from behind, where its slit does not show,
  read as a pawn; a king as a rook or bishop.
- **Sure and wrong.** The network is often certain of a piece it reads wrong.
  The squares it is less sure of than 0.7 hold about half of the wrong ones,
  and nine in ten of them are right. The app therefore marks no square in the
  position it shows for checking: a mark would make the others look checked.
- **The frame of the board.** The playing area is not found when the black
  frame does not stand out from what the board lies on (a black hob against the
  light), and less exactly where the sun glares on the frame.
- **Small sets.** All of this rests on a few dozen positions at a handful of
  places, with one board, one set of pieces and one phone.

---

## 8) Summary

1. Never judge a recogniser on photos that were trained on.
2. Compare by the squares read wrong before the usual numbers of pieces are
   kept; give boards right beside it.
3. Give the range a difference has; a few boards between two recognisers say
   nothing.
4. Look at the photos of what was read wrong before drawing conclusions.
