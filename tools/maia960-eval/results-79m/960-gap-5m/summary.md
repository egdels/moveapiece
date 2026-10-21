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
| 1400 | 1400 | std | 100 | 74/4/22 | 76.0 % | +200 [+131, +289] |
| 1400 | 1400 | 960 | 100 | 61/5/34 | 63.5 % | +96 [+30, +170] |
| 1400 | 1800 | std | 100 | 18/9/73 | 22.5 % | -215 [-308, -143] |
| 1400 | 1800 | 960 | 100 | 7/4/89 | 9.0 % | -402 [-552, -315] |
| 1400 | 2200 | std | 100 | 6/6/88 | 9.0 % | -402 [-543, -318] |
| 1400 | 2200 | 960 | 100 | 0/3/97 | 1.5 % | -727 [< -920, -594] |
| 1700 | 1400 | std | 100 | 84/3/13 | 85.5 % | +308 [+222, +444] |
| 1700 | 1400 | 960 | 100 | 58/4/38 | 60.0 % | +70 [+8, +138] |
| 1700 | 1800 | std | 100 | 26/7/67 | 29.5 % | -151 [-235, -82] |
| 1700 | 1800 | 960 | 100 | 10/4/86 | 12.0 % | -346 [-467, -268] |
| 1700 | 2200 | std | 100 | 11/5/84 | 13.5 % | -323 [-432, -248] |
| 1700 | 2200 | 960 | 100 | 3/3/94 | 4.5 % | -531 [-821, -421] |
| 2000 | 1400 | std | 100 | 85/3/12 | 86.5 % | +323 [+240, +452] |
| 2000 | 1400 | 960 | 100 | 74/2/24 | 75.0 % | +191 [+122, +277] |
| 2000 | 1800 | std | 100 | 28/8/64 | 32.0 % | -131 [-196, -74] |
| 2000 | 1800 | 960 | 100 | 22/4/74 | 24.0 % | -200 [-279, -137] |
| 2000 | 2200 | std | 100 | 16/14/70 | 23.0 % | -210 [-278, -154] |
| 2000 | 2200 | 960 | 100 | 8/2/90 | 9.0 % | -402 [-561, -313] |
| 2300 | 1400 | std | 100 | 92/2/6 | 93.0 % | +449 [+352, +648] |
| 2300 | 1400 | 960 | 100 | 80/1/19 | 80.5 % | +246 [+179, +334] |
| 2300 | 1800 | std | 100 | 42/8/50 | 46.0 % | -28 [-98, +40] |
| 2300 | 1800 | 960 | 100 | 21/8/71 | 25.0 % | -191 [-295, -111] |
| 2300 | 2200 | std | 100 | 30/11/59 | 35.5 % | -104 [-185, -32] |
| 2300 | 2200 | 960 | 100 | 6/3/91 | 7.5 % | -436 [-616, -343] |

## Elo shift Chess960 minus standard per Maia level

Performance rating against the three Stockfish levels together (on Stockfish's nominal UCI_Elo scale), per variant; shift with a bootstrap 95 % CI.

| Maia | Perf. standard | Perf. 960 | Shift 960 − std [95 % CI] |
|---|---|---|---|
| 1400 | 1619 | 1468 | -151 [-226, -77] |
| 1700 | 1710 | 1480 | -230 [-302, -155] |
| 2000 | 1764 | 1622 | -143 [-212, -75] |
| 2300 | 1903 | 1643 | -260 [-342, -172] |

Shift per pairing (Elo diff 960 − standard, bootstrap 95 % CI; scores of 0 or 100 % are clamped to half a game):

| Maia | SF | Shift [95 % CI] |
|---|---|---|
| 1400 | 1400 | -104 [-215, +0] |
| 1400 | 1800 | -187 [-335, -53] |
| 1400 | 2200 | -325 [-573, -140] |
| 1700 | 1400 | -238 [-374, -126] |
| 1700 | 1800 | -195 [-317, -75] |
| 1700 | 2200 | -208 [-451, -58] |
| 2000 | 1400 | -132 [-272, -16] |
| 2000 | 1800 | -69 [-164, +18] |
| 2000 | 2200 | -192 [-347, -75] |
| 2300 | 1400 | -203 [-379, -73] |
| 2300 | 1800 | -163 [-277, -57] |
| 2300 | 2200 | -333 [-499, -207] |

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
| Maia 1400 | mvs | 92 % (276/300) | 9.0 | 35 % (106/300) | 9.1 |
| Maia 1700 | mvs | 90 % (271/300) | 9.0 | 36 % (109/300) | 9.8 |
| Maia 2000 | mvs | 91 % (273/300) | 8.7 | 36 % (107/300) | 9.7 |
| Maia 2300 | mvs | 94 % (283/300) | 8.2 | 36 % (107/300) | 11.5 |
| Stockfish 1400 | mvs | 56 % (225/400) | 11.7 | 30 % (119/400) | 11.3 |
| Stockfish 1800 | mvs | 67 % (269/400) | 11.8 | 40 % (158/400) | 10.4 |
| Stockfish 2200 | mvs | 64 % (254/400) | 11.9 | 33 % (132/400) | 11.0 |

## Blunders

Maia moves losing more than 200 cp by the referee, per 100 Maia moves (the first 2 sampled moves per side included).

| Maia | Mode | Std | 960 |
|---|---|---|---|
| 1400 | mvs | 5.78 (709/12257) | 8.46 (857/10128) |
| 1700 | mvs | 5.40 (666/12340) | 7.80 (873/11197) |
| 2000 | mvs | 4.85 (645/13311) | 6.53 (770/11786) |
| 2300 | mvs | 4.28 (555/12982) | 5.89 (714/12120) |

## Mapping diagnostics

| Variant | Maia moves | Raw top slot not a legal move (informational) | Positions with two legal moves on one slot | Anomalies (wrong castle, illegal move, diagnostic mismatch) |
|---|---|---|---|---|
| std | 50890 | 38536 (75.72 %) | 0 | 0 |
| 960 | 45231 | 38074 (84.18 %) | 2141 | 0 |

Every case is in events.tsv with its FEN, of the raw top slot only the first 50.

## Game ends

| Mode, variant | Mean plies | Endings |
|---|---|---|
| mvs 960 | 76 | {adjudication=488, checkmate=669, fifty-moves=3, insufficient-material=10, repetition=21, stalemate=9} |
| mvs std | 85 | {adjudication=574, checkmate=546, fifty-moves=1, insufficient-material=15, repetition=52, stalemate=12} |

