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
| 1400 | 1400 | std | 100 | 75/4/21 | 77.0 % | +210 [+141, +298] |
| 1400 | 1800 | std | 100 | 12/11/77 | 17.5 % | -269 [-375, -194] |
| 1400 | 2200 | std | 100 | 10/8/82 | 14.0 % | -315 [-450, -230] |
| 1700 | 1400 | std | 100 | 78/5/17 | 80.5 % | +246 [+172, +347] |
| 1700 | 1800 | std | 100 | 31/10/59 | 36.0 % | -100 [-169, -38] |
| 1700 | 2200 | std | 100 | 13/5/82 | 15.5 % | -295 [-409, -216] |
| 2000 | 1400 | std | 100 | 90/3/7 | 91.5 % | +413 [+312, +617] |
| 2000 | 1800 | std | 100 | 39/12/49 | 45.0 % | -35 [-109, +36] |
| 2000 | 2200 | std | 100 | 25/13/62 | 31.5 % | -135 [-209, -71] |
| 2300 | 1400 | std | 100 | 93/2/5 | 94.0 % | +478 [+375, +708] |
| 2300 | 1800 | std | 100 | 58/12/30 | 64.0 % | +100 [+41, +166] |
| 2300 | 2200 | std | 100 | 37/6/57 | 40.0 % | -70 [-140, -6] |

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
| Maia 1400 | mvs | 91 % (272/300) | 8.7 | – | – |
| Maia 1700 | mvs | 92 % (276/300) | 9.0 | – | – |
| Maia 2000 | mvs | 94 % (281/300) | 8.9 | – | – |
| Maia 2300 | mvs | 90 % (271/300) | 8.3 | – | – |
| Stockfish 1400 | mvs | 57 % (227/400) | 11.9 | – | – |
| Stockfish 1800 | mvs | 64 % (255/400) | 11.8 | – | – |
| Stockfish 2200 | mvs | 67 % (267/400) | 11.7 | – | – |

## Blunders

Maia moves losing more than 200 cp by the referee, per 100 Maia moves (the first 2 sampled moves per side included).

| Maia | Mode | Std | 960 |
|---|---|---|---|
| 1400 | mvs | 6.01 (771/12835) | – |
| 1700 | mvs | 4.77 (618/12959) | – |
| 2000 | mvs | 4.16 (559/13448) | – |
| 2300 | mvs | 3.29 (436/13233) | – |

## Mapping diagnostics

| Variant | Maia moves | Raw top slot not a legal move (informational) | Positions with two legal moves on one slot | Anomalies (wrong castle, illegal move, diagnostic mismatch) |
|---|---|---|---|---|
| std | 52475 | 25671 (48.92 %) | 0 | 0 |
| 960 | 0 | 0 (0.00 %) | 0 | 0 |

Every case is in events.tsv with its FEN, of the raw top slot only the first 50.

## Game ends

| Mode, variant | Mean plies | Endings |
|---|---|---|
| mvs std | 87 | {adjudication=564, checkmate=545, fifty-moves=2, insufficient-material=11, repetition=55, stalemate=23} |

## Sampled opening moves

Loss by the referee (eval before minus after, mover's view, capped at ±1000 cp).

| Maia | Sampled moves | Loss > 100 cp | Loss > 200 cp |
|---|---|---|---|
| 1400 | 600 | 1.83 % (11) | 0.00 % (0) |
| 1700 | 600 | 1.50 % (9) | 0.00 % (0) |
| 2000 | 600 | 0.50 % (3) | 0.00 % (0) |
| 2300 | 600 | 0.50 % (3) | 0.00 % (0) |

The 20 worst:

| Loss | Maia | Move | FEN | Game, ply |
|---|---|---|---|---|
| 178 | 1400 | g7g5 | `rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1` | mvs-std-m1400-s2200-p46-mb, 1 |
| 173 | 1400 | e4e5 | `rnbqkbnr/ppp1pppp/3p4/8/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 2` | mvs-std-m1400-s1400-p01-mw, 2 |
| 169 | 1400 | g7g5 | `rnbqkbnr/pppppppp/8/8/8/5N2/PPPPPPPP/RNBQKB1R b KQkq - 1 1` | mvs-std-m1400-s2200-p19-mb, 1 |
| 160 | 1400 | g1f3 | `rnbqkbnr/ppp1pppp/8/3p4/4P3/8/PPPP1PPP/RNBQKBNR w KQkq d6 0 2` | mvs-std-m1400-s1800-p17-mw, 2 |
| 160 | 1700 | g1f3 | `rnbqkbnr/ppp1pppp/8/3p4/4P3/8/PPPP1PPP/RNBQKBNR w KQkq d6 0 2` | mvs-std-m1700-s1800-p20-mw, 2 |
| 160 | 1400 | g1f3 | `rnbqkbnr/ppp1pppp/8/3p4/4P3/8/PPPP1PPP/RNBQKBNR w KQkq d6 0 2` | mvs-std-m1400-s1800-p28-mw, 2 |
| 140 | 1700 | h1h3 | `rnbqkbnr/pp1ppppp/8/2p5/7P/8/PPPPPPP1/RNBQKBNR w KQkq c6 0 2` | mvs-std-m1700-s1400-p40-mw, 2 |
| 137 | 2300 | g2g4 | `rnbqkbnr/ppp1pppp/8/3p4/8/7P/PPPPPPP1/RNBQKBNR w KQkq d6 0 2` | mvs-std-m2300-s1800-p49-mw, 2 |
| 129 | 1400 | f7f6 | `rnbqkbnr/pppppppp/8/8/8/5N2/PPPPPPPP/RNBQKB1R b KQkq - 1 1` | mvs-std-m1400-s1800-p03-mb, 1 |
| 125 | 1700 | d2d4 | `rnbqkbnr/ppp1pppp/8/3p4/4P3/8/PPPP1PPP/RNBQKBNR w KQkq d6 0 2` | mvs-std-m1700-s1800-p34-mw, 2 |
| 114 | 1700 | e7e5 | `rnbqkbnr/ppp1pppp/8/3p4/3P1B2/8/PPP1PPPP/RN1QKBNR b KQkq - 1 2` | mvs-std-m1700-s1400-p11-mb, 3 |
| 113 | 2300 | g2g3 | `r1bqkbnr/pppppppp/2n5/8/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 1 2` | mvs-std-m2300-s2200-p22-mw, 2 |
| 111 | 1700 | g1h3 | `r1bqkbnr/pppppppp/2n5/8/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 1 2` | mvs-std-m1700-s2200-p05-mw, 2 |
| 111 | 1700 | b1c3 | `rnbqkbnr/ppp1pppp/8/3p4/4P3/8/PPPP1PPP/RNBQKBNR w KQkq d6 0 2` | mvs-std-m1700-s1800-p17-mw, 2 |
| 111 | 1700 | b1c3 | `rnbqkbnr/ppp1pppp/8/3p4/4P3/8/PPPP1PPP/RNBQKBNR w KQkq d6 0 2` | mvs-std-m1700-s1800-p22-mw, 2 |
| 111 | 2300 | g2g4 | `rnbqkb1r/pppppppp/5n2/8/8/5N2/PPPPPPPP/RNBQKB1R w KQkq - 2 2` | mvs-std-m2300-s1400-p38-mw, 2 |
| 109 | 1400 | e7e5 | `rnbqkbnr/pppppppp/8/8/8/5N2/PPPPPPPP/RNBQKB1R b KQkq - 1 1` | mvs-std-m1400-s1800-p02-mb, 1 |
| 109 | 1700 | e7e5 | `rnbqkbnr/pppppppp/8/8/8/5N2/PPPPPPPP/RNBQKB1R b KQkq - 1 1` | mvs-std-m1700-s1400-p13-mb, 1 |
| 109 | 2000 | e7e5 | `rnbqkbnr/pppppppp/8/8/8/5N2/PPPPPPPP/RNBQKB1R b KQkq - 1 1` | mvs-std-m2000-s1800-p26-mb, 1 |
| 109 | 1400 | e7e5 | `rnbqkbnr/pppppppp/8/8/8/5N2/PPPPPPPP/RNBQKB1R b KQkq - 1 1` | mvs-std-m1400-s1400-p44-mb, 1 |

