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

Games in games.csv: 1200, of them without a result (error): 0.

## Maia against Stockfish

Score and Elo difference from Maia's view, 95 % CI from the position pairs.

| Maia | SF | Variant | Games | +/=/- | Score | Elo diff [95 % CI] |
|---|---|---|---|---|---|---|
| 1400 | 1400 | std | 100 | 71/3/26 | 72.5 % | +168 [+103, +247] |
| 1400 | 1800 | std | 100 | 15/6/79 | 18.0 % | -263 [-371, -187] |
| 1400 | 2200 | std | 100 | 3/5/92 | 5.5 % | -494 [-703, -396] |
| 1700 | 1400 | std | 100 | 85/4/11 | 87.0 % | +330 [+247, +462] |
| 1700 | 1800 | std | 100 | 25/8/67 | 29.0 % | -156 [-240, -86] |
| 1700 | 2200 | std | 100 | 16/5/79 | 18.5 % | -258 [-373, -177] |
| 2000 | 1400 | std | 100 | 83/1/16 | 83.5 % | +282 [+199, +404] |
| 2000 | 1800 | std | 100 | 38/3/59 | 39.5 % | -74 [-135, -17] |
| 2000 | 2200 | std | 100 | 15/16/69 | 23.0 % | -210 [-284, -150] |
| 2300 | 1400 | std | 100 | 87/3/10 | 88.5 % | +354 [+259, +526] |
| 2300 | 1800 | std | 100 | 44/7/49 | 47.5 % | -17 [-81, +45] |
| 2300 | 2200 | std | 100 | 24/14/62 | 31.0 % | -139 [-222, -69] |

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
| Maia 1400 | mvs | 91 % (274/300) | 8.9 | – | – |
| Maia 1700 | mvs | 93 % (280/300) | 8.7 | – | – |
| Maia 2000 | mvs | 92 % (275/300) | 8.0 | – | – |
| Maia 2300 | mvs | 95 % (284/300) | 7.9 | – | – |
| Stockfish 1400 | mvs | 57 % (228/400) | 11.2 | – | – |
| Stockfish 1800 | mvs | 68 % (271/400) | 11.8 | – | – |
| Stockfish 2200 | mvs | 69 % (275/400) | 11.5 | – | – |

## Blunders

Maia moves losing more than 200 cp by the referee, per 100 Maia moves (the first 4 sampled moves per side included).

| Maia | Mode | Std | 960 |
|---|---|---|---|
| 1400 | mvs | 6.22 (767/12335) | – |
| 1700 | mvs | 5.45 (704/12928) | – |
| 2000 | mvs | 4.98 (658/13202) | – |
| 2300 | mvs | 4.48 (589/13133) | – |

## Mapping diagnostics

| Variant | Maia moves | Raw top slot not a legal move (informational) | Positions with two legal moves on one slot | Anomalies (wrong castle, illegal move, diagnostic mismatch) |
|---|---|---|---|---|
| std | 51598 | 39021 (75.63 %) | 0 | 0 |
| 960 | 0 | 0 (0.00 %) | 0 | 0 |

Every case is in events.tsv with its FEN, of the raw top slot only the first 50.

## Game ends

| Mode, variant | Mean plies | Endings |
|---|---|---|
| mvs std | 86 | {adjudication=548, checkmate=577, fifty-moves=2, insufficient-material=13, repetition=38, stalemate=22} |

## Sampled opening moves

Loss by the referee (eval before minus after, mover's view, capped at ±1000 cp).

| Maia | Sampled moves | Loss > 100 cp | Loss > 200 cp |
|---|---|---|---|
| 1400 | 1200 | 1.83 % (22) | 0.58 % (7) |
| 1700 | 1200 | 1.42 % (17) | 0.25 % (3) |
| 2000 | 1200 | 1.08 % (13) | 0.17 % (2) |
| 2300 | 1200 | 0.67 % (8) | 0.17 % (2) |

The 20 worst:

| Loss | Maia | Move | FEN | Game, ply |
|---|---|---|---|---|
| 831 | 1400 | f4f5 | `r1bqkbnr/pppp1ppp/2n5/4p3/4PP2/8/PPPP2PP/RNBQKBNR w KQkq e6 0 3` | mvs-std-m1400-s1400-p39-mw, 4 |
| 736 | 1400 | d4f5 | `rnbqkb1r/ppp1pppp/5n2/3p4/3N4/8/PPPPPPPP/RNBQKB1R w KQkq d6 0 3` | mvs-std-m1400-s2200-p16-mw, 4 |
| 618 | 2300 | b8a6 | `rnbqkbnr/pp2pppp/8/1N1p4/3p1B2/8/PPP1PPPP/R2QKBNR b KQkq - 1 4` | mvs-std-m2300-s1400-p22-mb, 7 |
| 526 | 1700 | c4f7 | `rnbqkb1r/ppp1pppp/3p1n2/8/2B1P3/8/PPPP1PPP/RNBQK1NR w KQkq - 2 3` | mvs-std-m1700-s1400-p43-mw, 4 |
| 487 | 1700 | c4f7 | `rnbqkb1r/pppp1ppp/5n2/4p3/2B1P3/8/PPPP1PPP/RNBQK1NR w KQkq - 2 3` | mvs-std-m1700-s1400-p35-mw, 4 |
| 431 | 1400 | c5f2 | `rnbqk2r/pppp1ppp/5n2/2b1N3/8/2P3P1/PP1PPP1P/RNBQKB1R b KQkq - 0 4` | mvs-std-m1400-s1800-p17-mb, 7 |
| 398 | 1400 | c6b4 | `r1bqkbnr/ppp1pppp/2n5/3P4/2p5/5N2/PP2PPPP/RNBQKB1R b KQkq - 0 4` | mvs-std-m1400-s2200-p43-mb, 7 |
| 333 | 1700 | c4f7 | `r1bqkbnr/ppp1pppp/3p4/n7/2B1P3/5N2/PPPP1PPP/RNBQK2R w KQkq - 2 4` | mvs-std-m1700-s1400-p09-mw, 6 |
| 296 | 1400 | d5e6 | `rnb1kbnr/ppp2ppp/4pq2/3P4/8/1P6/P2PPPPP/RNBQKBNR w KQkq - 1 4` | mvs-std-m1400-s1800-p35-mw, 6 |
| 277 | 2300 | b1c3 | `r1bqkbnr/ppp1pppp/8/3P4/1n6/5N2/PPPP1PPP/RNBQKB1R w KQkq - 1 4` | mvs-std-m2300-s1800-p15-mw, 6 |
| 259 | 1400 | h5f3 | `r1b1kbnr/pppp1ppp/2n2q2/4p2Q/2B1P3/8/PPPP1PPP/RNB1K1NR w KQkq - 4 4` | mvs-std-m1400-s1400-p31-mw, 6 |
| 225 | 2000 | f7f5 | `rnbqkbnr/pppp1ppp/8/4p3/2P5/5N2/PP1PPPPP/RNBQKB1R b KQkq - 1 2` | mvs-std-m2000-s1800-p33-mb, 3 |
| 218 | 1400 | e5f3 | `rnb1kbnr/ppp1qppp/8/3pN3/4P3/8/PPPP1PPP/RNBQKB1R w KQkq - 1 4` | mvs-std-m1400-s1400-p17-mw, 6 |
| 203 | 2000 | b7b6 | `rnbqkbnr/pppppp1p/8/6p1/7P/4P3/PPPP1PP1/RNBQKBNR b KQkq h3 0 2` | mvs-std-m2000-s2200-p39-mb, 3 |
| 192 | 1400 | f8c5 | `r1bqkb1r/pppp1ppp/2n2n2/1B2p3/8/2P1PN2/PP1P1PPP/RNBQK2R b KQkq - 2 4` | mvs-std-m1400-s1400-p43-mb, 7 |
| 186 | 2000 | f7f5 | `rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1` | mvs-std-m2000-s2200-p27-mb, 1 |
| 177 | 1700 | c2c3 | `r1b1kbnr/pp1ppppp/2n5/q1p5/3PP3/5N2/PPP2PPP/RNBQKB1R w KQkq - 1 4` | mvs-std-m1700-s1400-p23-mw, 6 |
| 160 | 1400 | b2b4 | `r1bqkbnr/pp1ppppp/n7/2P5/8/8/PPP1PPPP/RNBQKBNR w KQkq - 1 3` | mvs-std-m1400-s2200-p01-mw, 4 |
| 160 | 1700 | f8b4 | `rnbqkb1r/ppp2ppp/4pn2/3p4/3PP3/2NB4/PPP2PPP/R1BQK1NR b KQkq - 2 4` | mvs-std-m1700-s2200-p15-mb, 7 |
| 151 | 1400 | b1c3 | `r1b1kbnr/pp1ppppp/2n5/q1p5/3PP3/5N2/PPP2PPP/RNBQKB1R w KQkq - 1 4` | mvs-std-m1400-s1400-p01-mw, 6 |

