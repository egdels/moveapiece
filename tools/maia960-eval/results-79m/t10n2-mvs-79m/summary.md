# Maia960 eval: summary

Generated from games.csv by `Summary`; see REPORT.md for the discussion.

## Parameters

| Parameter | Value |
|---|---|
| Seed (positions, sampling) | 960 |
| Chess960 positions | 345 664 307 507 403 702 901 485 632 737 651 343 529 425 595 939 898 759 822 350 108 415 714 603 946 515 429 226 281 845 322 469 201 200 120 607 176 220 386 774 804 691 699 619 621 913 462 138 133 676 |
| Maia ratings | [1400, 1700, 2000, 2300] |
| Stockfish UCI_Elo | [1400, 1800, 2200] |
| Maia vs Maia | 1400 vs 2000, 1700 vs 2300 |
| Stockfish movetime | 100 ms |
| Maia's sampled opening moves per side | 2 at temperature 1.0 |
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
| 1400 | 1400 | std | 100 | 73/2/25 | 74.0 % | +182 [+116, +262] |
| 1400 | 1800 | std | 100 | 14/8/78 | 18.0 % | -263 [-371, -187] |
| 1400 | 2200 | std | 100 | 8/5/87 | 10.5 % | -372 [-503, -291] |
| 1700 | 1400 | std | 100 | 88/1/11 | 88.5 % | +354 [+264, +510] |
| 1700 | 1800 | std | 100 | 38/7/55 | 41.5 % | -60 [-123, +0] |
| 1700 | 2200 | std | 100 | 17/8/75 | 21.0 % | -230 [-337, -152] |
| 2000 | 1400 | std | 100 | 91/2/7 | 92.0 % | +424 [+331, +600] |
| 2000 | 1800 | std | 100 | 52/10/38 | 57.0 % | +49 [-21, +123] |
| 2000 | 2200 | std | 100 | 25/13/62 | 31.5 % | -135 [-215, -66] |
| 2300 | 1400 | std | 100 | 93/1/6 | 93.5 % | +463 [+361, +685] |
| 2300 | 1800 | std | 100 | 65/4/31 | 67.0 % | +123 [+54, +203] |
| 2300 | 2200 | std | 100 | 40/9/51 | 44.5 % | -38 [-104, +24] |

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
| Maia 1400 | mvs | 89 % (268/300) | 9.1 | – | – |
| Maia 1700 | mvs | 94 % (282/300) | 8.5 | – | – |
| Maia 2000 | mvs | 93 % (279/300) | 8.2 | – | – |
| Maia 2300 | mvs | 92 % (276/300) | 8.3 | – | – |
| Stockfish 1400 | mvs | 55 % (218/400) | 12.1 | – | – |
| Stockfish 1800 | mvs | 65 % (261/400) | 11.8 | – | – |
| Stockfish 2200 | mvs | 67 % (268/400) | 11.4 | – | – |

## Blunders

Maia moves losing more than 200 cp by the referee, per 100 Maia moves (the first 2 sampled moves per side included).

| Maia | Mode | Std | 960 |
|---|---|---|---|
| 1400 | mvs | 5.91 (715/12105) | – |
| 1700 | mvs | 4.67 (613/13129) | – |
| 2000 | mvs | 3.90 (527/13519) | – |
| 2300 | mvs | 3.52 (467/13252) | – |

## Mapping diagnostics

| Variant | Maia moves | Raw top slot not a legal move (informational) | Positions with two legal moves on one slot | Anomalies (wrong castle, illegal move, diagnostic mismatch) |
|---|---|---|---|---|
| std | 52005 | 24934 (47.95 %) | 0 | 0 |
| 960 | 0 | 0 (0.00 %) | 0 | 0 |

Every case is in events.tsv with its FEN, of the raw top slot only the first 50.

## Game ends

| Mode, variant | Mean plies | Endings |
|---|---|---|
| mvs std | 87 | {adjudication=603, checkmate=527, fifty-moves=1, insufficient-material=5, ply-limit=1, repetition=46, stalemate=17} |

## Sampled opening moves

Loss by the referee (eval before minus after, mover's view, capped at ±1000 cp).

| Maia | Sampled moves | Loss > 100 cp | Loss > 200 cp |
|---|---|---|---|
| 1400 | 600 | 1.17 % (7) | 0.00 % (0) |
| 1700 | 600 | 1.00 % (6) | 0.00 % (0) |
| 2000 | 600 | 0.83 % (5) | 0.00 % (0) |
| 2300 | 600 | 0.33 % (2) | 0.00 % (0) |

The 20 worst:

| Loss | Maia | Move | FEN | Game, ply |
|---|---|---|---|---|
| 164 | 2300 | g2g4 | `rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1` | mvs-std-m2300-s1800-p21-mw, 0 |
| 160 | 1400 | g1f3 | `rnbqkbnr/ppp1pppp/8/3p4/4P3/8/PPPP1PPP/RNBQKBNR w KQkq d6 0 2` | mvs-std-m1400-s1800-p17-mw, 2 |
| 140 | 1400 | h7h6 | `rnbqkbnr/pppp1ppp/8/4p3/3PP3/8/PPP2PPP/RNBQKBNR b KQkq d3 0 2` | mvs-std-m1400-s2200-p39-mb, 3 |
| 138 | 1700 | h1h2 | `rnbqkbnr/ppp1pppp/8/3p4/8/7P/PPPPPPP1/RNBQKBNR w KQkq d6 0 2` | mvs-std-m1700-s1400-p40-mw, 2 |
| 129 | 2300 | f7f6 | `rnbqkbnr/pppppppp/8/8/8/5N2/PPPPPPPP/RNBQKB1R b KQkq - 1 1` | mvs-std-m2300-s1400-p03-mb, 1 |
| 129 | 2000 | f7f6 | `rnbqkbnr/pppppppp/8/8/8/5N2/PPPPPPPP/RNBQKB1R b KQkq - 1 1` | mvs-std-m2000-s1800-p07-mb, 1 |
| 123 | 1700 | e7e5 | `rnbqkbnr/ppp1pppp/8/3p4/8/5NP1/PPPPPP1P/RNBQKB1R b KQkq - 0 2` | mvs-std-m1700-s2200-p12-mb, 3 |
| 123 | 1400 | f3g5 | `rnbqkbnr/ppp1pppp/8/3p4/8/5N2/PPPPPPPP/RNBQKB1R w KQkq d6 0 2` | mvs-std-m1400-s2200-p16-mw, 2 |
| 122 | 2000 | f6g8 | `rnbqkb1r/pppppppp/5n2/8/4P3/2N5/PPPP1PPP/R1BQKBNR b KQkq - 2 2` | mvs-std-m2000-s2200-p20-mb, 3 |
| 115 | 1700 | h1h2 | `rnbqkb1r/pppppppp/5n2/8/8/7P/PPPPPPP1/RNBQKBNR w KQkq - 1 2` | mvs-std-m1700-s2200-p08-mw, 2 |
| 111 | 1700 | e7e5 | `rnbqkbnr/ppp1pppp/8/3p4/8/4PN2/PPPP1PPP/RNBQKB1R b KQkq - 1 2` | mvs-std-m1700-s2200-p30-mb, 3 |
| 111 | 1400 | b1c3 | `rnbqkbnr/ppp1pppp/8/3p4/4P3/8/PPPP1PPP/RNBQKBNR w KQkq d6 0 2` | mvs-std-m1400-s1800-p34-mw, 2 |
| 109 | 2000 | e7e5 | `rnbqkbnr/pppppppp/8/8/8/5N2/PPPPPPPP/RNBQKB1R b KQkq - 1 1` | mvs-std-m2000-s1800-p09-mb, 1 |
| 109 | 1700 | e7e5 | `rnbqkbnr/pppppppp/8/8/8/5N2/PPPPPPPP/RNBQKB1R b KQkq - 1 1` | mvs-std-m1700-s1800-p29-mb, 1 |
| 109 | 1700 | e7e5 | `rnbqkbnr/pppppppp/8/8/8/5N2/PPPPPPPP/RNBQKB1R b KQkq - 1 1` | mvs-std-m1700-s2200-p29-mb, 1 |
| 109 | 2000 | e7e5 | `rnbqkbnr/pppppppp/8/8/8/5N2/PPPPPPPP/RNBQKB1R b KQkq - 1 1` | mvs-std-m2000-s1800-p44-mb, 1 |
| 108 | 1400 | f7f6 | `rnbqkbnr/pppp1ppp/4p3/8/8/2N2N2/PPPPPPPP/R1BQKB1R b KQkq - 1 2` | mvs-std-m1400-s1800-p01-mb, 3 |
| 105 | 1400 | e2e4 | `rnbqkb1r/pppppppp/5n2/8/3P4/8/PPP1PPPP/RNBQKBNR w KQkq - 1 2` | mvs-std-m1400-s2200-p08-mw, 2 |
| 104 | 1400 | h7h5 | `rnbqkbnr/pppp1ppp/4p3/8/3P4/5N2/PPP1PPPP/RNBQKB1R b KQkq - 1 2` | mvs-std-m1400-s1400-p28-mb, 3 |
| 101 | 2000 | f7f5 | `rnbqkbnr/pppp1ppp/8/4p3/8/4P3/PPPPNPPP/RNBQKB1R b KQkq - 1 2` | mvs-std-m2000-s1800-p08-mb, 3 |

