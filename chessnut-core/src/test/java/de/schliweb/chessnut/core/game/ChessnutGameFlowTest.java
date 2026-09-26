/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.chessnut.core.game;

import static de.schliweb.chessnut.core.game.Boards.lift;
import static de.schliweb.chessnut.core.game.Boards.move;
import static de.schliweb.chessnut.core.game.Boards.put;
import static de.schliweb.chessnut.core.game.Boards.sq;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import de.schliweb.chessnut.core.protocol.ChessnutCommands;
import de.schliweb.chessnut.core.protocol.ChessnutLedController;
import de.schliweb.pegasus.core.chess.ChessPosition;
import de.schliweb.pegasus.core.protocol.BoardState;
import de.schliweb.pegasus.core.protocol.PieceCodes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class ChessnutGameFlowTest {

    /** Records LED commands as square-name lists ("off" for all-zero). */
    private final List<String> leds = new ArrayList<>();

    private final List<String> events = new ArrayList<>();

    private final ChessnutGameFlow flow =
            new ChessnutGameFlow(
                    new ChessnutLedController(this::recordLed),
                    new ChessnutGameFlow.Listener() {
                        @Override
                        public void onPhysicalMoveConfirmed(String uci) {
                            events.add("move:" + uci);
                        }

                        @Override
                        public void onBoardMismatch(boolean mismatched) {
                            events.add("mismatch:" + mismatched);
                        }

                        @Override
                        public void onEngineMoveGuidanceComplete() {
                            events.add("guided");
                        }

                        @Override
                        public void onGuideDeviation(boolean deviating) {
                            events.add("deviation:" + deviating);
                        }
                    });

    private void recordLed(byte[] command) {
        List<String> names = new ArrayList<>();
        for (int square = 0; square < 64; square++) {
            byte[] single = ChessnutCommands.encodeLeds(square);
            for (int i = 2; i < 10; i++) {
                if ((single[i] & command[i]) != 0) {
                    names.add(BoardState.squareName(square));
                    break;
                }
            }
        }
        Collections.sort(names);
        leds.add(names.isEmpty() ? "off" : String.join(",", names));
    }

    private static String afterE4() {
        return "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1";
    }

    @Test
    public void startBoardIsInSyncWithNothingLit() {
        flow.onPhysicalBoard(Boards.start());

        assertEquals(Collections.singletonList("mismatch:false"), events);
        assertTrue(leds.isEmpty());
        assertTrue(flow.isBoardInSync());
        assertFalse(flow.isBoardMismatched());
        assertEquals(ChessPosition.STARTING_FEN, flow.trackedFen());
    }

    @Test
    public void physicalMoveIsReportedAndTracked() {
        flow.onPhysicalBoard(Boards.start());
        flow.onPhysicalBoard(lift(Boards.start(), "e2"));
        flow.onPhysicalBoard(move(Boards.start(), "e2", "e4"));

        assertEquals(Arrays.asList("mismatch:false", "move:e2e4", "mismatch:false"), events);
        assertEquals(afterE4(), flow.trackedFen());
        assertTrue(leds.isEmpty());
    }

    @Test
    public void checkLightsTheKingUntilTheReplyResolvesIt() {
        String fen = "4k3/8/8/8/8/8/8/4K2R w - - 0 1";
        flow.syncBoardToPosition(fen);
        flow.onPhysicalBoard(Boards.of(fen));
        flow.onPhysicalBoard(move(Boards.of(fen), "h1", "h8"));

        assertEquals("e8", leds.get(leds.size() - 1));
        assertEquals("move:h1h8", events.get(events.size() - 2));

        BoardState afterCheck = move(Boards.of(fen), "h1", "h8");
        flow.onPhysicalBoard(move(afterCheck, "e8", "d7"));
        assertEquals("off", leds.get(leds.size() - 1));
    }

    @Test
    public void mismatchLightsSquaresAndClearsOnRestore() {
        flow.onPhysicalBoard(Boards.start());
        flow.onPhysicalBoard(move(Boards.start(), "e2", "e5"));

        assertEquals("e2,e5", leds.get(0));
        assertEquals("mismatch:true", events.get(events.size() - 1));
        assertTrue(flow.isBoardMismatched());
        assertFalse(flow.isBoardInSync());
        assertEquals(Arrays.asList(sq("e2"), sq("e5")), flow.mismatchSquares());

        flow.onPhysicalBoard(Boards.start());
        assertEquals("off", leds.get(1));
        assertEquals("mismatch:false", events.get(events.size() - 1));
        assertTrue(flow.mismatchSquares().isEmpty());
    }

    @Test
    public void guideLightsMoveUntilExecutedThenPlayContinues() {
        flow.onPhysicalBoard(Boards.start());
        flow.guideEngineMove("e2e4");

        assertTrue(flow.isGuideActive());
        assertEquals(Collections.singletonList("e2,e4"), leds);

        flow.onPhysicalBoard(lift(Boards.start(), "e2"));
        assertEquals(1, leds.size()); // same pattern, deduped

        flow.onPhysicalBoard(move(Boards.start(), "e2", "e4"));
        assertFalse(flow.isGuideActive());
        assertEquals("off", leds.get(1));
        assertEquals("guided", events.get(events.size() - 1));
        assertEquals(afterE4(), flow.trackedFen());

        BoardState reply = move(move(Boards.start(), "e2", "e4"), "d7", "d5");
        flow.onPhysicalBoard(reply);
        assertEquals("move:d7d5", events.get(events.size() - 2));
    }

    @Test
    public void guideWithPieceAlreadyInHandStarts() {
        flow.onPhysicalBoard(Boards.start());
        flow.onPhysicalBoard(lift(Boards.start(), "e2"));
        flow.guideEngineMove("e2e4");

        assertTrue(flow.isGuideActive());
        assertEquals("e2,e4", leds.get(leds.size() - 1));
    }

    @Test
    public void guideIgnoredWhilePiecesAreMisplacedOrMoveIllegal() {
        flow.onPhysicalBoard(Boards.start());
        flow.onPhysicalBoard(move(Boards.start(), "e2", "e5"));
        flow.guideEngineMove("e2e4");
        assertFalse(flow.isGuideActive());

        flow.onPhysicalBoard(Boards.start());
        flow.guideEngineMove("e2e5");
        assertFalse(flow.isGuideActive());
        flow.guideEngineMove("garbage");
        assertFalse(flow.isGuideActive());
    }

    @Test
    public void quizModeStaysDarkButRevealsDeviation() {
        flow.onPhysicalBoard(Boards.start());
        flow.guideEngineMove("e2e4", false);

        assertTrue(flow.isGuideActive());
        assertTrue(leds.isEmpty());

        BoardState wrong = move(Boards.start(), "d2", "d4");
        flow.onPhysicalBoard(wrong);
        assertEquals("d2,d4", leds.get(leds.size() - 1));
        assertEquals("deviation:true", events.get(events.size() - 1));
        assertTrue(flow.isBoardMismatched());
        assertEquals(Arrays.asList(sq("d2"), sq("d4")), flow.mismatchSquares());

        flow.onPhysicalBoard(Boards.start());
        assertEquals("off", leds.get(leds.size() - 1));
        assertEquals("deviation:false", events.get(events.size() - 1));
        assertFalse(flow.isBoardMismatched());

        flow.onPhysicalBoard(move(Boards.start(), "e2", "e4"));
        assertEquals("guided", events.get(events.size() - 1));
    }

    @Test
    public void guideCaptureShowsBothSquaresAndCompletesOnAttackerPlaced() {
        String fen = "rnbqkbnr/ppp1pppp/8/3p4/4P3/8/PPPP1PPP/RNBQKBNR w KQkq d6 0 2";
        flow.syncBoardToPosition(fen);
        flow.onPhysicalBoard(Boards.of(fen));
        flow.guideEngineMove("e4d5");

        assertEquals("d5,e4", leds.get(leds.size() - 1));
        flow.onPhysicalBoard(lift(Boards.of(fen), "e4"));
        assertTrue(flow.isGuideActive());
        flow.onPhysicalBoard(move(Boards.of(fen), "e4", "d5"));
        assertFalse(flow.isGuideActive());
        assertEquals("guided", events.get(events.size() - 1));
    }

    @Test
    public void newGameLightsABoardThatIsNotReset() {
        flow.onPhysicalBoard(Boards.start());
        flow.onPhysicalBoard(move(Boards.start(), "e2", "e4"));
        flow.resetForNewGame();

        assertEquals(ChessPosition.STARTING_FEN, flow.trackedFen());
        assertEquals("e2,e4", leds.get(leds.size() - 1));
        assertEquals("mismatch:true", events.get(events.size() - 1));

        flow.onPhysicalBoard(Boards.start());
        assertEquals("mismatch:false", events.get(events.size() - 1));
        assertTrue(flow.isBoardInSync());
    }

    @Test
    public void syncToPositionIgnoresMalformedFenAndAbortsGuide() {
        flow.onPhysicalBoard(Boards.start());
        flow.guideEngineMove("e2e4");
        flow.syncBoardToPosition("not a fen");
        assertTrue(flow.isGuideActive());

        flow.syncBoardToPosition(afterE4());
        assertFalse(flow.isGuideActive());
        assertEquals("e2,e4", leds.get(leds.size() - 1));
        assertEquals("mismatch:true", events.get(events.size() - 1));
    }

    @Test
    public void reconnectKeepsPositionAndWaitsForTheBoard() {
        flow.onPhysicalBoard(Boards.start());
        flow.onPhysicalBoard(move(Boards.start(), "e2", "e4"));
        flow.onConnected();

        assertEquals(afterE4(), flow.trackedFen());
        assertFalse(flow.isBoardInSync());

        flow.onPhysicalBoard(move(Boards.start(), "e2", "e4"));
        assertTrue(flow.isBoardInSync());
        assertEquals("mismatch:false", events.get(events.size() - 1));
    }

    @Test
    public void pawnOnBackRankAsksForThePromotionPiece() {
        String fen = "4k3/P7/8/8/8/8/8/4K3 w - - 0 1";
        flow.syncBoardToPosition(fen);
        flow.onPhysicalBoard(Boards.of(fen));
        assertEquals(-1, flow.promotionSquareAwaitingPiece());

        flow.onPhysicalBoard(move(Boards.of(fen), "a7", "a8"));
        assertEquals("mismatch:true", events.get(events.size() - 1));
        assertEquals(sq("a8"), flow.promotionSquareAwaitingPiece());
        assertEquals(Arrays.asList(sq("a7"), sq("a8")), flow.mismatchSquares());

        flow.onPhysicalBoard(put(lift(Boards.of(fen), "a7"), "a8", PieceCodes.WQUEEN));
        assertEquals("move:a7a8q", events.get(events.size() - 2));
        assertEquals(-1, flow.promotionSquareAwaitingPiece());
    }

    @Test
    public void capturePromotionAndGuidedPromotionAlsoAskForThePiece() {
        String fen = "1n2k3/P7/8/8/8/8/8/4K3 w - - 0 1";
        flow.syncBoardToPosition(fen);
        flow.onPhysicalBoard(Boards.of(fen));
        flow.onPhysicalBoard(move(Boards.of(fen), "a7", "b8"));
        assertEquals(sq("b8"), flow.promotionSquareAwaitingPiece());

        flow.onPhysicalBoard(Boards.of(fen));
        flow.guideEngineMove("a7a8q");
        assertTrue(flow.isGuideActive());
        assertEquals(-1, flow.promotionSquareAwaitingPiece());
        flow.onPhysicalBoard(move(Boards.of(fen), "a7", "a8"));
        assertTrue(flow.isGuideActive());
        assertEquals(sq("a8"), flow.promotionSquareAwaitingPiece());
        flow.onPhysicalBoard(put(lift(Boards.of(fen), "a7"), "a8", PieceCodes.WQUEEN));
        assertEquals("guided", events.get(events.size() - 1));
    }

    @Test
    public void ordinaryMismatchIsNotAPromotionHint() {
        flow.onPhysicalBoard(Boards.start());
        flow.onPhysicalBoard(move(Boards.start(), "e2", "e5"));
        assertEquals(-1, flow.promotionSquareAwaitingPiece());
    }

    @Test
    public void promotionNeedsNoDialog() {
        String fen = "4k3/P7/8/8/8/8/8/4K3 w - - 0 1";
        flow.syncBoardToPosition(fen);
        flow.onPhysicalBoard(Boards.of(fen));
        flow.onPhysicalBoard(put(lift(Boards.of(fen), "a7"), "a8", PieceCodes.WQUEEN));

        assertEquals("move:a7a8q", events.get(events.size() - 2));
    }
}
