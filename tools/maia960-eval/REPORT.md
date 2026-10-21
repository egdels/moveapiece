# Maia in Chess960: does the rating setting still hold?

Measured 2026-10-21 on `main` at 51451b8 (branch `maia960-eval`). Question:
how much weaker or stronger does Maia play in Chess960 than in standard chess
at the same rating setting, and does it behave plausibly? Only the shift
between the variants is measured, not an absolute Elo.

All numbers below come from `results/summary.md`, which `Summary.java`
generates from the run's `games.csv`. The raw output of the runs (every game
as PGN and CSV, every event with its FEN) is not in the repository; it is
kept on the branch `maia960-eval`, and `reproduce.sh` regenerates it.

The main run and the human-game measurements were made before the app got
its Chess960 castling bonus (7ec6efb): "the app" in them means `king:0`.
Since then the harness plays the app's current scoring (`king:1`) unless
`--castle` says otherwise.

## Setup

- **Maia** as the app runs it: `MaiaEngine` with `setElo`, `setPosition(startFen,
  moves)`, `go()`. One shared engine serves all games.
- **Opening variety:** with `go()` (temperature 0) Maia is deterministic, so
  Maia against Maia in standard chess would produce only two different games.
  So in every game, in both variants and both modes, Maia's first 2 moves per
  side are sampled at temperature 1 (`go(1.0)`, seeded per game and ply).
  After that it plays at temperature 0 like the app.
- **Opponent:** the app's `StockfishEngine`, `setStrength(elo)`
  (`UCI_LimitStrength` + `UCI_Elo`), `setChess960` in 960, `movetime 100 ms`
  for both variants. The app itself uses 800 ms (desktop) and 1200 ms (Android).
- **Positions:** 50 random Chess960 starting positions (seed 960, SP 518
  excluded; the list is in `summary.md`). The control group is 50 standard
  games per pairing. Every position is played twice with colours swapped.
- **Matches:** Maia 1400/1700/2000/2300 × Stockfish 1400/1800/2200, 100 games
  each per variant (2400 games). Maia 1400 vs 2000 and 1700 vs 2300, 100 games
  each per variant (400 games). 2800 games in total, none lost to errors.
- **Game end:** mate, stalemate, threefold repetition, 50-move rule,
  insufficient material (chesslib, via `ChessGame`). Draw after 300 plies.
  **Adjudication is on:** a game is scored as a win when the referee sees
  |eval| > 10 pawns for the same side 10 plies in a row (1162 of 2800 games).
- **Referee:** Stockfish at full strength, depth 12, 1 thread, 16 MB hash. It
  evaluates every position for blunders and adjudication. Blunder: a Maia
  move that loses more than 200 cp, with evals capped at ±1000 cp (so a won
  position that stays won does not count).
- **Statistics:** the position pair is the sampling unit. Score CIs use the
  standard error of the pair means. Shift CIs are percentile bootstraps over
  pairs (2000 resamples, seed 4242). The performance rating per Maia level is
  the rating whose expected score against all three Stockfish levels together
  equals the actual score.
- **Versions:** Stockfish 19 (`sf_19`, built by `:desktop:buildStockfishHost`,
  binary SHA-256 `64cf224d…439a`, net `nn-1a298aa575a0.nnue`). Maia-3
  `maia3-5m.onnx` (SHA-256 `f7255029…c589`, see MAIA_PROVENANCE.md). ONNX
  Runtime 1.29.0, JDK 21, macOS 27.0.1 on an Apple M4 with 9 workers. Run time
  37 min.

Reproducing it (downloads 116 MB of Lichess data into the ignored `data/`):

```sh
tools/maia960-eval/reproduce.sh            # or: games | bonus | human
```

The module is part of the build only with `-Pmaia960eval` (see
`settings.gradle`), so no other build, the F-Droid build of `:app` included,
configures or resolves anything for it. Every parameter can be passed as `--name=value` (see `Config.java`).
Positions and Maia's sampling are seeded. Stockfish is not reproducible bit
for bit: with `UCI_LimitStrength` it picks moves with a time-seeded random
generator, and `movetime` depends on machine load. Reruns therefore give
other games with the same statistics.

## Results

### Maia against Stockfish: shift per Maia level

Performance rating on Stockfish's nominal `UCI_Elo` scale at 100 ms:

| Maia | Standard | Chess960 | Shift 960 − std [95 % CI] |
|---|---|---|---|
| 1400 | 1613 | 1417 | **−196** [−265, −125] |
| 1700 | 1714 | 1510 | **−204** [−279, −133] |
| 2000 | 1775 | 1569 | **−206** [−279, −131] |
| 2300 | 1863 | 1630 | **−233** [−306, −158] |

All 12 single pairings point the same way (−134 to −338). The per-pairing
table with scores and CIs is in `summary.md`.

### Maia against Maia

| Pairing | Std score | 960 score | Elo gap std → 960 | Change [95 % CI] |
|---|---|---|---|---|
| 2000 vs 1400 | 71.0 % | 72.0 % | +156 → +164 | +9 [−69, +91] |
| 2300 vs 1700 | 86.5 % | 66.0 % | +323 → +115 | −207 [−324, −103] |

### Behaviour

| | Standard | Chess960 |
|---|---|---|
| Maia castles (games vs Stockfish, all levels) | 91–94 % | 17–19 % |
| Stockfish castles (same games) | 55–69 % | 35 % |
| Mean move number of Maia's castle | 8.2–9.0 | 9.0–10.9 |
| Blunders per 100 Maia moves vs Stockfish (1400 → 2300) | 6.0 → 4.3 | 8.2 → 6.4 |
| Blunders per 100 Maia moves, Maia vs Maia | 3.7–6.9 | 5.4–7.3 |
| Illegal move played, wrong castle, engine hang | 0 | 0 |

**Mapping:** no Chess960 castle ended with king and rook anywhere but g/f or
c/d, and Maia never played an illegal move. A diagnostic second forward pass
always agreed with `MaiaEngine`'s choice (0 mismatches). In 3444 Maia
decisions in 960 (5.8 %) two legal moves shared one slot. Each time it was the
known case from MAIA_PROVENANCE.md: the king stands next to its castling
square, so the plain king step and the castle share the slot. Maia played one
of the two moves in 31 of these positions: 28 times the king step, 3 times the
castle. Which one it gets is down to the order of chesslib's move list.

**"Top move illegal / fallback":** `MaiaEngine` has no fallback. It takes the
best of the legal moves' slots only, so an illegal move cannot come out. Over
all slots, the raw policy maximum is an illegal move in 75 % (std) and 83 %
(960) of positions, e.g. `e7e4` for Black in the starting position. The
network was evidently trained with illegal moves masked, so those logits carry
no meaning. The number is reported in `summary.md` as information, not as an
error. `events.tsv` keeps the first 50 cases as examples.

## Assessment

- **The order of the levels holds** in Chess960 (1417 < 1510 < 1569 < 1630),
  and blunder rates fall with rating in both variants.
- **The shift is about −200 Elo at every level,** above the 150 the brief set
  as acceptable; no upper CI bound is above −125. This is on Stockfish's scale.
  Measured on Maia's own scale it weighs more: in standard chess, the whole
  range from Maia 1400 to 2300 spans only 250 points against Stockfish. So
  Maia 2300 in Chess960 plays about like Maia 1400 in standard chess.
- **Maia against Maia** confirms this only in part. The 1400/2000 gap is
  unchanged, while 2300 vs 1700 shrinks by about 200. Given the small sample,
  that fits "the higher levels lose more in 960" and does not contradict it.
- **Castling is the plain behavioural weakness:** Maia castles in under a fifth
  of Chess960 games, half as often as the weakened Stockfish and a fifth as
  often as in standard chess. When it does castle, the timing is normal. No
  hard malfunction was found. The king-step/castle ambiguity is real but rare
  (31 positions in 1400 Maia games in 960).

**In short:** the setting stays usable as a relative strength control. As a
promise of "plays like a 1700 player", it is about 200 Elo too optimistic in
Chess960 on Stockfish's scale, more on Maia's own scale.

## Limits of the method

- `UCI_Elo` is calibrated by Stockfish on standard chess at long time
  controls. At 100 ms it is a different scale, and its fit to 960 is not
  established either. Only the difference between the variants is
  interpreted, assuming Stockfish's own strength changes little between
  standard and 960. If limited Stockfish played worse in 960, the shift
  would be understated.
- 100 games per pairing; CIs are ±70–100 Elo for a shift. The 2200 pairings
  are near 0 % and hardly informative alone.
- Short thinking time for Stockfish; Maia does not search, so it is unaffected.
- The 2 sampled opening moves make Maia somewhat weaker than in the app, in
  both variants alike.
- Depth-12 referee and adjudication after 10 plies above 10 pawns; a rare
  swindle from a lost position would not be counted.
- 50 of 959 positions; positions with king next to its castling square or
  rooks in the corners are represented by chance only.

## Recommendation for the app

A short note in the New Game dialog when Maia and Chess960 are both selected,
for example:

> Maia learnt from standard games only. In Chess960 it plays noticeably
> weaker than the chosen rating and rarely castles.

The rating should not be corrected automatically, for example by +200 in 960:
at 2400 there is no headroom, the size of the correction depends on the scale,
and it would hide what is happening. The Chess960 section of
MAIA_PROVENANCE.md (and the Javadoc of `GameSetup.supportsChess960`) can cite
these numbers next to the move-matching accuracy measured there.

## Experiment: a castling bonus (2026-10-21)

Maia castles far less in Chess960 than in standard chess, so the first
attempt to close the gap changed only how a Chess960 castle is scored
(`CastleVariant`, harness only; the app is unchanged):

- `king`: the app's mapping (the king's move to its castling square).
- `std`: the slot of standard castling, e1g1/e1c1.
- `max`: the higher of the two.
- Each with a bonus added to the castle's logit.

**Calibration on human games** (`HumanMatch`, Lichess Chess960 database
2026-08, rated, estimated duration ≥ 180 s, SP 518 excluded, both ratings as
input). Run on games 1–1000, checked on the next 1000 (games 2501 onward):

| Variant | Accuracy | Castle top when the human castled | Maia castles / humans castle (where legal) |
|---|---|---|---|
| king:0 (app) | 49.60 % / 50.97 % | 18.4 % / 17.2 % | 500 / 1231 · 448 / 1220 |
| std:0 | 49.46 % | 11.0 % | 394 / 1231 |
| max:0 | 49.57 % | 18.6 % | 599 / 1231 |
| king:1 | 49.66 % / 51.03 % | 34.9 % / 34.7 % | 1325 / 1231 · 1205 / 1220 |

(first set / held-out set; all variants in `results-castle-bonus1/human-*.md`).
`std` is worse and `max` no better than the app. A bonus of 1 makes Maia
castle as often as the humans, doubles the castle hits and leaves overall
accuracy unchanged.

**Strength with `king:1`** (`results-castle-bonus1/`, the same 960 positions
and seeds, 1400 games, compared with the standard games of the main run):

| Maia | 960 perf. app | 960 perf. king:1 | Shift app | Shift king:1 [95 % CI] |
|---|---|---|---|---|
| 1400 | 1417 | 1425 | −196 | −188 [−258, −118] |
| 1700 | 1510 | 1442 | −204 | −272 [−349, −197] |
| 2000 | 1569 | 1584 | −206 | −191 [−264, −115] |
| 2300 | 1630 | 1641 | −233 | −222 [−299, −141] |

Maia now castles in 35–39 % of its Chess960 games (app: 17–19 %), slightly
more than the limited Stockfish (30–36 %). Blunder rates are unchanged (vs
Stockfish 8.2 → 6.4 per 100 moves from 1400 to 2300). Maia against Maia: 2000
vs 1400 +210 (app +164), 2300 vs 1700 +143 (app +115), both within noise. No
anomalies, and the diagnostic pass matched `MaiaEngine` on every move,
sampled moves included.

**Conclusion:** the missing castles do not explain the −200. Fixing them
makes Maia's Chess960 play more human-like at no cost in accuracy, but not
stronger. The rest of the gap lies in how the network handles unfamiliar
positions, especially in the opening (32 % accuracy in plies 0–9 against
50 % in standard chess), which only training on Chess960 games can change.

### Where the gap lies, and the shared slot

Move-matching accuracy by ply on 1000 human games each (Lichess 2026-08, same
filters; standard games from the start of the standard dump;
`results-castle-bonus1/human-*-by-ply.md`):

| Plies | 0–9 | 10–19 | 20–29 | 30+ | 20+ |
|---|---|---|---|---|---|
| Standard | 49.5 % | 52.3 % | 53.0 % | 57.2 % | 56.4 % |
| Chess960 | 31.5 % | 44.5 % | 48.8 % | 56.4 % | 54.9 % |

From ply 20 on the network is almost as good in Chess960 as in standard chess.
The gap is in the first 20 plies. There human Chess960 openings are also far
less uniform than standard opening theory, so a perfect model would match
fewer moves too. Fine-tuning could gain at most part of this opening gap.

Where the castle and the king's plain step to the castling square share a
slot (1921 positions), the humans castled 286 times and made the king step 5
times. The app therefore gives the shared slot to the castle: with the bonus
on the castle only, the castle always scores higher, whatever the order of
the move list.

Adopted in the app with bonus 1 (see MAIA_PROVENANCE.md).

## Study: more variety in Maia's standard openings (2026-10-21)

Branch `maia-opening-variance`, from `main` at e476b49. Question: does sampling
Maia's first own moves in standard chess, instead of always playing the most
likely move, give human-like opening variety without a notable strength loss
and without inhuman moves? Numbers from `results-opening/comparison.md`
(`VarianceReport.java`); `opening-study.sh` reproduces everything.

### Setup

- **Baseline:** pure argmax from move 1 (the app today).
- **Variants:** temperature T ∈ {0.5, 0.7, 1.0} for Maia's first N ∈ {2, 4, 6}
  own moves (`tTnN`, e.g. `t07n4`), plus the three best moves at T = 1.0 for
  N = 4 (`top3n4`). After move N Maia plays argmax again.
- **Variety:** 100 games Maia against Maia each for 1700–1700 and
  1400–2000, standard start position, 50 seeds with both colour assignments.
- **Human-likeness:** 1000 rated Lichess games (August 2026, ≥ 180 s
  estimated duration) per band: both players 1650–1750, and one player
  1300–1500 against one 1900–2100. Opening names from
  lichess-org/chess-openings (CC0) at a6189a3; the family is the name up to
  the first colon. TV is the total variation distance to the humans.
- **Strength:** Maia 1400/1700/2000/2300 against Stockfish 1400/1800/2200,
  50 position pairs (both colours) each, 1200 games per variant; shift of the
  performance rating against the baseline, bootstrap 95 % CI over pairs.
- **Outliers:** every sampled move rated by Stockfish 19 at depth 12; loss
  in cp from the mover's view, evals capped at ±1000.

**Reduced selection.** The full grid (11 variants × 1400 games) would have
taken about 4 h. Stage A ran the variety games for all 11 variants (about
40 min). The N = 6 variants failed the outlier rule there (1.5–3.4 % of
sampled moves over 200 cp, because moves 5–6 are already in sharper
positions), and T = 0.5 gave little variety. Stage B measured strength for the
baseline and the four candidates left: `t07n4`, `t10n4`, `top3n4`, `t10n2`
(about 100 min). No engine errors; the only events are the informational
top-slot-illegal ones.

### Results

| Variant | Distinct @8 (1700 / 1400–2000) | TV first move | TV family | Top-10 share | > 200 cp | > 100 cp | Mean shift [95 % CI] |
|---|---|---|---|---|---|---|---|
| base | 1 / 2 | 0.39 / 0.41 | 0.95 / 0.84 | 100 / 100 % | – | – | – |
| t07n4 | 24 / 31 | 0.14 / 0.16 | 0.69 / 0.64 | 55 / 48 % | 0.31 % | 1.59 % | −28 [−66, +13] |
| t10n2 | 28 / 28 | 0.08 / 0.09 | 0.67 / 0.69 | 65 / 58 % | 0.06 % | 0.84 % | −18 [−57, +20] |
| t10n4 | 44 / 49 | 0.08 / 0.09 | 0.64 / 0.66 | 42 / 29 % | 0.67 % | 3.06 % | −16 [−57, +23] |
| top3n4 | 14 / 21 | 0.11 / 0.11 | 0.74 / 0.70 | 81 / 70 % | 0.13 % | 0.55 % | +9 [−31, +49] |
| humans | 98.9 / 97.7 | – | – | 57 / 55 % | | | |

Per Maia level (shift against the baseline):

| Variant | 1400 | 1700 | 2000 | 2300 |
|---|---|---|---|---|
| t07n4 | −83 [−155, −7] | −36 [−116, +44] | +4 [−73, +80] | +4 [−78, +91] |
| t10n2 | −33 [−109, +41] | −61 [−140, +17] | −15 [−86, +57] | +36 [−49, +120] |
| t10n4 | −77 [−154, −2] | −65 [−141, +11] | +13 [−65, +94] | +64 [−13, +136] |
| top3n4 | −33 [−117, +52] | −57 [−135, +23] | +65 [−8, +141] | +62 [−19, +140] |

The baseline plays two openings (Italian 75 %, Sicilian 25 %); the humans
play 91 families, led by the Sicilian (12 %) and the Queen's Pawn Game (11 %).
`t10n2` plays 24 families, `t10n4` 26, `top3n4` only 8 (its top three
moves skip the humans' second and third choices often enough to stay narrow).

The worst sampled moves of `t10n2` (2 of 3200 over 200 cp): 2…f5 after
1.e4 Nc6 2.d4 (212 cp, Maia 2000) and 2…Nb8 after 1.g4 Nc6 2.Bg2 (210 cp).
`t07n4` has 20 over 200 cp, 9 of them at 1400 and almost all in plies 4–7
(piece moves such as an early Bxf7). The 20
worst moves of each run with FEN are in `results-opening/<variant>-m*/summary.md`.

### Assessment

All four candidates pass the rule as written: mean shift under 30 Elo and
under 1 % of sampled moves over 200 cp. They differ in how human the variety
is:

- `t10n4` spreads more than humans (top-10 share 29–42 % against 55–57 %)
  and costs about 70 Elo at 1400 and 1700, the CI at 1400 excluding zero.
- `t07n4` costs 83 Elo at 1400 (CI excludes zero) and its mean of −28 sits
  right at the threshold; its first-move distribution is further from the
  humans' (TV 0.14–0.16).
- `top3n4` is the strongest but the least human: it never plays the long
  tail and stays at 8 families.
- `t10n2` has the humans' first-move distribution (TV 0.08–0.09), a top-10
  share close to theirs (58–65 %), the fewest bad moves (0.06 % over 200 cp)
  and no level with a CI excluding zero.

### Recommendation

**`t10n2`:** sample Maia's first two own moves at temperature 1.0 (the
network's own distribution), then argmax. It is the most human-like variety
that stays clearly inside both limits. `t07n4` would give somewhat more
distinct lines but is borderline on strength.

Adopted in the app (`MaiaOpeningSampling`, see MAIA_PROVENANCE.md); the
harness samples this way by default.

### Limits

- With 1200 games per variant the CI of the mean shift is about ±40 Elo, so
  the 30 Elo threshold is near the resolution of the measurement; none of the
  candidates shows a significant mean loss, but a 30 Elo loss could not be
  excluded either.
- Distinct lines in Maia against Maia stay far below the humans' (28 against
  about 98 of 100): after the sampled moves Maia plays argmax, so lines
  converge, and two humans of the same rating bring two different
  repertoires. Against a human player the variety is larger, since the human's
  moves vary too.
- Stockfish's skill-level randomness is time-seeded, so stage B is not
  bit-reproducible; the CIs cover that noise.
