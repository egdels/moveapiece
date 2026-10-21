/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.maia960eval;

import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.PieceType;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.move.Move;
import de.schliweb.moveapiece.engine.MaiaEvalAccess;
import de.schliweb.moveapiece.logic.Chess960;
import de.schliweb.moveapiece.logic.ChessGame;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scores Maia against human games: for every position, Maia's top move under each Chess960 castle
 * variant ({@link CastleVariant}) against the move actually played, with both players' ratings as
 * input. Reads PGN from stdin (a Lichess database dump, e.g. via {@code zstd -dc}) and takes the
 * first {@code --games} rated games with an estimated duration of at least {@code --mintime}
 * seconds (base + 40 x increment): Chess960 games without SP 518, or with {@code --standard=true}
 * standard games.
 *
 * <p>For Chess960 it also counts what the humans played where the king's step to its castling
 * square and the castle share one policy slot, and with {@code --candidates=N} prints up to N
 * positions where the human castled and the castle is Maia's top move with {@code --bonus} but not
 * without it, by a clear margin both ways - material for a test.
 */
public final class HumanMatch {

    private static final Pattern TAG = Pattern.compile("^\\[(\\w+) \"(.*)\"]$");
    private static final String[] BUCKETS = {"0-9", "10-19", "20-29", "30+"};

    /** Counters for one variant. */
    private static final class Tally {
        final long[] positions = new long[BUCKETS.length];
        final long[] matched = new long[BUCKETS.length];
        long humanCastled;
        long castleMatched;
        long castleLegal;
        long maiaCastles;
        long humanCastlesWhereLegal;
        long maiaCastledWrongly;
    }

    private static boolean standard;
    private static double bonus;
    private static double margin;
    private static int candidates;
    private static long sharedSlotPositions;
    private static long sharedSlotHumanCastled;
    private static long sharedSlotHumanStepped;

    private HumanMatch() {}

    public static void main(String[] args) throws Exception {
        Map<String, String> a = new HashMap<>();
        for (String arg : args) {
            a.put(arg.substring(2, arg.indexOf('=')), arg.substring(arg.indexOf('=') + 1));
        }
        int maxGames = Integer.parseInt(a.getOrDefault("games", "1000"));
        int minTime = Integer.parseInt(a.getOrDefault("mintime", "180"));
        standard = Boolean.parseBoolean(a.getOrDefault("standard", "false"));
        bonus = Double.parseDouble(a.getOrDefault("bonus", "1"));
        margin = Double.parseDouble(a.getOrDefault("margin", "0.3"));
        candidates = Integer.parseInt(a.getOrDefault("candidates", "0"));
        Path model =
                Path.of(
                        a.getOrDefault(
                                "model",
                                "desktop/src/main/resources/de/schliweb/moveapiece/desktop/"
                                        + "maia/maia3-5m.onnx"));
        Map<CastleVariant, Tally> tallies = new LinkedHashMap<>();
        for (String v :
                a.getOrDefault(
                                "variants",
                                "king:0,king:1,king:2,king:3,std:0,std:1,std:2,std:3,"
                                        + "max:0,max:1,max:2,max:3")
                        .split(",")) {
            tallies.put(CastleVariant.parse(v), new Tally());
        }

        int games = 0;
        long started = System.currentTimeMillis();
        try (SyncMaia maia = new SyncMaia(model, 60_000);
                BufferedReader in =
                        new BufferedReader(
                                new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            StringBuilder text = new StringBuilder();
            Map<String, String> tags = new HashMap<>();
            String line;
            while (games < maxGames && (line = in.readLine()) != null) {
                if (line.startsWith("[Event ") && text.length() > 0) {
                    if (qualifies(tags, minTime) && score(maia, text.toString(), tags, tallies)) {
                        games++;
                        if (games % 100 == 0) {
                            System.err.printf(
                                    "%d games, %d s%n",
                                    games, (System.currentTimeMillis() - started) / 1000);
                        }
                    }
                    text.setLength(0);
                    tags.clear();
                }
                Matcher m = TAG.matcher(line);
                if (m.matches()) {
                    tags.put(m.group(1), m.group(2));
                }
                text.append(line).append('\n');
            }
        }
        String report = report(games, minTime, tallies);
        System.out.print(report);
        if (a.containsKey("out")) {
            Files.writeString(Path.of(a.get("out")), report);
        }
    }

    private static boolean qualifies(Map<String, String> tags, int minTime) {
        String tc = tags.getOrDefault("TimeControl", "-");
        if (!tc.contains("+")
                || !tags.getOrDefault("WhiteElo", "?").matches("\\d+")
                || !tags.getOrDefault("BlackElo", "?").matches("\\d+")) {
            return false;
        }
        String[] p = tc.split("\\+");
        if (Integer.parseInt(p[0]) + 40 * Integer.parseInt(p[1]) < minTime) {
            return false;
        }
        if (standard) {
            return !tags.containsKey("FEN");
        }
        if (!tags.containsKey("FEN")) {
            return false;
        }
        String backRank = tags.get("FEN").split("/")[7].split(" ")[0];
        return Chess960.numberOf(backRank) != Chess960.STANDARD;
    }

    private static boolean score(
            SyncMaia maia, String pgn, Map<String, String> tags, Map<CastleVariant, Tally> tallies)
            throws Exception {
        ChessGame game = new ChessGame();
        if (!game.loadPgn(pgn) || game.isChess960() == standard) {
            return false;
        }
        int whiteElo = Integer.parseInt(tags.get("WhiteElo"));
        int blackElo = Integer.parseInt(tags.get("BlackElo"));
        Board board = new Board();
        board.loadFromFen(game.startFen());
        Deque<int[]> history = new ArrayDeque<>();
        history.addLast(MaiaEvalAccess.placement(board));
        String[] moves = game.toUciMoveList().split(" ");
        for (int ply = 0; ply < moves.length; ply++) {
            boolean white = board.getSideToMove() == Side.WHITE;
            int selfElo = white ? whiteElo : blackElo;
            float[] policy =
                    maia.policy(new ArrayList<>(history), selfElo, white ? blackElo : whiteElo);
            List<Move> legal = board.legalMoves();
            Move human = new Move(moves[ply], board.getSideToMove());
            boolean humanCastle = CastleVariant.isChess960Castle(board, human);
            boolean castleLegal = false;
            for (Move m : legal) {
                castleLegal |= CastleVariant.isChess960Castle(board, m);
            }
            countSharedSlot(board, legal, human, humanCastle);
            if (humanCastle && candidates > 0) {
                reportCandidate(board, legal, human, policy, game, ply, selfElo);
            }
            int bucket = ply < 10 ? 0 : ply < 20 ? 1 : ply < 30 ? 2 : 3;
            for (Map.Entry<CastleVariant, Tally> e : tallies.entrySet()) {
                double[] scores = new double[legal.size()];
                for (int i = 0; i < legal.size(); i++) {
                    scores[i] = e.getKey().score(board, legal.get(i), policy);
                }
                Move top = CastleVariant.choose(legal, scores, 0, null);
                boolean match = top.equals(human);
                boolean topCastle = CastleVariant.isChess960Castle(board, top);
                Tally t = e.getValue();
                t.positions[bucket]++;
                if (match) {
                    t.matched[bucket]++;
                }
                if (humanCastle) {
                    t.humanCastled++;
                    if (match) {
                        t.castleMatched++;
                    }
                }
                if (castleLegal) {
                    t.castleLegal++;
                    if (topCastle) {
                        t.maiaCastles++;
                        if (!humanCastle) {
                            t.maiaCastledWrongly++;
                        }
                    }
                    if (humanCastle) {
                        t.humanCastlesWhereLegal++;
                    }
                }
            }
            board.doMove(human, true);
            if (history.size() == MaiaEvalAccess.HISTORY_STEPS) {
                history.removeFirst();
            }
            history.addLast(MaiaEvalAccess.placement(board));
        }
        return true;
    }

    /** Where a castle and a plain king step share a slot: which of the two did the human play? */
    private static void countSharedSlot(
            Board board, List<Move> legal, Move human, boolean humanCastle) {
        for (Move castle : legal) {
            if (!CastleVariant.isChess960Castle(board, castle)) {
                continue;
            }
            int slot = MaiaEvalAccess.indexOf(board, castle);
            for (Move step : legal) {
                if (step != castle
                        && board.getPiece(step.getFrom()).getPieceType() == PieceType.KING
                        && MaiaEvalAccess.indexOf(board, step) == slot) {
                    sharedSlotPositions++;
                    if (humanCastle && human.equals(castle)) {
                        sharedSlotHumanCastled++;
                    } else if (human.equals(step)) {
                        sharedSlotHumanStepped++;
                    }
                }
            }
        }
    }

    private static void reportCandidate(
            Board board,
            List<Move> legal,
            Move human,
            float[] policy,
            ChessGame game,
            int ply,
            int elo) {
        CastleVariant plain = new CastleVariant("king", 0);
        double castle = plain.score(board, human, policy);
        double bestOther = Double.NEGATIVE_INFINITY;
        for (Move m : legal) {
            if (!m.equals(human)) {
                bestOther = Math.max(bestOther, plain.score(board, m, policy));
            }
        }
        if (castle < bestOther - margin && castle + bonus > bestOther + margin) {
            candidates--;
            String[] moves = game.toUciMoveList().split(" ");
            System.err.printf(
                    Locale.ROOT,
                    "CANDIDATE elo=%d castle=%s castleLogit=%.3f bestOther=%.3f%n"
                            + "  startFen=%s%n  moves=%s%n",
                    elo,
                    human,
                    castle,
                    bestOther,
                    game.startFen(),
                    String.join(" ", java.util.Arrays.copyOf(moves, ply)));
        }
    }

    private static String report(int games, int minTime, Map<CastleVariant, Tally> tallies) {
        StringBuilder md = new StringBuilder();
        md.append(
                String.format(
                        "%d %s games, estimated duration >= %d s%s.%n%n",
                        games,
                        standard ? "standard" : "Chess960",
                        minTime,
                        standard ? "" : ", SP 518 excluded"));
        md.append("| Variant |");
        for (String b : BUCKETS) {
            md.append(" Acc. ").append(b).append(" |");
        }
        md.append(
                " Acc. 20+ | Acc. all | Castle top when human castled | Maia castles / humans"
                        + " castle (where legal) | Castle picked, human did not |\n");
        md.append("|---|---|---|---|---|---|---|---|---|---|\n");
        for (Map.Entry<CastleVariant, Tally> e : tallies.entrySet()) {
            Tally t = e.getValue();
            md.append("| ").append(e.getKey()).append(" |");
            for (int i = 0; i < BUCKETS.length; i++) {
                md.append(
                        String.format(
                                Locale.ROOT, " %.1f %% |", pct(t.matched[i], t.positions[i])));
            }
            long n = sum(t.positions);
            long m = sum(t.matched);
            md.append(
                    String.format(
                            Locale.ROOT,
                            " %.1f %% | %.2f %% | %.1f %% (%d/%d) | %d / %d | %d |%n",
                            pct(t.matched[2] + t.matched[3], t.positions[2] + t.positions[3]),
                            pct(m, n),
                            pct(t.castleMatched, t.humanCastled),
                            t.castleMatched,
                            t.humanCastled,
                            t.maiaCastles,
                            t.humanCastlesWhereLegal,
                            t.maiaCastledWrongly));
        }
        Tally first = tallies.values().iterator().next();
        md.append(
                String.format(
                        "%nPositions: %d (plies 0-9: %d, 10-19: %d, 20-29: %d, 30+: %d)%n",
                        sum(first.positions),
                        first.positions[0],
                        first.positions[1],
                        first.positions[2],
                        first.positions[3]));
        if (!standard) {
            md.append(
                    String.format(
                            "%nCastle and king step on one slot: %d positions; the human castled"
                                    + " in %d, made the king step in %d.%n",
                            sharedSlotPositions, sharedSlotHumanCastled, sharedSlotHumanStepped));
        }
        return md.toString();
    }

    private static long sum(long[] values) {
        long s = 0;
        for (long v : values) {
            s += v;
        }
        return s;
    }

    private static double pct(long a, long b) {
        return b == 0 ? 0 : 100.0 * a / b;
    }
}
