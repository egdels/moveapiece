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
| Workers | 9 |
| Bootstrap | 2000 resamples of position pairs, seed 4242 |

Games in games.csv: 2800, of them without a result (error): 0.

## Maia against Stockfish

Score and Elo difference from Maia's view, 95 % CI from the position pairs.

| Maia | SF | Variant | Games | +/=/- | Score | Elo diff [95 % CI] |
|---|---|---|---|---|---|---|
| 1400 | 1400 | std | 100 | 79/3/18 | 80.5 % | +246 [+181, +332] |
| 1400 | 1400 | 960 | 100 | 51/5/44 | 53.5 % | +24 [-39, +89] |
| 1400 | 1800 | std | 100 | 15/6/79 | 18.0 % | -263 [-351, -198] |
| 1400 | 1800 | 960 | 100 | 6/3/91 | 7.5 % | -436 [-643, -337] |
| 1400 | 2200 | std | 100 | 6/3/91 | 7.5 % | -436 [-673, -331] |
| 1400 | 2200 | 960 | 100 | 2/1/97 | 2.5 % | -636 [< -920, -498] |
| 1700 | 1400 | std | 100 | 85/4/11 | 87.0 % | +330 [+254, +444] |
| 1700 | 1400 | 960 | 100 | 63/0/37 | 63.0 % | +92 [+21, +173] |
| 1700 | 1800 | std | 100 | 28/4/68 | 30.0 % | -147 [-222, -84] |
| 1700 | 1800 | 960 | 100 | 13/7/80 | 16.5 % | -282 [-376, -213] |
| 1700 | 2200 | std | 100 | 9/7/84 | 12.5 % | -338 [-448, -264] |
| 1700 | 2200 | 960 | 100 | 3/1/96 | 3.5 % | -576 [< -920, -451] |
| 2000 | 1400 | std | 100 | 94/1/5 | 94.5 % | +494 [+386, +761] |
| 2000 | 1400 | 960 | 100 | 70/2/28 | 71.0 % | +156 [+90, +234] |
| 2000 | 1800 | std | 100 | 31/10/59 | 36.0 % | -100 [-176, -32] |
| 2000 | 1800 | 960 | 100 | 17/4/79 | 19.0 % | -252 [-365, -172] |
| 2000 | 2200 | std | 100 | 10/7/83 | 13.5 % | -323 [-428, -250] |
| 2000 | 2200 | 960 | 100 | 5/2/93 | 6.0 % | -478 [-708, -375] |
| 2300 | 1400 | std | 100 | 91/5/4 | 93.5 % | +463 [+369, +648] |
| 2300 | 1400 | 960 | 100 | 76/2/22 | 77.0 % | +210 [+144, +293] |
| 2300 | 1800 | std | 100 | 40/10/50 | 45.0 % | -35 [-101, +29] |
| 2300 | 1800 | 960 | 100 | 18/8/74 | 22.0 % | -220 [-301, -156] |
| 2300 | 2200 | std | 100 | 22/9/69 | 26.5 % | -177 [-267, -105] |
| 2300 | 2200 | 960 | 100 | 7/8/85 | 11.0 % | -363 [-492, -282] |

## Elo shift Chess960 minus standard per Maia level

Performance rating against the three Stockfish levels together (on Stockfish's nominal UCI_Elo scale), per variant; shift with a bootstrap 95 % CI.

| Maia | Perf. standard | Perf. 960 | Shift 960 − std [95 % CI] |
|---|---|---|---|
| 1400 | 1613 | 1417 | -196 [-265, -125] |
| 1700 | 1714 | 1510 | -204 [-279, -133] |
| 2000 | 1775 | 1569 | -206 [-279, -131] |
| 2300 | 1863 | 1630 | -233 [-306, -158] |

Shift per pairing (Elo diff 960 − standard, bootstrap 95 % CI; scores of 0 or 100 % are clamped to half a game):

| Maia | SF | Shift [95 % CI] |
|---|---|---|
| 1400 | 1400 | -222 [-324, -128] |
| 1400 | 1800 | -173 [-363, -42] |
| 1400 | 2200 | -200 [-507, +33] |
| 1700 | 1400 | -238 [-364, -126] |
| 1700 | 1800 | -134 [-245, -32] |
| 1700 | 2200 | -238 [-547, -69] |
| 2000 | 1400 | -338 [-545, -203] |
| 2000 | 1800 | -152 [-287, -49] |
| 2000 | 2200 | -155 [-348, +0] |
| 2300 | 1400 | -253 [-425, -128] |
| 2300 | 1800 | -185 [-284, -89] |
| 2300 | 2200 | -186 [-324, -75] |

## Maia against Maia

Score of the higher-rated Maia; nominal gap 600.

| Pairing | Variant | Games | +/=/- | Score | Elo diff [95 % CI] |
|---|---|---|---|---|---|
| 2000 vs 1400 | std | 100 | 49/44/7 | 71.0 % | +156 [+113, +203] |
| 2000 vs 1400 | 960 | 100 | 65/14/21 | 72.0 % | +164 [+100, +241] |
| 2300 vs 1700 | std | 100 | 81/11/8 | 86.5 % | +323 [+250, +428] |
| 2300 vs 1700 | 960 | 100 | 59/14/27 | 66.0 % | +115 [+49, +190] |

| Pairing | Score 960 − std | Elo gap 960 − std [95 % CI] |
|---|---|---|
| 2000 vs 1400 | +1.0 pp | +9 [-69, +91] |
| 2300 vs 1700 | -20.5 pp | -207 [-324, -103] |

## Castling

Share of games in which a side castled, and the mean move number (full moves) of the castle. Stockfish's own sides for comparison.

| Player | Mode | Std: castled | Std: mean move | 960: castled | 960: mean move |
|---|---|---|---|---|---|
| Maia 1400 | mvs | 91 % (272/300) | 9.0 | 17 % (52/300) | 9.0 |
| Maia 1700 | mvs | 91 % (273/300) | 8.8 | 18 % (55/300) | 9.1 |
| Maia 2000 | mvs | 94 % (283/300) | 8.7 | 19 % (58/300) | 9.6 |
| Maia 2300 | mvs | 91 % (273/300) | 8.2 | 17 % (51/300) | 10.9 |
| Maia 1400 | mvm | 74 % (74/100) | 7.2 | 22 % (22/100) | 9.2 |
| Maia 1700 | mvm | 91 % (91/100) | 9.1 | 17 % (17/100) | 8.1 |
| Maia 2000 | mvm | 92 % (92/100) | 7.7 | 21 % (21/100) | 9.1 |
| Maia 2300 | mvm | 86 % (86/100) | 8.8 | 21 % (21/100) | 9.0 |
| Stockfish 1400 | mvs | 55 % (221/400) | 11.8 | 35 % (139/400) | 10.7 |
| Stockfish 1800 | mvs | 69 % (275/400) | 11.5 | 35 % (140/400) | 11.2 |
| Stockfish 2200 | mvs | 68 % (273/400) | 11.7 | 35 % (141/400) | 10.7 |

## Blunders

Maia moves losing more than 200 cp by the referee, per 100 Maia moves (the first 2 sampled moves per side included).

| Maia | Mode | Std | 960 |
|---|---|---|---|
| 1400 | mvs | 6.03 (731/12130) | 8.19 (877/10706) |
| 1700 | mvs | 5.32 (687/12919) | 7.63 (833/10919) |
| 2000 | mvs | 4.67 (608/13015) | 6.82 (799/11724) |
| 2300 | mvs | 4.31 (553/12833) | 6.38 (742/11630) |
| 1400 | mvm | 6.88 (254/3693) | 7.26 (263/3621) |
| 1700 | mvm | 6.65 (239/3594) | 7.09 (261/3679) |
| 2000 | mvm | 5.68 (210/3697) | 5.39 (196/3637) |
| 2300 | mvm | 3.72 (135/3625) | 5.48 (202/3683) |

## Mapping diagnostics

| Variant | Maia moves | Raw top slot not a legal move (informational) | Positions with two legal moves on one slot | Anomalies (wrong castle, illegal move, diagnostic mismatch) |
|---|---|---|---|---|
| std | 65506 | 48977 (74.77 %) | 0 | 0 |
| 960 | 59599 | 49299 (82.72 %) | 3444 | 0 |

Every case is in events.tsv with its FEN, of the raw top slot only the first 50.

## Game ends

| Mode, variant | Mean plies | Endings |
|---|---|---|
| mvm 960 | 73 | {adjudication=71, checkmate=101, insufficient-material=5, repetition=22, stalemate=1} |
| mvm std | 73 | {adjudication=64, checkmate=81, insufficient-material=1, repetition=54} |
| mvs 960 | 75 | {adjudication=458, checkmate=699, insufficient-material=8, repetition=29, stalemate=6} |
| mvs std | 85 | {adjudication=569, checkmate=562, insufficient-material=9, repetition=46, stalemate=14} |

