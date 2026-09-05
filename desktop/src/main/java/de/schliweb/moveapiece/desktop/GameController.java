/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.desktop;

import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.Square;
import de.schliweb.moveapiece.engine.EngineListener;
import de.schliweb.moveapiece.engine.NnueAssets;
import de.schliweb.moveapiece.engine.StockfishEngine;
import de.schliweb.moveapiece.engine.UciInfoParser;
import de.schliweb.moveapiece.logic.ChessGame;
import de.schliweb.moveapiece.logic.PgnGames;
import de.schliweb.moveapiece.training.OpeningLine;
import de.schliweb.moveapiece.training.TrainingSession;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Slider;
import javafx.scene.control.TextArea;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * Wires a {@link ChessGame}, a {@link StockfishEngine} and a {@link BoardCanvas} together into a
 * playable window: human-vs-human, human-vs-Stockfish, an in-game opening trainer that drills a
 * fixed line from {@code core}'s opening library, adjustable engine strength, undo, and PGN
 * import/export. Mirrors the Android app's {@code MainActivity} orchestration at a much smaller
 * scope.
 */
final class GameController implements BoardCanvas.MoveSource, EngineListener {

    private enum Mode {
        HUMAN_VS_HUMAN,
        HUMAN_VS_STOCKFISH,
        TRAINING
    }

    /**
     * Tracks what an in-flight {@code go} search is for, and which "epoch" (see {@link
     * #searchGeneration}) it was started in - so a reply that arrives after the position has since
     * moved on (undo, new game, PGN import) can be told apart from a real, still-relevant one.
     */
    private enum SearchPurpose {
        REAL_MOVE,
        ANALYSIS,
        HINT,
        POST_GAME
    }

    private record PendingSearch(SearchPurpose purpose, int generation) {}

    private static final int MOVETIME_MS = 800;
    private static final int ANALYSIS_MOVETIME_MS = 1500;
    private static final int HINT_MOVETIME_MS = 1500;
    private static final int HINT_MULTI_PV_LINES = 3;
    private static final int POST_GAME_MOVETIME_MS = 400;
    private static final int TRAINING_AUTO_MOVE_DELAY_MS = 600;

    private final Stage stage;
    private final ChessGame game = new ChessGame();
    private final BoardCanvas boardCanvas = new BoardCanvas();
    private final Label statusLabel = new Label();
    private final TextArea moveListArea = new TextArea();
    private final Slider strengthSlider = new Slider(1320, 3190, Settings.getEngineElo());
    private final Label strengthLabel = new Label();
    // Same Material icon glyphs as the Android app's ic_undo.xml/ic_flip_board.xml
    // (SVG path data reused verbatim - both use the same path-string syntax).
    private static final String NEW_GAME_ICON_PATH = "M19,13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z";
    private static final String UNDO_ICON_PATH =
            "M12.5,8c-2.65,0 -5.05,0.99 -6.9,2.6L2,7v9h9l-3.62,-3.62c1.39,-1.16 3.16,-1.88 5.12,-1.88 "
                    + "3.54,0 6.55,2.31 7.6,5.5l2.37,-0.78C21.08,11.03 17.15,8 12.5,8z";
    private static final String FLIP_BOARD_ICON_PATH =
            "M16,17.01V10h-2v7.01h-3L15,21l4,-3.99h-3zM9,3L5,6.99h3V14h2V6.99h3L9,3z";
    private static final String OPENING_LIBRARY_ICON_PATH =
            "M3,19H21V21H3Z M5,4H8V19H5Z M10,7H13V19H10Z M15,10H19V19H15Z";
    // Same Material "lightbulb" glyph as the Android app's ic_hint.xml.
    private static final String HINT_ICON_PATH =
            "M9,21c0,0.55 0.45,1 1,1h4c0.55,0 1,-0.45 1,-1v-1H9V21zM12,2C8.14,2 5,5.14 5,9c0,2.38 "
                    + "1.19,4.47 3,5.74V17c0,0.55 0.45,1 1,1h6c0.55,0 1,-0.45 1,-1v-2.26c1.81,-1.27 "
                    + "3,-3.36 3,-5.74C19,5.14 15.86,2 12,2z";
    // Same Material "download"/"upload"/"search" glyphs as the Android app's ic_export.xml/
    // ic_import.xml/ic_analyze.xml.
    private static final String EXPORT_ICON_PATH = "M19,9h-4V3H9v6H5l7,7l7,-7zM5,18v2h14v-2H5z";
    private static final String IMPORT_ICON_PATH = "M9,16h6v-6h4l-7,-7l-7,7h4v6zM5,18h14v2H5v-2z";
    private static final String ANALYZE_ICON_PATH =
            "M15.5,14h-0.79l-0.28,-0.27C15.41,12.59 16,11.11 16,9.5C16,5.91 13.09,3 9.5,3S3,5.91 "
                    + "3,9.5S5.91,16 9.5,16c1.61,0 3.09,-0.59 4.23,-1.57l0.27,0.28v0.79l5,4.99L20.49,19"
                    + "L15.5,14zM9.5,14C7.01,14 5,11.99 5,9.5S7.01,5 9.5,5S14,7.01 14,9.5S11.99,14 9.5,14z";

    private final Button undoButton = iconButton(UNDO_ICON_PATH, Messages.get("menu_undo"));
    private final Button newGameButton =
            iconButton(NEW_GAME_ICON_PATH, Messages.get("menu_new_game"));
    private final Button importButton =
            iconButton(IMPORT_ICON_PATH, Messages.get("action_import_pgn"));
    private final Button exportButton =
            iconButton(EXPORT_ICON_PATH, Messages.get("action_export_pgn"));
    private final Button analyzeGameButton =
            iconButton(OPENING_LIBRARY_ICON_PATH, Messages.get("action_analyze_game"));
    private final Button openingLibraryButton =
            iconButton(ANALYZE_ICON_PATH, Messages.get("menu_opening_library"));
    private final Button flipBoardButton =
            iconButton(FLIP_BOARD_ICON_PATH, Messages.get("menu_flip_board"));
    private final Button hintButton = iconButton(HINT_ICON_PATH, Messages.get("menu_hint"));
    private final CheckBox evaluationCheckbox =
            new CheckBox(Messages.get("evaluation_toggle_label"));
    private final Label evaluationLabel = new Label();
    private final Label moveQualityLabel = new Label();
    private final Label hintAlternativesLabel = new Label();
    private final Label analysisProgressLabel = new Label();
    private final Label trainingProgressLabel = new Label();
    private final MoveSoundPlayer soundPlayer = new MoveSoundPlayer();

    private StockfishEngine engine;
    private StockfishLocator.Location engineLocation;
    private boolean engineReady = false;
    private boolean waitingForEngineMove = false;
    private boolean waitingForHint = false;
    private boolean boardFlipped = false;
    private boolean evaluationEnabled = Settings.isEvaluationDisplayEnabled();
    private Side analysisSideToMove;

    // ---- Move-quality (blunder check) state ------------------------------------
    /**
     * Freshest known eval of the position currently on the board, from the side-to-move's own
     * perspective (raw UCI score, unlike {@link #onInfo}'s white-relative display value) - updated
     * from every "info" line regardless of which search it belongs to. {@link
     * #lastPositionEvalMoveCount} pins it to a specific ply; -1 means "none yet" (requires {@link
     * #evaluationEnabled}, so a fresh game or a toggle-off leaves it stale until the next search
     * completes).
     */
    private int lastPositionEvalCp;

    private int lastPositionEvalMoveCount = -1;

    /**
     * Snapshot of {@link #lastPositionEvalCp}/{@link #lastPositionEvalMoveCount} taken by {@link
     * #recordMoveQualityBaseline} right before a graded move is applied - compared against the eval
     * of the resulting position once that comes in (see {@link #maybeFinalizeMoveQuality}). -1
     * means "not currently grading a move".
     */
    private int moveQualityBaselineCp;

    private int moveQualityBaselineMoveCount = -1;

    private static final int INACCURACY_CP_LOSS = 50;
    private static final int MISTAKE_CP_LOSS = 150;
    private static final int BLUNDER_CP_LOSS = 300;

    // ---- Multi-PV hint state ----------------------------------------------------
    /**
     * True only while a {@link #requestHint} search (run with MultiPV raised to {@link
     * #HINT_MULTI_PV_LINES}) is in flight - {@link #onInfo} routes every line to {@link
     * #captureMultiPvCandidate} instead of the single-eval/move-quality/post-game-analysis paths
     * while this is set, since none of those want a non-PV-1 line's score.
     */
    private boolean multiPvSearchActive;

    private final String[] multiPvMoveByRank = new String[HINT_MULTI_PV_LINES];
    private final int[] multiPvCpByRank = new int[HINT_MULTI_PV_LINES];

    // ---- Post-game analysis state ------------------------------------------------
    /**
     * The game's UCI move list, split into individual moves, while a post-game analysis (see {@link
     * #startPostGameAnalysis}) is replaying and grading it one ply at a time; null when idle.
     */
    private List<String> postGameUciMoves;

    /** Raw (side-to-move-relative) eval collected so far, one per position (size = ply + 1). */
    private List<Integer> postGamePositionEvals;

    /**
     * Latest raw score seen for the position currently being searched during post-game analysis.
     */
    private int postGameLiveScoreCp;

    private final Deque<PendingSearch> pendingSearches = new ArrayDeque<>();
    private int searchGeneration = 0;
    private Mode mode = Mode.HUMAN_VS_STOCKFISH;
    private Side humanSide = Side.WHITE;
    private TrainingSession trainingSession;
    private PauseTransition pendingBookMove;

    private Toggle vsHumanToggle;
    private Toggle vsStockfishToggle;
    private Toggle trainerToggle;
    private Toggle lastConfirmedToggle;
    private boolean suppressModeChange;

    /** Outlined, circular icon-only button (Material "icon button" look) from raw SVG path data. */
    private static Button iconButton(String svgPathData, String tooltipText) {
        SVGPath icon = new SVGPath();
        icon.setContent(svgPathData);
        icon.getStyleClass().add("icon-shape");
        Button button = new Button();
        button.setGraphic(icon);
        button.getStyleClass().add("icon-button");
        button.setTooltip(new Tooltip(tooltipText));
        return button;
    }

    GameController(Stage stage) {
        this.stage = stage;
        startEngine();
    }

    private static final double BOARD_HOLDER_PADDING = 14;

    BorderPane buildView() {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(16));

        StackPane boardHolder = new StackPane(boardCanvas);
        boardHolder.getStyleClass().addAll("card", "board-holder");
        boardHolder.setMinSize(240, 240);
        boardHolder.widthProperty().addListener((obs, old, val) -> resizeBoard(boardHolder));
        boardHolder.heightProperty().addListener((obs, old, val) -> resizeBoard(boardHolder));
        BorderPane.setMargin(boardHolder, new Insets(0, 16, 0, 0));
        root.setCenter(boardHolder);

        root.setRight(buildSidebar());

        boardCanvas.setMoveSource(this);
        boardCanvas.setOnMoveListener(this::onMoveChosen);
        refresh();
        return root;
    }

    private void resizeBoard(StackPane holder) {
        double size =
                Math.max(
                        0,
                        Math.min(holder.getWidth(), holder.getHeight()) - 2 * BOARD_HOLDER_PADDING);
        boardCanvas.setSize(size);
    }

    private VBox buildSidebar() {
        ToggleGroup modeGroup = new ToggleGroup();
        RadioButton vsHuman = new RadioButton(Messages.get("mode_human_vs_human"));
        RadioButton vsStockfish = new RadioButton(Messages.get("mode_human_vs_stockfish"));
        RadioButton trainer = new RadioButton(Messages.get("mode_opening_trainer"));
        vsHuman.setToggleGroup(modeGroup);
        vsStockfish.setToggleGroup(modeGroup);
        trainer.setToggleGroup(modeGroup);
        vsStockfish.setSelected(true);
        vsHumanToggle = vsHuman;
        vsStockfishToggle = vsStockfish;
        trainerToggle = trainer;
        lastConfirmedToggle = vsStockfish;

        modeGroup
                .selectedToggleProperty()
                .addListener(
                        (obs, old, selected) -> {
                            if (suppressModeChange) {
                                return;
                            }
                            if (selected == trainer) {
                                Optional<TrainingChoice> choice = TrainingSetupDialog.show(stage);
                                if (choice.isPresent()) {
                                    TrainingChoice c = choice.get();
                                    startTraining(c.opening(), c.side(), c.hintsEnabled());
                                    lastConfirmedToggle = trainer;
                                } else {
                                    suppressModeChange = true;
                                    lastConfirmedToggle.setSelected(true);
                                    suppressModeChange = false;
                                }
                                return;
                            }
                            mode =
                                    selected == vsStockfish
                                            ? Mode.HUMAN_VS_STOCKFISH
                                            : Mode.HUMAN_VS_HUMAN;
                            trainingSession = null;
                            stopPendingBookMove();
                            humanSide = Side.WHITE;
                            boardFlipped = false;
                            boardCanvas.setFlipped(false);
                            boardCanvas.setTrainingHint(null, null);
                            strengthSlider.setDisable(mode != Mode.HUMAN_VS_STOCKFISH);
                            lastConfirmedToggle = selected;
                            newGame();
                        });

        flipBoardButton.setOnAction(
                e -> {
                    boardFlipped = !boardFlipped;
                    boardCanvas.setFlipped(boardFlipped);
                });

        evaluationCheckbox.setSelected(evaluationEnabled);
        evaluationCheckbox.setOnAction(
                e -> {
                    evaluationEnabled = evaluationCheckbox.isSelected();
                    Settings.setEvaluationDisplayEnabled(evaluationEnabled);
                    if (!evaluationEnabled) {
                        evaluationLabel.setText("");
                    } else {
                        maybeTriggerAnalysis();
                    }
                });
        evaluationLabel.managedProperty().bind(evaluationLabel.visibleProperty());
        evaluationCheckbox.managedProperty().bind(evaluationCheckbox.visibleProperty());

        trainingProgressLabel.setWrapText(true);
        trainingProgressLabel.managedProperty().bind(trainingProgressLabel.visibleProperty());

        strengthSlider.setShowTickLabels(false);
        strengthSlider
                .valueProperty()
                .addListener(
                        (obs, old, val) -> {
                            strengthLabel.setText(
                                    Messages.get("dialog_strength_format", val.intValue()));
                            Settings.setEngineElo(val.intValue());
                            if (engineReady) {
                                engine.setStrength(val.intValue());
                            }
                        });
        strengthLabel.setText(
                Messages.get("dialog_strength_format", (int) strengthSlider.getValue()));

        moveListArea.setEditable(false);
        moveListArea.setWrapText(true);
        moveListArea.setPrefRowCount(10);
        moveListArea.getStyleClass().add("move-list");
        VBox.setVgrow(moveListArea, Priority.ALWAYS);

        statusLabel.getStyleClass().add("status-label");
        trainingProgressLabel.getStyleClass().add("training-progress-label");

        newGameButton.setOnAction(
                e -> {
                    if (mode == Mode.TRAINING) {
                        TrainingSetupDialog.show(stage)
                                .ifPresent(
                                        c ->
                                                startTraining(
                                                        c.opening(), c.side(), c.hintsEnabled()));
                    } else {
                        newGame();
                    }
                });
        undoButton.setOnAction(e -> undo());
        importButton.setOnAction(e -> importPgn());
        exportButton.setOnAction(e -> exportPgn());
        analyzeGameButton.setOnAction(e -> startPostGameAnalysis());
        openingLibraryButton.setOnAction(e -> OpeningLibraryWindow.show(stage));
        hintButton.setOnAction(e -> requestHint());
        hintAlternativesLabel.getStyleClass().add("training-progress-label");
        hintAlternativesLabel.setWrapText(true);
        hintAlternativesLabel.setVisible(false);
        hintAlternativesLabel.managedProperty().bind(hintAlternativesLabel.visibleProperty());
        analysisProgressLabel.getStyleClass().add("training-progress-label");
        analysisProgressLabel.setVisible(false);
        analysisProgressLabel.managedProperty().bind(analysisProgressLabel.visibleProperty());

        VBox modeBox = new VBox(4, vsHuman, vsStockfish, trainer);
        HBox pgnBox = new HBox(8, importButton, exportButton, analyzeGameButton);
        pgnBox.setAlignment(Pos.CENTER_LEFT);
        HBox actionBox =
                new HBox(
                        8,
                        newGameButton,
                        undoButton,
                        flipBoardButton,
                        openingLibraryButton,
                        hintButton);
        actionBox.setAlignment(Pos.CENTER_LEFT);
        HBox evalBox = new HBox(8, evaluationCheckbox, evaluationLabel);
        evalBox.setAlignment(Pos.CENTER_LEFT);
        moveQualityLabel.getStyleClass().add("move-quality-label");
        moveQualityLabel.setVisible(false);
        moveQualityLabel.managedProperty().bind(moveQualityLabel.visibleProperty());

        Label movesHeading = new Label(Messages.get("move_history_title"));
        movesHeading.getStyleClass().add("section-label");

        VBox sidebar =
                new VBox(
                        10,
                        modeBox,
                        strengthLabel,
                        strengthSlider,
                        actionBox,
                        hintAlternativesLabel,
                        evalBox,
                        moveQualityLabel,
                        statusLabel,
                        trainingProgressLabel,
                        movesHeading,
                        moveListArea,
                        pgnBox,
                        analysisProgressLabel);
        sidebar.getStyleClass().addAll("card", "sidebar");
        sidebar.setPrefWidth(260);
        return sidebar;
    }

    // ---- BoardCanvas.MoveSource ------------------------------------------------

    @Override
    public java.util.List<Square> legalDestinationsFrom(Square from) {
        return game.legalDestinationsFrom(from);
    }

    @Override
    public boolean hasOwnPieceOn(Square square) {
        Piece piece = game.pieceAt(square);
        return piece != Piece.NONE && piece.getPieceSide() == game.sideToMove();
    }

    // ---- move handling ----------------------------------------------------------

    private void onMoveChosen(Square from, Square to) {
        if (mode == Mode.TRAINING) {
            onTrainingMoveChosen(from, to);
            return;
        }
        Piece promotion = null;
        if (game.isPromotion(from, to)) {
            promotion = askPromotionPiece(game.sideToMove());
            if (promotion == null) {
                refresh();
                return;
            }
        }
        boolean wasCapture = game.pieceAt(to) != Piece.NONE;
        recordMoveQualityBaseline();
        if (!game.applyMove(from, to, promotion)) {
            refresh();
            return;
        }
        boardCanvas.setLastMove(from, to);
        playMoveSound(wasCapture);
        refresh();
        maybeStartEngineMove();
    }

    /** Check sound takes priority over move/capture, matching common chess-app UX. */
    private void playMoveSound(boolean wasCapture) {
        if (game.isCheck()) {
            soundPlayer.playCheck();
        } else if (wasCapture) {
            soundPlayer.playCapture();
        } else {
            soundPlayer.playMove();
        }
    }

    /**
     * Promotion picker shown as the four promotion pieces' own board artwork
     * (queen/rook/bishop/knight, in that order) in the color of the promoting side, rather than a
     * plain text list - ported from the Android app's {@code
     * MainActivity.showPromotionPickerDialog}.
     */
    private Piece askPromotionPiece(Side side) {
        boolean white = side == Side.WHITE;
        String colorPrefix = white ? "w" : "b";
        Piece[] result = new Piece[1];

        Stage dialog = new Stage();
        dialog.initOwner(stage);
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.setTitle(Messages.get("promotion_title"));
        dialog.setResizable(false);

        HBox box =
                new HBox(
                        12,
                        promotionButton(
                                colorPrefix,
                                "q",
                                white ? Piece.WHITE_QUEEN : Piece.BLACK_QUEEN,
                                result,
                                dialog),
                        promotionButton(
                                colorPrefix,
                                "r",
                                white ? Piece.WHITE_ROOK : Piece.BLACK_ROOK,
                                result,
                                dialog),
                        promotionButton(
                                colorPrefix,
                                "b",
                                white ? Piece.WHITE_BISHOP : Piece.BLACK_BISHOP,
                                result,
                                dialog),
                        promotionButton(
                                colorPrefix,
                                "n",
                                white ? Piece.WHITE_KNIGHT : Piece.BLACK_KNIGHT,
                                result,
                                dialog));
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(16));

        Label heading = new Label(Messages.get("promotion_heading"));
        heading.getStyleClass().add("section-label");
        VBox content = new VBox(10, heading, box);
        content.setAlignment(Pos.CENTER);
        content.getStyleClass().add("card");
        content.setPadding(new Insets(16));

        Scene scene = new Scene(content);
        Styles.apply(scene);
        dialog.setScene(scene);
        dialog.showAndWait();
        return result[0];
    }

    private Button promotionButton(
            String colorPrefix, String typeSuffix, Piece piece, Piece[] result, Stage dialog) {
        ImageView imageView = new ImageView(loadPieceImage(colorPrefix, typeSuffix));
        imageView.setFitWidth(64);
        imageView.setFitHeight(64);
        Button button = new Button();
        button.setGraphic(imageView);
        button.setOnAction(
                e -> {
                    result[0] = piece;
                    dialog.close();
                });
        return button;
    }

    private static Image loadPieceImage(String colorPrefix, String typeSuffix) {
        return new Image(
                GameController.class.getResourceAsStream(
                        "pieces/piece_" + colorPrefix + typeSuffix + ".png"));
    }

    private void maybeStartEngineMove() {
        if (mode != Mode.HUMAN_VS_STOCKFISH || game.isGameOver()) {
            return;
        }
        if (game.sideToMove() == humanSide) {
            return;
        }
        if (!engineReady) {
            return;
        }
        waitingForEngineMove = true;
        boardCanvas.setInteractive(false);
        startEngineSearch(true, MOVETIME_MS);
    }

    /**
     * Stops whatever the engine is currently doing, queues the purpose of the search being started
     * (see {@link PendingSearch}) and kicks it off from the current position - shared by the
     * real-move and analysis triggers so neither can silently run into the other's still-active
     * search.
     */
    private void startEngineSearch(boolean isRealMove, int movetimeMs) {
        engine.stop();
        pendingSearches.add(
                new PendingSearch(
                        isRealMove ? SearchPurpose.REAL_MOVE : SearchPurpose.ANALYSIS,
                        searchGeneration));
        analysisSideToMove = game.sideToMove();
        engine.setPosition(game.toUciMoveList());
        engine.go(movetimeMs);
    }

    /**
     * Asks Stockfish for the best move in the current position and shows it as a board highlight
     * (reusing {@link BoardCanvas#setTrainingHint}) without applying it - the human decides whether
     * to play it. Always searches at full strength regardless of {@link #strengthSlider}, then
     * restores that strength for whatever search comes next (see {@link #onBestMove}), so a
     * weakened opponent doesn't leak into the hint.
     */
    private void requestHint() {
        if (!engineReady
                || mode == Mode.TRAINING
                || waitingForHint
                || postGameUciMoves != null
                || !isBoardInteractiveNow()) {
            return;
        }
        waitingForHint = true;
        updateHintButtonState();
        engine.stop();
        pendingSearches.add(new PendingSearch(SearchPurpose.HINT, searchGeneration));
        multiPvSearchActive = true;
        java.util.Arrays.fill(multiPvMoveByRank, null);
        engine.setFullStrength();
        engine.setMultiPv(HINT_MULTI_PV_LINES);
        engine.setPosition(game.toUciMoveList());
        engine.go(HINT_MOVETIME_MS);
    }

    private void showHint(String uci) {
        Square from = Square.fromValue(uci.substring(0, 2).toUpperCase(Locale.ROOT));
        Square to = Square.fromValue(uci.substring(2, 4).toUpperCase(Locale.ROOT));
        boardCanvas.setTrainingHint(from, to);
    }

    /**
     * Shows the 2nd/3rd-best candidates collected by {@link #captureMultiPvCandidate} during the
     * hint search that just finished, as plain from-to text next to the board-highlighted best move
     * (rank 1) - hidden if the engine didn't report that many distinct lines (e.g. very few legal
     * moves).
     */
    private void showHintAlternatives() {
        List<String> alternatives = new ArrayList<>();
        for (int rank = 1; rank < HINT_MULTI_PV_LINES; rank++) {
            String uci = multiPvMoveByRank[rank];
            if (uci == null) {
                continue;
            }
            String squares =
                    uci.substring(0, 2).toLowerCase(Locale.ROOT)
                            + "-"
                            + uci.substring(2, 4).toLowerCase(Locale.ROOT);
            String cpLossText = String.format(Locale.ROOT, "%+.1f", multiPvCpByRank[rank] / 100.0);
            alternatives.add(Messages.get("hint_alternative_format", squares, cpLossText));
        }
        if (alternatives.isEmpty()) {
            hintAlternativesLabel.setVisible(false);
            return;
        }
        hintAlternativesLabel.setText(
                Messages.get("hint_alternatives_label", String.join(", ", alternatives)));
        hintAlternativesLabel.setVisible(true);
    }

    /**
     * Records one MultiPV line's move+score, indexed by its 1-based {@code multipv} rank (see
     * {@link UciInfoParser#parseMultiPv}) - only called while {@link #multiPvSearchActive}. Later
     * lines for the same rank (deeper iterations) simply overwrite earlier ones, so what's left
     * once the search ends is the converged answer.
     */
    private void captureMultiPvCandidate(String infoLine) {
        OptionalInt multiPv = UciInfoParser.parseMultiPv(infoLine);
        if (multiPv.isEmpty()
                || multiPv.getAsInt() < 1
                || multiPv.getAsInt() > HINT_MULTI_PV_LINES) {
            return;
        }
        Optional<String> pvMove = UciInfoParser.parsePvFirstMove(infoLine);
        if (pvMove.isEmpty()) {
            return;
        }
        OptionalInt mate = UciInfoParser.parseScoreMate(infoLine);
        OptionalInt cp =
                mate.isPresent() ? OptionalInt.empty() : UciInfoParser.parseScoreCp(infoLine);
        if (mate.isEmpty() && cp.isEmpty()) {
            return;
        }
        int rank = multiPv.getAsInt() - 1;
        multiPvMoveByRank[rank] = pvMove.get();
        multiPvCpByRank[rank] = mate.isPresent() ? mateToCp(mate.getAsInt()) : cp.getAsInt();
    }

    private void updateHintButtonState() {
        hintButton.setVisible(mode != Mode.TRAINING);
        hintButton.setManaged(mode != Mode.TRAINING);
        hintButton.setDisable(
                !engineReady
                        || waitingForHint
                        || postGameUciMoves != null
                        || !isBoardInteractiveNow());
    }

    /**
     * Converts a "mate in N" score to a centipawn-scale value that still dominates normal evals.
     */
    private static int mateToCp(int mateIn) {
        int magnitude = 100000 - Math.min(Math.abs(mateIn), 100) * 100;
        return mateIn >= 0 ? magnitude : -magnitude;
    }

    private void recordPositionEval(int rawCp) {
        lastPositionEvalCp = rawCp;
        lastPositionEvalMoveCount = game.moveCount();
    }

    /**
     * Snapshots {@link #lastPositionEvalCp} for the position about to be left, so {@link
     * #maybeFinalizeMoveQuality} can grade the move once a fresh eval for the resulting position
     * comes in. Called right before any move is committed to {@link #game} (human move or engine
     * move); silently skips grading this move (baseline left at -1) in training mode or when no
     * eval is known for exactly the current position - e.g. right after toggling evaluation display
     * back on, or the game's very first ply before any search has finished.
     */
    private void recordMoveQualityBaseline() {
        if (mode == Mode.TRAINING || lastPositionEvalMoveCount != game.moveCount()) {
            moveQualityBaselineMoveCount = -1;
            return;
        }
        moveQualityBaselineCp = lastPositionEvalCp;
        moveQualityBaselineMoveCount = game.moveCount();
        moveQualityLabel.setVisible(false);
    }

    /**
     * If a move is currently being graded and the eval that just finished belongs to the resulting
     * position, classifies the move's centipawn loss (baseline eval minus the resulting position's
     * eval, both from the mover's perspective - the latter is the raw, not-yet-flipped score of the
     * position with the opponent to move, so adding rather than subtracting it does the flip) and
     * shows a label for anything worse than a minor inaccuracy.
     */
    private void maybeFinalizeMoveQuality() {
        if (moveQualityBaselineMoveCount < 0
                || lastPositionEvalMoveCount != moveQualityBaselineMoveCount + 1) {
            return;
        }
        int cpLoss = moveQualityBaselineCp + lastPositionEvalCp;
        moveQualityBaselineMoveCount = -1;
        showMoveQualityIfNotable(cpLoss);
    }

    /**
     * Move-quality Messages key for a given centipawn loss, or null if not notable enough to flag.
     */
    private static String moveQualityLabelKey(int cpLoss) {
        if (cpLoss >= BLUNDER_CP_LOSS) {
            return "move_quality_blunder";
        }
        if (cpLoss >= MISTAKE_CP_LOSS) {
            return "move_quality_mistake";
        }
        if (cpLoss >= INACCURACY_CP_LOSS) {
            return "move_quality_inaccuracy";
        }
        return null;
    }

    private void showMoveQualityIfNotable(int cpLoss) {
        String labelKey = moveQualityLabelKey(cpLoss);
        if (labelKey == null) {
            moveQualityLabel.setVisible(false);
            return;
        }
        String cpLossText = String.format(Locale.ROOT, "%.1f", -cpLoss / 100.0);
        moveQualityLabel.setText(
                Messages.get("move_quality_format", Messages.get(labelKey), cpLossText));
        moveQualityLabel.setVisible(true);
    }

    /**
     * Replays the finished game from the start, one ply at a time, grading every move the same way
     * live blunder-check does ({@link #moveQualityLabelKey}) and showing a summary dialog once
     * done. Always searches at full strength, restored afterwards in {@link
     * #advancePostGameAnalysis}. Requires {@link ChessGame#isGameOver()} rather than just some
     * moves played - unlike every other search this method starts, it doesn't call {@link
     * StockfishEngine#newGame()} (which would send "isready" and re-enter {@link #onReadyOk}, and
     * if it were still someone's turn in the live game, fire off an unwanted real move), so nothing
     * may still be live for it to accidentally interfere with.
     */
    private void startPostGameAnalysis() {
        if (!engineReady
                || mode == Mode.TRAINING
                || !game.isGameOver()
                || postGameUciMoves != null) {
            return;
        }
        abandonPendingSearches();
        postGameUciMoves = java.util.Arrays.asList(game.toUciMoveList().split(" "));
        postGamePositionEvals = new ArrayList<>(postGameUciMoves.size() + 1);
        updateHintButtonState();
        updateAnalyzeGameButtonState();
        engine.setFullStrength();
        requestPostGameEvalFor(0);
    }

    /**
     * The final replayed position is the live game's own current one - if it's checkmate/stalemate,
     * it has no legal moves for Stockfish to search (in practice: no "info score" line ever
     * arrives, silently leaving {@link #postGameLiveScoreCp} stuck on the previous position's stale
     * value, which would badly mis-grade the final move). Score it directly from the game's own
     * verdict instead: very bad for whoever's mated, neutral for a stalemate.
     */
    private void requestPostGameEvalFor(int positionIndex) {
        if (positionIndex == postGameUciMoves.size()
                && (game.isCheckmate() || game.isStalemate())) {
            recordPostGameEval(game.isCheckmate() ? -mateToCp(0) : 0);
            return;
        }
        pendingSearches.add(new PendingSearch(SearchPurpose.POST_GAME, searchGeneration));
        engine.setPosition(String.join(" ", postGameUciMoves.subList(0, positionIndex)));
        engine.go(POST_GAME_MOVETIME_MS);
    }

    /** Called from {@link #onBestMove} once the search for one post-game position has finished. */
    private void advancePostGameAnalysis() {
        recordPostGameEval(postGameLiveScoreCp);
    }

    private void recordPostGameEval(int cp) {
        postGamePositionEvals.add(cp);
        int nextPositionIndex = postGamePositionEvals.size();
        updateAnalyzeGameButtonState();
        if (nextPositionIndex <= postGameUciMoves.size()) {
            requestPostGameEvalFor(nextPositionIndex);
            return;
        }
        List<String> uciMoves = postGameUciMoves;
        List<Integer> evals = postGamePositionEvals;
        postGameUciMoves = null;
        postGamePositionEvals = null;
        engine.setStrength((int) strengthSlider.getValue());
        updateHintButtonState();
        updateAnalyzeGameButtonState();
        showPostGameReport(uciMoves, evals);
    }

    /**
     * The icon-only Analyze button has no visible label to carry progress the way the old text
     * button did - {@link #analysisProgressLabel} fills that role instead, shown only while an
     * analysis is actually running.
     */
    private void updateAnalyzeGameButtonState() {
        boolean running = postGameUciMoves != null;
        analyzeGameButton.setDisable(
                running || !engineReady || mode == Mode.TRAINING || !game.isGameOver());
        if (running) {
            analysisProgressLabel.setText(
                    Messages.get(
                            "action_analyzing_format",
                            postGamePositionEvals.size(),
                            postGameUciMoves.size() + 1));
            analysisProgressLabel.setVisible(true);
        } else {
            analysisProgressLabel.setVisible(false);
        }
    }

    /**
     * Splits every SAN move out of {@link ChessGame#toSan()}'s numbered movetext (e.g. "1. e4 e5 2.
     * Nf3" -&gt; ["e4", "e5", "Nf3"]) - ply-ordered, so it lines up 1:1 with {@link
     * ChessGame#toUciMoveList()}'s split.
     */
    private static List<String> sanMoveList(String toSan) {
        List<String> moves = new ArrayList<>();
        for (String token : toSan.split("\\s+")) {
            if (!token.isEmpty() && !token.matches("\\d+\\.")) {
                moves.add(token);
            }
        }
        return moves;
    }

    private static String plyLabel(int plyIndex) {
        int moveNumber = plyIndex / 2 + 1;
        return plyIndex % 2 == 0 ? moveNumber + "." : moveNumber + "...";
    }

    /** Index into every per-side array below: 0 = White, 1 = Black. */
    private void showPostGameReport(List<String> uciMoves, List<Integer> evals) {
        List<String> sanMoves = sanMoveList(game.toSan());
        double[] lossSum = new double[2];
        int[] plies = new int[2];
        int[] inaccuracies = new int[2];
        int[] mistakes = new int[2];
        int[] blunders = new int[2];
        StringBuilder flaggedMoves = new StringBuilder();
        for (int ply = 0; ply < uciMoves.size(); ply++) {
            int side = ply % 2;
            plies[side]++;
            int cpLoss = Math.max(0, evals.get(ply) + evals.get(ply + 1));
            lossSum[side] += cpLoss;
            String labelKey = moveQualityLabelKey(cpLoss);
            if ("move_quality_blunder".equals(labelKey)) {
                blunders[side]++;
            } else if ("move_quality_mistake".equals(labelKey)) {
                mistakes[side]++;
            } else if ("move_quality_inaccuracy".equals(labelKey)) {
                inaccuracies[side]++;
            } else {
                continue;
            }
            String san = ply < sanMoves.size() ? sanMoves.get(ply) : uciMoves.get(ply);
            if (flaggedMoves.length() > 0) {
                flaggedMoves.append('\n');
            }
            String cpLossText = String.format(Locale.ROOT, "%.1f", -cpLoss / 100.0);
            flaggedMoves.append(
                    Messages.get(
                            "analysis_flagged_move_format",
                            plyLabel(ply),
                            san,
                            Messages.get(labelKey),
                            cpLossText));
        }
        StringBuilder report = new StringBuilder();
        String[] sideColorKey = {"color_white", "color_black"};
        for (int side = 0; side < 2; side++) {
            if (side > 0) {
                report.append('\n');
            }
            String avgLossText =
                    String.format(
                            Locale.ROOT,
                            "%.1f",
                            plies[side] == 0 ? 0.0 : lossSum[side] / plies[side] / 100.0);
            report.append(
                    Messages.get(
                            "analysis_side_summary_format",
                            Messages.get(sideColorKey[side]),
                            avgLossText,
                            inaccuracies[side],
                            mistakes[side],
                            blunders[side]));
        }
        report.append("\n\n");
        report.append(
                flaggedMoves.length() > 0
                        ? Messages.get("analysis_flagged_moves_header") + "\n" + flaggedMoves
                        : Messages.get("analysis_no_flagged_moves"));
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.initOwner(stage);
        alert.setTitle(Messages.get("analysis_dialog_title"));
        alert.setHeaderText(null);
        TextArea reportArea = new TextArea(report.toString());
        reportArea.setEditable(false);
        reportArea.setWrapText(true);
        reportArea.setPrefSize(420, 320);
        alert.getDialogPane().setContent(reportArea);
        Styles.apply(alert.getDialogPane());
        alert.showAndWait();
    }

    /**
     * Cancels any outstanding engine search and marks its eventual reply (and any other
     * already-queued one) as belonging to a position we've since left - used wherever the game is
     * reset out from under a possibly in-flight search (new game, undo, PGN import).
     */
    private void abandonPendingSearches() {
        if (engine != null) {
            engine.stop();
        }
        searchGeneration++;
        waitingForEngineMove = false;
        waitingForHint = false;
        if (multiPvSearchActive) {
            // A hint search was interrupted mid-flight (new game/undo/PGN import/post-game analysis
            // starting) - restore MultiPV so the next (unrelated) search's info lines aren't
            // misrouted to captureMultiPvCandidate() forever.
            multiPvSearchActive = false;
            if (engine != null) {
                engine.setMultiPv(1);
            }
        }
        postGameUciMoves = null;
        postGamePositionEvals = null;
        lastPositionEvalMoveCount = -1;
        moveQualityBaselineMoveCount = -1;
        moveQualityLabel.setVisible(false);
        updateAnalyzeGameButtonState();
    }

    /**
     * Starts a dedicated evaluation search when nothing else is already searching the current
     * position. If the engine is about to search for its own reply anyway ({@link
     * #maybeStartEngineMove}), that search's "info" stream already covers the evaluation display,
     * so a second, redundant search is skipped.
     */
    private void maybeTriggerAnalysis() {
        boolean visible = mode != Mode.TRAINING;
        evaluationCheckbox.setVisible(visible);
        evaluationLabel.setVisible(visible);
        if (!evaluationEnabled || mode == Mode.TRAINING || game.isGameOver()) {
            evaluationLabel.setText("");
            return;
        }
        if (!engineReady) {
            return;
        }
        boolean engineAboutToSearchAnyway =
                mode == Mode.HUMAN_VS_STOCKFISH && game.sideToMove() != humanSide;
        if (engineAboutToSearchAnyway) {
            return;
        }
        startEngineSearch(false, ANALYSIS_MOVETIME_MS);
    }

    private void newGame() {
        abandonPendingSearches();
        game.reset();
        boardCanvas.setLastMove(null, null);
        refresh();
    }

    private void undo() {
        if (mode == Mode.TRAINING) {
            undoTrainingMove();
            return;
        }
        abandonPendingSearches();
        if (!game.undoLastMove()) {
            return;
        }
        if (mode == Mode.HUMAN_VS_STOCKFISH
                && game.moveCount() > 0
                && game.sideToMove() != humanSide) {
            game.undoLastMove();
        }
        boardCanvas.setLastMove(null, null);
        refresh();
    }

    private void refresh() {
        Piece[] pieces = new Piece[64];
        for (int i = 0; i < 64; i++) {
            pieces[i] = game.pieceAt(Square.squareAt(i));
        }
        boardCanvas.setBoard(pieces);
        boardCanvas.setCheckedKingSquare(findCheckedKingSquare());
        boardCanvas.setInteractive(isBoardInteractiveNow());
        moveListArea.setText(game.toSan());
        statusLabel.setText(statusText());
        statusLabel.getStyleClass().removeAll("check", "gameover");
        if (game.isGameOver()) {
            statusLabel.getStyleClass().add("gameover");
        } else if (game.isCheck()) {
            statusLabel.getStyleClass().add("check");
        }
        updateTrainingProgressLabel();
        updateTrainingHint();
        maybeTriggerAnalysis();
        updateHintButtonState();
        updateAnalyzeGameButtonState();

        if (game.isGameOver()) {
            showGameOverAlert();
        }
    }

    private boolean isBoardInteractiveNow() {
        if (game.isGameOver() || waitingForEngineMove) {
            return false;
        }
        return switch (mode) {
            case HUMAN_VS_HUMAN -> true;
            case HUMAN_VS_STOCKFISH -> game.sideToMove() == humanSide;
            case TRAINING ->
                    trainingSession != null
                            && !trainingSession.isComplete()
                            && trainingSession.isHumanTurnNow();
        };
    }

    private Square findCheckedKingSquare() {
        if (!game.isCheck()) {
            return null;
        }
        Piece king = game.sideToMove() == Side.WHITE ? Piece.WHITE_KING : Piece.BLACK_KING;
        for (int i = 0; i < 64; i++) {
            Square square = Square.squareAt(i);
            if (game.pieceAt(square) == king) {
                return square;
            }
        }
        return null;
    }

    private String statusText() {
        if (game.isCheckmate()) {
            // Checkmate winner is whoever just moved, i.e. NOT sideToMove().
            return Messages.get(
                    game.sideToMove() == Side.WHITE
                            ? "status_checkmate_black"
                            : "status_checkmate_white");
        }
        if (game.isStalemate()) {
            return Messages.get("status_stalemate");
        }
        if (game.isDraw()) {
            return Messages.get("status_draw");
        }
        String turn =
                Messages.get(
                        game.sideToMove() == Side.WHITE
                                ? "status_white_to_move"
                                : "status_black_to_move");
        return game.isCheck() ? turn + " (" + Messages.get("status_check") + ")" : turn;
    }

    private void showGameOverAlert() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.initOwner(stage);
        alert.setTitle(Messages.get("dialog_game_over_title"));
        alert.setHeaderText(null);
        alert.setContentText(statusText());
        Styles.apply(alert.getDialogPane());
        alert.showAndWait();
    }

    // ---- opening trainer ------------------------------------------------------

    private void startTraining(OpeningLine opening, Side side, boolean hintsEnabled) {
        stopPendingBookMove();
        abandonPendingSearches();
        trainingSession = new TrainingSession(opening, side, hintsEnabled);
        mode = Mode.TRAINING;
        humanSide = side;
        game.reset();
        boardFlipped = side == Side.BLACK;
        boardCanvas.setFlipped(boardFlipped);
        boardCanvas.setLastMove(null, null);
        boardCanvas.setTrainingHint(null, null);
        boardCanvas.clearSelection();
        strengthSlider.setDisable(true);
        refresh();
        maybeAdvanceTraining();
    }

    /**
     * On-screen equivalent of the physical board's LED guidance: only accepts a click matching the
     * line's expected move for the current ply, silently ignoring anything else - no popup for a
     * wrong attempt. None of the curated lines reach a promotion within their depth, so a plain
     * four-character UCI comparison is enough (see the Android app's {@code
     * MainActivity.onTrainingMoveChosen} for the same reasoning).
     */
    private void onTrainingMoveChosen(Square from, Square to) {
        if (trainingSession == null
                || trainingSession.isComplete()
                || !trainingSession.isHumanTurnNow()) {
            return;
        }
        String tapped =
                from.toString().toLowerCase(Locale.ROOT) + to.toString().toLowerCase(Locale.ROOT);
        if (tapped.equals(trainingSession.currentExpectedUci())) {
            applyTrainingMove(tapped);
        }
    }

    private void applyTrainingMove(String uci) {
        if (!applyUciToGame(uci)) {
            refresh();
            return;
        }
        trainingSession.advance();
        refresh();
        maybeAdvanceTraining();
    }

    /**
     * Drives the training line forward: leaves the board waiting for the trainee's own next move,
     * or plays out the book side's move itself after a short delay so it doesn't feel
     * instantaneous.
     */
    private void maybeAdvanceTraining() {
        if (mode != Mode.TRAINING || trainingSession == null || game.isGameOver()) {
            return;
        }
        if (trainingSession.isComplete()) {
            showTrainingCompleteDialog();
            return;
        }
        if (trainingSession.isHumanTurnNow()) {
            refresh();
            return;
        }
        refresh();
        pendingBookMove = new PauseTransition(Duration.millis(TRAINING_AUTO_MOVE_DELAY_MS));
        pendingBookMove.setOnFinished(e -> applyTrainingMove(trainingSession.currentExpectedUci()));
        pendingBookMove.play();
    }

    private void stopPendingBookMove() {
        if (pendingBookMove != null) {
            pendingBookMove.stop();
            pendingBookMove = null;
        }
    }

    /** Steps the training line back to the trainee's own previous move so they can retry it. */
    private void undoTrainingMove() {
        if (trainingSession == null || trainingSession.plyIndex() == 0) {
            return;
        }
        stopPendingBookMove();
        game.undoLastMove();
        trainingSession.retreat();
        if (trainingSession.plyIndex() > 0 && !trainingSession.isHumanTurnNow()) {
            game.undoLastMove();
            trainingSession.retreat();
        }
        boardCanvas.setLastMove(null, null);
        refresh();
    }

    private void updateTrainingProgressLabel() {
        if (mode != Mode.TRAINING || trainingSession == null) {
            trainingProgressLabel.setVisible(false);
            return;
        }
        trainingProgressLabel.setVisible(true);
        int shownPly = Math.min(trainingSession.plyIndex() + 1, trainingSession.totalPlies());
        trainingProgressLabel.setText(
                Messages.get(
                        "status_training_progress_format",
                        OpeningNames.displayName(trainingSession.line()),
                        shownPly,
                        trainingSession.totalPlies()));
    }

    /** On-screen highlight of the trainee's own next expected move, when hints are enabled. */
    private void updateTrainingHint() {
        if (mode != Mode.TRAINING
                || trainingSession == null
                || trainingSession.isComplete()
                || !trainingSession.isHumanTurnNow()
                || !trainingSession.hintsEnabled()) {
            boardCanvas.setTrainingHint(null, null);
            hintAlternativesLabel.setVisible(false);
            return;
        }
        String uci = trainingSession.currentExpectedUci();
        Square from = Square.fromValue(uci.substring(0, 2).toUpperCase(Locale.ROOT));
        Square to = Square.fromValue(uci.substring(2, 4).toUpperCase(Locale.ROOT));
        boardCanvas.setTrainingHint(from, to);
    }

    private void showTrainingCompleteDialog() {
        OpeningLine line = trainingSession.line();
        Side side = trainingSession.humanSide();
        boolean hintsEnabled = trainingSession.hintsEnabled();

        ButtonType repeatType = new ButtonType(Messages.get("action_repeat"));
        ButtonType pickType = new ButtonType(Messages.get("action_pick_opening"));
        ButtonType continueType = new ButtonType(Messages.get("action_continue_free_play"));

        Alert alert = new Alert(Alert.AlertType.NONE);
        alert.initOwner(stage);
        alert.setTitle(Messages.get("dialog_training_complete_title"));
        alert.setHeaderText(null);
        alert.setContentText(
                Messages.get(
                        "dialog_training_complete_message_format", OpeningNames.displayName(line)));
        alert.getButtonTypes().setAll(repeatType, pickType, continueType);
        Styles.apply(alert.getDialogPane());

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isEmpty()) {
            return;
        }
        if (result.get() == repeatType) {
            startTraining(line, side, hintsEnabled);
        } else if (result.get() == pickType) {
            TrainingSetupDialog.show(stage)
                    .ifPresent(c -> startTraining(c.opening(), c.side(), c.hintsEnabled()));
        } else if (result.get() == continueType) {
            continueFreePlay(side);
        }
    }

    /**
     * Continues playing from the position the completed line ended at, against Stockfish or a
     * second human, instead of resetting - mirrors the Android app's "Continue free play"
     * training-complete option.
     */
    private record OpponentOption(boolean vsStockfish, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    private void continueFreePlay(Side trainedSide) {
        OpponentOption stockfishOption = new OpponentOption(true, Messages.get("choice_stockfish"));
        OpponentOption humanOption = new OpponentOption(false, Messages.get("choice_human"));
        ChoiceDialog<OpponentOption> dialog =
                new ChoiceDialog<>(stockfishOption, stockfishOption, humanOption);
        dialog.initOwner(stage);
        dialog.setTitle(Messages.get("action_continue_free_play"));
        dialog.setHeaderText(null);
        dialog.setContentText(Messages.get("continue_free_play_prompt"));
        Styles.apply(dialog.getDialogPane());
        Optional<OpponentOption> choice = dialog.showAndWait();
        if (choice.isEmpty()) {
            return;
        }
        stopPendingBookMove();
        trainingSession = null;
        boolean vsStockfish = choice.get().vsStockfish();
        mode = vsStockfish ? Mode.HUMAN_VS_STOCKFISH : Mode.HUMAN_VS_HUMAN;
        humanSide = trainedSide;
        suppressModeChange = true;
        Toggle target = vsStockfish ? vsStockfishToggle : vsHumanToggle;
        target.setSelected(true);
        suppressModeChange = false;
        lastConfirmedToggle = target;
        strengthSlider.setDisable(!vsStockfish);
        boardCanvas.setTrainingHint(null, null);
        refresh();
        if (vsStockfish && engineReady) {
            engine.setStrength((int) strengthSlider.getValue());
        }
        maybeStartEngineMove();
    }

    private boolean applyUciToGame(String uci) {
        Square from = Square.fromValue(uci.substring(0, 2).toUpperCase(Locale.ROOT));
        Square to = Square.fromValue(uci.substring(2, 4).toUpperCase(Locale.ROOT));
        boolean wasCapture = game.pieceAt(to) != Piece.NONE;
        recordMoveQualityBaseline();
        if (!game.applyUciMove(uci)) {
            return false;
        }
        boardCanvas.setLastMove(from, to);
        playMoveSound(wasCapture);
        return true;
    }

    // ---- PGN ----------------------------------------------------------------

    /**
     * A file with more than one game (tournament/opening database) is offered as a pick list
     * instead of silently importing whatever chesslib happens to parse first - see {@link
     * PgnGames#splitGames}, a cheap header-only scan that doesn't pay chesslib's full per-game
     * parse cost just to build the list. Ported from the Android app's {@code
     * MainActivity.onPgnFileSelected}/{@code showPgnGameSelectionDialog}.
     */
    private void importPgn() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(Messages.get("action_import_pgn"));
        chooser.getExtensionFilters()
                .add(new FileChooser.ExtensionFilter(Messages.get("pgn_file_filter"), "*.pgn"));
        java.io.File file = chooser.showOpenDialog(stage);
        if (file == null) {
            return;
        }
        String text;
        try {
            text = readPgnText(file.toPath());
        } catch (IOException e) {
            showError(Messages.get("error_read_file_format", file, e.getMessage()));
            return;
        }
        abandonPendingSearches();
        List<String> games = PgnGames.splitGames(text);
        if (games.size() <= 1) {
            finishPgnImport(text);
        } else {
            showPgnGameSelectionDialog(games);
        }
    }

    /**
     * Real-world PGN database exports are frequently Windows-1252/Latin-1, not UTF-8 (e.g. accented
     * player names), so a strict UTF-8 decode is tried first and only falls back to Windows-1252 if
     * that fails - rather than either always assuming UTF-8 (breaks on those files) or always
     * assuming Windows-1252 (silently mangles genuinely UTF-8 files).
     */
    private static String readPgnText(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        try {
            return StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            return new String(bytes, Charset.forName("windows-1252"));
        }
    }

    private record PgnGameOption(int index, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    private void showPgnGameSelectionDialog(List<String> games) {
        List<PgnGameOption> options = new ArrayList<>();
        for (int i = 0; i < games.size(); i++) {
            options.add(new PgnGameOption(i, PgnGames.summarize(games.get(i))));
        }
        ChoiceDialog<PgnGameOption> dialog = new ChoiceDialog<>(options.get(0), options);
        dialog.initOwner(stage);
        dialog.setTitle(Messages.get("pgn_select_game_title"));
        dialog.setHeaderText(null);
        dialog.setContentText(Messages.get("pgn_select_game_message"));
        Styles.apply(dialog.getDialogPane());
        dialog.showAndWait().ifPresent(option -> finishPgnImport(games.get(option.index())));
    }

    private void finishPgnImport(String pgnText) {
        if (!game.loadPgn(pgnText)) {
            showError(Messages.get("pgn_import_failed"));
            return;
        }
        stopPendingBookMove();
        trainingSession = null;
        mode = Mode.HUMAN_VS_HUMAN;
        humanSide = Side.WHITE;
        suppressModeChange = true;
        vsHumanToggle.setSelected(true);
        suppressModeChange = false;
        lastConfirmedToggle = vsHumanToggle;
        boardFlipped = false;
        boardCanvas.setFlipped(false);
        boardCanvas.setLastMove(null, null);
        boardCanvas.setTrainingHint(null, null);
        boardCanvas.clearSelection();
        strengthSlider.setDisable(true);
        if (engineReady) {
            engine.newGame();
        }
        refresh();
    }

    private void exportPgn() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(Messages.get("action_export_pgn"));
        chooser.getExtensionFilters()
                .add(new FileChooser.ExtensionFilter(Messages.get("pgn_file_filter"), "*.pgn"));
        chooser.setInitialFileName("game.pgn");
        java.io.File file = chooser.showSaveDialog(stage);
        if (file == null) {
            return;
        }
        String white;
        String black;
        if (mode == Mode.HUMAN_VS_STOCKFISH) {
            String you = Messages.get("pgn_you");
            String stockfish = Messages.get("choice_stockfish");
            white = humanSide == Side.WHITE ? you : stockfish;
            black = humanSide == Side.WHITE ? stockfish : you;
        } else {
            white = Messages.get("color_white");
            black = Messages.get("color_black");
        }
        try {
            Files.writeString(
                    Path.of(file.toURI()), game.toPgn(white, black), StandardCharsets.UTF_8);
        } catch (IOException e) {
            showError(Messages.get("error_write_file_format", file, e.getMessage()));
        }
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initOwner(stage);
        alert.setTitle(Messages.get("dialog_error_title"));
        alert.setHeaderText(null);
        alert.setContentText(message);
        Styles.apply(alert.getDialogPane());
        alert.showAndWait();
    }

    // ---- engine lifecycle -----------------------------------------------------

    private void startEngine() {
        try {
            engineLocation = StockfishLocator.locate();
        } catch (IOException e) {
            statusLabel.setText(Messages.get("status_engine_unavailable") + ": " + e.getMessage());
            return;
        }
        engine = new StockfishEngine(engineLocation.exe().getAbsolutePath(), Platform::runLater);
        engine.setListener(this);
        engine.start();
    }

    void shutdown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    // ---- EngineListener -------------------------------------------------------

    @Override
    public void onUciOk() {
        try {
            NnueAssets.Paths paths =
                    NnueAssets.extractIfNeeded(
                            path -> {
                                throw new FileNotFoundException(
                                        "NNUE net "
                                                + path
                                                + " missing from "
                                                + engineLocation.homeDir()
                                                + " - run :desktop:buildStockfishHost");
                            },
                            engineLocation.homeDir());
            engine.setEvalFiles(paths.bigNetPath, paths.smallNetPath);
        } catch (IOException e) {
            statusLabel.setText(Messages.get("error_nnue_load_failed") + ": " + e.getMessage());
            return;
        }
        engine.setStrength((int) strengthSlider.getValue());
        engine.newGame();
    }

    @Override
    public void onReadyOk() {
        engineReady = true;
        maybeStartEngineMove();
        maybeTriggerAnalysis();
        updateHintButtonState();
        updateAnalyzeGameButtonState();
    }

    @Override
    public void onBestMove(String bestMoveUci, String ponderUci) {
        PendingSearch search = pendingSearches.poll();
        if (search == null || search.generation() != searchGeneration) {
            // Either unexpected (defensive only - every go() we send queues an
            // entry), or a reply for a search abandoned by
            // abandonPendingSearches() (new game, undo, PGN import); discard it.
            return;
        }
        maybeFinalizeMoveQuality();
        if (search.purpose() == SearchPurpose.ANALYSIS) {
            // Analysis-only search; onInfo() already streamed eval updates for it.
            return;
        }
        if (search.purpose() == SearchPurpose.HINT) {
            waitingForHint = false;
            multiPvSearchActive = false;
            engine.setMultiPv(1);
            engine.setStrength((int) strengthSlider.getValue());
            updateHintButtonState();
            if (bestMoveUci != null && !"(none)".equals(bestMoveUci)) {
                showHint(bestMoveUci);
            }
            showHintAlternatives();
            return;
        }
        if (search.purpose() == SearchPurpose.POST_GAME) {
            advancePostGameAnalysis();
            return;
        }
        waitingForEngineMove = false;
        if (bestMoveUci == null || "(none)".equals(bestMoveUci)) {
            refresh();
            return;
        }
        applyUciToGame(bestMoveUci);
        refresh();
    }

    @Override
    public void onInfo(String infoLine) {
        if (multiPvSearchActive) {
            captureMultiPvCandidate(infoLine);
            return;
        }
        OptionalInt mate = UciInfoParser.parseScoreMate(infoLine);
        OptionalInt cp =
                mate.isPresent() ? OptionalInt.empty() : UciInfoParser.parseScoreCp(infoLine);
        if (mate.isEmpty() && cp.isEmpty()) {
            return;
        }
        int rawCp = mate.isPresent() ? mateToCp(mate.getAsInt()) : cp.getAsInt();
        if (postGameUciMoves != null) {
            // Post-game analysis replays past positions - never the live eval/move-quality state.
            postGameLiveScoreCp = rawCp;
            return;
        }
        if (!evaluationEnabled || mode == Mode.TRAINING || analysisSideToMove == null) {
            return;
        }
        recordPositionEval(rawCp);
        if (mate.isPresent()) {
            int whiteRelativeMate =
                    analysisSideToMove == Side.BLACK ? -mate.getAsInt() : mate.getAsInt();
            String side = Messages.get(whiteRelativeMate >= 0 ? "color_white" : "color_black");
            evaluationLabel.setText(
                    Messages.get("evaluation_mate_format", Math.abs(whiteRelativeMate), side));
            return;
        }
        if (cp.isPresent()) {
            int whiteRelativeCp = analysisSideToMove == Side.BLACK ? -cp.getAsInt() : cp.getAsInt();
            evaluationLabel.setText(String.format(Locale.ROOT, "%+.1f", whiteRelativeCp / 100.0));
        }
    }

    @Override
    public void onEngineError(Exception error) {
        pendingSearches.clear();
        waitingForEngineMove = false;
        waitingForHint = false;
        multiPvSearchActive = false;
        postGameUciMoves = null;
        postGamePositionEvals = null;
        updateHintButtonState();
        updateAnalyzeGameButtonState();
        statusLabel.setText(Messages.get("error_engine_generic") + ": " + error.getMessage());
    }
}
