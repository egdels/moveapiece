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
| 1700 vs 1700 | std | 100 | 65/5/30 | 67.5 % | +127 [+65, +197] |
| 2000 vs 1400 | std | 100 | 49/44/7 | 71.0 % | +156 [+113, +203] |

| Pairing | Score 960 − std | Elo gap 960 − std [95 % CI] |
|---|---|---|

## Castling

Share of games in which a side castled, and the mean move number (full moves) of the castle. Stockfish's own sides for comparison.

| Player | Mode | Std: castled | Std: mean move | 960: castled | 960: mean move |
|---|---|---|---|---|---|
| Maia 1400 | mvm | 74 % (74/100) | 7.2 | – | – |
| Maia 1700 | mvm | 97 % (193/200) | 8.5 | – | – |
| Maia 2000 | mvm | 92 % (92/100) | 7.7 | – | – |

## Blunders

Maia moves losing more than 200 cp by the referee, per 100 Maia moves (the first 2 sampled moves per side included).

| Maia | Mode | Std | 960 |
|---|---|---|---|
| 1400 | mvm | 6.88 (254/3693) | – |
| 1700 | mvm | 6.74 (401/5946) | – |
| 2000 | mvm | 5.68 (210/3697) | – |

## Mapping diagnostics

| Variant | Maia moves | Raw top slot not a legal move (informational) | Positions with two legal moves on one slot | Anomalies (wrong castle, illegal move, diagnostic mismatch) |
|---|---|---|---|---|
| std | 13336 | 8975 (67.30 %) | 0 | 0 |
| 960 | 0 | 0 (0.00 %) | 0 | 0 |

Every case is in events.tsv with its FEN, of the raw top slot only the first 50.

## Game ends

| Mode, variant | Mean plies | Endings |
|---|---|---|
| mvm std | 67 | {adjudication=45, checkmate=106, repetition=48, stalemate=1} |

## Sampled opening moves

Loss by the referee (eval before minus after, mover's view, capped at ±1000 cp).

| Maia | Sampled moves | Loss > 100 cp | Loss > 200 cp |
|---|---|---|---|
| 1400 | 200 | 0.50 % (1) | 0.00 % (0) |
| 1700 | 400 | 1.00 % (4) | 0.00 % (0) |
| 2000 | 200 | 1.00 % (2) | 0.50 % (1) |

The 20 worst:

| Loss | Maia | Move | FEN | Game, ply |
|---|---|---|---|---|
| 210 | 2000 | c6b8 | `r1bqkbnr/pppppppp/2n5/8/6P1/8/PPPPPPBP/RNBQK1NR b KQkq - 2 2` | mvm-std-a1400-b2000-p21-lw, 3 |
| 178 | 1700 | g7g5 | `rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1` | mvm-std-a1700-b1700-p14-hw, 1 |
| 177 | 1700 | g8f6 | `r1bqkbnr/pppppppp/2n5/8/6P1/8/PPPPPPBP/RNBQK1NR b KQkq - 2 2` | mvm-std-a1700-b1700-p46-hw, 3 |
| 164 | 1400 | g2g4 | `rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1` | mvm-std-a1400-b2000-p21-lw, 0 |
| 164 | 1700 | g2g4 | `rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1` | mvm-std-a1700-b1700-p46-hw, 0 |
| 107 | 2000 | a8a6 | `rnbqkbnr/1ppppppp/8/p7/P7/1P6/2PPPPPP/RNBQKBNR b KQkq - 0 2` | mvm-std-a1400-b2000-p29-lw, 3 |
| 107 | 1700 | f7f6 | `rnbqkbnr/pppp1ppp/4p3/8/4P3/5N2/PPPP1PPP/RNBQKB1R b KQkq - 1 2` | mvm-std-a1700-b1700-p49-lw, 3 |
| 94 | 2000 | h8g8 | `rnbqkb1r/pppppppp/5n2/6N1/8/8/PPPPPPPP/RNBQKB1R b KQkq - 3 2` | mvm-std-a1400-b2000-p22-lw, 3 |
| 87 | 1400 | f3g5 | `rnbqkb1r/pppppppp/5n2/8/8/5N2/PPPPPPPP/RNBQKB1R w KQkq - 2 2` | mvm-std-a1400-b2000-p16-lw, 2 |
| 87 | 1400 | f3g5 | `rnbqkb1r/pppppppp/5n2/8/8/5N2/PPPPPPPP/RNBQKB1R w KQkq - 2 2` | mvm-std-a1400-b2000-p22-lw, 2 |
| 85 | 1700 | g1h3 | `rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1` | mvm-std-a1700-b1700-p48-lw, 0 |
| 73 | 1700 | g8h6 | `rnbqkbnr/pppppppp/8/8/8/7N/PPPPPPPP/RNBQKB1R b KQkq - 1 1` | mvm-std-a1700-b1700-p48-lw, 1 |
| 69 | 1700 | g8f6 | `rnbqkbnr/pppp1ppp/4p3/8/4P3/5N2/PPPP1PPP/RNBQKB1R b KQkq - 1 2` | mvm-std-a1700-b1700-p01-hw, 3 |
| 65 | 2000 | a7a5 | `rnbqkbnr/pppppppp/8/8/P7/8/1PPPPPPP/RNBQKBNR b KQkq a3 0 1` | mvm-std-a1400-b2000-p29-lw, 1 |
| 64 | 1700 | b2b4 | `rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1` | mvm-std-a1700-b1700-p33-hw, 0 |
| 63 | 1400 | f3e5 | `rnbqkb1r/pppppppp/5n2/8/8/5N2/PPPPPPPP/RNBQKB1R w KQkq - 2 2` | mvm-std-a1400-b2000-p12-lw, 2 |
| 63 | 1400 | b2b3 | `rnbqkbnr/1ppppppp/8/p7/P7/8/1PPPPPPP/RNBQKBNR w KQkq a6 0 2` | mvm-std-a1400-b2000-p29-lw, 2 |
| 60 | 1700 | f1c4 | `rnbqkbnr/pppppp1p/8/6p1/4P3/8/PPPP1PPP/RNBQKBNR w KQkq g6 0 2` | mvm-std-a1700-b1700-p14-hw, 2 |
| 57 | 1400 | b8c6 | `rnbqkbnr/pppp1ppp/4p3/8/4P3/5N2/PPPP1PPP/RNBQKB1R b KQkq - 1 2` | mvm-std-a1400-b2000-p18-hw, 3 |
| 57 | 1400 | b8c6 | `rnbqkbnr/pppp1ppp/4p3/8/4P3/5N2/PPPP1PPP/RNBQKB1R b KQkq - 1 2` | mvm-std-a1400-b2000-p32-hw, 3 |

