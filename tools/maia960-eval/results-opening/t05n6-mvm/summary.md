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
| Maia's sampled opening moves per side | 6 at temperature 0.5 |
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
| 1700 vs 1700 | std | 100 | 25/44/31 | 47.0 % | -21 [-75, +32] |
| 2000 vs 1400 | std | 100 | 77/7/16 | 80.5 % | +246 [+173, +345] |

| Pairing | Score 960 − std | Elo gap 960 − std [95 % CI] |
|---|---|---|

## Castling

Share of games in which a side castled, and the mean move number (full moves) of the castle. Stockfish's own sides for comparison.

| Player | Mode | Std: castled | Std: mean move | 960: castled | 960: mean move |
|---|---|---|---|---|---|
| Maia 1400 | mvm | 92 % (92/100) | 7.8 | – | – |
| Maia 1700 | mvm | 90 % (179/200) | 9.0 | – | – |
| Maia 2000 | mvm | 94 % (94/100) | 8.1 | – | – |

## Blunders

Maia moves losing more than 200 cp by the referee, per 100 Maia moves (the first 6 sampled moves per side included).

| Maia | Mode | Std | 960 |
|---|---|---|---|
| 1400 | mvm | 5.60 (235/4195) | – |
| 1700 | mvm | 6.01 (562/9346) | – |
| 2000 | mvm | 3.44 (145/4217) | – |

## Mapping diagnostics

| Variant | Maia moves | Raw top slot not a legal move (informational) | Positions with two legal moves on one slot | Anomalies (wrong castle, illegal move, diagnostic mismatch) |
|---|---|---|---|---|
| std | 17758 | 11925 (67.15 %) | 0 | 0 |
| 960 | 0 | 0 (0.00 %) | 0 | 0 |

Every case is in events.tsv with its FEN, of the raw top slot only the first 50.

## Game ends

| Mode, variant | Mean plies | Endings |
|---|---|---|
| mvm std | 89 | {adjudication=60, checkmate=89, repetition=47, stalemate=4} |

## Sampled opening moves

Loss by the referee (eval before minus after, mover's view, capped at ±1000 cp).

| Maia | Sampled moves | Loss > 100 cp | Loss > 200 cp |
|---|---|---|---|
| 1400 | 598 | 5.85 % (35) | 2.68 % (16) |
| 1700 | 1200 | 3.42 % (41) | 1.25 % (15) |
| 2000 | 597 | 2.18 % (13) | 0.84 % (5) |

The 20 worst:

| Loss | Maia | Move | FEN | Game, ply |
|---|---|---|---|---|
| 1217 | 2000 | e4f6 | `rnbqkb1r/pppppppp/8/7Q/2B1n3/8/PPPP1PPP/RNB1K1NR b KQkq - 1 3` | mvm-std-a1400-b2000-p16-lw, 5 |
| 729 | 1400 | f3d3 | `r1bqkb1r/pppppppp/5n2/4n3/2B1P3/5Q2/PPPP1PPP/RNB1K1NR w KQkq - 5 4` | mvm-std-a1400-b2000-p12-lw, 6 |
| 472 | 1400 | b1c3 | `rnbqkbnr/p3pppp/8/1p6/2pP4/4P3/1P3PPP/RNBQKBNR w KQkq - 0 6` | mvm-std-a1400-b2000-p07-lw, 10 |
| 472 | 1700 | b1c3 | `rnbqkbnr/p3pppp/8/1p6/2pP4/4P3/1P3PPP/RNBQKBNR w KQkq - 0 6` | mvm-std-a1700-b1700-p33-hw, 10 |
| 342 | 1700 | c6b5 | `rnbqkbnr/p3pppp/2p5/1P6/2pP4/4P3/1P3PPP/RNBQKBNR b KQkq - 0 5` | mvm-std-a1700-b1700-p00-lw, 9 |
| 342 | 1700 | c6b5 | `rnbqkbnr/p3pppp/2p5/1P6/2pP4/4P3/1P3PPP/RNBQKBNR b KQkq - 0 5` | mvm-std-a1700-b1700-p02-hw, 9 |
| 342 | 1400 | c6b5 | `rnbqkbnr/p3pppp/2p5/1P6/2pP4/4P3/1P3PPP/RNBQKBNR b KQkq - 0 5` | mvm-std-a1400-b2000-p03-hw, 9 |
| 342 | 1400 | c6b5 | `rnbqkbnr/p3pppp/2p5/1P6/2pP4/4P3/1P3PPP/RNBQKBNR b KQkq - 0 5` | mvm-std-a1400-b2000-p04-hw, 9 |
| 342 | 2000 | c6b5 | `rnbqkbnr/p3pppp/2p5/1P6/2pP4/4P3/1P3PPP/RNBQKBNR b KQkq - 0 5` | mvm-std-a1400-b2000-p07-lw, 9 |
| 342 | 2000 | c6b5 | `rnbqkbnr/p3pppp/2p5/1P6/2pP4/4P3/1P3PPP/RNBQKBNR b KQkq - 0 5` | mvm-std-a1400-b2000-p09-lw, 9 |
| 342 | 1700 | c6b5 | `rnbqkbnr/p3pppp/2p5/1P6/2pP4/4P3/1P3PPP/RNBQKBNR b KQkq - 0 5` | mvm-std-a1700-b1700-p10-lw, 9 |
| 342 | 1400 | c6b5 | `rnbqkbnr/p3pppp/2p5/1P6/2pP4/4P3/1P3PPP/RNBQKBNR b KQkq - 0 5` | mvm-std-a1400-b2000-p14-hw, 9 |
| 342 | 1400 | c6b5 | `rnbqkbnr/p3pppp/2p5/1P6/2pP4/4P3/1P3PPP/RNBQKBNR b KQkq - 0 5` | mvm-std-a1400-b2000-p16-hw, 9 |
| 342 | 1700 | c6b5 | `rnbqkbnr/p3pppp/2p5/1P6/2pP4/4P3/1P3PPP/RNBQKBNR b KQkq - 0 5` | mvm-std-a1700-b1700-p25-hw, 9 |
| 342 | 1700 | c6b5 | `rnbqkbnr/p3pppp/2p5/1P6/2pP4/4P3/1P3PPP/RNBQKBNR b KQkq - 0 5` | mvm-std-a1700-b1700-p27-lw, 9 |
| 342 | 1700 | c6b5 | `rnbqkbnr/p3pppp/2p5/1P6/2pP4/4P3/1P3PPP/RNBQKBNR b KQkq - 0 5` | mvm-std-a1700-b1700-p30-hw, 9 |
| 342 | 1700 | c6b5 | `rnbqkbnr/p3pppp/2p5/1P6/2pP4/4P3/1P3PPP/RNBQKBNR b KQkq - 0 5` | mvm-std-a1700-b1700-p33-hw, 9 |
| 342 | 1700 | c6b5 | `rnbqkbnr/p3pppp/2p5/1P6/2pP4/4P3/1P3PPP/RNBQKBNR b KQkq - 0 5` | mvm-std-a1700-b1700-p35-hw, 9 |
| 342 | 1700 | c6b5 | `rnbqkbnr/p3pppp/2p5/1P6/2pP4/4P3/1P3PPP/RNBQKBNR b KQkq - 0 5` | mvm-std-a1700-b1700-p36-lw, 9 |
| 342 | 1700 | c6b5 | `rnbqkbnr/p3pppp/2p5/1P6/2pP4/4P3/1P3PPP/RNBQKBNR b KQkq - 0 5` | mvm-std-a1700-b1700-p36-hw, 9 |

