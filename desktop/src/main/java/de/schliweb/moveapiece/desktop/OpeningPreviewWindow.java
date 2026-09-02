/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.desktop;

import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.Square;
import de.schliweb.moveapiece.logic.ChessGame;
import de.schliweb.moveapiece.training.OpeningLine;
import de.schliweb.moveapiece.training.OpeningLinePlayer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Read-only step-through viewer for a single {@link OpeningLine}: a non-interactive board plus
 * Previous/Next controls, backed by {@link OpeningLinePlayer}. Opened from {@link
 * OpeningLibraryWindow}. Ported from the Android app's {@code ui.OpeningPreviewActivity}.
 */
final class OpeningPreviewWindow {

    private OpeningPreviewWindow() {}

    static void show(Stage owner, OpeningLine line) {
        OpeningLinePlayer player = new OpeningLinePlayer(line);

        BoardCanvas board = new BoardCanvas();
        board.setInteractive(false);

        Label progressLabel = new Label();
        progressLabel.getStyleClass().add("training-progress-label");
        TextArea moveListArea = new TextArea(fullSan(line));
        moveListArea.setEditable(false);
        moveListArea.setWrapText(true);
        moveListArea.setPrefRowCount(4);
        moveListArea.getStyleClass().add("move-list");

        Button previousButton = new Button("◀ " + Messages.get("action_previous_move"));
        Button nextButton = new Button(Messages.get("action_next_move") + " ▶");

        Runnable refresh =
                () -> {
                    ChessGame game = player.gameAtCurrentPly();
                    Piece[] pieces = new Piece[64];
                    for (int i = 0; i < 64; i++) {
                        pieces[i] = game.pieceAt(Square.squareAt(i));
                    }
                    board.setBoard(pieces);
                    Square[] lastMove = player.lastMoveSquares();
                    board.setLastMove(
                            lastMove == null ? null : lastMove[0],
                            lastMove == null ? null : lastMove[1]);
                    progressLabel.setText(
                            Messages.get(
                                    "opening_preview_progress_format",
                                    player.ply(),
                                    player.totalPlies()));
                    previousButton.setDisable(!player.hasPrevious());
                    nextButton.setDisable(!player.hasNext());
                };

        previousButton.setOnAction(
                e -> {
                    player.previous();
                    refresh.run();
                });
        nextButton.setOnAction(
                e -> {
                    player.next();
                    refresh.run();
                });

        StackPane boardHolder = new StackPane(board);
        boardHolder.getStyleClass().add("card");
        boardHolder.setMinSize(320, 320);
        boardHolder.widthProperty().addListener((obs, old, val) -> resizeBoard(board, boardHolder));
        boardHolder
                .heightProperty()
                .addListener((obs, old, val) -> resizeBoard(board, boardHolder));

        HBox navBox = new HBox(8, previousButton, nextButton);
        navBox.setAlignment(Pos.CENTER);

        Label movesHeading = new Label(Messages.get("move_history_title"));
        movesHeading.getStyleClass().add("section-label");

        VBox root = new VBox(10, boardHolder, progressLabel, navBox, movesHeading, moveListArea);
        VBox.setVgrow(boardHolder, Priority.ALWAYS);
        root.setPadding(new Insets(16));
        root.setAlignment(Pos.TOP_CENTER);

        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.setTitle(OpeningNames.displayName(line));
        Scene scene = new Scene(root, 420, 600);
        Styles.apply(scene);
        stage.setScene(scene);

        refresh.run();
        stage.show();
    }

    private static final double BOARD_HOLDER_PADDING = 16;

    private static void resizeBoard(BoardCanvas board, StackPane holder) {
        double size =
                Math.max(
                        0,
                        Math.min(holder.getWidth(), holder.getHeight()) - 2 * BOARD_HOLDER_PADDING);
        board.setSize(size);
    }

    private static String fullSan(OpeningLine line) {
        ChessGame game = new ChessGame();
        for (String uci : line.uciMoves()) {
            game.applyUciMove(uci);
        }
        return game.toSan();
    }
}
