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
| 1400 | 1400 | 960 | 100 | 29/1/70 | 29.5 % | -151 [-229, -86] |
| 1400 | 1800 | 960 | 100 | 5/0/95 | 5.0 % | -512 [-837, -398] |
| 1400 | 2200 | 960 | 100 | 0/1/99 | 0.5 % | < -920 [< -920, -729] |
| 1700 | 1400 | 960 | 100 | 54/6/40 | 57.0 % | +49 [-17, +118] |
| 1700 | 1800 | 960 | 100 | 7/4/89 | 9.0 % | -402 [-570, -310] |
| 1700 | 2200 | 960 | 100 | 3/0/97 | 3.0 % | -604 [< -920, -468] |
| 2000 | 1400 | 960 | 100 | 58/2/40 | 59.0 % | +63 [+0, +131] |
| 2000 | 1800 | 960 | 100 | 17/1/82 | 17.5 % | -269 [-375, -194] |
| 2000 | 2200 | 960 | 100 | 5/3/92 | 6.5 % | -463 [-666, -365] |
| 2300 | 1400 | 960 | 100 | 72/1/27 | 72.5 % | +168 [+94, +261] |
| 2300 | 1800 | 960 | 100 | 33/5/62 | 35.5 % | -104 [-185, -32] |
| 2300 | 2200 | 960 | 100 | 12/7/81 | 15.5 % | -295 [-397, -222] |

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
| Maia 1400 | mvs | – | – | 25 % (74/300) | 9.6 |
| Maia 1700 | mvs | – | – | 26 % (79/300) | 8.9 |
| Maia 2000 | mvs | – | – | 30 % (89/300) | 8.7 |
| Maia 2300 | mvs | – | – | 29 % (87/300) | 7.5 |
| Stockfish 1400 | mvs | – | – | 40 % (159/400) | 11.2 |
| Stockfish 1800 | mvs | – | – | 38 % (151/400) | 10.9 |
| Stockfish 2200 | mvs | – | – | 40 % (161/400) | 10.2 |

## Blunders

Maia moves losing more than 200 cp by the referee, per 100 Maia moves (the first 2 sampled moves per side included).

| Maia | Mode | Std | 960 |
|---|---|---|---|
| 1400 | mvs | – | 8.42 (790/9386) |
| 1700 | mvs | – | 7.74 (828/10703) |
| 2000 | mvs | – | 6.51 (703/10801) |
| 2300 | mvs | – | 5.55 (653/11768) |

## Mapping diagnostics

| Variant | Maia moves | Raw top slot not a legal move (informational) | Positions with two legal moves on one slot | Anomalies (wrong castle, illegal move, diagnostic mismatch) |
|---|---|---|---|---|
| std | 0 | 0 (0.00 %) | 0 | 0 |
| 960 | 42658 | 24168 (56.66 %) | 988 | 0 |

Every case is in events.tsv with its FEN, of the raw top slot only the first 50.

## Game ends

| Mode, variant | Mean plies | Endings |
|---|---|---|
| mvs 960 | 71 | {adjudication=465, checkmate=704, fifty-moves=6, repetition=19, stalemate=6} |

## Sampled opening moves

Loss by the referee (eval before minus after, mover's view, capped at ±1000 cp).

| Maia | Sampled moves | Loss > 100 cp | Loss > 200 cp |
|---|---|---|---|
| 1400 | 600 | 12.00 % (72) | 2.17 % (13) |
| 1700 | 600 | 10.50 % (63) | 3.00 % (18) |
| 2000 | 600 | 8.83 % (53) | 2.00 % (12) |
| 2300 | 600 | 8.67 % (52) | 1.50 % (9) |

The 20 worst:

| Loss | Maia | Move | FEN | Game, ply |
|---|---|---|---|---|
| 862 | 1400 | g2g3 | `brkrnbnq/p1pppppp/8/1p6/8/4P3/PPPP1PPP/BRKRNBNQ w DBdb b6 0 2` | mvs-960-m1400-s1400-p24-mw, 2 |
| 836 | 2000 | b7b5 | `rnqkbrnb/pp1ppppp/8/2p5/8/2BP4/PPP1PPPP/RNQK1RNB b FAfa - 1 2` | mvs-960-m2000-s2200-p43-mb, 3 |
| 834 | 1700 | b8c6 | `rnqbkrb1/pppppppp/6n1/7N/8/8/PPPPPPPP/RNQBKRB1 b FAfa - 3 2` | mvs-960-m1700-s1800-p44-mb, 3 |
| 834 | 2000 | g6h4 | `rnqbkrb1/pppppppp/6n1/7N/8/8/PPPPPPPP/RNQBKRB1 b FAfa - 3 2` | mvs-960-m2000-s1800-p44-mb, 3 |
| 821 | 1700 | h7h5 | `rqnkbnrb/ppppp1pp/5p2/8/P7/8/QPPPPPPP/R1NKBNRB b GAga - 1 2` | mvs-960-m1700-s1800-p03-mb, 3 |
| 804 | 1700 | g7g5 | `qrbbnknr/ppppppp1/8/7p/1P6/5N2/P1PPPPPP/QRBBNK1R b HBhb b3 0 2` | mvs-960-m1700-s1800-p07-mb, 3 |
| 728 | 2300 | b7b6 | `rbbkqnrn/ppp1pppp/8/Q2p4/3P4/8/PPP1PPPP/RBBK1NRN b GAga - 1 2` | mvs-960-m2300-s2200-p40-mb, 3 |
| 717 | 1700 | f8g6 | `nbqrknbr/ppppppp1/7p/8/2P5/2Q5/PP1PPPPP/NB1RKNBR b HDhd - 1 2` | mvs-960-m1700-s1800-p37-mb, 3 |
| 712 | 1400 | b2b3 | `nrkbbqrn/pppp1ppp/8/4p3/8/P7/1PPPPPPP/NRKBBQRN w GBgb e6 0 2` | mvs-960-m1400-s2200-p00-mw, 2 |
| 645 | 2000 | d7d5 | `bnqrkr1b/pppppppp/5B2/8/8/1P6/P1PPPPPP/1NQRKRNB b FDfd - 0 2` | mvs-960-m2000-s2200-p02-mb, 3 |
| 636 | 1400 | g7g6 | `Brqnkrnb/p1pppppp/1p6/8/6P1/8/PPPPPP1P/BRQNKRN1 b FBfb - 0 2` | mvs-960-m1400-s1800-p14-mb, 3 |
| 629 | 2300 | e7e6 | `rbnkbrnq/ppppppp1/7p/8/P7/6P1/1PPPPP1P/RBNKBRNQ b FAfa - 0 2` | mvs-960-m2300-s1800-p01-mb, 3 |
| 558 | 1700 | a6a5 | `rbnkbrnq/1ppppppp/p7/8/3P4/6P1/PPP1PP1P/RBNKBRNQ b FAfa d3 0 2` | mvs-960-m1700-s1400-p01-mb, 3 |
| 550 | 1700 | a5a4 | `nbqrbnkr/1ppppppp/8/p7/2P5/4N3/PP1PPPPP/NBQRB1KR b HDhd - 1 2` | mvs-960-m1700-s2200-p34-mb, 3 |
| 523 | 2300 | h1d5 | `rnkrbqnb/ppp1pppp/8/3p4/8/6P1/PPPPPP1P/RNKRBQNB w DAda d6 0 2` | mvs-960-m2300-s2200-p10-mw, 2 |
| 522 | 2300 | g2g4 | `rbnkbrnq/pppppp1p/8/6p1/8/5P2/PPPPP1PP/RBNKBRNQ w FAfa g6 0 2` | mvs-960-m2300-s1400-p01-mw, 2 |
| 506 | 1700 | h2h3 | `rbnkbrnq/1ppppppp/8/p7/8/6P1/PPPPPP1P/RBNKBRNQ w FAfa a6 0 2` | mvs-960-m1700-s1400-p01-mw, 2 |
| 480 | 2000 | h7h6 | `nrbkqrnb/ppppp1pp/8/5p2/8/3PB3/PPP1PPPP/NR1KQRNB b FBfb - 1 2` | mvs-960-m2000-s1400-p11-mb, 3 |
| 466 | 1700 | a7a6 | `rbnkbrnq/pppppppp/8/8/8/6P1/PPPPPP1P/RBNKBRNQ b FAfa - 0 1` | mvs-960-m1700-s1400-p01-mb, 1 |
| 405 | 1700 | g8f6 | `brqnkrnB/pppppp1p/6p1/8/1P6/8/P1PPPPPP/1RQNKRNB b FBfb - 0 2` | mvs-960-m1700-s1400-p14-mb, 3 |

