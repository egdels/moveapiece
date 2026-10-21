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
| Maia's sampled opening moves per side | 4 at temperature 0.7 |
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
| 1700 vs 1700 | std | 100 | 38/37/25 | 56.5 % | +45 [-12, +106] |
| 2000 vs 1400 | std | 100 | 84/9/7 | 88.5 % | +354 [+266, +504] |

| Pairing | Score 960 − std | Elo gap 960 − std [95 % CI] |
|---|---|---|

## Castling

Share of games in which a side castled, and the mean move number (full moves) of the castle. Stockfish's own sides for comparison.

| Player | Mode | Std: castled | Std: mean move | 960: castled | 960: mean move |
|---|---|---|---|---|---|
| Maia 1400 | mvm | 95 % (95/100) | 7.6 | – | – |
| Maia 1700 | mvm | 90 % (179/200) | 10.4 | – | – |
| Maia 2000 | mvm | 97 % (97/100) | 8.6 | – | – |

## Blunders

Maia moves losing more than 200 cp by the referee, per 100 Maia moves (the first 4 sampled moves per side included).

| Maia | Mode | Std | 960 |
|---|---|---|---|
| 1400 | mvm | 7.21 (283/3923) | – |
| 1700 | mvm | 6.65 (611/9182) | – |
| 2000 | mvm | 4.48 (177/3951) | – |

## Mapping diagnostics

| Variant | Maia moves | Raw top slot not a legal move (informational) | Positions with two legal moves on one slot | Anomalies (wrong castle, illegal move, diagnostic mismatch) |
|---|---|---|---|---|
| std | 17056 | 12009 (70.41 %) | 0 | 0 |
| 960 | 0 | 0 (0.00 %) | 0 | 0 |

Every case is in events.tsv with its FEN, of the raw top slot only the first 50.

## Game ends

| Mode, variant | Mean plies | Endings |
|---|---|---|
| mvm std | 85 | {adjudication=35, checkmate=119, repetition=38, stalemate=8} |

## Sampled opening moves

Loss by the referee (eval before minus after, mover's view, capped at ±1000 cp).

| Maia | Sampled moves | Loss > 100 cp | Loss > 200 cp |
|---|---|---|---|
| 1400 | 400 | 3.25 % (13) | 0.75 % (3) |
| 1700 | 800 | 2.63 % (21) | 0.25 % (2) |
| 2000 | 400 | 2.00 % (8) | 0.25 % (1) |

The 20 worst:

| Loss | Maia | Move | FEN | Game, ply |
|---|---|---|---|---|
| 620 | 1400 | e5f7 | `r1bqkb1r/pppppppp/2n2n2/4N3/8/8/PPPPPPPP/RNBQKB1R w KQkq - 4 3` | mvm-std-a1400-b2000-p22-lw, 4 |
| 422 | 1700 | b1c3 | `rnbqk2r/ppppppbp/5np1/8/2B1P3/5Q2/PPPP1PPP/RNB1K1NR w KQkq - 4 4` | mvm-std-a1700-b1700-p17-hw, 6 |
| 394 | 1700 | g8f6 | `rnbqk1nr/ppppppbp/6p1/8/2B1P3/5Q2/PPPP1PPP/RNB1K1NR b KQkq - 3 3` | mvm-std-a1700-b1700-p17-hw, 5 |
| 262 | 1400 | f1c4 | `rnbqkb1r/ppppp1pp/5n2/6N1/4p3/8/PPPP1PPP/RNBQKB1R w KQkq - 2 4` | mvm-std-a1400-b2000-p19-lw, 6 |
| 245 | 2000 | f7g8 | `r1bq1b1r/pppppkpp/2n2n2/8/8/2N5/PPPPPPPP/R1BQKB1R b KQ - 1 4` | mvm-std-a1400-b2000-p22-lw, 7 |
| 212 | 1400 | g1f3 | `rnbqkbnr/ppppp1pp/8/5p2/4P3/8/PPPP1PPP/RNBQKBNR w KQkq f6 0 2` | mvm-std-a1400-b2000-p19-lw, 2 |
| 186 | 2000 | f7f5 | `rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1` | mvm-std-a1400-b2000-p19-lw, 1 |
| 182 | 1400 | a1a2 | `1nbqkbnr/1ppppppp/r7/p7/P7/1P6/2PPPPPP/RNBQKBNR w KQk - 1 3` | mvm-std-a1400-b2000-p29-lw, 4 |
| 114 | 2000 | a8a7 | `rnbqkbnr/1ppppppp/8/p7/P7/RP6/2PPPPPP/1NBQKBNR b Kk - 4 4` | mvm-std-a1400-b2000-p29-lw, 7 |
| 114 | 2000 | b8c6 | `rnbqkbnr/pp2pppp/8/3P4/3Q4/8/PP2PPPP/RNB1KBNR b KQkq - 0 4` | mvm-std-a1400-b2000-p39-lw, 7 |
| 111 | 1700 | f8g7 | `r1bqkb1r/pppppp1p/2n2np1/8/4PP2/2N2N2/PPPP2PP/R1BQKB1R b KQkq e3 0 4` | mvm-std-a1700-b1700-p30-lw, 7 |
| 110 | 1400 | a2a3 | `rnbqkbnr/1ppppppp/8/p7/P7/1P6/R1PPPPPP/1NBQKBNR w Kk - 3 4` | mvm-std-a1400-b2000-p29-lw, 6 |
| 107 | 2000 | a6a8 | `1nbqkbnr/1ppppppp/r7/p7/P7/1P6/R1PPPPPP/1NBQKBNR b Kk - 2 3` | mvm-std-a1400-b2000-p29-lw, 5 |
| 106 | 2000 | a8a6 | `rnbqkbnr/1ppppppp/8/p7/P7/1P6/2PPPPPP/RNBQKBNR b KQkq a3 0 2` | mvm-std-a1400-b2000-p29-lw, 3 |
| 103 | 1700 | c7c6 | `rnbqkbnr/p1p1pppp/8/1p6/P1pP4/4P3/1P3PPP/RNBQKBNR b KQkq a3 0 4` | mvm-std-a1700-b1700-p00-lw, 7 |
| 103 | 1400 | c7c6 | `rnbqkbnr/p1p1pppp/8/1p6/P1pP4/4P3/1P3PPP/RNBQKBNR b KQkq a3 0 4` | mvm-std-a1400-b2000-p03-hw, 7 |
| 103 | 1400 | c7c6 | `rnbqkbnr/p1p1pppp/8/1p6/P1pP4/4P3/1P3PPP/RNBQKBNR b KQkq a3 0 4` | mvm-std-a1400-b2000-p04-hw, 7 |
| 103 | 1700 | c7c6 | `rnbqkbnr/p1p1pppp/8/1p6/P1pP4/4P3/1P3PPP/RNBQKBNR b KQkq a3 0 4` | mvm-std-a1700-b1700-p04-hw, 7 |
| 103 | 1400 | c7c6 | `rnbqkbnr/p1p1pppp/8/1p6/P1pP4/4P3/1P3PPP/RNBQKBNR b KQkq a3 0 4` | mvm-std-a1400-b2000-p06-hw, 7 |
| 103 | 2000 | c7c6 | `rnbqkbnr/p1p1pppp/8/1p6/P1pP4/4P3/1P3PPP/RNBQKBNR b KQkq a3 0 4` | mvm-std-a1400-b2000-p09-lw, 7 |

