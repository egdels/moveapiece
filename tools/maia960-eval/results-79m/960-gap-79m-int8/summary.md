# Maia960 eval: summary

Generated from games.csv by `Summary`; see REPORT.md for the discussion.

## Parameters

| Parameter | Value |
|---|---|
| Seed (positions, sampling) | 960 |
| Chess960 positions | 345 664 307 507 403 702 901 485 632 737 651 343 529 425 595 939 898 759 822 350 108 415 714 603 946 515 429 226 281 845 322 469 201 200 120 607 176 220 386 774 804 691 699 619 621 913 462 138 133 676 |
| Maia ratings | [1400, 1700, 2000, 2300] |
| Stockfish UCI_Elo | [1400, 1800, 2200] |
| Maia vs Maia | 1400 vs 2000, 1700 vs 2300 |
| Stockfish movetime | 100 ms |
| Maia's sampled opening moves per side | 2 at temperature 1.0 |
| Ply limit (draw) | 300 |
| Adjudication | |eval| > 1000 cp, same side, 10 plies in a row |
| Referee | Stockfish full strength, depth 12, Threads 1, Hash 16 |
| Blunder | loss > 200 cp, evals capped at ±1000 cp |
| Chess960 castle scoring | app (king:1.0) |
| Workers | 9 |
| Bootstrap | 2000 resamples of position pairs, seed 4242 |

Games in games.csv: 2400, of them without a result (error): 0.

## Maia against Stockfish

Score and Elo difference from Maia's view, 95 % CI from the position pairs.

| Maia | SF | Variant | Games | +/=/- | Score | Elo diff [95 % CI] |
|---|---|---|---|---|---|---|
| 1400 | 1400 | std | 100 | 75/4/21 | 77.0 % | +210 [+141, +298] |
| 1400 | 1400 | 960 | 100 | 29/1/70 | 29.5 % | -151 [-229, -86] |
| 1400 | 1800 | std | 100 | 12/11/77 | 17.5 % | -269 [-375, -194] |
| 1400 | 1800 | 960 | 100 | 5/0/95 | 5.0 % | -512 [-837, -398] |
| 1400 | 2200 | std | 100 | 10/8/82 | 14.0 % | -315 [-450, -230] |
| 1400 | 2200 | 960 | 100 | 0/1/99 | 0.5 % | < -920 [< -920, -729] |
| 1700 | 1400 | std | 100 | 78/5/17 | 80.5 % | +246 [+172, +347] |
| 1700 | 1400 | 960 | 100 | 54/6/40 | 57.0 % | +49 [-17, +118] |
| 1700 | 1800 | std | 100 | 31/10/59 | 36.0 % | -100 [-169, -38] |
| 1700 | 1800 | 960 | 100 | 7/4/89 | 9.0 % | -402 [-570, -310] |
| 1700 | 2200 | std | 100 | 13/5/82 | 15.5 % | -295 [-409, -216] |
| 1700 | 2200 | 960 | 100 | 3/0/97 | 3.0 % | -604 [< -920, -468] |
| 2000 | 1400 | std | 100 | 90/3/7 | 91.5 % | +413 [+312, +617] |
| 2000 | 1400 | 960 | 100 | 58/2/40 | 59.0 % | +63 [+0, +131] |
| 2000 | 1800 | std | 100 | 39/12/49 | 45.0 % | -35 [-109, +36] |
| 2000 | 1800 | 960 | 100 | 17/1/82 | 17.5 % | -269 [-375, -194] |
| 2000 | 2200 | std | 100 | 25/13/62 | 31.5 % | -135 [-209, -71] |
| 2000 | 2200 | 960 | 100 | 5/3/92 | 6.5 % | -463 [-666, -365] |
| 2300 | 1400 | std | 100 | 93/2/5 | 94.0 % | +478 [+375, +708] |
| 2300 | 1400 | 960 | 100 | 72/1/27 | 72.5 % | +168 [+94, +261] |
| 2300 | 1800 | std | 100 | 58/12/30 | 64.0 % | +100 [+41, +166] |
| 2300 | 1800 | 960 | 100 | 33/5/62 | 35.5 % | -104 [-185, -32] |
| 2300 | 2200 | std | 100 | 37/6/57 | 40.0 % | -70 [-140, -6] |
| 2300 | 2200 | 960 | 100 | 12/7/81 | 15.5 % | -295 [-397, -222] |

## Elo shift Chess960 minus standard per Maia level

Performance rating against the three Stockfish levels together (on Stockfish's nominal UCI_Elo scale), per variant; shift with a bootstrap 95 % CI.

| Maia | Perf. standard | Perf. 960 | Shift 960 − std [95 % CI] |
|---|---|---|---|
| 1400 | 1624 | 1256 | -368 [-460, -284] |
| 1700 | 1724 | 1444 | -280 [-361, -203] |
| 2000 | 1876 | 1510 | -366 [-450, -285] |
| 2300 | 2005 | 1688 | -316 [-398, -231] |

Shift per pairing (Elo diff 960 − standard, bootstrap 95 % CI; scores of 0 or 100 % are clamped to half a game):

| Maia | SF | Shift [95 % CI] |
|---|---|---|
| 1400 | 1400 | -361 [-470, -267] |
| 1400 | 1800 | -242 [-504, -82] |
| 1400 | 2200 | -604 [-684, -355] |
| 1700 | 1400 | -197 [-315, -98] |
| 1700 | 1800 | -302 [-457, -190] |
| 1700 | 2200 | -309 [-611, -120] |
| 2000 | 1400 | -350 [-525, -226] |
| 2000 | 1800 | -235 [-354, -124] |
| 2000 | 2200 | -328 [-529, -200] |
| 2300 | 1400 | -310 [-533, -167] |
| 2300 | 1800 | -204 [-303, -113] |
| 2300 | 2200 | -224 [-339, -125] |

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
| Maia 1400 | mvs | 91 % (272/300) | 8.7 | 25 % (74/300) | 9.6 |
| Maia 1700 | mvs | 92 % (276/300) | 9.0 | 26 % (79/300) | 8.9 |
| Maia 2000 | mvs | 94 % (281/300) | 8.9 | 30 % (89/300) | 8.7 |
| Maia 2300 | mvs | 90 % (271/300) | 8.3 | 29 % (87/300) | 7.5 |
| Stockfish 1400 | mvs | 57 % (227/400) | 11.9 | 40 % (159/400) | 11.2 |
| Stockfish 1800 | mvs | 64 % (255/400) | 11.8 | 38 % (151/400) | 10.9 |
| Stockfish 2200 | mvs | 67 % (267/400) | 11.7 | 40 % (161/400) | 10.2 |

## Blunders

Maia moves losing more than 200 cp by the referee, per 100 Maia moves (the first 2 sampled moves per side included).

| Maia | Mode | Std | 960 |
|---|---|---|---|
| 1400 | mvs | 6.01 (771/12835) | 8.42 (790/9386) |
| 1700 | mvs | 4.77 (618/12959) | 7.74 (828/10703) |
| 2000 | mvs | 4.16 (559/13448) | 6.51 (703/10801) |
| 2300 | mvs | 3.29 (436/13233) | 5.55 (653/11768) |

## Mapping diagnostics

| Variant | Maia moves | Raw top slot not a legal move (informational) | Positions with two legal moves on one slot | Anomalies (wrong castle, illegal move, diagnostic mismatch) |
|---|---|---|---|---|
| std | 52475 | 25671 (48.92 %) | 0 | 0 |
| 960 | 42658 | 24168 (56.66 %) | 988 | 0 |

Every case is in events.tsv with its FEN, of the raw top slot only the first 50.

## Game ends

| Mode, variant | Mean plies | Endings |
|---|---|---|
| mvs 960 | 71 | {adjudication=465, checkmate=704, fifty-moves=6, repetition=19, stalemate=6} |
| mvs std | 87 | {adjudication=564, checkmate=545, fifty-moves=2, insufficient-material=11, repetition=55, stalemate=23} |

