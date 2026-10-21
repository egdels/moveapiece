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
| 1400 | 1400 | std | 100 | 74/4/22 | 76.0 % | +200 [+131, +289] |
| 1400 | 1800 | std | 100 | 18/9/73 | 22.5 % | -215 [-308, -143] |
| 1400 | 2200 | std | 100 | 6/6/88 | 9.0 % | -402 [-543, -318] |
| 1700 | 1400 | std | 100 | 84/3/13 | 85.5 % | +308 [+222, +444] |
| 1700 | 1800 | std | 100 | 26/7/67 | 29.5 % | -151 [-235, -82] |
| 1700 | 2200 | std | 100 | 11/5/84 | 13.5 % | -323 [-432, -248] |
| 2000 | 1400 | std | 100 | 85/3/12 | 86.5 % | +323 [+240, +452] |
| 2000 | 1800 | std | 100 | 28/8/64 | 32.0 % | -131 [-196, -74] |
| 2000 | 2200 | std | 100 | 16/14/70 | 23.0 % | -210 [-278, -154] |
| 2300 | 1400 | std | 100 | 92/2/6 | 93.0 % | +449 [+352, +648] |
| 2300 | 1800 | std | 100 | 42/8/50 | 46.0 % | -28 [-98, +40] |
| 2300 | 2200 | std | 100 | 30/11/59 | 35.5 % | -104 [-185, -32] |

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
| Maia 1400 | mvs | 92 % (276/300) | 9.0 | – | – |
| Maia 1700 | mvs | 90 % (271/300) | 9.0 | – | – |
| Maia 2000 | mvs | 91 % (273/300) | 8.7 | – | – |
| Maia 2300 | mvs | 94 % (283/300) | 8.2 | – | – |
| Stockfish 1400 | mvs | 56 % (225/400) | 11.7 | – | – |
| Stockfish 1800 | mvs | 67 % (269/400) | 11.8 | – | – |
| Stockfish 2200 | mvs | 64 % (254/400) | 11.9 | – | – |

## Blunders

Maia moves losing more than 200 cp by the referee, per 100 Maia moves (the first 2 sampled moves per side included).

| Maia | Mode | Std | 960 |
|---|---|---|---|
| 1400 | mvs | 5.78 (709/12257) | – |
| 1700 | mvs | 5.40 (666/12340) | – |
| 2000 | mvs | 4.85 (645/13311) | – |
| 2300 | mvs | 4.28 (555/12982) | – |

## Mapping diagnostics

| Variant | Maia moves | Raw top slot not a legal move (informational) | Positions with two legal moves on one slot | Anomalies (wrong castle, illegal move, diagnostic mismatch) |
|---|---|---|---|---|
| std | 50890 | 38536 (75.72 %) | 0 | 0 |
| 960 | 0 | 0 (0.00 %) | 0 | 0 |

Every case is in events.tsv with its FEN, of the raw top slot only the first 50.

## Game ends

| Mode, variant | Mean plies | Endings |
|---|---|---|
| mvs std | 85 | {adjudication=574, checkmate=546, fifty-moves=1, insufficient-material=15, repetition=52, stalemate=12} |

## Sampled opening moves

Loss by the referee (eval before minus after, mover's view, capped at ±1000 cp).

| Maia | Sampled moves | Loss > 100 cp | Loss > 200 cp |
|---|---|---|---|
| 1400 | 600 | 1.17 % (7) | 0.00 % (0) |
| 1700 | 600 | 1.00 % (6) | 0.00 % (0) |
| 2000 | 600 | 0.67 % (4) | 0.17 % (1) |
| 2300 | 600 | 0.50 % (3) | 0.00 % (0) |

The 20 worst:

| Loss | Maia | Move | FEN | Game, ply |
|---|---|---|---|---|
| 212 | 2000 | f7f5 | `r1bqkbnr/pppppppp/2n5/8/3PP3/8/PPP2PPP/RNBQKBNR b KQkq d3 0 2` | mvs-std-m2000-s2200-p27-mb, 3 |
| 164 | 2300 | g2g4 | `rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1` | mvs-std-m2300-s1800-p00-mw, 0 |
| 164 | 2000 | g2g4 | `rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1` | mvs-std-m2000-s1800-p03-mw, 0 |
| 164 | 2000 | g2g4 | `rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1` | mvs-std-m2000-s1800-p21-mw, 0 |
| 164 | 2300 | g2g4 | `rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1` | mvs-std-m2300-s1800-p21-mw, 0 |
| 164 | 1700 | g2g4 | `rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1` | mvs-std-m1700-s1400-p35-mw, 0 |
| 160 | 1400 | g1f3 | `rnbqkbnr/ppp1pppp/8/3p4/4P3/8/PPPP1PPP/RNBQKBNR w KQkq d6 0 2` | mvs-std-m1400-s1800-p17-mw, 2 |
| 160 | 1400 | g1f3 | `rnbqkbnr/ppp1pppp/8/3p4/4P3/8/PPPP1PPP/RNBQKBNR w KQkq d6 0 2` | mvs-std-m1400-s1800-p28-mw, 2 |
| 135 | 2300 | a6a5 | `rnbqkbnr/1ppppppp/p7/8/8/2N2N2/PPPPPPPP/R1BQKB1R b KQkq - 1 2` | mvs-std-m2300-s2200-p22-mb, 3 |
| 126 | 1400 | f2f3 | `rnbqkbnr/ppp1pppp/8/3p4/4P3/8/PPPP1PPP/RNBQKBNR w KQkq d6 0 2` | mvs-std-m1400-s1800-p37-mw, 2 |
| 122 | 1400 | e7e5 | `rnbqkbnr/ppp1pppp/8/3p4/3P4/5N2/PPP1PPPP/RNBQKB1R b KQkq - 1 2` | mvs-std-m1400-s1400-p35-mb, 3 |
| 110 | 2000 | f1g2 | `rnbqkbnr/ppppppp1/8/7p/6P1/8/PPPPPP1P/RNBQKBNR w KQkq h6 0 2` | mvs-std-m2000-s1800-p21-mw, 2 |
| 110 | 1700 | f1g2 | `rnbqkbnr/ppppppp1/8/7p/6P1/8/PPPPPP1P/RNBQKBNR w KQkq h6 0 2` | mvs-std-m1700-s1400-p35-mw, 2 |
| 109 | 1400 | e7e5 | `rnbqkbnr/pppppppp/8/8/8/5N2/PPPPPPPP/RNBQKB1R b KQkq - 1 1` | mvs-std-m1400-s1400-p25-mb, 1 |
| 109 | 1700 | e7e5 | `rnbqkbnr/pppppppp/8/8/8/5N2/PPPPPPPP/RNBQKB1R b KQkq - 1 1` | mvs-std-m1700-s1800-p29-mb, 1 |
| 109 | 1700 | e7e5 | `rnbqkbnr/pppppppp/8/8/8/5N2/PPPPPPPP/RNBQKB1R b KQkq - 1 1` | mvs-std-m1700-s1800-p43-mb, 1 |
| 104 | 1700 | g7g5 | `rnbqkbnr/ppppppp1/7p/8/3P4/5N2/PPP1PPPP/RNBQKB1R b KQkq d3 0 2` | mvs-std-m1700-s1800-p11-mb, 3 |
| 104 | 1400 | d7d5 | `rnbqkbnr/pppp1ppp/8/4p3/3P4/4P3/PPP2PPP/RNBQKBNR b KQkq d3 0 2` | mvs-std-m1400-s2200-p27-mb, 3 |
| 102 | 1400 | e7e5 | `rnbqkbnr/ppp1pppp/8/3p4/3P4/4P3/PPP2PPP/RNBQKBNR b KQkq d3 0 2` | mvs-std-m1400-s1800-p08-mb, 3 |
| 102 | 1700 | e7e5 | `rnbqkbnr/ppp1pppp/8/3p4/3P4/4P3/PPP2PPP/RNBQKBNR b KQkq d3 0 2` | mvs-std-m1700-s2200-p47-mb, 3 |

