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
| Maia's sampled opening moves per side | 6 at temperature 0.7 |
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
| 1700 vs 1700 | std | 100 | 22/43/35 | 43.5 % | -45 [-101, +8] |
| 2000 vs 1400 | std | 100 | 68/13/19 | 74.5 % | +186 [+125, +260] |

| Pairing | Score 960 − std | Elo gap 960 − std [95 % CI] |
|---|---|---|

## Castling

Share of games in which a side castled, and the mean move number (full moves) of the castle. Stockfish's own sides for comparison.

| Player | Mode | Std: castled | Std: mean move | 960: castled | 960: mean move |
|---|---|---|---|---|---|
| Maia 1400 | mvm | 90 % (90/100) | 8.6 | – | – |
| Maia 1700 | mvm | 92 % (183/200) | 9.5 | – | – |
| Maia 2000 | mvm | 92 % (92/100) | 9.7 | – | – |

## Blunders

Maia moves losing more than 200 cp by the referee, per 100 Maia moves (the first 6 sampled moves per side included).

| Maia | Mode | Std | 960 |
|---|---|---|---|
| 1400 | mvm | 4.72 (199/4214) | – |
| 1700 | mvm | 5.31 (444/8366) | – |
| 2000 | mvm | 3.52 (149/4233) | – |

## Mapping diagnostics

| Variant | Maia moves | Raw top slot not a legal move (informational) | Positions with two legal moves on one slot | Anomalies (wrong castle, illegal move, diagnostic mismatch) |
|---|---|---|---|---|
| std | 16813 | 11647 (69.27 %) | 0 | 0 |
| 960 | 0 | 0 (0.00 %) | 0 | 0 |

Every case is in events.tsv with its FEN, of the raw top slot only the first 50.

## Game ends

| Mode, variant | Mean plies | Endings |
|---|---|---|
| mvm std | 84 | {adjudication=45, checkmate=99, repetition=55, stalemate=1} |

## Sampled opening moves

Loss by the referee (eval before minus after, mover's view, capped at ±1000 cp).

| Maia | Sampled moves | Loss > 100 cp | Loss > 200 cp |
|---|---|---|---|
| 1400 | 600 | 12.00 % (72) | 4.50 % (27) |
| 1700 | 1200 | 6.75 % (81) | 2.58 % (31) |
| 2000 | 600 | 3.83 % (23) | 0.83 % (5) |

The 20 worst:

| Loss | Maia | Move | FEN | Game, ply |
|---|---|---|---|---|
| 824 | 2000 | d8e8 | `r1bq1bkr/ppppp1pp/2n2n2/3N4/8/8/PPPPPPPP/R1BQKB1R b KQ - 3 5` | mvm-std-a1400-b2000-p22-lw, 9 |
| 790 | 1400 | f3h4 | `rn1qkbnr/pp3ppp/2p1p3/3pPb2/3P4/5N2/PPP2PPP/RNBQKB1R w KQkq - 0 5` | mvm-std-a1400-b2000-p04-lw, 8 |
| 790 | 1400 | f3h4 | `rn1qkbnr/pp3ppp/2p1p3/3pPb2/3P4/5N2/PPP2PPP/RNBQKB1R w KQkq - 0 5` | mvm-std-a1400-b2000-p40-lw, 8 |
| 790 | 1400 | f3h4 | `rn1qkbnr/pp3ppp/2p1p3/3pPb2/3P4/5N2/PPP2PPP/RNBQKB1R w KQkq - 0 5` | mvm-std-a1400-b2000-p44-lw, 8 |
| 651 | 1700 | a3b4 | `rnbqkbnr/1p1p1ppp/8/2P1p3/1p6/P7/2P1PPPP/RNBQKBNR w KQkq - 0 5` | mvm-std-a1700-b1700-p33-hw, 8 |
| 620 | 1400 | e5f7 | `r1bqkb1r/pppppppp/2n2n2/4N3/8/8/PPPPPPPP/RNBQKB1R w KQkq - 4 3` | mvm-std-a1400-b2000-p22-lw, 4 |
| 614 | 1400 | f3d4 | `r1bqk2r/pppp1ppp/2n2n2/2b5/2BpP3/2N2N2/PPP2PPP/R1BQK2R w KQkq - 0 6` | mvm-std-a1400-b2000-p01-lw, 10 |
| 614 | 1400 | f3d4 | `r1bqk2r/pppp1ppp/2n2n2/2b5/2BpP3/2N2N2/PPP2PPP/R1BQK2R w KQkq - 0 6` | mvm-std-a1400-b2000-p08-lw, 10 |
| 614 | 1400 | f3d4 | `r1bqk2r/pppp1ppp/2n2n2/2b5/2BpP3/2N2N2/PPP2PPP/R1BQK2R w KQkq - 0 6` | mvm-std-a1400-b2000-p26-lw, 10 |
| 472 | 1400 | b1c3 | `rnbqkbnr/p3pppp/8/1p6/2pP4/4P3/1P3PPP/RNBQKBNR w KQkq - 0 6` | mvm-std-a1400-b2000-p09-lw, 10 |
| 472 | 1700 | b1c3 | `rnbqkbnr/p3pppp/8/1p6/2pP4/4P3/1P3PPP/RNBQKBNR w KQkq - 0 6` | mvm-std-a1700-b1700-p10-lw, 10 |
| 472 | 1400 | b1c3 | `rnbqkbnr/p3pppp/8/1p6/2pP4/4P3/1P3PPP/RNBQKBNR w KQkq - 0 6` | mvm-std-a1400-b2000-p30-lw, 10 |
| 472 | 1700 | b1c3 | `rnbqkbnr/p3pppp/8/1p6/2pP4/4P3/1P3PPP/RNBQKBNR w KQkq - 0 6` | mvm-std-a1700-b1700-p35-hw, 10 |
| 472 | 1700 | b1c3 | `rnbqkbnr/p3pppp/8/1p6/2pP4/4P3/1P3PPP/RNBQKBNR w KQkq - 0 6` | mvm-std-a1700-b1700-p36-lw, 10 |
| 472 | 1700 | b1c3 | `rnbqkbnr/p3pppp/8/1p6/2pP4/4P3/1P3PPP/RNBQKBNR w KQkq - 0 6` | mvm-std-a1700-b1700-p45-hw, 10 |
| 450 | 1700 | c6d4 | `r1bqk2r/pppp1ppp/2n5/2b1p3/2B1P1n1/2NP1N2/PPP2PPP/R1BQ1RK1 b kq - 2 6` | mvm-std-a1700-b1700-p33-lw, 11 |
| 434 | 1400 | c3d5 | `r1bq1bkr/ppppp1pp/2n2n2/8/8/2N5/PPPPPPPP/R1BQKB1R w KQ - 2 5` | mvm-std-a1400-b2000-p22-lw, 8 |
| 422 | 1700 | b1c3 | `rnbqk2r/ppppppbp/5np1/8/2B1P3/5Q2/PPPP1PPP/RNB1K1NR w KQkq - 4 4` | mvm-std-a1700-b1700-p17-hw, 6 |
| 404 | 1700 | b7b6 | `1nbqkbnr/1p1p1ppp/8/2P1p3/1P6/4P3/2P2PPP/rNBQKBNR b Kk - 0 6` | mvm-std-a1700-b1700-p33-hw, 11 |
| 396 | 2000 | c6b4 | `r1bqkbnr/pp2pppp/2n5/3P4/8/8/PP2PPPP/RNBQKBNR b KQkq - 2 5` | mvm-std-a1400-b2000-p39-lw, 9 |

