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

Games in games.csv: 1200, of them without a result (error): 0.

## Maia against Stockfish

Score and Elo difference from Maia's view, 95 % CI from the position pairs.

| Maia | SF | Variant | Games | +/=/- | Score | Elo diff [95 % CI] |
|---|---|---|---|---|---|---|
| 1400 | 1400 | 960 | 100 | 61/5/34 | 63.5 % | +96 [+30, +170] |
| 1400 | 1800 | 960 | 100 | 7/4/89 | 9.0 % | -402 [-552, -315] |
| 1400 | 2200 | 960 | 100 | 0/3/97 | 1.5 % | -727 [< -920, -594] |
| 1700 | 1400 | 960 | 100 | 58/4/38 | 60.0 % | +70 [+8, +138] |
| 1700 | 1800 | 960 | 100 | 10/4/86 | 12.0 % | -346 [-467, -268] |
| 1700 | 2200 | 960 | 100 | 3/3/94 | 4.5 % | -531 [-821, -421] |
| 2000 | 1400 | 960 | 100 | 74/2/24 | 75.0 % | +191 [+122, +277] |
| 2000 | 1800 | 960 | 100 | 22/4/74 | 24.0 % | -200 [-279, -137] |
| 2000 | 2200 | 960 | 100 | 8/2/90 | 9.0 % | -402 [-561, -313] |
| 2300 | 1400 | 960 | 100 | 80/1/19 | 80.5 % | +246 [+179, +334] |
| 2300 | 1800 | 960 | 100 | 21/8/71 | 25.0 % | -191 [-295, -111] |
| 2300 | 2200 | 960 | 100 | 6/3/91 | 7.5 % | -436 [-616, -343] |

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
| Maia 1400 | mvs | – | – | 35 % (106/300) | 9.1 |
| Maia 1700 | mvs | – | – | 36 % (109/300) | 9.8 |
| Maia 2000 | mvs | – | – | 36 % (107/300) | 9.7 |
| Maia 2300 | mvs | – | – | 36 % (107/300) | 11.5 |
| Stockfish 1400 | mvs | – | – | 30 % (119/400) | 11.3 |
| Stockfish 1800 | mvs | – | – | 40 % (158/400) | 10.4 |
| Stockfish 2200 | mvs | – | – | 33 % (132/400) | 11.0 |

## Blunders

Maia moves losing more than 200 cp by the referee, per 100 Maia moves (the first 2 sampled moves per side included).

| Maia | Mode | Std | 960 |
|---|---|---|---|
| 1400 | mvs | – | 8.46 (857/10128) |
| 1700 | mvs | – | 7.80 (873/11197) |
| 2000 | mvs | – | 6.53 (770/11786) |
| 2300 | mvs | – | 5.89 (714/12120) |

## Mapping diagnostics

| Variant | Maia moves | Raw top slot not a legal move (informational) | Positions with two legal moves on one slot | Anomalies (wrong castle, illegal move, diagnostic mismatch) |
|---|---|---|---|---|
| std | 0 | 0 (0.00 %) | 0 | 0 |
| 960 | 45231 | 38074 (84.18 %) | 2141 | 0 |

Every case is in events.tsv with its FEN, of the raw top slot only the first 50.

## Game ends

| Mode, variant | Mean plies | Endings |
|---|---|---|
| mvs 960 | 76 | {adjudication=488, checkmate=669, fifty-moves=3, insufficient-material=10, repetition=21, stalemate=9} |

## Sampled opening moves

Loss by the referee (eval before minus after, mover's view, capped at ±1000 cp).

| Maia | Sampled moves | Loss > 100 cp | Loss > 200 cp |
|---|---|---|---|
| 1400 | 600 | 13.00 % (78) | 3.50 % (21) |
| 1700 | 600 | 11.17 % (67) | 3.00 % (18) |
| 2000 | 600 | 8.67 % (52) | 1.67 % (10) |
| 2300 | 600 | 9.67 % (58) | 1.83 % (11) |

The 20 worst:

| Loss | Maia | Move | FEN | Game, ply |
|---|---|---|---|---|
| 976 | 2300 | c2c4 | `brnbkqnr/pppp1ppp/4p3/8/3P4/8/PPP1PPPP/BRNBKQNR w HBhb - 0 2` | mvs-960-m2300-s1400-p12-mw, 2 |
| 898 | 1400 | g7g6 | `qbnrbknr/pppppppp/8/8/1P6/8/P1PPPPPP/QBNRBKNR b HDhd b3 0 1` | mvs-960-m1400-s1400-p33-mb, 1 |
| 851 | 2000 | g7g5 | `brkrnbnq/ppppppp1/8/7p/1P6/4P3/P1PP1PPP/BRKRNBNQ b DBdb b3 0 2` | mvs-960-m2000-s2200-p24-mb, 3 |
| 737 | 1400 | e7e6 | `qbbrknnr/ppp1pppp/8/3p4/8/1P3N2/P1PPPPPP/QBBRKN1R b HDhd - 1 2` | mvs-960-m1400-s1400-p49-mb, 3 |
| 717 | 2000 | b7b6 | `bbnrnkrq/pp1ppppp/8/2p5/8/1P4P1/P1PPPP1P/BBNRNKRQ b GDgd - 0 2` | mvs-960-m2000-s1400-p36-mb, 3 |
| 665 | 1400 | g3h5 | `qbbrknnr/ppppppp1/8/7p/8/6N1/PPPPPPPP/QBBRK1NR w HDhd h6 0 2` | mvs-960-m1400-s1400-p49-mw, 2 |
| 659 | 1400 | b3d4 | `rknbrqbn/pppp1ppp/8/4p3/8/1N6/PPPPPPPP/RK1BRQBN w EAea e6 0 2` | mvs-960-m1400-s1400-p29-mw, 2 |
| 658 | 1400 | b2b4 | `rbbkqnrn/pp1ppppp/2p5/8/2P5/8/PP1PPPPP/RBBKQNRN w GAga - 0 2` | mvs-960-m1400-s2200-p40-mw, 2 |
| 658 | 2300 | b8b6 | `Brqknnrb/p1pppppp/8/1p6/8/6P1/PPPPPP1P/BRQKNNR1 b GBgb - 0 2` | mvs-960-m2300-s2200-p41-mb, 3 |
| 639 | 1400 | e7e6 | `Bqnkbnrb/p1pppppp/1p6/8/8/6P1/PPPPPP1P/RQNKBNR1 b GAg - 0 2` | mvs-960-m1400-s2200-p03-mb, 3 |
| 624 | 1400 | f7f6 | `bqrnnbkr/pppp1ppp/4p3/4N3/8/8/PPPPPPPP/BQRN1BKR b HChc - 1 2` | mvs-960-m1400-s1800-p38-mb, 3 |
| 623 | 2000 | a7a5 | `brqk1nrb/pppppppp/5B2/8/1P6/8/P1PPPPPP/1RQKNNRB b GBgb - 0 2` | mvs-960-m2000-s2200-p41-mb, 3 |
| 618 | 1400 | c7c5 | `Brqnnkrb/p1pppppp/8/1p6/8/6P1/PPPPPP1P/BRQNNKR1 b GBgb - 0 2` | mvs-960-m1400-s1800-p04-mb, 3 |
| 575 | 1400 | b6a4 | `rk1brqbn/pppppppp/1n6/8/P3P3/8/1PPP1PPP/RKNBRQBN b EAea a3 0 2` | mvs-960-m1400-s1400-p29-mb, 3 |
| 571 | 1700 | b2b3 | `rbbkqnrn/pp1ppppp/8/2p5/2P5/8/PP1PPPPP/RBBKQNRN w GAga c6 0 2` | mvs-960-m1700-s1800-p40-mw, 2 |
| 558 | 1400 | b6a4 | `rb1kbrnq/pppppppp/1n6/8/P7/5N2/1PPPPPPP/RBNKBR1Q b FAfa a3 0 2` | mvs-960-m1400-s1800-p01-mb, 3 |
| 513 | 1700 | c8b6 | `bbnr1krq/pppppppp/3n4/8/2P5/6P1/PP1PPP1P/BBNRNKRQ b GDgd c3 0 2` | mvs-960-m1700-s1800-p36-mb, 3 |
| 500 | 2000 | h2h3 | `rnqbkrbn/ppp1pppp/3p4/8/8/6P1/PPPPPP1P/RNQBKRBN w FAfa - 0 2` | mvs-960-m2000-s1400-p44-mw, 2 |
| 487 | 1400 | a7a5 | `nrkbbqrn/p1pppppp/1p6/8/5P2/4P3/PPPP2PP/NRKBBQRN b GBgb f3 0 2` | mvs-960-m1400-s1400-p00-mb, 3 |
| 483 | 1700 | b7b5 | `rkrnbqnb/pppppppp/8/8/P7/8/1PPPPPPP/RKRNBQNB b CAca a3 0 1` | mvs-960-m1700-s1800-p15-mb, 1 |

