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
| Maia's sampled opening moves per side | 2 at temperature 0.5 |
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
| 1700 vs 1700 | std | 100 | 94/6/0 | 97.0 % | +604 [+502, +855] |
| 2000 vs 1400 | std | 100 | 49/48/3 | 73.0 % | +173 [+153, +194] |

| Pairing | Score 960 − std | Elo gap 960 − std [95 % CI] |
|---|---|---|

## Castling

Share of games in which a side castled, and the mean move number (full moves) of the castle. Stockfish's own sides for comparison.

| Player | Mode | Std: castled | Std: mean move | 960: castled | 960: mean move |
|---|---|---|---|---|---|
| Maia 1400 | mvm | 60 % (60/100) | 8.3 | – | – |
| Maia 1700 | mvm | 100 % (200/200) | 8.9 | – | – |
| Maia 2000 | mvm | 99 % (99/100) | 8.0 | – | – |

## Blunders

Maia moves losing more than 200 cp by the referee, per 100 Maia moves (the first 2 sampled moves per side included).

| Maia | Mode | Std | 960 |
|---|---|---|---|
| 1400 | mvm | 8.46 (309/3653) | – |
| 1700 | mvm | 10.48 (472/4505) | – |
| 2000 | mvm | 7.06 (258/3656) | – |

## Mapping diagnostics

| Variant | Maia moves | Raw top slot not a legal move (informational) | Positions with two legal moves on one slot | Anomalies (wrong castle, illegal move, diagnostic mismatch) |
|---|---|---|---|---|
| std | 11814 | 7808 (66.09 %) | 0 | 0 |
| 960 | 0 | 0 (0.00 %) | 0 | 0 |

Every case is in events.tsv with its FEN, of the raw top slot only the first 50.

## Game ends

| Mode, variant | Mean plies | Endings |
|---|---|---|
| mvm std | 59 | {adjudication=24, checkmate=122, repetition=54} |

## Sampled opening moves

Loss by the referee (eval before minus after, mover's view, capped at ±1000 cp).

| Maia | Sampled moves | Loss > 100 cp | Loss > 200 cp |
|---|---|---|---|
| 1400 | 200 | 1.00 % (2) | 0.50 % (1) |
| 1700 | 400 | 0.00 % (0) | 0.00 % (0) |
| 2000 | 200 | 0.00 % (0) | 0.00 % (0) |

The 20 worst:

| Loss | Maia | Move | FEN | Game, ply |
|---|---|---|---|---|
| 207 | 1400 | f1c4 | `rnbqkb1r/pppppppp/5n2/8/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 1 2` | mvm-std-a1400-b2000-p16-lw, 2 |
| 141 | 1400 | d1f3 | `rnbqkb1r/pppppppp/5n2/8/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 1 2` | mvm-std-a1400-b2000-p22-lw, 2 |
| 75 | 1400 | f1c4 | `r1bqkbnr/pppppppp/2n5/8/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 1 2` | mvm-std-a1400-b2000-p12-lw, 2 |
| 57 | 2000 | g8f6 | `rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1` | mvm-std-a1400-b2000-p16-lw, 1 |
| 57 | 2000 | g8f6 | `rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1` | mvm-std-a1400-b2000-p22-lw, 1 |
| 39 | 1700 | d7d5 | `rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1` | mvm-std-a1700-b1700-p19-lw, 1 |
| 39 | 1700 | d7d5 | `rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1` | mvm-std-a1700-b1700-p20-hw, 1 |
| 39 | 1700 | d7d5 | `rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1` | mvm-std-a1700-b1700-p23-hw, 1 |
| 36 | 2000 | b7b6 | `rnbqkbnr/pppppppp/8/8/2P5/8/PP1PPPPP/RNBQKBNR b KQkq c3 0 1` | mvm-std-a1400-b2000-p29-lw, 1 |
| 36 | 1400 | d2d3 | `rnbqkbnr/p1pppppp/1p6/8/2P5/8/PP1PPPPP/RNBQKBNR w KQkq - 0 2` | mvm-std-a1400-b2000-p29-lw, 2 |
| 23 | 2000 | b8c6 | `rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1` | mvm-std-a1400-b2000-p12-lw, 1 |
| 22 | 1400 | c2c4 | `rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1` | mvm-std-a1400-b2000-p29-lw, 0 |
| 21 | 2000 | c7c6 | `rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1` | mvm-std-a1400-b2000-p03-lw, 1 |
| 21 | 2000 | c7c6 | `rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1` | mvm-std-a1400-b2000-p15-lw, 1 |
| 21 | 1700 | c7c6 | `rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1` | mvm-std-a1700-b1700-p34-hw, 1 |
| 21 | 1700 | c7c6 | `rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1` | mvm-std-a1700-b1700-p35-lw, 1 |
| 21 | 2000 | c7c6 | `rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1` | mvm-std-a1400-b2000-p37-lw, 1 |
| 21 | 1700 | c7c6 | `rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1` | mvm-std-a1700-b1700-p42-hw, 1 |
| 21 | 1700 | c7c6 | `rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1` | mvm-std-a1700-b1700-p44-lw, 1 |
| 16 | 2000 | e7e6 | `rnbqkbnr/pp1ppppp/8/2p5/4P3/5N2/PPPP1PPP/RNBQKB1R b KQkq - 1 2` | mvm-std-a1400-b2000-p04-lw, 3 |

