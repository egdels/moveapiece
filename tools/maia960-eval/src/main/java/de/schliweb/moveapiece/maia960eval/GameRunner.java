/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.maia960eval;

import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.File;
import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.PieceType;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.Square;
import com.github.bhlangonijr.chesslib.move.Move;
import de.schliweb.moveapiece.engine.MaiaEvalAccess;
import de.schliweb.moveapiece.logic.ChessGame;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/** Plays one game of a {@link Job} and collects everything the report needs about it. */
final class GameRunner {

    /** One side of a game. */
    record Player(boolean maia, int elo) {
        String label() {
            return (maia ? "Maia " : "Stockfish ") + elo;
        }
    }

    /**
     * @param mvm Maia against Maia (else Maia against Stockfish)
     * @param sp Chess960 starting position number, -1 for standard chess
     * @param pair index of the position; both colour assignments of a position share it
     */
    record Job(String id, boolean mvm, int sp, int pair, Player white, Player black, long seed) {
        boolean chess960() {
            return sp >= 0;
        }
    }

    /**
     * Per side: Maia's move count, blunders, castling ply (-1: none), top-slot-illegal count, and
     * how many of Maia's sampled opening moves lost more than 100 and 200 cp.
     */
    static final class SideStats {
        int maiaMoves;
        int blunders;
        int castlePly = -1;
        int topIllegal;
        int sampled;
        int sampledLoss100;
        int sampledLoss200;
    }

    /** One of Maia's sampled opening moves and what the referee says it lost. */
    record SampledMove(String gameId, int elo, int ply, String fen, String move, int loss) {}

    record Result(
            Job job,
            String result,
            String termination,
            int plies,
            SideStats white,
            SideStats black,
            int slotCollisions,
            int anomalies,
            long millis,
            String pgn,
            String opening,
            List<SampledMove> sampledMoves) {}

    private final Config config;
    private final SyncMaia maia;
    private final SyncStockfish stockfish;
    private final Analyzer analyzer;
    private final EvalLog log;

    GameRunner(
            Config config, SyncMaia maia, SyncStockfish stockfish, Analyzer analyzer, EvalLog log) {
        this.config = config;
        this.maia = maia;
        this.stockfish = stockfish;
        this.analyzer = analyzer;
        this.log = log;
    }

    Result play(Job job) throws Exception {
        long started = System.currentTimeMillis();
        ChessGame game = new ChessGame();
        if (job.chess960()) {
            game.resetChess960(job.sp());
        } else {
            game.reset();
        }
        String startFen = game.startFen();
        // Replayed exactly as inside MaiaEngine (loadFromFen without a variant flag), so the
        // diagnostic forward pass sees the same input as the real one.
        Board shadow = new Board();
        shadow.loadFromFen(startFen);
        Deque<int[]> history = new ArrayDeque<>();
        history.addLast(MaiaEvalAccess.placement(shadow));

        Player sfPlayer = job.white().maia() ? job.black() : job.white();
        if (!job.mvm()) {
            stockfish.newGame(sfPlayer.elo(), job.chess960());
        }
        analyzer.newGame(job.chess960());

        SideStats white = new SideStats();
        SideStats black = new SideStats();
        List<Integer> evals = new ArrayList<>();
        List<Boolean> byMaia = new ArrayList<>();
        List<SampledMove> sampledMoves = new ArrayList<>();
        evals.add(analyzer.eval(startFen, ""));
        int collisions = 0;
        int anomalies = 0;
        int streak = 0;
        Side streakWinner = null;
        String result;
        String termination;

        while (true) {
            int ply = game.moveCount();
            if (game.isCheckmate()) {
                result = game.sideToMove() == Side.WHITE ? "0-1" : "1-0";
                termination = "checkmate";
                break;
            }
            if (game.isStalemate()) {
                result = "1/2-1/2";
                termination = "stalemate";
                break;
            }
            if (game.isDraw()) {
                result = "1/2-1/2";
                termination = drawReason(shadow);
                break;
            }
            if (ply >= config.maxPlies()) {
                result = "1/2-1/2";
                termination = "ply-limit";
                break;
            }
            if (config.adjudicate() && streak >= config.adjudicationPlies()) {
                result = streakWinner == Side.WHITE ? "1-0" : "0-1";
                termination = "adjudication";
                break;
            }

            Side side = game.sideToMove();
            Player player = side == Side.WHITE ? job.white() : job.black();
            SideStats stats = side == Side.WHITE ? white : black;
            String fen = game.toFen();
            String uci;
            if (player.maia()) {
                boolean sampled = stats.maiaMoves < config.sampledMoves();
                long seed = job.seed() * 1_000_003L + ply;
                SyncMaia.Decision d =
                        maia.play(
                                startFen,
                                game.toUciMoveList(),
                                player.elo(),
                                sampled ? config.sampledTemperature() : 0.0,
                                seed,
                                shadow,
                                new ArrayList<>(history),
                                config.castleVariant(),
                                config.sampledTopK());
                uci = d.move();
                stats.maiaMoves++;
                if (sampled) {
                    stats.sampled++;
                    // The loss is filled in after the game, once every position is evaluated.
                    sampledMoves.add(new SampledMove(job.id(), player.elo(), ply, fen, uci, 0));
                }
                if (!d.topSlotLegal()) {
                    stats.topIllegal++;
                    log.event(
                            "top-slot-illegal",
                            job.id(),
                            ply,
                            fen,
                            "slot " + describeSlot(d.topSlot(), side) + ", played " + uci);
                }
                if (!d.slotCollisions().isEmpty()) {
                    collisions++;
                    log.event(
                            "slot-collision",
                            job.id(),
                            ply,
                            fen,
                            String.join(" ", d.slotCollisions()) + ", played " + uci);
                }
                if (!d.appMove().equals(d.engineMove())) {
                    // The diagnostic pass recomputes the app's choice, sampled moves included,
                    // from the same seed; a difference means it does not see what MaiaEngine
                    // sees.
                    anomalies++;
                    log.event(
                            "diag-mismatch",
                            job.id(),
                            ply,
                            fen,
                            "engine " + d.engineMove() + ", diagnostic " + d.appMove());
                }
            } else {
                uci = stockfish.move(startFen, game.toUciMoveList(), config.movetimeMs());
            }

            boolean castle = isCastle(game, uci);
            if (!game.applyUciMove(uci)) {
                log.event("illegal-move", job.id(), ply, fen, (player.label()) + " played " + uci);
                anomalies++;
                result = "*";
                termination = "error";
                break;
            }
            shadow.doMove(new Move(uci, shadow.getSideToMove()), true);
            if (history.size() == MaiaEvalAccess.HISTORY_STEPS) {
                history.removeFirst();
            }
            history.addLast(MaiaEvalAccess.placement(shadow));
            byMaia.add(player.maia());

            if (castle) {
                if (stats.castlePly < 0) {
                    stats.castlePly = ply;
                }
                if (!castledCorrectly(shadow, uci, side)) {
                    anomalies++;
                    log.event("castle-wrong", job.id(), ply, fen, player.label() + " " + uci);
                }
            }

            int eval;
            if (game.isCheckmate()) {
                eval = -Analyzer.MATE;
            } else if (game.isGameOver()) {
                eval = 0;
            } else {
                eval = analyzer.eval(startFen, game.toUciMoveList());
            }
            evals.add(eval);
            if (Math.abs(eval) > config.adjudicationCp()) {
                Side toMove = game.sideToMove();
                Side ahead = eval > 0 ? toMove : toMove.flip();
                streak = ahead == streakWinner ? streak + 1 : 1;
                streakWinner = ahead;
            } else {
                streak = 0;
                streakWinner = null;
            }
        }

        // A move loses (eval before, mover's view) - (eval after, mover's view), both capped.
        for (int i = 0; i < byMaia.size(); i++) {
            if (!byMaia.get(i)) {
                continue;
            }
            int before = cap(evals.get(i));
            int after = -cap(evals.get(i + 1));
            if (before - after > config.blunderCp()) {
                Side mover = (game.startPly() + i) % 2 == 0 ? Side.WHITE : Side.BLACK;
                (mover == Side.WHITE ? white : black).blunders++;
            }
        }

        for (int i = 0; i < sampledMoves.size(); i++) {
            SampledMove m = sampledMoves.get(i);
            if (m.ply() + 1 >= evals.size()) {
                continue; // the game ended on an error right at this move
            }
            int loss = cap(evals.get(m.ply())) + cap(evals.get(m.ply() + 1));
            sampledMoves.set(
                    i, new SampledMove(m.gameId(), m.elo(), m.ply(), m.fen(), m.move(), loss));
            SideStats side = (game.startPly() + m.ply()) % 2 == 0 ? white : black;
            if (loss > 100) {
                side.sampledLoss100++;
            }
            if (loss > 200) {
                side.sampledLoss200++;
            }
        }
        String[] played = game.toUciMoveList().split(" ");
        String opening =
                String.join(" ", java.util.Arrays.copyOf(played, Math.min(12, played.length)));

        String pgn = pgn(game, job, result, termination);
        return new Result(
                job,
                result,
                termination,
                game.moveCount(),
                white,
                black,
                collisions,
                anomalies,
                System.currentTimeMillis() - started,
                pgn,
                opening,
                sampledMoves);
    }

    private int cap(int cp) {
        return Math.max(-config.evalCapCp(), Math.min(config.evalCapCp(), cp));
    }

    private static String drawReason(Board board) {
        if (board.isInsufficientMaterial()) {
            return "insufficient-material";
        }
        if (board.isRepetition()) {
            return "repetition";
        }
        if (board.getHalfMoveCounter() >= 100) {
            return "fifty-moves";
        }
        return "draw";
    }

    private static boolean isCastle(ChessGame game, String uci) {
        Square from = square(uci.substring(0, 2));
        Square to = square(uci.substring(2, 4));
        Piece mover = game.pieceAt(from);
        if (mover.getPieceType() != PieceType.KING) {
            return false;
        }
        if (game.isChess960()) {
            Piece target = game.pieceAt(to);
            return target.getPieceType() == PieceType.ROOK
                    && target.getPieceSide() == mover.getPieceSide();
        }
        return Math.abs(from.getFile().ordinal() - to.getFile().ordinal()) == 2;
    }

    /** After a castle, king and rook must stand on g/f (king side) or c/d (queen side). */
    private static boolean castledCorrectly(Board after, String uci, Side side) {
        Square from = square(uci.substring(0, 2));
        Square to = square(uci.substring(2, 4));
        boolean kingSide = to.getFile().ordinal() > from.getFile().ordinal();
        Piece king = side == Side.WHITE ? Piece.WHITE_KING : Piece.BLACK_KING;
        Piece rook = side == Side.WHITE ? Piece.WHITE_ROOK : Piece.BLACK_ROOK;
        Square kingTo = Square.encode(from.getRank(), kingSide ? File.FILE_G : File.FILE_C);
        Square rookTo = Square.encode(from.getRank(), kingSide ? File.FILE_F : File.FILE_D);
        return after.getPiece(kingTo) == king && after.getPiece(rookTo) == rook;
    }

    private static Square square(String name) {
        return Square.valueOf(name.toUpperCase(Locale.ROOT));
    }

    /** A policy slot as a move in board coordinates, for the log. */
    private static String describeSlot(int slot, Side side) {
        if (slot >= 64 * 64) {
            int p = slot - 64 * 64;
            char from = (char) ('a' + p / 32);
            char to = (char) ('a' + (p / 4) % 8);
            boolean black = side == Side.BLACK;
            return "" + from + (black ? 2 : 7) + to + (black ? 1 : 8) + "qrbn".charAt(p % 4);
        }
        return square(slot / 64, side) + square(slot % 64, side);
    }

    private static String square(int index, Side side) {
        int rank = index / 8;
        if (side == Side.BLACK) {
            rank = 7 - rank;
        }
        return "" + (char) ('a' + index % 8) + (rank + 1);
    }

    private static String pgn(ChessGame game, Job job, String result, String termination) {
        String text = game.toPgn(job.white().label(), job.black().label());
        text = text.replace("[Event \"MoveAPiece-Partie\"]", "[Event \"Maia960 eval\"]");
        text = text.replace("[Round \"?\"]", "[Round \"" + job.id() + "\"]");
        text = text.replaceFirst("\\[Result \"[^\"]*\"]", "[Result \"" + result + "\"]");
        text =
                text.replaceFirst(
                        "(\\[Result \"[^\"]*\"]\n)", "$1[Termination \"" + termination + "\"]\n");
        // The movetext's own result token: "*" when the game was cut off rather than finished.
        int end = text.stripTrailing().lastIndexOf(' ');
        if (end > 0 && text.stripTrailing().endsWith("*")) {
            text = text.substring(0, end) + " " + result + "\n";
        }
        return text;
    }
}
