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
| Maia's sampled opening moves per side | 6 at temperature 1.0 |
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
| 1700 vs 1700 | std | 100 | 42/24/34 | 54.0 % | +28 [-36, +94] |
| 2000 vs 1400 | std | 100 | 65/16/19 | 73.0 % | +173 [+113, +244] |

| Pairing | Score 960 − std | Elo gap 960 − std [95 % CI] |
|---|---|---|

## Castling

Share of games in which a side castled, and the mean move number (full moves) of the castle. Stockfish's own sides for comparison.

| Player | Mode | Std: castled | Std: mean move | 960: castled | 960: mean move |
|---|---|---|---|---|---|
| Maia 1400 | mvm | 74 % (74/100) | 9.0 | – | – |
| Maia 1700 | mvm | 95 % (189/200) | 9.4 | – | – |
| Maia 2000 | mvm | 92 % (92/100) | 8.6 | – | – |

## Blunders

Maia moves losing more than 200 cp by the referee, per 100 Maia moves (the first 6 sampled moves per side included).

| Maia | Mode | Std | 960 |
|---|---|---|---|
| 1400 | mvm | 6.13 (231/3771) | – |
| 1700 | mvm | 4.64 (398/8584) | – |
| 2000 | mvm | 4.41 (167/3788) | – |

## Mapping diagnostics

| Variant | Maia moves | Raw top slot not a legal move (informational) | Positions with two legal moves on one slot | Anomalies (wrong castle, illegal move, diagnostic mismatch) |
|---|---|---|---|---|
| std | 16143 | 11342 (70.26 %) | 0 | 0 |
| 960 | 0 | 0 (0.00 %) | 0 | 0 |

Every case is in events.tsv with its FEN, of the raw top slot only the first 50.

## Game ends

| Mode, variant | Mean plies | Endings |
|---|---|---|
| mvm std | 81 | {adjudication=48, checkmate=112, insufficient-material=1, repetition=35, stalemate=4} |

## Sampled opening moves

Loss by the referee (eval before minus after, mover's view, capped at ±1000 cp).

| Maia | Sampled moves | Loss > 100 cp | Loss > 200 cp |
|---|---|---|---|
| 1400 | 600 | 13.17 % (79) | 5.67 % (34) |
| 1700 | 1195 | 7.28 % (87) | 3.10 % (37) |
| 2000 | 600 | 6.33 % (38) | 1.67 % (10) |

The 20 worst:

| Loss | Maia | Move | FEN | Game, ply |
|---|---|---|---|---|
| 972 | 1700 | h1g1 | `r1b1kb1r/ppp1pppp/2p2n2/6q1/8/5N2/PPPPPP1P/RNBQK2R w KQkq - 0 6` | mvm-std-a1700-b1700-p46-hw, 10 |
| 934 | 1700 | g8f6 | `rnbqk1nr/ppppppbp/8/6pQ/2B1P3/8/PPPP1PPP/RNB1K1NR b KQkq - 3 3` | mvm-std-a1700-b1700-p14-hw, 5 |
| 781 | 1700 | d5g5 | `r1b1kb1r/ppp1pppp/2p2n2/3q2P1/8/5N2/PPPPPP1P/RNBQK2R b KQkq - 2 5` | mvm-std-a1700-b1700-p46-hw, 9 |
| 779 | 2000 | d7d6 | `r1bqk1nr/pppp1ppp/2n5/2b1p3/2BPP3/5N2/PPP2PPP/RNBQK2R b KQkq d3 0 4` | mvm-std-a1400-b2000-p28-lw, 7 |
| 749 | 2000 | g8h7 | `rnbq2kr/ppppppp1/5n2/3N4/8/8/PPPPPPPP/R1BQKB1R b KQ - 3 6` | mvm-std-a1400-b2000-p22-lw, 11 |
| 716 | 1700 | f3g5 | `r1bqk1nr/pppp1ppp/2n5/2b1p3/2B1P3/5N2/PPPP1PPP/RNBQK2R w KQkq - 4 4` | mvm-std-a1700-b1700-p44-hw, 6 |
| 688 | 1400 | c3d5 | `rnbq2kr/ppppppp1/5n2/8/8/2N5/PPPPPPPP/R1BQKB1R w KQ - 2 6` | mvm-std-a1400-b2000-p22-lw, 10 |
| 684 | 1400 | g5h7 | `r1bqkb1r/pppppppp/2n2n2/6N1/8/8/PPPPPPPP/RNBQKB1R w KQkq - 4 3` | mvm-std-a1400-b2000-p16-lw, 4 |
| 620 | 1400 | e5f7 | `r1bqkb1r/pppppppp/2n2n2/4N3/8/8/PPPPPPPP/RNBQKB1R w KQkq - 4 3` | mvm-std-a1400-b2000-p12-lw, 4 |
| 525 | 1400 | g1h3 | `rnbqk2r/ppppppbp/5np1/8/2B1P3/5Q2/PPPP1PPP/RNB1K1NR w KQkq - 4 4` | mvm-std-a1400-b2000-p17-lw, 6 |
| 504 | 1700 | c1d2 | `rnbqkbnr/p3pppp/8/1p6/2pP4/4P3/1P3PPP/RNBQKBNR w KQkq - 0 6` | mvm-std-a1700-b1700-p44-lw, 10 |
| 503 | 2000 | g8h8 | `rnbqkbr1/pppppppN/5n2/8/8/8/PPPPPPPP/RNBQKB1R b KQq - 0 3` | mvm-std-a1400-b2000-p22-lw, 5 |
| 476 | 1700 | g1f3 | `rnbqkbnr/p3pppp/8/1p6/2pP4/4P3/1P3PPP/RNBQKBNR w KQkq - 0 6` | mvm-std-a1700-b1700-p04-hw, 10 |
| 476 | 1700 | g1f3 | `rnbqkbnr/p3pppp/8/1p6/2pP4/4P3/1P3PPP/RNBQKBNR w KQkq - 0 6` | mvm-std-a1700-b1700-p42-hw, 10 |
| 472 | 1700 | b1c3 | `rnbqkbnr/p3pppp/8/1p6/2pP4/4P3/1P3PPP/RNBQKBNR w KQkq - 0 6` | mvm-std-a1700-b1700-p00-lw, 10 |
| 472 | 2000 | b1c3 | `rnbqkbnr/p3pppp/8/1p6/2pP4/4P3/1P3PPP/RNBQKBNR w KQkq - 0 6` | mvm-std-a1400-b2000-p03-hw, 10 |
| 472 | 1700 | b1c3 | `rnbqkbnr/p3pppp/8/1p6/2pP4/4P3/1P3PPP/RNBQKBNR w KQkq - 0 6` | mvm-std-a1700-b1700-p13-lw, 10 |
| 472 | 1700 | b1c3 | `rnbqkbnr/p3pppp/8/1p6/2pP4/4P3/1P3PPP/RNBQKBNR w KQkq - 0 6` | mvm-std-a1700-b1700-p21-hw, 10 |
| 472 | 1700 | b1c3 | `rnbqkbnr/p3pppp/8/1p6/2pP4/4P3/1P3PPP/RNBQKBNR w KQkq - 0 6` | mvm-std-a1700-b1700-p25-hw, 10 |
| 450 | 1700 | c6d4 | `r1bqk2r/pppp1ppp/2n5/2b1p3/2B1P1n1/2NP1N2/PPP2PPP/R1BQ1RK1 b kq - 2 6` | mvm-std-a1700-b1700-p33-lw, 11 |

