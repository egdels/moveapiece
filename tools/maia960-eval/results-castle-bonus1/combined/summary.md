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
| Maia sampled moves (T = 1) per side | 2 |
| Ply limit (draw) | 300 |
| Adjudication | |eval| > 1000 cp, same side, 10 plies in a row |
| Referee | Stockfish full strength, depth 12, Threads 1, Hash 16 |
| Blunder | loss > 200 cp, evals capped at ±1000 cp |
| Chess960 castle scoring | king:1.0 (experimental, not the app) |
| Workers | 9 |
| Bootstrap | 2000 resamples of position pairs, seed 4242 |

Games in games.csv: 2800, of them without a result (error): 0.

## Maia against Stockfish

Score and Elo difference from Maia's view, 95 % CI from the position pairs.

| Maia | SF | Variant | Games | +/=/- | Score | Elo diff [95 % CI] |
|---|---|---|---|---|---|---|
| 1400 | 1400 | std | 100 | 79/3/18 | 80.5 % | +246 [+181, +332] |
| 1400 | 1400 | 960 | 100 | 54/2/44 | 55.0 % | +35 [-30, +102] |
| 1400 | 1800 | std | 100 | 15/6/79 | 18.0 % | -263 [-351, -198] |
| 1400 | 1800 | 960 | 100 | 6/3/91 | 7.5 % | -436 [-616, -343] |
| 1400 | 2200 | std | 100 | 6/3/91 | 7.5 % | -436 [-673, -331] |
| 1400 | 2200 | 960 | 100 | 2/1/97 | 2.5 % | -636 [< -920, -478] |
| 1700 | 1400 | std | 100 | 85/4/11 | 87.0 % | +330 [+254, +444] |
| 1700 | 1400 | 960 | 100 | 54/1/45 | 54.5 % | +31 [-40, +106] |
| 1700 | 1800 | std | 100 | 28/4/68 | 30.0 % | -147 [-222, -84] |
| 1700 | 1800 | 960 | 100 | 5/6/89 | 8.0 % | -424 [-577, -338] |
| 1700 | 2200 | std | 100 | 9/7/84 | 12.5 % | -338 [-448, -264] |
| 1700 | 2200 | 960 | 100 | 5/2/93 | 6.0 % | -478 [-708, -375] |
| 2000 | 1400 | std | 100 | 94/1/5 | 94.5 % | +494 [+386, +761] |
| 2000 | 1400 | 960 | 100 | 67/5/28 | 69.5 % | +143 [+73, +227] |
| 2000 | 1800 | std | 100 | 31/10/59 | 36.0 % | -100 [-176, -32] |
| 2000 | 1800 | 960 | 100 | 21/4/75 | 23.0 % | -210 [-302, -139] |
| 2000 | 2200 | std | 100 | 10/7/83 | 13.5 % | -323 [-428, -250] |
| 2000 | 2200 | 960 | 100 | 4/6/90 | 7.0 % | -449 [-617, -360] |
| 2300 | 1400 | std | 100 | 91/5/4 | 93.5 % | +463 [+369, +648] |
| 2300 | 1400 | 960 | 100 | 77/1/22 | 77.5 % | +215 [+140, +314] |
| 2300 | 1800 | std | 100 | 40/10/50 | 45.0 % | -35 [-101, +29] |
| 2300 | 1800 | 960 | 100 | 21/2/77 | 22.0 % | -220 [-314, -148] |
| 2300 | 2200 | std | 100 | 22/9/69 | 26.5 % | -177 [-267, -105] |
| 2300 | 2200 | 960 | 100 | 10/6/84 | 13.0 % | -330 [-466, -245] |

## Elo shift Chess960 minus standard per Maia level

Performance rating against the three Stockfish levels together (on Stockfish's nominal UCI_Elo scale), per variant; shift with a bootstrap 95 % CI.

| Maia | Perf. standard | Perf. 960 | Shift 960 − std [95 % CI] |
|---|---|---|---|
| 1400 | 1613 | 1425 | -188 [-258, -118] |
| 1700 | 1714 | 1442 | -272 [-349, -197] |
| 2000 | 1775 | 1584 | -191 [-264, -115] |
| 2300 | 1863 | 1641 | -222 [-299, -141] |

Shift per pairing (Elo diff 960 − standard, bootstrap 95 % CI; scores of 0 or 100 % are clamped to half a game):

| Maia | SF | Shift [95 % CI] |
|---|---|---|
| 1400 | 1400 | -211 [-315, -118] |
| 1400 | 1800 | -173 [-342, -48] |
| 1400 | 2200 | -200 [-538, +58] |
| 1700 | 1400 | -299 [-423, -189] |
| 1700 | 1800 | -277 [-431, -161] |
| 1700 | 2200 | -140 [-342, +8] |
| 2000 | 1400 | -351 [-557, -211] |
| 2000 | 1800 | -110 [-222, -9] |
| 2000 | 2200 | -127 [-284, +16] |
| 2300 | 1400 | -248 [-422, -111] |
| 2300 | 1800 | -185 [-298, -81] |
| 2300 | 2200 | -153 [-302, -35] |

## Maia against Maia

Score of the higher-rated Maia; nominal gap 600.

| Pairing | Variant | Games | +/=/- | Score | Elo diff [95 % CI] |
|---|---|---|---|---|---|
| 2000 vs 1400 | std | 100 | 49/44/7 | 71.0 % | +156 [+113, +203] |
| 2000 vs 1400 | 960 | 100 | 71/12/17 | 77.0 % | +210 [+144, +293] |
| 2300 vs 1700 | std | 100 | 81/11/8 | 86.5 % | +323 [+250, +428] |
| 2300 vs 1700 | 960 | 100 | 65/9/26 | 69.5 % | +143 [+73, +227] |

| Pairing | Score 960 − std | Elo gap 960 − std [95 % CI] |
|---|---|---|
| 2000 vs 1400 | +6.0 pp | +54 [-23, +145] |
| 2300 vs 1700 | -17.0 pp | -180 [-298, -73] |

## Castling

Share of games in which a side castled, and the mean move number (full moves) of the castle. Stockfish's own sides for comparison.

| Player | Mode | Std: castled | Std: mean move | 960: castled | 960: mean move |
|---|---|---|---|---|---|
| Maia 1400 | mvs | 91 % (272/300) | 9.0 | 39 % (116/300) | 9.5 |
| Maia 1700 | mvs | 91 % (273/300) | 8.8 | 35 % (105/300) | 10.6 |
| Maia 2000 | mvs | 94 % (283/300) | 8.7 | 38 % (114/300) | 10.2 |
| Maia 2300 | mvs | 91 % (273/300) | 8.2 | 35 % (106/300) | 10.6 |
| Maia 1400 | mvm | 74 % (74/100) | 7.2 | 39 % (39/100) | 9.3 |
| Maia 1700 | mvm | 91 % (91/100) | 9.1 | 37 % (37/100) | 8.2 |
| Maia 2000 | mvm | 92 % (92/100) | 7.7 | 38 % (38/100) | 9.7 |
| Maia 2300 | mvm | 86 % (86/100) | 8.8 | 37 % (37/100) | 8.3 |
| Stockfish 1400 | mvs | 55 % (221/400) | 11.8 | 30 % (121/400) | 10.9 |
| Stockfish 1800 | mvs | 69 % (275/400) | 11.5 | 32 % (126/400) | 12.2 |
| Stockfish 2200 | mvs | 68 % (273/400) | 11.7 | 36 % (142/400) | 10.8 |

## Blunders

Maia moves losing more than 200 cp by the referee, per 100 Maia moves (the first 2 sampled moves per side included).

| Maia | Mode | Std | 960 |
|---|---|---|---|
| 1400 | mvs | 6.03 (731/12130) | 8.16 (817/10007) |
| 1700 | mvs | 5.32 (687/12919) | 7.88 (865/10975) |
| 2000 | mvs | 4.67 (608/13015) | 6.92 (798/11539) |
| 2300 | mvs | 4.31 (553/12833) | 6.39 (708/11075) |
| 1400 | mvm | 6.88 (254/3693) | 7.78 (286/3674) |
| 1700 | mvm | 6.65 (239/3594) | 7.42 (258/3479) |
| 2000 | mvm | 5.68 (210/3697) | 5.63 (208/3695) |
| 2300 | mvm | 3.72 (135/3625) | 5.25 (183/3489) |

## Mapping diagnostics

| Variant | Maia moves | Raw top slot not a legal move (informational) | Positions with two legal moves on one slot | Anomalies (wrong castle, illegal move, diagnostic mismatch) |
|---|---|---|---|---|
| std | 65506 | 48977 (74.77 %) | 0 | 0 |
| 960 | 57933 | 47878 (82.64 %) | 2936 | 0 |

Every case is in events.tsv with its FEN, of the raw top slot only the first 50.

## Game ends

| Mode, variant | Mean plies | Endings |
|---|---|---|
| mvm 960 | 72 | {adjudication=69, checkmate=110, insufficient-material=4, repetition=14, stalemate=3} |
| mvm std | 73 | {adjudication=64, checkmate=81, insufficient-material=1, repetition=54} |
| mvs 960 | 73 | {adjudication=467, checkmate=694, fifty-moves=3, insufficient-material=7, repetition=21, stalemate=8} |
| mvs std | 85 | {adjudication=569, checkmate=562, insufficient-material=9, repetition=46, stalemate=14} |

