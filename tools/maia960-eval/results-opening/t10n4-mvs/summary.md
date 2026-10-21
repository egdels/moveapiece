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
| Maia's sampled opening moves per side | 4 at temperature 1.0 |
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
| 1400 | 1400 | std | 100 | 72/2/26 | 73.0 % | +173 [+101, +262] |
| 1400 | 1800 | std | 100 | 17/4/79 | 19.0 % | -252 [-365, -172] |
| 1400 | 2200 | std | 100 | 2/7/91 | 5.5 % | -494 [-677, -402] |
| 1700 | 1400 | std | 100 | 85/3/12 | 86.5 % | +323 [+240, +452] |
| 1700 | 1800 | std | 100 | 20/8/72 | 24.0 % | -200 [-284, -134] |
| 1700 | 2200 | std | 100 | 12/10/78 | 17.0 % | -275 [-377, -202] |
| 2000 | 1400 | std | 100 | 85/3/12 | 86.5 % | +323 [+234, +468] |
| 2000 | 1800 | std | 100 | 35/10/55 | 40.0 % | -70 [-145, -2] |
| 2000 | 2200 | std | 100 | 18/7/75 | 21.5 % | -225 [-308, -160] |
| 2300 | 1400 | std | 100 | 91/6/3 | 94.0 % | +478 [+385, +663] |
| 2300 | 1800 | std | 100 | 54/12/34 | 60.0 % | +70 [+13, +132] |
| 2300 | 2200 | std | 100 | 22/10/68 | 27.0 % | -173 [-251, -108] |

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
| Maia 1400 | mvs | 89 % (268/300) | 9.0 | – | – |
| Maia 1700 | mvs | 91 % (273/300) | 9.1 | – | – |
| Maia 2000 | mvs | 93 % (280/300) | 7.9 | – | – |
| Maia 2300 | mvs | 93 % (278/300) | 8.1 | – | – |
| Stockfish 1400 | mvs | 56 % (222/400) | 11.6 | – | – |
| Stockfish 1800 | mvs | 64 % (256/400) | 12.0 | – | – |
| Stockfish 2200 | mvs | 67 % (269/400) | 12.0 | – | – |

## Blunders

Maia moves losing more than 200 cp by the referee, per 100 Maia moves (the first 4 sampled moves per side included).

| Maia | Mode | Std | 960 |
|---|---|---|---|
| 1400 | mvs | 5.56 (718/12918) | – |
| 1700 | mvs | 5.38 (687/12761) | – |
| 2000 | mvs | 4.80 (631/13139) | – |
| 2300 | mvs | 4.32 (537/12426) | – |

## Mapping diagnostics

| Variant | Maia moves | Raw top slot not a legal move (informational) | Positions with two legal moves on one slot | Anomalies (wrong castle, illegal move, diagnostic mismatch) |
|---|---|---|---|---|
| std | 51244 | 38917 (75.94 %) | 0 | 0 |
| 960 | 0 | 0 (0.00 %) | 0 | 0 |

Every case is in events.tsv with its FEN, of the raw top slot only the first 50.

## Game ends

| Mode, variant | Mean plies | Endings |
|---|---|---|
| mvs std | 85 | {adjudication=559, checkmate=559, fifty-moves=2, insufficient-material=13, ply-limit=1, repetition=55, stalemate=11} |

## Sampled opening moves

Loss by the referee (eval before minus after, mover's view, capped at ±1000 cp).

| Maia | Sampled moves | Loss > 100 cp | Loss > 200 cp |
|---|---|---|---|
| 1400 | 1200 | 4.00 % (48) | 1.17 % (14) |
| 1700 | 1200 | 2.42 % (29) | 0.25 % (3) |
| 2000 | 1200 | 2.33 % (28) | 0.42 % (5) |
| 2300 | 1200 | 1.58 % (19) | 0.58 % (7) |

The 20 worst:

| Loss | Maia | Move | FEN | Game, ply |
|---|---|---|---|---|
| 828 | 1400 | e2e3 | `r1bqkb1r/pppppppp/5n2/8/2BnP3/8/PPPPQPPP/RNB1K1NR w KQkq - 5 4` | mvs-std-m1400-s1800-p28-mw, 6 |
| 799 | 1400 | d2d3 | `rnb1kbnr/ppp2ppp/4p3/3q4/8/1P6/P2PPPPP/RNBQKBNR w KQkq - 0 4` | mvs-std-m1400-s1800-p35-mw, 6 |
| 640 | 1400 | g5h7 | `rnbqkbnr/ppp2ppp/3p4/4p1N1/8/8/PPPPPPPP/RNBQKB1R w KQkq e6 0 3` | mvs-std-m1400-s2200-p16-mw, 4 |
| 568 | 2300 | f3g5 | `rnbqkbnr/ppp2ppp/3p4/8/4p3/5NP1/PPPPPPBP/RNBQK2R w KQkq - 0 4` | mvs-std-m2300-s1800-p36-mw, 6 |
| 535 | 2000 | f1g2 | `rnbqkbnr/ppp2ppp/4p3/8/3p4/2N2NP1/PPPPPP1P/R1BQKB1R w KQkq - 0 4` | mvs-std-m2000-s2200-p14-mw, 6 |
| 487 | 1400 | c4f7 | `rnbqkb1r/pppp1ppp/5n2/4p3/2B1P3/8/PPPP1PPP/RNBQK1NR w KQkq - 2 3` | mvs-std-m1400-s2200-p15-mw, 4 |
| 456 | 2300 | e8g8 | `rnbqk2r/ppppppbp/5np1/4P3/3P4/2N5/PPP2PPP/R1BQKBNR b KQkq - 0 4` | mvs-std-m2300-s1400-p12-mb, 7 |
| 441 | 1400 | c4f7 | `rnbqkbnr/1pp1pp1p/3p2p1/p7/2B1P3/5N2/PPPP1PPP/RNBQK2R w KQkq a6 0 4` | mvs-std-m1400-s1400-p02-mw, 6 |
| 427 | 1400 | g1f3 | `rnbqkbnr/1p1p1ppp/p3p3/2p5/2B1P3/8/PPPPQPPP/RNB1K1NR w KQkq - 0 4` | mvs-std-m1400-s2200-p00-mw, 6 |
| 397 | 1700 | b1c3 | `r1bqkbnr/ppp1pppp/8/n2P4/8/5N2/PPPP1PPP/RNBQKB1R w KQkq - 1 4` | mvs-std-m1700-s1400-p02-mw, 6 |
| 397 | 2300 | b1c3 | `r1bqkbnr/ppp1pppp/8/n2P4/8/5N2/PPPP1PPP/RNBQKB1R w KQkq - 1 4` | mvs-std-m2300-s1400-p15-mw, 6 |
| 381 | 1400 | c4f7 | `r1bqkb1r/pppppppp/2n2n2/8/2B1P3/8/PPPP1PPP/RNBQK1NR w KQkq - 3 3` | mvs-std-m1400-s1400-p17-mw, 4 |
| 323 | 2000 | e5f7 | `rnbqk1nr/ppp2ppp/8/3pN3/1b2P3/8/PPPP1PPP/RNBQKB1R w KQkq - 1 4` | mvs-std-m2000-s2200-p31-mw, 6 |
| 318 | 1400 | f6g4 | `r1bqkb1r/pppppppp/5n2/8/1nPP4/2N2N2/PP2PPPP/R1BQKB1R b KQkq - 2 4` | mvs-std-m1400-s2200-p18-mb, 7 |
| 277 | 1700 | b1c3 | `r1bqkbnr/ppp1pppp/8/3P4/1n6/5N2/PPPP1PPP/RNBQKB1R w KQkq - 1 4` | mvs-std-m1700-s1800-p10-mw, 6 |
| 275 | 1400 | c4f7 | `r1bqkbnr/pp1ppppp/8/n1p5/2B1P3/5N2/PPPP1PPP/RNBQK2R w KQkq - 4 4` | mvs-std-m1400-s1800-p23-mw, 6 |
| 261 | 1400 | e4f2 | `rnbqkb1r/pppp1ppp/8/4p3/2BPn3/5N2/PPP2PPP/RNBQK2R b KQkq - 1 4` | mvs-std-m1400-s1400-p18-mb, 7 |
| 259 | 2300 | b7b5 | `rnbqkbnr/1ppppppp/8/8/p2PP3/P1N5/1PP2PPP/R1BQKBNR b KQkq - 0 4` | mvs-std-m2300-s2200-p22-mb, 7 |
| 255 | 1400 | f8b4 | `rnbqkb1r/pp1p1ppp/2p2n2/4P3/2P5/4P3/PP3PPP/RNBQKBNR b KQkq - 0 4` | mvs-std-m1400-s2200-p35-mb, 7 |
| 240 | 2300 | d4e3 | `rnbqkbnr/p2ppppp/1p6/8/2Bp4/4PN2/PPP2PPP/RNBQK2R b KQkq - 1 4` | mvs-std-m2300-s2200-p31-mb, 7 |

