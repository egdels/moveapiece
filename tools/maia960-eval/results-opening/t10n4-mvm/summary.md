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

Games in games.csv: 200, of them without a result (error): 0.

## Maia against Stockfish

Score and Elo difference from Maia's view, 95 % CI from the position pairs.

| Maia | SF | Variant | Games | +/=/- | Score | Elo diff [95 % CI] |
|---|---|---|---|---|---|---|

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
| 1700 vs 1700 | std | 100 | 36/23/41 | 47.5 % | -17 [-83, +47] |
| 2000 vs 1400 | std | 100 | 80/10/10 | 85.0 % | +301 [+224, +414] |

| Pairing | Score 960 − std | Elo gap 960 − std [95 % CI] |
|---|---|---|

## Castling

Share of games in which a side castled, and the mean move number (full moves) of the castle. Stockfish's own sides for comparison.

| Player | Mode | Std: castled | Std: mean move | 960: castled | 960: mean move |
|---|---|---|---|---|---|
| Maia 1400 | mvm | 91 % (91/100) | 8.2 | – | – |
| Maia 1700 | mvm | 89 % (178/200) | 9.9 | – | – |
| Maia 2000 | mvm | 90 % (90/100) | 9.5 | – | – |

## Blunders

Maia moves losing more than 200 cp by the referee, per 100 Maia moves (the first 4 sampled moves per side included).

| Maia | Mode | Std | 960 |
|---|---|---|---|
| 1400 | mvm | 6.99 (244/3493) | – |
| 1700 | mvm | 6.31 (579/9172) | – |
| 2000 | mvm | 3.87 (136/3518) | – |

## Mapping diagnostics

| Variant | Maia moves | Raw top slot not a legal move (informational) | Positions with two legal moves on one slot | Anomalies (wrong castle, illegal move, diagnostic mismatch) |
|---|---|---|---|---|
| std | 16183 | 11372 (70.27 %) | 0 | 0 |
| 960 | 0 | 0 (0.00 %) | 0 | 0 |

Every case is in events.tsv with its FEN, of the raw top slot only the first 50.

## Game ends

| Mode, variant | Mean plies | Endings |
|---|---|---|
| mvm std | 81 | {adjudication=51, checkmate=116, insufficient-material=1, repetition=30, stalemate=2} |

## Sampled opening moves

Loss by the referee (eval before minus after, mover's view, capped at ±1000 cp).

| Maia | Sampled moves | Loss > 100 cp | Loss > 200 cp |
|---|---|---|---|
| 1400 | 400 | 5.50 % (22) | 1.25 % (5) |
| 1700 | 799 | 4.26 % (34) | 0.50 % (4) |
| 2000 | 400 | 4.00 % (16) | 1.25 % (5) |

The 20 worst:

| Loss | Maia | Move | FEN | Game, ply |
|---|---|---|---|---|
| 934 | 1700 | g8f6 | `rnbqk1nr/ppppppbp/8/6pQ/2B1P3/8/PPPP1PPP/RNB1K1NR b KQkq - 3 3` | mvm-std-a1700-b1700-p14-hw, 5 |
| 779 | 2000 | d7d6 | `r1bqk1nr/pppp1ppp/2n5/2b1p3/2BPP3/5N2/PPP2PPP/RNBQK2R b KQkq d3 0 4` | mvm-std-a1400-b2000-p28-lw, 7 |
| 716 | 1700 | f3g5 | `r1bqk1nr/pppp1ppp/2n5/2b1p3/2B1P3/5N2/PPPP1PPP/RNBQK2R w KQkq - 4 4` | mvm-std-a1700-b1700-p44-hw, 6 |
| 684 | 1400 | g5h7 | `r1bqkb1r/pppppppp/2n2n2/6N1/8/8/PPPPPPPP/RNBQKB1R w KQkq - 4 3` | mvm-std-a1400-b2000-p16-lw, 4 |
| 620 | 1400 | e5f7 | `r1bqkb1r/pppppppp/2n2n2/4N3/8/8/PPPPPPPP/RNBQKB1R w KQkq - 4 3` | mvm-std-a1400-b2000-p12-lw, 4 |
| 525 | 1400 | g1h3 | `rnbqk2r/ppppppbp/5np1/8/2B1P3/5Q2/PPPP1PPP/RNB1K1NR w KQkq - 4 4` | mvm-std-a1400-b2000-p17-lw, 6 |
| 503 | 2000 | g8h8 | `rnbqkbr1/pppppppN/5n2/8/8/8/PPPPPPPP/RNBQKB1R b KQq - 0 3` | mvm-std-a1400-b2000-p22-lw, 5 |
| 427 | 1400 | g5h7 | `rnbqkbr1/pppppppp/5n2/6N1/8/8/PPPPPPPP/RNBQKB1R w KQq - 4 3` | mvm-std-a1400-b2000-p22-lw, 4 |
| 394 | 2000 | g8f6 | `rnbqk1nr/ppppppbp/6p1/8/2B1P3/5Q2/PPPP1PPP/RNB1K1NR b KQkq - 3 3` | mvm-std-a1400-b2000-p17-lw, 5 |
| 274 | 1700 | b7b6 | `rnbqkbnr/pp3ppp/3p4/2p1p3/2P5/P2PP3/1P3PPP/RNBQKBNR b KQkq - 0 4` | mvm-std-a1700-b1700-p45-hw, 7 |
| 255 | 1700 | f3h4 | `rnbqkb1r/pppp1pp1/4pn1p/8/2B1P3/5N2/PPPP1PPP/RNBQK2R w KQkq - 2 4` | mvm-std-a1700-b1700-p11-lw, 6 |
| 245 | 2000 | f7g8 | `r1bq1b1r/pppppkpp/2n2n2/8/8/2N5/PPPPPPPP/R1BQKB1R b KQ - 1 4` | mvm-std-a1400-b2000-p12-lw, 7 |
| 218 | 1400 | a1a3 | `1nbqkbnr/1ppppppp/r7/p7/P7/1P6/2PPPPPP/RNBQKBNR w KQk - 1 3` | mvm-std-a1400-b2000-p29-lw, 4 |
| 210 | 2000 | c6b8 | `r1bqkbnr/pppppppp/2n5/8/6P1/8/PPPPPPBP/RNBQK1NR b KQkq - 2 2` | mvm-std-a1400-b2000-p21-lw, 3 |
| 193 | 2000 | f6g8 | `rnbqkb1r/pppppppp/5n2/8/6P1/5N2/PPPPPPBP/RNBQ1RK1 b kq - 6 4` | mvm-std-a1400-b2000-p21-lw, 7 |
| 178 | 1700 | g7g5 | `rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1` | mvm-std-a1700-b1700-p14-hw, 1 |
| 177 | 1700 | g8f6 | `r1bqkbnr/pppppppp/2n5/8/6P1/8/PPPPPPBP/RNBQK1NR b KQkq - 2 2` | mvm-std-a1700-b1700-p46-hw, 3 |
| 175 | 2000 | e8g8 | `rnbqk2r/ppppppbp/5np1/8/2B1P3/5Q1N/PPPP1PPP/RNB1K2R b KQkq - 5 4` | mvm-std-a1400-b2000-p17-lw, 7 |
| 167 | 1400 | g1e2 | `rnb1kbnr/ppp1pppp/8/8/2Pq4/8/PP1P1PPP/RNBQKBNR w KQkq - 0 4` | mvm-std-a1400-b2000-p00-lw, 6 |
| 167 | 1400 | f8c5 | `rnbqkb1r/pppp1ppp/5n2/4N3/4P3/8/PPPP1PPP/RNBQKB1R b KQkq - 0 3` | mvm-std-a1400-b2000-p19-hw, 5 |

