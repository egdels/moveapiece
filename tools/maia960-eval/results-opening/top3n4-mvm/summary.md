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
| Maia's sampled opening moves per side | 4 at temperature 1.0, top 3 |
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
| 1700 vs 1700 | std | 100 | 81/13/6 | 87.5 % | +338 [+266, +444] |
| 2000 vs 1400 | std | 100 | 55/21/24 | 65.5 % | +111 [+54, +175] |

| Pairing | Score 960 − std | Elo gap 960 − std [95 % CI] |
|---|---|---|

## Castling

Share of games in which a side castled, and the mean move number (full moves) of the castle. Stockfish's own sides for comparison.

| Player | Mode | Std: castled | Std: mean move | 960: castled | 960: mean move |
|---|---|---|---|---|---|
| Maia 1400 | mvm | 66 % (66/100) | 8.6 | – | – |
| Maia 1700 | mvm | 98 % (196/200) | 8.6 | – | – |
| Maia 2000 | mvm | 100 % (100/100) | 8.3 | – | – |

## Blunders

Maia moves losing more than 200 cp by the referee, per 100 Maia moves (the first 4 sampled moves per side included).

| Maia | Mode | Std | 960 |
|---|---|---|---|
| 1400 | mvm | 6.72 (282/4196) | – |
| 1700 | mvm | 7.59 (421/5545) | – |
| 2000 | mvm | 6.13 (257/4195) | – |

## Mapping diagnostics

| Variant | Maia moves | Raw top slot not a legal move (informational) | Positions with two legal moves on one slot | Anomalies (wrong castle, illegal move, diagnostic mismatch) |
|---|---|---|---|---|
| std | 13936 | 9221 (66.17 %) | 0 | 0 |
| 960 | 0 | 0 (0.00 %) | 0 | 0 |

Every case is in events.tsv with its FEN, of the raw top slot only the first 50.

## Game ends

| Mode, variant | Mean plies | Endings |
|---|---|---|
| mvm std | 70 | {adjudication=54, checkmate=112, repetition=33, stalemate=1} |

## Sampled opening moves

Loss by the referee (eval before minus after, mover's view, capped at ±1000 cp).

| Maia | Sampled moves | Loss > 100 cp | Loss > 200 cp |
|---|---|---|---|
| 1400 | 400 | 0.00 % (0) | 0.00 % (0) |
| 1700 | 800 | 0.00 % (0) | 0.00 % (0) |
| 2000 | 400 | 0.00 % (0) | 0.00 % (0) |

The 20 worst:

| Loss | Maia | Move | FEN | Game, ply |
|---|---|---|---|---|
| 93 | 2000 | f7f5 | `rn1qkbnr/pbpp1ppp/1p2p3/8/2PPP3/3B4/PP3PPP/RNBQK1NR b KQkq - 2 4` | mvm-std-a1400-b2000-p05-lw, 7 |
| 93 | 2000 | f7f5 | `rn1qkbnr/pbpp1ppp/1p2p3/8/2PPP3/3B4/PP3PPP/RNBQK1NR b KQkq - 2 4` | mvm-std-a1400-b2000-p17-lw, 7 |
| 93 | 2000 | f7f5 | `rn1qkbnr/pbpp1ppp/1p2p3/8/2PPP3/3B4/PP3PPP/RNBQK1NR b KQkq - 2 4` | mvm-std-a1400-b2000-p19-lw, 7 |
| 93 | 2000 | f7f5 | `rn1qkbnr/pbpp1ppp/1p2p3/8/2PPP3/3B4/PP3PPP/RNBQK1NR b KQkq - 2 4` | mvm-std-a1400-b2000-p21-lw, 7 |
| 93 | 2000 | b8d7 | `rnbqkbnr/pp3ppp/4p3/1Bpp4/4P3/2N2N2/PPPP1PPP/R1BQK2R b KQkq - 1 4` | mvm-std-a1400-b2000-p29-lw, 7 |
| 68 | 1400 | c2c3 | `rnbqkbnr/pp1p1ppp/4p3/2p5/3PP3/8/PPP2PPP/RNBQKBNR w KQkq c6 0 3` | mvm-std-a1400-b2000-p31-lw, 4 |
| 65 | 2000 | b7b6 | `rnbqkbnr/pppp1ppp/4p3/8/3PP3/8/PPP2PPP/RNBQKBNR b KQkq e3 0 2` | mvm-std-a1400-b2000-p05-lw, 3 |
| 65 | 2000 | b7b6 | `rnbqkbnr/pppp1ppp/4p3/8/3PP3/8/PPP2PPP/RNBQKBNR b KQkq e3 0 2` | mvm-std-a1400-b2000-p17-lw, 3 |
| 65 | 2000 | b7b6 | `rnbqkbnr/pppp1ppp/4p3/8/3PP3/8/PPP2PPP/RNBQKBNR b KQkq e3 0 2` | mvm-std-a1400-b2000-p19-lw, 3 |
| 65 | 2000 | b7b6 | `rnbqkbnr/pppp1ppp/4p3/8/3PP3/8/PPP2PPP/RNBQKBNR b KQkq e3 0 2` | mvm-std-a1400-b2000-p21-lw, 3 |
| 60 | 2000 | d7d5 | `rnbqkbnr/pp1p1ppp/4p3/2p5/4P3/2N2N2/PPPP1PPP/R1BQKB1R b KQkq - 1 3` | mvm-std-a1400-b2000-p29-lw, 5 |
| 58 | 1700 | g7f6 | `rnbqkb1r/pppp1pp1/4pB1p/8/3P4/5N2/PPP1PPPP/RN1QKB1R b KQkq - 0 4` | mvm-std-a1700-b1700-p29-hw, 7 |
| 58 | 1700 | g7f6 | `rnbqkb1r/pppp1pp1/4pB1p/8/3P4/5N2/PPP1PPPP/RN1QKB1R b KQkq - 0 4` | mvm-std-a1700-b1700-p40-lw, 7 |
| 54 | 1700 | g7g6 | `rnbqkb1r/pp2pppp/3p1n2/2p5/2B1P3/2N2N2/PPPP1PPP/R1BQK2R b KQkq - 3 4` | mvm-std-a1700-b1700-p03-hw, 7 |
| 54 | 1700 | g7g6 | `rnbqkb1r/pp2pppp/3p1n2/2p5/2B1P3/2N2N2/PPPP1PPP/R1BQK2R b KQkq - 3 4` | mvm-std-a1700-b1700-p14-lw, 7 |
| 54 | 1700 | g7g6 | `rnbqkb1r/pp2pppp/3p1n2/2p5/2B1P3/2N2N2/PPPP1PPP/R1BQK2R b KQkq - 3 4` | mvm-std-a1700-b1700-p26-hw, 7 |
| 54 | 1700 | g7g6 | `rnbqkb1r/pp2pppp/3p1n2/2p5/2B1P3/2N2N2/PPPP1PPP/R1BQK2R b KQkq - 3 4` | mvm-std-a1700-b1700-p33-lw, 7 |
| 53 | 2000 | c7c5 | `rnbqkbnr/pppp1ppp/4p3/8/3PP3/8/PPP2PPP/RNBQKBNR b KQkq e3 0 2` | mvm-std-a1400-b2000-p31-lw, 3 |
| 41 | 1700 | e7e5 | `r1bqkb1r/pp1ppppp/2n2n2/2p5/2P5/2N1P3/PP1PNPPP/R1BQKB1R b KQkq - 2 4` | mvm-std-a1700-b1700-p48-lw, 7 |
| 38 | 1400 | d5c4 | `rnbqkb1r/ppp1pppp/5n2/3p4/2PP4/4P3/PP3PPP/RNBQKBNR b KQkq - 0 3` | mvm-std-a1400-b2000-p21-hw, 5 |

