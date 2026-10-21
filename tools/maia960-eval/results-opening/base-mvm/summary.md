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
| Maia's sampled opening moves per side | none |
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
| 1700 vs 1700 | std | 100 | 100/0/0 | 100.0 % | > +920 [> +920, > +920] |
| 2000 vs 1400 | std | 100 | 0/50/50 | 25.0 % | -191 [-191, -191] |

| Pairing | Score 960 − std | Elo gap 960 − std [95 % CI] |
|---|---|---|

## Castling

Share of games in which a side castled, and the mean move number (full moves) of the castle. Stockfish's own sides for comparison.

| Player | Mode | Std: castled | Std: mean move | 960: castled | 960: mean move |
|---|---|---|---|---|---|
| Maia 1400 | mvm | 50 % (50/100) | 9.0 | – | – |
| Maia 1700 | mvm | 100 % (200/200) | 9.0 | – | – |
| Maia 2000 | mvm | 100 % (100/100) | 8.0 | – | – |

## Blunders

Maia moves losing more than 200 cp by the referee, per 100 Maia moves (the first 0 sampled moves per side included).

| Maia | Mode | Std | 960 |
|---|---|---|---|
| 1400 | mvm | 8.33 (300/3600) | – |
| 1700 | mvm | 14.29 (500/3500) | – |
| 2000 | mvm | 11.27 (400/3550) | – |

## Mapping diagnostics

| Variant | Maia moves | Raw top slot not a legal move (informational) | Positions with two legal moves on one slot | Anomalies (wrong castle, illegal move, diagnostic mismatch) |
|---|---|---|---|---|
| std | 10650 | 6950 (65.26 %) | 0 | 0 |
| 960 | 0 | 0 (0.00 %) | 0 | 0 |

Every case is in events.tsv with its FEN, of the raw top slot only the first 50.

## Game ends

| Mode, variant | Mean plies | Endings |
|---|---|---|
| mvm std | 53 | {adjudication=50, checkmate=100, repetition=50} |

## Sampled opening moves

Loss by the referee (eval before minus after, mover's view, capped at ±1000 cp).

| Maia | Sampled moves | Loss > 100 cp | Loss > 200 cp |
|---|---|---|---|

The 20 worst:

| Loss | Maia | Move | FEN | Game, ply |
|---|---|---|---|---|

