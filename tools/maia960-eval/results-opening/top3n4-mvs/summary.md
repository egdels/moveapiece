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

Games in games.csv: 1200, of them without a result (error): 0.

## Maia against Stockfish

Score and Elo difference from Maia's view, 95 % CI from the position pairs.

| Maia | SF | Variant | Games | +/=/- | Score | Elo diff [95 % CI] |
|---|---|---|---|---|---|---|
| 1400 | 1400 | std | 100 | 73/3/24 | 74.5 % | +186 [+113, +279] |
| 1400 | 1800 | std | 100 | 22/8/70 | 26.0 % | -182 [-280, -105] |
| 1400 | 2200 | std | 100 | 4/6/90 | 7.0 % | -449 [-648, -352] |
| 1700 | 1400 | std | 100 | 81/5/14 | 83.5 % | +282 [+206, +390] |
| 1700 | 1800 | std | 100 | 32/3/65 | 33.5 % | -119 [-199, -50] |
| 1700 | 2200 | std | 100 | 9/7/84 | 12.5 % | -338 [-467, -256] |
| 2000 | 1400 | std | 100 | 92/1/7 | 92.5 % | +436 [+340, +629] |
| 2000 | 1800 | std | 100 | 47/10/43 | 52.0 % | +14 [-55, +83] |
| 2000 | 2200 | std | 100 | 11/10/79 | 16.0 % | -288 [-395, -213] |
| 2300 | 1400 | std | 100 | 94/4/2 | 96.0 % | +552 [+443, +847] |
| 2300 | 1800 | std | 100 | 49/11/40 | 54.5 % | +31 [-38, +103] |
| 2300 | 2200 | std | 100 | 23/14/63 | 30.0 % | -147 [-227, -80] |

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
| Maia 1400 | mvs | 91 % (273/300) | 8.7 | – | – |
| Maia 1700 | mvs | 93 % (278/300) | 8.7 | – | – |
| Maia 2000 | mvs | 93 % (279/300) | 8.9 | – | – |
| Maia 2300 | mvs | 94 % (281/300) | 8.8 | – | – |
| Stockfish 1400 | mvs | 57 % (227/400) | 11.6 | – | – |
| Stockfish 1800 | mvs | 65 % (260/400) | 12.0 | – | – |
| Stockfish 2200 | mvs | 71 % (283/400) | 11.3 | – | – |

## Blunders

Maia moves losing more than 200 cp by the referee, per 100 Maia moves (the first 4 sampled moves per side included).

| Maia | Mode | Std | 960 |
|---|---|---|---|
| 1400 | mvs | 5.87 (756/12874) | – |
| 1700 | mvs | 5.65 (706/12500) | – |
| 2000 | mvs | 4.67 (588/12585) | – |
| 2300 | mvs | 4.58 (617/13474) | – |

## Mapping diagnostics

| Variant | Maia moves | Raw top slot not a legal move (informational) | Positions with two legal moves on one slot | Anomalies (wrong castle, illegal move, diagnostic mismatch) |
|---|---|---|---|---|
| std | 51433 | 39122 (76.06 %) | 0 | 0 |
| 960 | 0 | 0 (0.00 %) | 0 | 0 |

Every case is in events.tsv with its FEN, of the raw top slot only the first 50.

## Game ends

| Mode, variant | Mean plies | Endings |
|---|---|---|
| mvs std | 86 | {adjudication=588, checkmate=530, fifty-moves=2, insufficient-material=12, repetition=50, stalemate=18} |

## Sampled opening moves

Loss by the referee (eval before minus after, mover's view, capped at ±1000 cp).

| Maia | Sampled moves | Loss > 100 cp | Loss > 200 cp |
|---|---|---|---|
| 1400 | 1200 | 1.33 % (16) | 0.25 % (3) |
| 1700 | 1200 | 0.58 % (7) | 0.00 % (0) |
| 2000 | 1200 | 0.67 % (8) | 0.33 % (4) |
| 2300 | 1200 | 0.33 % (4) | 0.08 % (1) |

The 20 worst:

| Loss | Maia | Move | FEN | Game, ply |
|---|---|---|---|---|
| 558 | 1400 | c8d7 | `rnbqkbnr/ppp2ppp/3p4/4N3/Q1P1p3/8/PP1PPPPP/RNB1KB1R b KQkq - 1 4` | mvs-std-m1400-s1400-p44-mb, 7 |
| 397 | 1400 | b1c3 | `r1bqkbnr/ppp1pppp/8/n2P4/8/5N2/PPPP1PPP/RNBQKB1R w KQkq - 1 4` | mvs-std-m1400-s1800-p21-mw, 6 |
| 290 | 2000 | d5d4 | `rnbqkb1r/ppp2ppp/5n2/3pp3/2P5/2N1P3/PP1PQPPP/R1B1KBNR b KQkq - 1 4` | mvs-std-m2000-s1800-p35-mb, 7 |
| 277 | 2000 | b1c3 | `r1bqkbnr/ppp1pppp/8/3P4/1n6/5N2/PPPP1PPP/RNBQKB1R w KQkq - 1 4` | mvs-std-m2000-s1800-p46-mw, 6 |
| 240 | 2300 | b1c3 | `r1bqkb1r/pp1ppppp/2n2n2/2p5/3PP3/5N2/PPP2PPP/RNBQKB1R w KQkq - 1 4` | mvs-std-m2300-s1400-p09-mw, 6 |
| 231 | 2000 | b7b5 | `rnbqkb1r/ppp1pppp/5n2/4N3/2p5/6P1/PP1PPP1P/RNBQKB1R b KQkq - 1 4` | mvs-std-m2000-s1800-p08-mb, 7 |
| 221 | 1400 | f8c5 | `r1bqkb1r/pppp1ppp/2n1pn2/8/2P1P3/2N2N2/PP1P1PPP/R1BQKB1R b KQkq e3 0 4` | mvs-std-m1400-s2200-p11-mb, 7 |
| 208 | 2000 | c7c5 | `rnbqkb1r/ppp1pppp/5n2/1N6/3p4/P4N2/1PPPPPPP/R1BQKB1R b KQkq - 1 4` | mvs-std-m2000-s1400-p36-mb, 7 |
| 191 | 1400 | b8c6 | `rnbqkbnr/pp1p1ppp/4p3/2p5/3PP3/5N2/PPP2PPP/RNBQKB1R b KQkq e3 0 3` | mvs-std-m1400-s2200-p10-mb, 5 |
| 187 | 2000 | b8c6 | `rnbqkbnr/pp1p1ppp/4p3/2p5/3PP3/2N5/PPP2PPP/R1BQKBNR b KQkq d3 0 3` | mvs-std-m2000-s1400-p15-mb, 5 |
| 178 | 1400 | c8g4 | `r1bqkbnr/ppp1pppp/2n5/8/2B1P3/5N2/PP1P1PPP/RNBQK2R b KQkq - 0 4` | mvs-std-m1400-s2200-p08-mb, 7 |
| 162 | 2000 | c1e3 | `r1bqkb1r/pp1ppppp/n4n2/2P5/8/2N5/PPP1PPPP/R1BQKBNR w KQkq - 3 4` | mvs-std-m2000-s2200-p02-mw, 6 |
| 145 | 1700 | e5f3 | `rnbqk2r/pppp1ppp/3b1n2/4N3/4P3/8/PPPP1PPP/RNBQKB1R w KQkq - 1 4` | mvs-std-m1700-s2200-p04-mw, 6 |
| 145 | 1400 | e5f3 | `rnbqk2r/pppp1ppp/3b1n2/4N3/4P3/8/PPPP1PPP/RNBQKB1R w KQkq - 1 4` | mvs-std-m1400-s2200-p17-mw, 6 |
| 140 | 1700 | d8e7 | `rnbqkbnr/pppp1ppp/8/8/8/4pN2/PPP1QPPP/RNB1KB1R b KQkq - 1 4` | mvs-std-m1700-s1800-p31-mb, 7 |
| 137 | 1400 | c8g4 | `rnbqkbnr/pp2pppp/2p5/3P4/8/5N2/PPPP1PPP/RNBQKB1R b KQkq - 1 3` | mvs-std-m1400-s2200-p18-mb, 5 |
| 134 | 2300 | b8c6 | `rnbqk1nr/pp1pppbp/6p1/2p5/3PP3/2N2N2/PPP2PPP/R1BQKB1R b KQkq e3 0 4` | mvs-std-m2300-s1800-p16-mb, 7 |
| 133 | 1700 | f3d4 | `rnbqkbnr/ppp2ppp/8/3P4/4p3/5N2/PPPP1PPP/RNBQKB1R w KQkq - 0 4` | mvs-std-m1700-s2200-p01-mw, 6 |
| 133 | 1700 | f3d4 | `rnbqkbnr/ppp2ppp/8/3P4/4p3/5N2/PPPP1PPP/RNBQKB1R w KQkq - 0 4` | mvs-std-m1700-s2200-p14-mw, 6 |
| 133 | 1400 | f3d4 | `rnbqkbnr/ppp2ppp/8/3P4/4p3/5N2/PPPP1PPP/RNBQKB1R w KQkq - 0 4` | mvs-std-m1400-s1800-p19-mw, 6 |

