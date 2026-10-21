#!/bin/sh
# Copyright (C) 2026 Christian Kierdorf
# SPDX-License-Identifier: GPL-3.0-or-later
#
# Reruns every measurement in REPORT.md and rewrites the summaries under results*/.
# Needs a JDK 21, the Stockfish toolchain of :desktop, zstd and curl. Downloads the Lichess
# Chess960 database of August 2026 (116 MB) into data/ and streams the first games of the
# standard database; nothing of it is committed. Takes about an hour on an Apple M4.
#
#   tools/maia960-eval/reproduce.sh [games|bonus|human|all]   (default: all)
#
# Games are not reproduced bit for bit: Stockfish with UCI_LimitStrength picks its moves with a
# time-seeded random generator. Positions and Maia's sampling are seeded.
set -eu

cd "$(dirname "$0")/../.."
STEP=${1:-all}
DIR=tools/maia960-eval
DATA=$DIR/data
THREADS=${THREADS:-$(($(sysctl -n hw.ncpu 2>/dev/null || nproc) - 1))}
C960=$DATA/lichess_db_chess960_rated_2026-08.pgn.zst
C960_SHA256=f88ffd57a6c82031c94e8d988253ead3d8d223f8206c56d5cf99b92bfae41c11
STANDARD_URL=https://database.lichess.org/standard/lichess_db_standard_rated_2026-08.pgn.zst

./gradlew -q -Pmaia960eval :maia960-eval:installDist
EVAL=$DIR/build/install/maia960-eval/bin/maia960-eval
CP="$DIR/build/install/maia960-eval/lib/*"
human() {
    java -cp "$CP" de.schliweb.moveapiece.maia960eval.HumanMatch "$@"
}

# The main run: standard chess and Chess960, Maia as the app played before the castling bonus.
if [ "$STEP" = games ] || [ "$STEP" = all ]; then
    rm -f $DIR/results/games.csv $DIR/results/games-*.pgn $DIR/results/events.tsv
    $EVAL --threads="$THREADS" --castle=king:0 --out=$DIR/results
fi

# Chess960 again with the castling bonus, compared with the standard games of the main run.
if [ "$STEP" = bonus ] || [ "$STEP" = all ]; then
    OUT=$DIR/results-castle-bonus1
    rm -f $OUT/games.csv $OUT/games-*.pgn $OUT/events.tsv
    $EVAL --threads="$THREADS" --only=960only --castle=king:1 --out=$OUT
    mkdir -p $OUT/combined
    {
        head -1 $DIR/results/games.csv
        awk -F, 'NR > 1 && $3 == "std"' $DIR/results/games.csv
        tail -n +2 $OUT/games.csv
    } > $OUT/combined/games.csv
    $EVAL --only=summary --castle=king:1 --out=$OUT/combined
fi

# Castle variants and accuracy by ply against human games.
if [ "$STEP" = human ] || [ "$STEP" = all ]; then
    mkdir -p $DATA
    if [ ! -f $C960 ]; then
        curl -fsSL -o $C960 https://database.lichess.org/chess960/$(basename $C960)
    fi
    echo "$C960_SHA256  $C960" | shasum -a 256 -c -
    OUT=$DIR/results-castle-bonus1
    # Games 1-1000 for the variants, then 1000 further games from game 2501 on as a check.
    zstd -dc $C960 | human --games=1000 --out=$OUT/human-games-1-1000.md > /dev/null
    zstd -dc $C960 | awk '/^\[Event /{n++} n > 2500' \
        | human --games=1000 --variants=king:0,king:0.5,king:0.75,king:1,king:1.25,king:1.5 \
            --out=$OUT/human-games-holdout.md > /dev/null
    zstd -dc $C960 | human --games=1000 --variants=king:0,king:1 \
        --out=$OUT/human-960-by-ply.md > /dev/null
    # The standard dump is about 30 GB; only its first games are read. curl stops at the pipe.
    curl -fsSL $STANDARD_URL 2>/dev/null | zstd -dc 2>/dev/null \
        | human --standard=true --games=1000 --variants=king:0 \
            --out=$OUT/human-std-by-ply.md > /dev/null
fi
