# Maia960 eval: summary

Generated from games.csv by `Summary`; see REPORT.md for the discussion.

## Parameters

| Parameter | Value |
|---|---|
| Seed (positions, sampling) | 960 |
| Chess960 positions | 345 664 307 507 403 702 901 485 632 737 651 343 529 425 595 939 898 759 822 350 108 415 714 603 946 515 429 226 281 845 322 469 201 200 120 607 176 220 386 774 804 691 699 619 621 913 462 138 133 676 |
| Maia ratings | [1400, 1700, 2000, 2300] |
| Stockfish UCI_Elo | [1400, 1800, 2200] |
| Maia vs Maia | 1700 vs 1700, 1400 vs 2000 |
| Stockfish movetime | 100 ms |
| Maia's sampled opening moves per side | none |
| Ply limit (draw) | 300 |
| Adjudication | |eval| > 1000 cp, same side, 10 plies in a row |
| Referee | Stockfish full strength, depth 12, Threads 1, Hash 16 |
| Blunder | loss > 200 cp, evals capped at ±1000 cp |
| Chess960 castle scoring | app (king:1.0) |
| Workers | 9 |
| Bootstrap | 2000 resamples of position pairs, seed 4242 |

Games in games.csv: 1200, of them without a result (error): 0.

## Maia against Stockfish

Score and Elo difference from Maia's view, 95 % CI from the position pairs.

| Maia | SF | Variant | Games | +/=/- | Score | Elo diff [95 % CI] |
|---|---|---|---|---|---|---|
| 1400 | 1400 | std | 100 | 78/4/18 | 80.0 % | +241 [+172, +332] |
| 1400 | 1800 | std | 100 | 22/5/73 | 24.5 % | -196 [-290, -122] |
| 1400 | 2200 | std | 100 | 9/3/88 | 10.5 % | -372 [-522, -284] |
| 1700 | 1400 | std | 100 | 84/4/12 | 86.0 % | +315 [+241, +423] |
| 1700 | 1800 | std | 100 | 32/9/59 | 36.5 % | -96 [-168, -31] |
| 1700 | 2200 | std | 100 | 16/9/75 | 20.5 % | -235 [-340, -159] |
| 2000 | 1400 | std | 100 | 90/3/7 | 91.5 % | +413 [+323, +575] |
| 2000 | 1800 | std | 100 | 35/4/61 | 37.0 % | -92 [-171, -22] |
| 2000 | 2200 | std | 100 | 11/11/78 | 16.5 % | -282 [-385, -208] |
| 2300 | 1400 | std | 100 | 88/5/7 | 90.5 % | +392 [+296, +572] |
| 2300 | 1800 | std | 100 | 41/11/48 | 46.5 % | -24 [-83, +33] |
| 2300 | 2200 | std | 100 | 26/6/68 | 29.0 % | -156 [-248, -80] |

## Elo shift Chess960 minus standard per Maia level

Performance rating against the three Stockfish levels together (on Stockfish's nominal UCI_Elo scale), per variant; shift with a bootstrap 95 % CI.

| Maia | Perf. standard | Perf. 960 | Shift 960 − std [95 % CI] |
|---|---|---|---|

Shift per pairing (Elo diff 960 − standard, bootstrap 95 % CI; scores of 0 or 100 % are clamped to half a game):

| Maia | SF | Shift [95 % CI] |
|---|---|---|

## Maia against Maia

Score of the higher-rated Maia; nominal gap 600.

| Pairing | Variant | Games | +/=/- | Score | Elo diff [95 % CI] |
|---|---|---|---|---|---|

| Pairing | Score 960 − std | Elo gap 960 − std [95 % CI] |
|---|---|---|

## Castling

Share of games in which a side castled, and the mean move number (full moves) of the castle. Stockfish's own sides for comparison.

| Player | Mode | Std: castled | Std: mean move | 960: castled | 960: mean move |
|---|---|---|---|---|---|
| Maia 1400 | mvs | 91 % (274/300) | 8.7 | – | – |
| Maia 1700 | mvs | 94 % (283/300) | 8.9 | – | – |
| Maia 2000 | mvs | 97 % (292/300) | 8.3 | – | – |
| Maia 2300 | mvs | 94 % (282/300) | 7.3 | – | – |
| Stockfish 1400 | mvs | 58 % (232/400) | 11.1 | – | – |
| Stockfish 1800 | mvs | 67 % (266/400) | 11.2 | – | – |
| Stockfish 2200 | mvs | 67 % (269/400) | 11.9 | – | – |

## Blunders

Maia moves losing more than 200 cp by the referee, per 100 Maia moves (the first 0 sampled moves per side included).

| Maia | Mode | Std | 960 |
|---|---|---|---|
| 1400 | mvs | 5.57 (687/12334) | – |
| 1700 | mvs | 4.94 (623/12609) | – |
| 2000 | mvs | 4.85 (614/12668) | – |
| 2300 | mvs | 4.50 (602/13385) | – |

## Mapping diagnostics

| Variant | Maia moves | Raw top slot not a legal move (informational) | Positions with two legal moves on one slot | Anomalies (wrong castle, illegal move, diagnostic mismatch) |
|---|---|---|---|---|
| std | 50996 | 38341 (75.18 %) | 0 | 0 |
| 960 | 0 | 0 (0.00 %) | 0 | 0 |

Every case is in events.tsv with its FEN, of the raw top slot only the first 50.

## Game ends

| Mode, variant | Mean plies | Endings |
|---|---|---|
| mvs std | 85 | {adjudication=577, checkmate=549, insufficient-material=11, ply-limit=1, repetition=43, stalemate=19} |

## Sampled opening moves

Loss by the referee (eval before minus after, mover's view, capped at ±1000 cp).

| Maia | Sampled moves | Loss > 100 cp | Loss > 200 cp |
|---|---|---|---|

The 20 worst:

| Loss | Maia | Move | FEN | Game, ply |
|---|---|---|---|---|

