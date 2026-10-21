#!/bin/sh
# Copyright (C) 2026 Christian Kierdorf
# SPDX-License-Identifier: GPL-3.0-or-later
#
# The opening-variety study in REPORT.md: ways of sampling Maia's first own moves in standard
# chess, compared with pure argmax (the baseline) and with human games.
#
#   tools/maia960-eval/opening-study.sh human            # human openings by rating, ~10 min
#   tools/maia960-eval/opening-study.sh variety          # stage A: every variant, Maia vs Maia
#   tools/maia960-eval/opening-study.sh strength V...    # stage B: these variants vs Stockfish
#   tools/maia960-eval/opening-study.sh report V...      # the comparison over these variants
#
# Variants: base (temperature 0 from move 1), tTnN (temperature T/10 for Maia's first N own
# moves, e.g. t07n4), top3n4 (the three best moves at temperature 1.0 for N = 4). Needs what
# reproduce.sh needs; the openings list (CC0) and the Lichess games go into data/.
set -eu

cd "$(dirname "$0")/../.."
STEP=$1
shift
DIR=tools/maia960-eval
DATA=$DIR/data
OUT=$DIR/results-opening
THREADS=${THREADS:-$(($(sysctl -n hw.ncpu 2>/dev/null || nproc) - 1))}
OPENINGS_COMMIT=a6189a30dc273ccb21fc2536a9a2fefd5592a67a
ALL="base t05n2 t05n4 t05n6 t07n2 t07n4 t07n6 t10n2 t10n4 t10n6 top3n4"

./gradlew -q -Pmaia960eval :maia960-eval:installDist
EVAL=$DIR/build/install/maia960-eval/bin/maia960-eval
CP="$DIR/build/install/maia960-eval/lib/*"

# The sampling options of a variant.
options() {
    case $1 in
        base) echo "--sampled=0" ;;
        top3n4) echo "--sampled=4 --temperature=1.0 --topk=3" ;;
        t*n*)
            t=$(echo "$1" | sed 's/^t\([0-9]*\)n.*/\1/')
            n=$(echo "$1" | sed 's/.*n//')
            echo "--sampled=$n --temperature=$(echo "$t" | sed 's/^\(.\)/\1./')"
            ;;
    esac
}

run() { # variant, mode (mvm | mvs)
    out=$OUT/$1-$2
    rm -rf "$out"
    # shellcheck disable=SC2046
    $EVAL --threads="$THREADS" --only=stdonly,"$2" --pairs=1700-1700,1400-2000 \
        $(options "$1") --out="$out"
}

case $STEP in
    human)
        mkdir -p $DATA/chess-openings
        for f in a b c d e; do
            curl -fsSL -o $DATA/chess-openings/$f.tsv \
                https://raw.githubusercontent.com/lichess-org/chess-openings/$OPENINGS_COMMIT/$f.tsv
        done
        # The first rated standard games of August 2026 with an estimated duration of at
        # least 180 s, 1000 per rating band; curl stops at the pipe.
        curl -fsSL https://database.lichess.org/standard/lichess_db_standard_rated_2026-08.pgn.zst \
            2>/dev/null | zstd -dc 2>/dev/null \
            | java -cp "$CP" de.schliweb.moveapiece.maia960eval.HumanMatch \
                --openings=1700-1700:50,1400-2000:100 --perband=1000 \
                --out=$DATA/human-openings.tsv
        ;;
    variety)
        for v in $ALL; do run "$v" mvm; done
        ;;
    strength)
        for v in "$@"; do run "$v" mvs; done
        ;;
    report)
        runs=""
        for v in $ALL; do
            rm -rf "${OUT:?}/$v"
            for mode in mvm mvs; do
                if [ -f "$OUT/$v-$mode/games.csv" ]; then
                    mkdir -p "$OUT/$v"
                    tail -n +2 "$OUT/$v-$mode/games.csv" >> "$OUT/$v/rows.tmp"
                    cat "$OUT/$v-$mode/sampled-moves.tsv" >> "$OUT/$v/sampled.tmp" 2>/dev/null || true
                fi
            done
            if [ -f "$OUT/$v/rows.tmp" ]; then
                head -1 "$(ls "$OUT/$v"-m*/games.csv | head -1)" > "$OUT/$v/games.csv"
                cat "$OUT/$v/rows.tmp" >> "$OUT/$v/games.csv"
                mv "$OUT/$v/sampled.tmp" "$OUT/$v/sampled-moves.tsv" 2>/dev/null || touch "$OUT/$v/sampled-moves.tsv"
                rm -f "$OUT/$v/rows.tmp"
                runs="$runs,$v=$OUT/$v"
            fi
        done
        java -cp "$CP" de.schliweb.moveapiece.maia960eval.VarianceReport \
            --runs="${runs#,}" --human=$DATA/human-openings.tsv \
            --openings=$DATA/chess-openings --out=$OUT/comparison.md
        ;;
esac
