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
| Maia's sampled opening moves per side | 2 at temperature 0.7 |
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
| 1700 vs 1700 | std | 100 | 83/8/9 | 87.0 % | +330 [+254, +444] |
| 2000 vs 1400 | std | 100 | 50/49/1 | 74.5 % | +186 [+163, +211] |

| Pairing | Score 960 − std | Elo gap 960 − std [95 % CI] |
|---|---|---|

## Castling

Share of games in which a side castled, and the mean move number (full moves) of the castle. Stockfish's own sides for comparison.

| Player | Mode | Std: castled | Std: mean move | 960: castled | 960: mean move |
|---|---|---|---|---|---|
| Maia 1400 | mvm | 65 % (65/100) | 7.4 | – | – |
| Maia 1700 | mvm | 100 % (200/200) | 8.6 | – | – |
| Maia 2000 | mvm | 96 % (96/100) | 7.5 | – | – |

## Blunders

Maia moves losing more than 200 cp by the referee, per 100 Maia moves (the first 2 sampled moves per side included).

| Maia | Mode | Std | 960 |
|---|---|---|---|
| 1400 | mvm | 8.49 (303/3568) | – |
| 1700 | mvm | 8.46 (441/5212) | – |
| 2000 | mvm | 6.93 (248/3577) | – |

## Mapping diagnostics

| Variant | Maia moves | Raw top slot not a legal move (informational) | Positions with two legal moves on one slot | Anomalies (wrong castle, illegal move, diagnostic mismatch) |
|---|---|---|---|---|
| std | 12357 | 8238 (66.67 %) | 0 | 0 |
| 960 | 0 | 0 (0.00 %) | 0 | 0 |

Every case is in events.tsv with its FEN, of the raw top slot only the first 50.

## Game ends

| Mode, variant | Mean plies | Endings |
|---|---|---|
| mvm std | 62 | {adjudication=29, checkmate=114, repetition=57} |

## Sampled opening moves

Loss by the referee (eval before minus after, mover's view, capped at ±1000 cp).

| Maia | Sampled moves | Loss > 100 cp | Loss > 200 cp |
|---|---|---|---|
| 1400 | 200 | 0.50 % (1) | 0.50 % (1) |
| 1700 | 400 | 0.00 % (0) | 0.00 % (0) |
| 2000 | 200 | 1.00 % (2) | 0.00 % (0) |

The 20 worst:

| Loss | Maia | Move | FEN | Game, ply |
|---|---|---|---|---|
| 212 | 1400 | g1f3 | `rnbqkbnr/ppppp1pp/8/5p2/4P3/8/PPPP1PPP/RNBQKBNR w KQkq f6 0 2` | mvm-std-a1400-b2000-p19-lw, 2 |
| 186 | 2000 | f7f5 | `rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1` | mvm-std-a1400-b2000-p19-lw, 1 |
| 106 | 2000 | a8a6 | `rnbqkbnr/1ppppppp/8/p7/P7/1P6/2PPPPPP/RNBQKBNR b KQkq a3 0 2` | mvm-std-a1400-b2000-p29-lw, 3 |
| 69 | 1400 | g8f6 | `rnbqkbnr/pppp1ppp/4p3/8/4P3/5N2/PPPP1PPP/RNBQKB1R b KQkq - 1 2` | mvm-std-a1400-b2000-p21-hw, 3 |
| 69 | 1400 | g8f6 | `rnbqkbnr/pppp1ppp/4p3/8/4P3/5N2/PPPP1PPP/RNBQKB1R b KQkq - 1 2` | mvm-std-a1400-b2000-p34-hw, 3 |
| 63 | 1400 | f3e5 | `rnbqkb1r/pppppppp/5n2/8/8/5N2/PPPPPPPP/RNBQKB1R w KQkq - 2 2` | mvm-std-a1400-b2000-p22-lw, 2 |
| 53 | 1700 | f2f4 | `rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1` | mvm-std-a1700-b1700-p30-lw, 0 |
| 52 | 1400 | b2b3 | `rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1` | mvm-std-a1400-b2000-p29-lw, 0 |
| 52 | 1700 | g7g6 | `rnbqkbnr/pppp1ppp/4p3/8/4P3/5N2/PPPP1PPP/RNBQKB1R b KQkq - 1 2` | mvm-std-a1700-b1700-p46-hw, 3 |
| 49 | 2000 | b8c6 | `rnbqkb1r/pppppppp/5n2/4N3/8/8/PPPPPPPP/RNBQKB1R b KQkq - 3 2` | mvm-std-a1400-b2000-p22-lw, 3 |
| 48 | 2000 | a7a5 | `rnbqkbnr/pppppppp/8/8/8/1P6/P1PPPPPP/RNBQKBNR b KQkq - 0 1` | mvm-std-a1400-b2000-p29-lw, 1 |
| 48 | 1700 | d7d6 | `rnbqkbnr/pppp1ppp/4p3/8/4P3/5N2/PPPP1PPP/RNBQKB1R b KQkq - 1 2` | mvm-std-a1700-b1700-p29-hw, 3 |
| 48 | 1700 | d7d6 | `rnbqkbnr/pppp1ppp/4p3/8/4P3/5N2/PPPP1PPP/RNBQKB1R b KQkq - 1 2` | mvm-std-a1700-b1700-p40-lw, 3 |
| 48 | 1700 | d7d6 | `rnbqkbnr/pppp1ppp/4p3/8/4P3/5N2/PPPP1PPP/RNBQKB1R b KQkq - 1 2` | mvm-std-a1700-b1700-p47-lw, 3 |
| 46 | 1700 | c7c5 | `rnbqkbnr/pppppppp/8/8/3P4/8/PPP1PPPP/RNBQKBNR b KQkq d3 0 1` | mvm-std-a1700-b1700-p33-hw, 1 |
| 46 | 1700 | c7c5 | `rnbqkbnr/pppppppp/8/8/3P4/8/PPP1PPPP/RNBQKBNR b KQkq d3 0 1` | mvm-std-a1700-b1700-p36-hw, 1 |
| 45 | 1400 | a2a4 | `rnbqkbnr/1ppppppp/8/p7/8/1P6/P1PPPPPP/RNBQKBNR w KQkq a6 0 2` | mvm-std-a1400-b2000-p29-lw, 2 |
| 44 | 2000 | g7g6 | `rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1` | mvm-std-a1400-b2000-p05-lw, 1 |
| 44 | 1700 | g7g6 | `rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1` | mvm-std-a1700-b1700-p17-hw, 1 |
| 44 | 2000 | g7g6 | `rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1` | mvm-std-a1400-b2000-p21-lw, 1 |

