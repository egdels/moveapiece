/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.desktop;

import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.Square;
import de.schliweb.chessnut.core.game.InvalidPositionException;
import de.schliweb.chessnut.core.protocol.ChessnutUuids;
import de.schliweb.moveapiece.desktop.board.ChessnutBoardAdapter;
import de.schliweb.moveapiece.desktop.board.PegasusBoardAdapter;
import de.schliweb.moveapiece.desktop.board.PhysicalBoardBridge;
import de.schliweb.moveapiece.desktop.chessnut.DesktopChessnutGameBridge;
import de.schliweb.moveapiece.desktop.pegasus.DesktopPegasusGameBridge;
import de.schliweb.moveapiece.desktop.pegasus.LinuxPegasusBleTransport;
import de.schliweb.moveapiece.desktop.pegasus.MacosPegasusBleTransport;
import de.schliweb.moveapiece.desktop.pegasus.WindowsPegasusBleTransport;
import de.schliweb.moveapiece.engine.EngineListener;
import de.schliweb.moveapiece.engine.MaiaEngine;
import de.schliweb.moveapiece.engine.MaiaEngineListener;
import de.schliweb.moveapiece.engine.MaiaRatings;
import de.schliweb.moveapiece.engine.NnueAssets;
import de.schliweb.moveapiece.engine.StockfishEngine;
import de.schliweb.moveapiece.engine.UciInfoParser;
import de.schliweb.moveapiece.logic.BoardType;
import de.schliweb.moveapiece.logic.ChessGame;
import de.schliweb.moveapiece.logic.PgnGames;
import de.schliweb.moveapiece.training.OpeningLine;
import de.schliweb.moveapiece.training.TrainingFlow;
import de.schliweb.moveapiece.training.TrainingSession;
import de.schliweb.pegasus.core.protocol.BoardState;
import de.schliweb.pegasus.core.transport.BleProfile;
import de.schliweb.pegasus.core.transport.ConnectionState;
import de.schliweb.pegasus.core.transport.PegasusTransport;
import de.schliweb.pegasus.core.transport.TransportError;
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
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * Wires a {@link ChessGame}, a {@link StockfishEngine}, a {@link MaiaEngine} and a {@link
 * BoardCanvas} together into a playable window: human-vs-human, human-vs-Stockfish (adjustable
 * playing strength), human-vs-Maia (a human-like opponent at a fixed rating - see {@link
 * #startMaiaGame}), an in-game opening trainer that drills a fixed line from {@code core}'s opening
 * library, undo, and PGN import/export. Mirrors the Android app's {@code MainActivity}
 * orchestration at a much smaller scope (Maia is desktop-only for now).
 *
 * <p>Stockfish and Maia coexist rather than one replacing the other during a Maia game: only
 * generating the opponent's own reply move goes through {@link #maiaEngine} - the live evaluation
 * display, hints, post-game analysis, and live move-quality/blunder-check all still go through the
 * same always-running {@link #engine} (Stockfish) regardless of {@link #mode}, since none of them
 * care who played the last move (see {@link #isPairedEngineMode} and {@link
 * #maybeTriggerAnalysis}).
 */
final class GameController
        implements BoardCanvas.MoveSource,
                EngineListener,
                DesktopPegasusGameBridge.Listener,
                DesktopChessnutGameBridge.Listener {

    private static final Logger LOG = Logger.getLogger(GameController.class.getName());

    private enum Mode {
        HUMAN_VS_HUMAN,
        HUMAN_VS_STOCKFISH,
        HUMAN_VS_MAIA,
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
    // Maia's own forward pass is near-instant (no search - see MaiaEngine's Javadoc), which reads
    // as inhumanly fast next to Stockfish's fixed MOVETIME_MS think time. #scheduleMaiaMove fills
    // the gap up to a randomized target somewhere in this range (never adds it on top of whatever
    // the forward pass itself took), so a slow device's own inference time doesn't stack with the
    // pause.
    private static final int MAIA_MOVE_MIN_DELAY_MS = 600;
    private static final int MAIA_MOVE_MAX_DELAY_MS = 1400;

    private final Stage stage;
    private final ChessGame game = new ChessGame();
    private final BoardCanvas boardCanvas = new BoardCanvas();
    private final Label statusLabel = new Label();
    private final Label pegasusMismatchLabel = new Label();
    private final TextFlow moveListFlow = new TextFlow();
    private final ScrollPane moveListScroll = new ScrollPane(moveListFlow);
    private final Slider strengthSlider = new Slider(1320, 3190, Settings.getEngineElo());
    private final Label strengthLabel = new Label();
    // Live-adjustable counterpart to strengthSlider/strengthLabel for HUMAN_VS_MAIA: unlike
    // Stockfish's Elo, Maia's rating isn't a UCI option on a running engine, it's a choice of which
    // bundled model to load, so picking a new value here swaps in a whole new MaiaEngine mid-game
    // (see #switchMaiaRating) instead of tweaking a parameter - the two pairs are shown one at a
    // time, never together (see #updateStrengthControlsVisibility). Bounds match MaiaRatings.ALL
    // (1100-1900 in steps of 100); snapToTicks/majorTickUnit/blockIncrement below keep the slider
    // on
    // those 9 values, since there's no bundled model for anything in between.
    private final Label maiaRatingLabel = new Label();
    private final Slider maiaRatingSlider = new Slider(1100, 1900, Settings.getMaiaRating());
    // Same Material icon glyphs as the Android app's ic_undo.xml/ic_flip_board.xml
    // (SVG path data reused verbatim - both use the same path-string syntax).
    private static final String NEW_GAME_ICON_PATH = "M19,13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z";
    private static final String UNDO_ICON_PATH =
            "M12.5,8c-2.65,0 -5.05,0.99 -6.9,2.6L2,7v9h9l-3.62,-3.62c1.39,-1.16 3.16,-1.88 5.12,-1.88 "
                    + "3.54,0 6.55,2.31 7.6,5.5l2.37,-0.78C21.08,11.03 17.15,8 12.5,8z";
    // Same Material "redo" glyph as the Android app's ic_redo.xml (mirror of UNDO_ICON_PATH).
    private static final String REDO_ICON_PATH =
            "M18.4,10.6C16.55,8.99 14.15,8 11.5,8c-4.65,0 -8.58,3.03 -9.96,7.22L3.9,16c1.05,-3.19 "
                    + "4.05,-5.5 7.6,-5.5 1.95,0 3.73,0.72 5.12,1.88L13,16h9v-9L18.4,10.6z";
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
    // Same Material "bluetooth" glyph as the Android app's ic_pegasus.xml/ic_pegasus_connected.xml.
    private static final String PEGASUS_ICON_PATH =
            "M17.71,7.71L12,2h-1v7.59L6.41,5L5,6.41L10.59,12L5,17.59L6.41,19L11,14.41V22h1l5.71,-5.71"
                    + "l-4.3,-4.29L17.71,7.71zM13,5.83l1.88,1.88L13,9.59V5.83zM14.88,16.29L13,18.17v-3.76"
                    + "L14.88,16.29z";

    private final Button undoButton = iconButton(UNDO_ICON_PATH, Messages.get("menu_undo"));
    private final Button redoButton = iconButton(REDO_ICON_PATH, Messages.get("menu_redo"));
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
    private final Button pegasusButton =
            iconButton(PEGASUS_ICON_PATH, Messages.get("menu_board_connect"));
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
    // Loaded fresh per Maia game (see #startMaiaGame) and again on every mid-game rating change
    // (see #switchMaiaRating), unlike engine/engineLocation above which are started once in the
    // constructor and live for the app's whole lifetime - a different rating is a different
    // bundled model file, not a UCI option to change on a running instance.
    private MaiaEngine maiaEngine;
    private boolean maiaReady = false;
    private int currentMaiaRating;
    // Bridges a #maybeStartEngineMove call's searchGeneration snapshot and go()-start time across
    // to the Maia listener's onBestMove and #finishMaiaMove, which run later and can't otherwise
    // tell a fresh reply from one that's since been left behind by an undo/new game/PGN import -
    // see #scheduleMaiaMove.
    private int maiaSearchGeneration = -1;
    private long maiaRequestStartNanos;
    private PauseTransition pendingMaiaMove;
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

    /**
     * The opening trainer's move flow (shared with Android, tested in core's TrainingFlowTest);
     * {@link #trainingSession} mirrors {@link TrainingFlow#session()} for the many read-only uses
     * below.
     */
    private final TrainingFlow trainingFlow =
            new TrainingFlow(game, new TrainingBoardAdapter(), new TrainingHostAdapter());

    // ---- Physical board (DGT Pegasus or Chessnut Air) ----------------------------
    /** The selected board's bridge; null on hosts without a transport (see createBoardBridge). */
    private PhysicalBoardBridge pegasusBridge;

    private BoardType boardType;

    /**
     * Whether an engine reply applied to the game still owes its move sound: with a Pegasus board
     * connected the reply is applied silently and sounds once the guide reports it executed on the
     * board (same reasoning as the trainer's deferred book-move sound); {@code
     * engineMoveWasCapture} remembers which sound. Cleared when the game is reset/undone.
     */
    private boolean engineMoveSoundPending;

    private boolean engineMoveWasCapture;

    /**
     * An engine reply that arrived while the physical board was out of sync (mismatched, or no
     * board dump received yet). Automatic moves are never applied onto a board that cannot follow
     * them; the reply is applied and guided as soon as {@link #onBoardMismatch} reports the board
     * back in sync, and dropped whenever the game is reset/undone ({@link
     * #abandonPendingSearches}).
     */
    private String heldEngineMoveUci;

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
        boardType = Settings.getBoardType();
        pegasusBridge = createBoardBridge(boardType);
        startEngine();
    }

    /**
     * All three desktop OSes now have a {@link de.schliweb.pegasus.core.transport.PegasusTransport}
     * implementation: macOS ({@link MacosPegasusBleTransport}), Windows ({@link
     * WindowsPegasusBleTransport}) and Linux ({@link LinuxPegasusBleTransport}). Every {@code
     * pegasusBridge}-dependent call site below is still null-guarded, exactly like the Android app
     * guards every Pegasus call site on whether the board is currently connected - useful should a
     * transport's own constructor ever need to signal "not actually usable on this host" by some
     * other means later (e.g. no D-Bus session reachable), not just by OS name.
     */
    private PhysicalBoardBridge createBoardBridge(BoardType type) {
        BleProfile profile =
                type == BoardType.CHESSNUT ? ChessnutUuids.PROFILE : BleProfile.PEGASUS;
        PegasusTransport transport;
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (osName.contains("mac")) {
            transport = new MacosPegasusBleTransport(profile);
        } else if (osName.contains("win")) {
            transport = new WindowsPegasusBleTransport(profile);
        } else if (osName.contains("nux")) {
            transport = new LinuxPegasusBleTransport(profile);
        } else {
            return null;
        }
        PhysicalBoardBridge bridge =
                type == BoardType.CHESSNUT
                        ? new ChessnutBoardAdapter(new DesktopChessnutGameBridge(transport, this))
                        : new PegasusBoardAdapter(new DesktopPegasusGameBridge(transport, this));
        maybeStartBoardRecording(bridge, type);
        return bridge;
    }

    /**
     * Raw BLE traffic recording for hardware-verification sessions, the desktop counterpart of the
     * Android debug build's automatic recording: set the environment variable {@code
     * MOVEAPIECE_BOARD_RECORDING} to a directory and every bridge writes its session there as
     * NDJSON. Non-critical if it fails.
     */
    private static void maybeStartBoardRecording(PhysicalBoardBridge bridge, BoardType type) {
        String dir = System.getenv("MOVEAPIECE_BOARD_RECORDING");
        if (dir == null || dir.isBlank()) {
            return;
        }
        java.io.File folder = new java.io.File(dir);
        if (!folder.isDirectory() && !folder.mkdirs()) {
            return;
        }
        try {
            bridge.startRecording(
                    new java.io.File(
                            folder,
                            type.key() + "-session-" + System.currentTimeMillis() + ".ndjson"));
        } catch (java.io.IOException e) {
            LOG.log(Level.WARNING, "board recording could not be started", e);
        }
    }

    /**
     * Swaps the bridge for {@code type} (called by {@link BoardConnectDialog} when the choice box
     * changes): the old one is detached and shut down, the choice persisted. Returns the bridge now
     * in use, which may be the unchanged current one.
     */
    private PhysicalBoardBridge switchBoardType(BoardType type) {
        if (type == boardType) {
            return pegasusBridge;
        }
        if (pegasusBridge != null) {
            // Detach first: a late callback from the old bridge must not be attributed to the
            // new board (tooltips and messages use boardType).
            pegasusBridge.detachListener();
            pegasusBridge.shutdown();
        }
        boardType = type;
        Settings.setBoardType(type);
        pegasusBridge = createBoardBridge(type);
        updatePegasusButtonState(
                pegasusBridge == null
                        ? ConnectionState.DISCONNECTED
                        : pegasusBridge.getConnectionState());
        updatePegasusMismatchLabel();
        return pegasusBridge;
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
        strengthLabel.managedProperty().bind(strengthLabel.visibleProperty());
        strengthSlider.managedProperty().bind(strengthSlider.visibleProperty());
        maiaRatingLabel.managedProperty().bind(maiaRatingLabel.visibleProperty());
        maiaRatingLabel.setVisible(false);

        maiaRatingSlider.setShowTickLabels(false);
        maiaRatingSlider.setSnapToTicks(true);
        maiaRatingSlider.setMajorTickUnit(100);
        maiaRatingSlider.setMinorTickCount(0);
        maiaRatingSlider.setBlockIncrement(100);
        // Label text tracks every tick while dragging for live feedback, but the actual (expensive
        // -
        // a full model reload) engine swap only fires once the drag ends, via the valueChanging
        // listener below - not on every intermediate tick a fast drag passes through.
        //
        // Both listeners snap the reported value themselves via MaiaRatings.nearest rather than
        // trusting the Slider's own snapToTicks to have already landed on an exact multiple of 100
        // -
        // see that method's Javadoc for why relying on it directly once failed to load a rating the
        // user had, visually, already dragged onto.
        maiaRatingSlider
                .valueProperty()
                .addListener(
                        (obs, old, val) -> {
                            int rating = MaiaRatings.nearest(val.doubleValue());
                            maiaRatingLabel.setText(
                                    Messages.get("dialog_maia_rating_format", rating));
                            if (!maiaRatingSlider.isValueChanging()) {
                                switchMaiaRating(rating);
                            }
                        });
        maiaRatingSlider
                .valueChangingProperty()
                .addListener(
                        (obs, wasChanging, isChanging) -> {
                            if (!isChanging) {
                                switchMaiaRating(MaiaRatings.nearest(maiaRatingSlider.getValue()));
                            }
                        });
        maiaRatingSlider.managedProperty().bind(maiaRatingSlider.visibleProperty());
        maiaRatingSlider.setVisible(false);

        moveListFlow.getStyleClass().add("move-list");
        moveListScroll.setFitToWidth(true);
        moveListScroll.setPrefHeight(180);
        VBox.setVgrow(moveListScroll, Priority.ALWAYS);

        statusLabel.getStyleClass().add("status-label");
        pegasusMismatchLabel.getStyleClass().add("mismatch-label");
        pegasusMismatchLabel.setWrapText(true);
        pegasusMismatchLabel.setVisible(false);
        pegasusMismatchLabel.managedProperty().bind(pegasusMismatchLabel.visibleProperty());
        pegasusMismatchLabel.setOnMouseClicked(e -> onMismatchLabelClicked());
        trainingProgressLabel.getStyleClass().add("training-progress-label");

        newGameButton.setOnAction(e -> openGameSetupDialog());
        undoButton.setOnAction(e -> undo());
        redoButton.setOnAction(e -> redo());
        importButton.setOnAction(e -> importPgn());
        exportButton.setOnAction(e -> exportPgn());
        analyzeGameButton.setOnAction(e -> startPostGameAnalysis());
        openingLibraryButton.setOnAction(e -> OpeningLibraryWindow.show(stage));
        hintButton.setOnAction(e -> requestHint());
        pegasusButton.setOnAction(e -> onPegasusButtonClicked());
        pegasusButton.setVisible(pegasusBridge != null);
        pegasusButton.setManaged(pegasusBridge != null);
        if (pegasusBridge != null) {
            updatePegasusButtonState(pegasusBridge.getConnectionState());
        }
        hintAlternativesLabel.getStyleClass().add("training-progress-label");
        hintAlternativesLabel.setWrapText(true);
        hintAlternativesLabel.setVisible(false);
        hintAlternativesLabel.managedProperty().bind(hintAlternativesLabel.visibleProperty());
        analysisProgressLabel.getStyleClass().add("training-progress-label");
        analysisProgressLabel.setVisible(false);
        analysisProgressLabel.managedProperty().bind(analysisProgressLabel.visibleProperty());

        HBox pgnBox = new HBox(8, importButton, exportButton, analyzeGameButton);
        pgnBox.setAlignment(Pos.CENTER_LEFT);
        HBox actionBox =
                new HBox(
                        8,
                        newGameButton,
                        undoButton,
                        redoButton,
                        flipBoardButton,
                        openingLibraryButton,
                        hintButton,
                        pegasusButton);
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
                        strengthLabel,
                        strengthSlider,
                        maiaRatingLabel,
                        maiaRatingSlider,
                        actionBox,
                        hintAlternativesLabel,
                        evalBox,
                        moveQualityLabel,
                        statusLabel,
                        pegasusMismatchLabel,
                        trainingProgressLabel,
                        movesHeading,
                        moveListScroll,
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
        scrollMoveHistoryToEnd = true;
        refresh();
        syncPegasusPosition();
        maybeStartEngineMove();
    }

    /** Check sound takes priority over move/capture, matching common chess-app UX. */
    private void playMoveSound(boolean wasCapture) {
        // A connected board with a speaker (Chessnut Air) plays the sound itself.
        if (pegasusBridge != null && pegasusBridge.playMoveSound(wasCapture, game.isCheck())) {
            return;
        }
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
        if (!isPairedEngineMode() || game.isGameOver()) {
            return;
        }
        if (game.sideToMove() == humanSide) {
            return;
        }
        if (mode == Mode.HUMAN_VS_MAIA) {
            if (!maiaReady) {
                return;
            }
            waitingForEngineMove = true;
            boardCanvas.setInteractive(false);
            maiaRatingSlider.setDisable(true);
            maiaSearchGeneration = searchGeneration;
            maiaRequestStartNanos = System.nanoTime();
            maiaEngine.setPosition(game.startFen(), game.toUciMoveList());
            maiaEngine.go();
            return;
        }
        if (!engineReady) {
            return;
        }
        waitingForEngineMove = true;
        boardCanvas.setInteractive(false);
        startEngineSearch(true, MOVETIME_MS);
    }

    // ---- Pegasus board -----------------------------------------------------------

    private void onPegasusButtonClicked() {
        if (pegasusBridge.getConnectionState() == ConnectionState.CONNECTED) {
            pegasusBridge.disconnect();
            return;
        }
        BoardConnectDialog.show(stage, boardType, this::switchBoardType)
                .ifPresent(
                        address -> {
                            if (pegasusBridge != null) {
                                pegasusBridge.connect(address);
                            }
                        });
    }

    /**
     * Mirrors the Android app's icon-only Pegasus button: swaps to an "active" style class instead
     * of visible text to carry connect/disconnect state.
     */
    private void updatePegasusButtonState(ConnectionState state) {
        boolean connected = state == ConnectionState.CONNECTED;
        pegasusButton.getStyleClass().removeAll("icon-button-active");
        if (connected) {
            pegasusButton.getStyleClass().add("icon-button-active");
        }
        pegasusButton.setTooltip(
                new Tooltip(
                        Messages.get(
                                connected
                                        ? "menu_board_disconnect_format"
                                        : "menu_board_connect_format",
                                boardType.displayName())));
    }

    /**
     * Keeps the Pegasus bridge's own position tracking current whenever {@link #game} changes
     * without it observing the move itself (tap-to-move, undo, PGN import) - see {@link
     * DesktopPegasusGameBridge#syncBoardToPosition} for why this is needed.
     */
    private void syncPegasusPosition() {
        if (pegasusBridge != null
                && pegasusBridge.getConnectionState() == ConnectionState.CONNECTED) {
            pegasusBridge.syncBoardToPosition(game.toFen());
        }
    }

    /**
     * Physical-board promotion: the board can only report that a pawn reached the back rank
     * (occupancy-only, never piece identity), so the choice always comes from here - resolved via
     * {@link DesktopPegasusGameBridge#selectPromotion}, reusing the same on-screen picker {@link
     * #askPromotionPiece} shows for a tapped promotion.
     */
    private void showPhysicalPromotionDialog() {
        Piece chosen = askPromotionPiece(game.sideToMove());
        if (chosen != null) {
            pegasusBridge.selectPromotion(toPegasusPieceType(chosen));
        }
    }

    private static de.schliweb.pegasus.core.chess.PieceType toPegasusPieceType(Piece piece) {
        return switch (piece) {
            case WHITE_ROOK, BLACK_ROOK -> de.schliweb.pegasus.core.chess.PieceType.ROOK;
            case WHITE_BISHOP, BLACK_BISHOP -> de.schliweb.pegasus.core.chess.PieceType.BISHOP;
            case WHITE_KNIGHT, BLACK_KNIGHT -> de.schliweb.pegasus.core.chess.PieceType.KNIGHT;
            default -> de.schliweb.pegasus.core.chess.PieceType.QUEEN;
        };
    }

    /**
     * Physical occupancy matches several legal moves at once (structurally near-unreachable in
     * practice - see {@link DesktopPegasusGameBridge.Listener#onAmbiguousMove}), resolved via
     * {@link DesktopPegasusGameBridge#selectCandidate}.
     */
    private void showAmbiguousMoveDialog(List<String> candidateUcis) {
        ChoiceDialog<String> dialog = new ChoiceDialog<>(candidateUcis.get(0), candidateUcis);
        dialog.initOwner(stage);
        dialog.setTitle(Messages.get("pegasus_ambiguous_title"));
        dialog.setHeaderText(null);
        dialog.setContentText(Messages.get("pegasus_ambiguous_title"));
        Styles.apply(dialog.getDialogPane());
        dialog.showAndWait().ifPresent(pegasusBridge::selectCandidate);
    }

    // ---- DesktopPegasusGameBridge.Listener / DesktopChessnutGameBridge.Listener --------

    @Override
    public void onConnectionStateChanged(ConnectionState state) {
        updatePegasusButtonState(state);
        updatePegasusMismatchLabel();
        if (state == ConnectionState.CONNECTED) {
            // The bridge only replays moves it actually observed (physical moves,
            // guided engine moves); on-screen play while the board was disconnected
            // leaves its own position stale. Push the authoritative position on
            // every (re)connect so the board can resume physical play correctly -
            // see DesktopPegasusGameBridge#syncBoardToPosition.
            pegasusBridge.syncBoardToPosition(game.toFen());
            if (mode == Mode.TRAINING) {
                maybeAdvanceTraining();
            }
        }
    }

    @Override
    public void onPhysicalMoveConfirmed(String uci) {
        if (mode == Mode.TRAINING) {
            onTrainingMoveConfirmedByDetection(uci);
            return;
        }
        if (!applyUciToGame(uci)) {
            refresh();
            return;
        }
        refresh();
        maybeStartEngineMove();
    }

    @Override
    public void onBoardMismatch(boolean mismatched) {
        updatePegasusMismatchLabel();
        if (mismatched) {
            // The board shows the squares via LEDs, the sidebar shows the banner; any automatic
            // move due meanwhile is held back (see pegasusBlocksAutoMoves) until resolved.
            return;
        }
        if (heldEngineMoveUci != null && isPairedEngineMode()) {
            String uci = heldEngineMoveUci;
            heldEngineMoveUci = null;
            applyEngineReply(uci);
            return;
        }
        if (mode == Mode.TRAINING) {
            trainingFlow.onBoardInSync();
        }
    }

    /**
     * Whether an automatic move (engine reply, book move) must currently be held back: the Pegasus
     * board is connected but not in sync with the game, so it could not be guided and the player
     * would be left with a screen that ran ahead of the board.
     */
    private boolean pegasusBlocksAutoMoves() {
        return pegasusBridge != null
                && pegasusBridge.getConnectionState() == ConnectionState.CONNECTED
                && !pegasusBridge.isBoardInSync();
    }

    /**
     * Applies an engine reply to the game and guides it on the physical board - or, while the board
     * is out of sync, holds it back in {@link #heldEngineMoveUci} until {@link #onBoardMismatch}
     * reports the board restored.
     */
    private void applyEngineReply(String uci) {
        if (pegasusBlocksAutoMoves()) {
            LOG.log(Level.INFO, "holding engine move {0}: physical board not in sync", uci);
            heldEngineMoveUci = uci;
            refresh();
            return;
        }
        boolean connected =
                pegasusBridge != null
                        && pegasusBridge.getConnectionState() == ConnectionState.CONNECTED;
        if (!connected) {
            applyUciToGame(uci);
            refresh();
            return;
        }
        Square to = Square.fromValue(uci.substring(2, 4).toUpperCase(Locale.ROOT));
        boolean wasCapture = game.pieceAt(to) != Piece.NONE;
        applyUciToGame(uci, false); // sound follows on physical confirmation
        pegasusBridge.guideEngineMove(uci);
        if (pegasusBridge.isGuideActive()) {
            engineMoveSoundPending = true;
            engineMoveWasCapture = wasCapture;
        } else {
            playMoveSound(wasCapture); // nothing to wait for
        }
        refresh();
    }

    /** Sidebar banner while the physical board disagrees with the position on screen. */
    private void updatePegasusMismatchLabel() {
        boolean mismatched =
                pegasusBridge != null
                        && pegasusBridge.getConnectionState() == ConnectionState.CONNECTED
                        && pegasusBridge.isBoardMismatched();
        if (!mismatched) {
            boardCanvas.setMismatchSquares(List.of());
            int pendingCapture =
                    pegasusBridge != null
                                    && pegasusBridge.getConnectionState()
                                            == ConnectionState.CONNECTED
                            ? pegasusBridge.pendingCaptureSquare()
                            : -1;
            int liftedPiece =
                    pegasusBridge != null
                                    && pegasusBridge.getConnectionState()
                                            == ConnectionState.CONNECTED
                            ? pegasusBridge.liftedPieceSquare()
                            : -1;
            if (pendingCapture >= 0) {
                pegasusMismatchLabel.setText(
                        Messages.get(
                                "board_pending_capture_hint_format",
                                BoardState.squareName(pendingCapture)));
                pegasusMismatchLabel.setVisible(true);
            } else if (liftedPiece >= 0) {
                pegasusMismatchLabel.setText(liftedPieceHint(liftedPiece));
                pegasusMismatchLabel.setVisible(true);
            } else {
                pegasusMismatchLabel.setVisible(false);
            }
            return;
        }
        List<Square> squares = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (int index : pegasusBridge.mismatchSquares()) {
            String name = BoardState.squareName(index);
            names.add(name);
            squares.add(Square.fromValue(name.toUpperCase(Locale.ROOT)));
        }
        boardCanvas.setMismatchSquares(squares);
        boolean autoMoveHeld =
                heldEngineMoveUci != null
                        || (mode == Mode.TRAINING && trainingFlow.isAutoMoveHeld());
        int promotionSquare = pegasusBridge.promotionSquareAwaitingPiece();
        String text =
                promotionSquare >= 0
                        ? Messages.get(
                                "board_promotion_piece_needed_format",
                                BoardState.squareName(promotionSquare))
                        : Messages.get("pegasus_board_mismatch");
        if (!names.isEmpty() && promotionSquare < 0) {
            text +=
                    "\n"
                            + Messages.get(
                                    "pegasus_mismatch_squares_format", String.join(", ", names));
        }
        if (autoMoveHeld) {
            text += "\n" + Messages.get("pegasus_auto_move_held");
        }
        if (canLoadPositionFromBoard()) {
            text += "\n" + Messages.get("board_load_position_hint");
        }
        pegasusMismatchLabel.setText(text);
        pegasusMismatchLabel.setVisible(true);
    }

    /**
     * Taking the board's position over is offered while a piece-identifying board (Chessnut) is
     * connected and disagrees with the screen, outside the opening trainer.
     */
    private boolean canLoadPositionFromBoard() {
        return pegasusBridge != null
                && pegasusBridge.canLoadPhysicalPosition()
                && pegasusBridge.getConnectionState() == ConnectionState.CONNECTED
                && mode != Mode.TRAINING;
    }

    /** Click on the mismatch banner: ask who is to move, then load the board's position. */
    private void onMismatchLabelClicked() {
        if (!canLoadPositionFromBoard()) {
            return;
        }
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.initOwner(stage);
        alert.setTitle(Messages.get("dialog_load_position_title"));
        alert.setHeaderText(null);
        alert.setContentText(Messages.get("dialog_load_position_side"));
        ButtonType white = new ButtonType(Messages.get("color_white"), ButtonBar.ButtonData.YES);
        ButtonType black = new ButtonType(Messages.get("color_black"), ButtonBar.ButtonData.NO);
        alert.getButtonTypes().setAll(white, black, ButtonType.CANCEL);
        Styles.apply(alert.getDialogPane());
        alert.showAndWait()
                .ifPresent(
                        choice -> {
                            if (choice == white) {
                                loadPositionFromBoard(true);
                            } else if (choice == black) {
                                loadPositionFromBoard(false);
                            }
                        });
    }

    /**
     * Replaces the game with whatever stands on the board, keeping mode, opponent and colours; the
     * opponent moves right away if it is its turn.
     */
    private void loadPositionFromBoard(boolean whiteToMove) {
        String fen;
        try {
            fen = pegasusBridge.physicalPositionFen(whiteToMove);
        } catch (InvalidPositionException e) {
            String key =
                    switch (e.reason()) {
                        case KINGS -> "board_position_invalid_kings";
                        case PAWN_ON_BACK_RANK -> "board_position_invalid_pawns";
                        case OPPONENT_IN_CHECK -> "board_position_invalid_check";
                        default -> "board_position_invalid";
                    };
            showError(Messages.get(key));
            return;
        }
        abandonPendingSearches();
        game.loadFen(fen);
        boardCanvas.setLastMove(null, null);
        boardCanvas.setTrainingHint(null, null);
        boardCanvas.clearSelection();
        if (engineReady) {
            engine.newGame();
            engine.setStrength((int) strengthSlider.getValue());
        }
        scrollMoveHistoryToEnd = true;
        refresh();
        syncPegasusPosition();
        maybeStartEngineMove();
    }

    @Override
    public void onPromotionRequired() {
        showPhysicalPromotionDialog();
    }

    @Override
    public void onAmbiguousMove(List<String> candidateUcis) {
        showAmbiguousMoveDialog(candidateUcis);
    }

    @Override
    public void onGuideDeviation(boolean deviating) {
        updatePegasusMismatchLabel();
    }

    @Override
    public void onEngineMoveGuidanceComplete() {
        if (mode == Mode.TRAINING && trainingFlow.onGuidanceComplete()) {
            return;
        }
        // GameController's own state was already updated when the engine move
        // was applied (applyEngineReply); only its deferred sound is left.
        if (engineMoveSoundPending) {
            engineMoveSoundPending = false;
            playMoveSound(engineMoveWasCapture);
        }
    }

    @Override
    public void onTransportError(TransportError error, String detail) {
        LOG.log(Level.WARNING, "Board transport error {0}: {1}", new Object[] {error, detail});
        showError(Messages.get("board_error_format", boardType.displayName(), error));
    }

    /**
     * Shared by both listener interfaces. For the Pegasus {@code low} means critically low (the
     * board shuts down within minutes, per DGT); for the Chessnut it is a plain low-battery hint.
     */
    @Override
    public void onBatteryStatus(int percent, boolean low) {
        LOG.log(Level.INFO, "Board battery: {0}% (low={1})", new Object[] {percent, low});
        String message;
        if (low && boardType == BoardType.PEGASUS) {
            message = Messages.get("pegasus_battery_critical_format", percent);
        } else if (low) {
            message = Messages.get("board_battery_low_format", boardType.displayName(), percent);
        } else {
            message = Messages.get("board_battery_format", boardType.displayName(), percent);
        }
        showToast(message);
    }

    /** What to say about a piece held in the air for a while: whose it is and where it may go. */
    private String liftedPieceHint(int square) {
        String name = BoardState.squareName(square);
        if (pegasusBridge.liftedPieceBelongsToOpponent()) {
            return Messages.get("board_lifted_opponent_piece_format", name);
        }
        List<Integer> destinations = pegasusBridge.liftedPieceDestinations();
        if (destinations.isEmpty()) {
            return Messages.get("board_lifted_piece_no_moves_format", name);
        }
        List<String> names = new ArrayList<>();
        for (int to : destinations) {
            names.add(BoardState.squareName(to));
        }
        return Messages.get("board_lifted_piece_moves_format", name, String.join(", ", names));
    }

    /** Pegasus only: the board sat in a state worth a hint, or left it. */
    @Override
    public void onBoardHint() {
        updatePegasusMismatchLabel();
    }

    /** Chessnut only: the board's NEW GAME button opens the same setup dialog as the button. */
    @Override
    public void onNewGameButton() {
        openGameSetupDialog();
    }

    /**
     * Self-dismissing, non-blocking notification anchored to the bottom of the window - the desktop
     * equivalent of the Android app's {@code Toast.makeText(...).show()} (used there for this same
     * battery report, plus connect/disconnect). A plain {@link Alert} would work too but blocks the
     * game (modal {@code showAndWait()}), which a routine "battery: 42%" report doesn't warrant.
     */
    private void showToast(String message) {
        Label label = new Label(message);
        label.setStyle(
                "-fx-background-color: rgba(33,33,33,0.92); -fx-text-fill: white; "
                        + "-fx-padding: 8 16 8 16; -fx-background-radius: 6; -fx-font-size: 12px;");
        Popup popup = new Popup();
        popup.setAutoFix(true);
        popup.getContent().add(label);
        double width = label.prefWidth(-1);
        double height = label.prefHeight(width);
        double x = stage.getX() + (stage.getWidth() - width) / 2;
        double y = stage.getY() + stage.getHeight() - height - 48;
        popup.show(stage, x, y);
        PauseTransition pause = new PauseTransition(Duration.seconds(3.5));
        pause.setOnFinished(e -> popup.hide());
        pause.play();
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
        engine.setPosition(game.startFen(), game.toUciMoveList());
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
        engine.setPosition(game.startFen(), game.toUciMoveList());
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
     * Replays the game played so far from the start, one ply at a time, grading every move the same
     * way live blunder-check does ({@link #moveQualityLabelKey}) and showing a summary dialog once
     * done. Works whether the game has actually ended or is still in progress - only {@link
     * ChessGame#moveCount()} needs to be positive, there has to be something to replay. Always
     * searches at full strength, restored afterwards in {@link #advancePostGameAnalysis}.
     *
     * <p>Unlike every other search this method starts, it doesn't call {@link
     * StockfishEngine#newGame()} before replaying (which would send "isready" and re-enter {@link
     * #onReadyOk}, undoing the full-strength setting and, if it were still someone's turn in a
     * still-live game, firing off an unwanted real move) - so instead, if the game isn't actually
     * over once the replay finishes, the live engine turn (if any) and live eval search that {@link
     * #abandonPendingSearches} cancelled below are explicitly restarted at the end of {@link
     * #recordPostGameEval}. {@link #isBoardInteractiveNow} blocks board clicks for the whole
     * replay, since a move played on the actual, live game mid-replay would go through the same
     * shared engine instance without this method noticing.
     */
    private void startPostGameAnalysis() {
        if (!engineReady
                || mode == Mode.TRAINING
                || game.moveCount() == 0
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
        engine.setPosition(
                game.startFen(), String.join(" ", postGameUciMoves.subList(0, positionIndex)));
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
        if (!game.isGameOver()) {
            // The game was still live when analysis started (see startPostGameAnalysis) -
            // abandonPendingSearches() there cancelled whatever live engine-move/eval search was
            // in flight, so resume it now the same way starting a new game kicks the engine off: a
            // plain refresh (board interactivity, live eval) plus an explicit engine-move trigger,
            // since refresh() itself never starts one.
            refresh();
            maybeStartEngineMove();
        }
    }

    /**
     * The icon-only Analyze button has no visible label to carry progress the way the old text
     * button did - {@link #analysisProgressLabel} fills that role instead, shown only while an
     * analysis is actually running.
     */
    private void updateAnalyzeGameButtonState() {
        boolean running = postGameUciMoves != null;
        analyzeGameButton.setDisable(
                running || !engineReady || mode == Mode.TRAINING || game.moveCount() == 0);
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
     * already-queued one, including a Maia move still waiting out {@link #scheduleMaiaMove}'s
     * humanlike pause) as belonging to a position we've since left - used wherever the game is
     * reset out from under a possibly in-flight search (new game, undo, PGN import).
     */
    private void abandonPendingSearches() {
        if (engine != null) {
            engine.stop();
        }
        searchGeneration++;
        waitingForEngineMove = false;
        heldEngineMoveUci = null;
        engineMoveSoundPending = false;
        stopPendingMaiaMove();
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
        // Skipped only when Stockfish is the paired engine and it's about to search for its own
        // reply move anyway (#maybeStartEngineMove) - that search's own "info" stream already
        // covers the evaluation display and move-quality grading, so a second, redundant search
        // here would be wasted. HUMAN_VS_MAIA doesn't get that for free: Maia's own move-generation
        // never touches this Stockfish instance at all, and its reply is a near-instant single
        // forward pass with no search - without #scheduleMaiaMove's deliberate humanlike pause
        // there'd be no time for a fresh analysis search to produce anything before the position
        // moved on. With that pause now in place there's genuine idle wall-clock time, so this
        // search always runs for HUMAN_VS_MAIA too - which is what lets #maybeFinalizeMoveQuality
        // grade the human's move (blunder/mistake/inaccuracy) in Maia games as well, not just
        // Stockfish ones. A slight mismatch is tolerated at the tail end: ANALYSIS_MOVETIME_MS
        // (1.5s) can outlast #scheduleMaiaMove's pause (max 1.4s), so this search sometimes gets
        // engine.stop()'d by the next one (started for the post-reply position) before finishing -
        // same "stopped and superseded" pattern already used everywhere else searches chain here,
        // and harmless since grading only needs the first info line, which arrives in milliseconds.
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
        if (pegasusBridge != null) {
            pegasusBridge.resetForNewGame();
        }
        refresh();
    }

    /**
     * The single "New Game" entry point - opponent, color, and (for the trainer) opening/hints -
     * mirrors the Android app's one {@code showNewGameDialog} used from both its "New Game" menu
     * action and its training-complete "Pick opening" button. Unlike that dialog, engine strength
     * stays out of it: it's a live sidebar slider on desktop, not a one-time setup choice.
     */
    private void openGameSetupDialog() {
        GameSetupDialog.show(stage).ifPresent(this::applyGameSetupChoice);
    }

    private void applyGameSetupChoice(GameSetupDialog.Choice choice) {
        switch (choice.opponent()) {
            case HUMAN -> startHumanVsHuman();
            case STOCKFISH -> startStockfishGame(choice.side());
            case MAIA -> startMaiaGame(choice.side(), Settings.getMaiaRating());
            case TRAINER -> startTraining(choice.opening(), choice.side(), choice.hintsEnabled());
        }
    }

    private void startHumanVsHuman() {
        mode = Mode.HUMAN_VS_HUMAN;
        trainingSession = null;
        trainingFlow.stop();
        humanSide = Side.WHITE;
        boardFlipped = false;
        boardCanvas.setFlipped(false);
        boardCanvas.setTrainingHint(null, null);
        strengthSlider.setDisable(true);
        updateStrengthControlsVisibility();
        newGame();
    }

    /** Starts a fresh Human vs Stockfish game with the human playing the given side. */
    private void startStockfishGame(Side side) {
        mode = Mode.HUMAN_VS_STOCKFISH;
        trainingSession = null;
        trainingFlow.stop();
        humanSide = side;
        boardFlipped = side == Side.BLACK;
        boardCanvas.setFlipped(boardFlipped);
        boardCanvas.setTrainingHint(null, null);
        strengthSlider.setDisable(false);
        updateStrengthControlsVisibility();
        newGame();
        maybeStartEngineMove();
    }

    /**
     * Starts a fresh Human vs Maia game with the human playing the given side, loading the ONNX
     * model for {@code rating} (one of {@link MaiaRatings#ALL}) fresh - unlike {@link #engine}
     * (Stockfish), which is started once in the constructor and lives for the app's whole lifetime,
     * a Maia network's "strength" is fixed at load time, not a UCI option on a running instance, so
     * a new {@link MaiaEngine} is created here (shutting down any previous one first) and again on
     * every later rating change via {@link #switchMaiaRating}.
     */
    void startMaiaGame(Side side, int rating) {
        mode = Mode.HUMAN_VS_MAIA;
        trainingSession = null;
        trainingFlow.stop();
        humanSide = side;
        boardFlipped = side == Side.BLACK;
        boardCanvas.setFlipped(boardFlipped);
        boardCanvas.setTrainingHint(null, null);
        strengthSlider.setDisable(true);
        currentMaiaRating = rating;
        updateStrengthControlsVisibility();
        loadMaiaEngine(rating);
        newGame();
        maybeStartEngineMove();
    }

    /**
     * Swaps in a different bundled Maia rating model without starting a new game - unlike {@link
     * #startMaiaGame}, {@link #game} and the move history are left untouched, and the next Maia
     * move (see {@link #maybeStartEngineMove}) simply replays the current position into the new
     * engine before asking it to move.
     *
     * <p>Ignored while Maia is still computing its previous move ({@link #maiaRatingSlider} is kept
     * disabled for the same reason - see {@link #refresh}): swapping the engine out from under an
     * in-flight search would let the outgoing engine's reply and the incoming engine's own reply to
     * the same pre-move position both land, racing to apply two different moves to one board.
     */
    private void switchMaiaRating(int rating) {
        if (mode != Mode.HUMAN_VS_MAIA || rating == currentMaiaRating || waitingForEngineMove) {
            return;
        }
        currentMaiaRating = rating;
        Settings.setMaiaRating(rating);
        maiaRatingLabel.setText(Messages.get("dialog_maia_rating_format", rating));
        loadMaiaEngine(rating);
    }

    /**
     * Shared by {@link #startMaiaGame} and {@link #switchMaiaRating}: (re)loads {@link
     * #maiaEngine}.
     *
     * <p>Captures the freshly created engine in {@code loadedEngine} and has the listener check
     * it's still the current {@link #maiaEngine} before touching {@link #maiaReady} - dragging
     * {@link #maiaRatingSlider} across several ticks in one release can call this again before a
     * slower, already-superseded model finishes loading, and that engine's belated {@code onReady}
     * must not flip {@link #maiaReady} back on for an engine nobody is going to search with.
     */
    private void loadMaiaEngine(int rating) {
        if (maiaEngine != null) {
            maiaEngine.shutdown();
        }
        maiaReady = false;
        MaiaEngine loadedEngine = new MaiaEngine(Platform::runLater);
        maiaEngine = loadedEngine;
        loadedEngine.setListener(
                new MaiaEngineListener() {
                    @Override
                    public void onReady() {
                        if (maiaEngine != loadedEngine) {
                            return;
                        }
                        maiaReady = true;
                        maybeStartEngineMove();
                    }

                    @Override
                    public void onBestMove(
                            String bestMoveUci,
                            float winProbability,
                            float drawProbability,
                            float lossProbability) {
                        if (maiaEngine != loadedEngine
                                || maiaSearchGeneration != searchGeneration) {
                            // Stale: the game moved on (undo/new game/PGN import/...) while this
                            // reply was in flight - discard it rather than applying a move to a
                            // position it no longer matches.
                            return;
                        }
                        if (bestMoveUci == null) {
                            waitingForEngineMove = false;
                            refresh();
                            return;
                        }
                        scheduleMaiaMove(bestMoveUci);
                    }

                    @Override
                    public void onEngineError(Exception error) {
                        waitingForEngineMove = false;
                        maiaRatingSlider.setDisable(false);
                        statusLabel.setText(
                                Messages.get("error_engine_generic") + ": " + error.getMessage());
                    }
                });
        try (java.io.InputStream model =
                GameController.class.getResourceAsStream(MaiaRatings.resourcePath(rating))) {
            if (model == null) {
                throw new FileNotFoundException(MaiaRatings.resourcePath(rating));
            }
            loadedEngine.start(model);
        } catch (IOException e) {
            statusLabel.setText(Messages.get("status_maia_unavailable") + ": " + e.getMessage());
        }
    }

    /**
     * Applies Maia's already-computed reply after a short, randomized pause between {@link
     * #MAIA_MOVE_MIN_DELAY_MS} and {@link #MAIA_MOVE_MAX_DELAY_MS} so the opponent doesn't look
     * inhumanly instant next to Stockfish's own fixed think time - filled up to that random target
     * from {@link #maiaRequestStartNanos} (when {@link MaiaEngine#go} was called), not added on top
     * of it, so a slower device's own (still near-instant) inference time doesn't stack with the
     * pause. Skipped (applies immediately) when the Pegasus board is connected, where physically
     * guiding the move there already takes real time of its own.
     */
    private void scheduleMaiaMove(String bestMoveUci) {
        stopPendingMaiaMove();
        boolean pegasusConnected =
                pegasusBridge != null
                        && pegasusBridge.getConnectionState() == ConnectionState.CONNECTED;
        long elapsedMs = (System.nanoTime() - maiaRequestStartNanos) / 1_000_000;
        long targetMs =
                pegasusConnected
                        ? 0
                        : java.util.concurrent.ThreadLocalRandom.current()
                                .nextInt(MAIA_MOVE_MIN_DELAY_MS, MAIA_MOVE_MAX_DELAY_MS + 1);
        long remainingMs = targetMs - elapsedMs;
        if (remainingMs <= 0) {
            finishMaiaMove(bestMoveUci);
            return;
        }
        PauseTransition pause = new PauseTransition(Duration.millis(remainingMs));
        pause.setOnFinished(e -> finishMaiaMove(bestMoveUci));
        pendingMaiaMove = pause;
        pause.play();
    }

    private void finishMaiaMove(String bestMoveUci) {
        pendingMaiaMove = null;
        if (maiaSearchGeneration != searchGeneration) {
            // Stale: undo/new game/PGN import/... reset the game while this pause was running.
            return;
        }
        waitingForEngineMove = false;
        applyEngineReply(bestMoveUci);
    }

    /**
     * Cancels a pause queued by {@link #scheduleMaiaMove}, if any - see {@link #pendingMaiaMove}.
     */
    private void stopPendingMaiaMove() {
        if (pendingMaiaMove != null) {
            pendingMaiaMove.stop();
            pendingMaiaMove = null;
        }
    }

    /**
     * Whether the current {@link #mode} plays exactly one engine reply after each human move
     * (Stockfish or Maia) - as opposed to {@code HUMAN_VS_HUMAN} (no engine turn at all) or {@code
     * TRAINING} (its own book-move logic). Shared by every place that treats a human move and its
     * paired engine reply as one unit: undo/redo, move-history highlighting, and history navigation
     * ({@link #jumpToPly}) all skip past the reply together rather than landing between the two.
     */
    private boolean isPairedEngineMode() {
        return mode == Mode.HUMAN_VS_STOCKFISH || mode == Mode.HUMAN_VS_MAIA;
    }

    /**
     * Shows exactly one of {@link #strengthSlider}/{@link #strengthLabel} (Stockfish) or {@link
     * #maiaRatingLabel}/{@link #maiaRatingSlider} (Maia) depending on {@link #mode} - both pairs
     * are live-adjustable for their running game, called from every place that changes {@link
     * #mode}.
     */
    private void updateStrengthControlsVisibility() {
        boolean isMaia = mode == Mode.HUMAN_VS_MAIA;
        strengthLabel.setVisible(!isMaia);
        strengthSlider.setVisible(!isMaia);
        maiaRatingLabel.setVisible(isMaia);
        maiaRatingSlider.setVisible(isMaia);
        if (isMaia) {
            maiaRatingLabel.setText(Messages.get("dialog_maia_rating_format", currentMaiaRating));
            maiaRatingSlider.setValue(currentMaiaRating);
        }
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
        if (isPairedEngineMode() && game.moveCount() > 0 && game.sideToMove() != humanSide) {
            game.undoLastMove();
        }
        boardCanvas.setLastMove(null, null);
        refresh();
        syncPegasusPosition();
    }

    /**
     * Mirrors {@link #undo()}: reapplies the move(s) undo most recently moved to the redo stack,
     * landing back on the human's own turn in a paired-engine mode ({@link #isPairedEngineMode})
     * the same way undo does (redo the human's move, then immediately redo the engine's reply too).
     * No-op if there is nothing to redo.
     */
    private void redo() {
        if (mode == Mode.TRAINING) {
            redoTrainingMove();
            return;
        }
        abandonPendingSearches();
        if (!game.redoMove()) {
            return;
        }
        if (isPairedEngineMode() && game.canRedo() && game.sideToMove() != humanSide) {
            game.redoMove();
        }
        boardCanvas.setLastMove(null, null);
        refresh();
        syncPegasusPosition();
    }

    private void refresh() {
        Piece[] pieces = new Piece[64];
        for (int i = 0; i < 64; i++) {
            pieces[i] = game.pieceAt(Square.squareAt(i));
        }
        boardCanvas.setBoard(pieces);
        boardCanvas.setCheckedKingSquare(findCheckedKingSquare());
        boardCanvas.setInteractive(isBoardInteractiveNow());
        maiaRatingSlider.setDisable(waitingForEngineMove);
        updateMoveHistory();
        statusLabel.setText(statusText());
        statusLabel.getStyleClass().removeAll("check", "gameover");
        if (game.isGameOver()) {
            statusLabel.getStyleClass().add("gameover");
        } else if (game.isCheck()) {
            statusLabel.getStyleClass().add("check");
        }
        updateTrainingProgressLabel();
        updatePegasusMismatchLabel();
        updateTrainingHint();
        maybeTriggerAnalysis();
        updateHintButtonState();
        updateAnalyzeGameButtonState();

        if (game.isGameOver()) {
            showGameOverAlert();
        }
    }

    /**
     * Turns {@link ChessGame#toSan()}'s numbered movetext (e.g. "1. e4 e5 2. Nf3") into individual
     * {@link Text} nodes in {@link #moveListFlow}: move-number tokens stay plain, every actual move
     * is clickable and jumps the game to the position right after it via {@link #jumpToPly}. The
     * training line has its own guided navigation (retreat()/advance() paired with undo/redo), so
     * its history stays plain, unclickable text - letting the trainee freely jump around it would
     * cut across the "prove you know the move" hinting.
     *
     * <p>Uses {@link ChessGame#toFullSan()}, not {@link ChessGame#toSan()}, outside TRAINING mode:
     * after stepping back via undo/{@link #jumpToPly}, the moves ahead are still redoable and must
     * stay visible and clickable, or the user could never navigate back and forth in the history -
     * only playing a genuinely new move should drop them. The move(s) that make up the position
     * currently on the board - however reached (a played move, {@link #undo()}/{@link #redo()}, or
     * a previous history click) - additionally get the {@code move-history-current} style, so the
     * list always shows where you currently are.
     *
     * <p>In a paired-engine mode ({@link #isPairedEngineMode}, Stockfish or Maia) that's always the
     * human's move plus the engine's paired reply (see {@link #jumpToPly}'s own pairing -
     * navigation there can never land between the two), so both get the style, not just {@link
     * ChessGame#moveCount()} alone: highlighting only the second half would look contradictory
     * after clicking the human's own move - the reply next to it would light up instead of the one
     * actually clicked. Outside those modes there is no such pairing, so only the exact current
     * move gets it.
     */
    private void updateMoveHistory() {
        moveListFlow.getChildren().clear();
        if (mode == Mode.TRAINING) {
            moveListFlow.getChildren().add(new Text(game.toSan()));
        } else {
            int ply = 0;
            int currentPly = game.moveCount();
            int currentRoundStart =
                    isPairedEngineMode() && currentPly > 1 ? currentPly - 1 : currentPly;
            boolean first = true;
            for (String token : game.toFullSan().split("\\s+")) {
                if (token.isEmpty()) {
                    continue;
                }
                if (!first) {
                    moveListFlow.getChildren().add(new Text(" "));
                }
                first = false;
                if (token.matches("\\d+\\.")) {
                    moveListFlow.getChildren().add(new Text(token));
                } else {
                    ply++;
                    int targetPly = ply;
                    Text moveText = new Text(token);
                    moveText.getStyleClass().add("move-history-link");
                    if (ply >= currentRoundStart && ply <= currentPly) {
                        moveText.getStyleClass().add("move-history-current");
                    }
                    moveText.setCursor(Cursor.HAND);
                    moveText.setOnMouseClicked(e -> jumpToPly(targetPly));
                    moveListFlow.getChildren().add(moveText);
                }
            }
        }
        if (scrollMoveHistoryToEnd) {
            scrollMoveHistoryToEnd = false;
            // Deferred, not called directly: the ScrollPane hasn't been laid out against
            // moveListFlow's new (possibly taller) content yet, so setVvalue(1.0) now would
            // scroll to the previous, shorter bottom instead of the true new one.
            Platform.runLater(() -> moveListScroll.setVvalue(1.0));
        }
    }

    /**
     * Set by {@link #applyUciToGame}/{@link #onMoveChosen} (a genuinely new move was just played -
     * by a human click, the engine, the physical board, or training auto-play) and by {@link
     * #finishPgnImport} (a whole new game was just loaded), consumed by the next {@link
     * #updateMoveHistory()} call to scroll the move list down to it. Deliberately not set by
     * undo/redo/{@link #jumpToPly} - those are the user explicitly choosing to look at a specific
     * point in the history, which auto-scrolling away from would defeat the purpose of.
     */
    private boolean scrollMoveHistoryToEnd;

    /**
     * Jumps the game to the position right after ply {@code targetPly} (1-based, matching a move's
     * position in {@link ChessGame#toUciMoveList()}) - like clicking a move in the history. Reuses
     * {@link ChessGame#jumpToPly}'s own undo/redo stacks for a single refresh/Pegasus-resync
     * instead of the several an equivalent run of {@link #undo()}/{@link #redo()} clicks would
     * trigger, and highlights the landed-on move like any other applied move. Only reachable
     * outside TRAINING mode - see {@link #updateMoveHistory()}.
     *
     * <p>In a paired-engine mode ({@link #isPairedEngineMode}), never leaves the browsed position
     * frozen on the engine's own turn - always advances one further ply forward instead, redoing
     * the engine's already-recorded reply (mirroring {@link #undo()}/{@link #redo()}'s own pairing,
     * never triggering a fresh engine decision - like other chess GUIs' history navigation, only
     * already-recorded moves are ever skipped past). Always forward, regardless of which direction
     * {@code targetPly} was reached from: pairing backward would instead undo the very move that
     * was clicked on, and would make clicking the same history entry repeatedly land on a different
     * position each time (the first click's pairing changes where the next click's "direction" is
     * computed from) - this way {@code jumpToPly} is a pure function of {@code targetPly} alone,
     * idempotent under repeated clicks on the same entry.
     */
    private void jumpToPly(int targetPly) {
        abandonPendingSearches();
        int before = game.moveCount();
        int reached = game.jumpToPly(targetPly);
        if (reached != targetPly) {
            return; // out of range; nothing changed
        }
        if (reached != before && isPairedEngineMode() && game.sideToMove() != humanSide) {
            game.redoMove();
            reached = game.moveCount();
        }
        if (reached > 0) {
            String[] uciMoves = game.toUciMoveList().split(" ");
            String uci = uciMoves[reached - 1];
            Square from = Square.valueOf(uci.substring(0, 2).toUpperCase(Locale.ROOT));
            Square to = Square.valueOf(uci.substring(2, 4).toUpperCase(Locale.ROOT));
            boardCanvas.setLastMove(from, to);
        } else {
            boardCanvas.setLastMove(null, null);
        }
        refresh();
        syncPegasusPosition();
    }

    private boolean isBoardInteractiveNow() {
        if (game.isGameOver()
                || waitingForEngineMove
                // A move played here while game analysis is running would corrupt it: the analysis
                // replays past positions through the same shared engine instance without going
                // through abandonPendingSearches() per position (that would restart the whole
                // analysis on every single ply), so it has no way to notice its captured
                // postGameUciMoves has gone stale, and its own in-flight search would race the
                // freshly triggered one for the new move on that one shared engine.
                || postGameUciMoves != null) {
            return false;
        }
        return switch (mode) {
            case HUMAN_VS_HUMAN -> true;
            case HUMAN_VS_STOCKFISH, HUMAN_VS_MAIA -> game.sideToMove() == humanSide;
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
        abandonPendingSearches();
        trainingFlow.start(opening, side, hintsEnabled);
        trainingSession = trainingFlow.session();
        mode = Mode.TRAINING;
        humanSide = side;
        boardFlipped = side == Side.BLACK;
        boardCanvas.setFlipped(boardFlipped);
        boardCanvas.setLastMove(null, null);
        boardCanvas.setTrainingHint(null, null);
        boardCanvas.clearSelection();
        strengthSlider.setDisable(true);
        updateStrengthControlsVisibility();
        if (pegasusBridge != null) {
            pegasusBridge.resetForNewGame();
        }
        refresh();
        trainingFlow.advance();
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
        trainingFlow.onUserMove(tapped);
    }

    /**
     * A move confirmed by plain move detection during training. Normally impossible: while a guide
     * is active, the bridge routes physical events to it exclusively, and the trainee's own move is
     * always guided. The one exception is a book-side capture completed by follow-up proof (the
     * trainee already lifted a piece before the capture was proven, see the bridge's {@code
     * isGuidedCaptureProvenByFollowUp}): the board was mid-move when the trainee's guide would have
     * started, so {@code guideEngineMove} no-op'd and the move arrives here instead. Accept it if
     * it is the line's expected move; otherwise pull the bridge's position back to the game's so
     * the wrong move lights up as a mismatch until undone, after which the retry in {@link
     * #onBoardMismatch} starts the guide as usual.
     */
    private void onTrainingMoveConfirmedByDetection(String uci) {
        trainingFlow.onPhysicalMoveConfirmed(uci);
    }

    /**
     * Drives the training line forward: shows a hint for the trainee's own next move (applied only
     * once the physical board confirms it - see {@link #onEngineMoveGuidanceComplete}), or plays
     * out the book side's move immediately - guided physically if connected, after a short delay
     * otherwise so it doesn't feel instantaneous.
     */
    private void maybeAdvanceTraining() {
        if (mode == Mode.TRAINING) {
            trainingFlow.advance();
        }
    }

    /**
     * Steps the training line back to the trainee's own previous move so they can retry it. Rolls
     * back an unconfirmed optimistic book-move apply first (see {@link TrainingFlow#undo()}) so
     * {@link #game} and {@link #trainingSession} never disagree, then resyncs the physical board -
     * any active guide is cancelled there too, and mismatch LEDs light up until the pieces are
     * moved back; once they match, {@link #onBoardMismatch} re-issues the hint for the retried move
     * on its own.
     */
    private void undoTrainingMove() {
        trainingFlow.undo();
    }

    /**
     * Mirrors {@link #undoTrainingMove()}: replays the trainee's next expected move (exactly the
     * move undo last retreated past, since {@link TrainingSession} tracks a fixed, known opening
     * line rather than free-form history - no separate redo stack is needed here), then immediately
     * replays the book/engine's reply too if that lands off the human's turn, mirroring undo's own
     * pairing. No-op if there is nothing to redo.
     */
    private void redoTrainingMove() {
        trainingFlow.redo();
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
        // JavaFX only honors the window's close button (X, Esc) when a button with the
        // CANCEL_CLOSE role exists; none of the three choices has it. Add one but keep it
        // out of sight so the dialog still reads as the same three-way choice - closing
        // simply leaves the finished line on the board.
        alert.getButtonTypes().setAll(repeatType, pickType, continueType, ButtonType.CLOSE);
        Styles.apply(alert.getDialogPane());
        javafx.scene.Node closeButton = alert.getDialogPane().lookupButton(ButtonType.CLOSE);
        closeButton.setVisible(false);
        closeButton.setManaged(false);

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isEmpty() || result.get() == ButtonType.CLOSE) {
            return;
        }
        if (result.get() == repeatType) {
            startTraining(line, side, hintsEnabled);
        } else if (result.get() == pickType) {
            openGameSetupDialog();
        } else if (result.get() == continueType) {
            continueFreePlay(side);
        }
    }

    /**
     * Continues playing from the position the completed line ended at, against Stockfish, Maia, or
     * a second human, instead of resetting - mirrors the Android app's "Continue free play"
     * training-complete option (Android has no Maia opponent yet, so its own version of this dialog
     * still offers only Stockfish/human).
     */
    private record OpponentOption(GameSetupDialog.Opponent opponent, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    private void continueFreePlay(Side trainedSide) {
        OpponentOption stockfishOption =
                new OpponentOption(
                        GameSetupDialog.Opponent.STOCKFISH, Messages.get("choice_stockfish"));
        OpponentOption maiaOption =
                new OpponentOption(GameSetupDialog.Opponent.MAIA, Messages.get("choice_maia"));
        OpponentOption humanOption =
                new OpponentOption(GameSetupDialog.Opponent.HUMAN, Messages.get("choice_human"));
        ChoiceDialog<OpponentOption> dialog =
                new ChoiceDialog<>(stockfishOption, stockfishOption, maiaOption, humanOption);
        dialog.initOwner(stage);
        dialog.setTitle(Messages.get("action_continue_free_play"));
        dialog.setHeaderText(null);
        dialog.setContentText(Messages.get("continue_free_play_prompt"));
        Styles.apply(dialog.getDialogPane());
        Optional<OpponentOption> choice = dialog.showAndWait();
        if (choice.isEmpty()) {
            return;
        }
        trainingFlow.stop();
        trainingSession = null;
        humanSide = trainedSide;
        boardCanvas.setTrainingHint(null, null);
        switch (choice.get().opponent()) {
            case STOCKFISH -> {
                mode = Mode.HUMAN_VS_STOCKFISH;
                strengthSlider.setDisable(false);
                updateStrengthControlsVisibility();
                refresh();
                if (engineReady) {
                    engine.setStrength((int) strengthSlider.getValue());
                }
            }
            case MAIA -> {
                mode = Mode.HUMAN_VS_MAIA;
                strengthSlider.setDisable(true);
                currentMaiaRating = Settings.getMaiaRating();
                updateStrengthControlsVisibility();
                loadMaiaEngine(currentMaiaRating);
                refresh();
            }
            default -> {
                mode = Mode.HUMAN_VS_HUMAN;
                strengthSlider.setDisable(true);
                updateStrengthControlsVisibility();
                refresh();
            }
        }
        maybeStartEngineMove();
    }

    private boolean applyUciToGame(String uci) {
        return applyUciToGame(uci, true);
    }

    /** As {@link #applyUciToGame(String)}; {@code withSound=false} defers the move sound. */
    private boolean applyUciToGame(String uci, boolean withSound) {
        Square from = Square.fromValue(uci.substring(0, 2).toUpperCase(Locale.ROOT));
        Square to = Square.fromValue(uci.substring(2, 4).toUpperCase(Locale.ROOT));
        boolean wasCapture = game.pieceAt(to) != Piece.NONE;
        recordMoveQualityBaseline();
        if (!game.applyUciMove(uci)) {
            return false;
        }
        boardCanvas.setLastMove(from, to);
        if (withSound) {
            playMoveSound(wasCapture);
        }
        scrollMoveHistoryToEnd = true;
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
        trainingFlow.stop();
        trainingSession = null;
        mode = Mode.HUMAN_VS_HUMAN;
        humanSide = Side.WHITE;
        boardFlipped = false;
        boardCanvas.setFlipped(false);
        boardCanvas.setLastMove(null, null);
        boardCanvas.setTrainingHint(null, null);
        boardCanvas.clearSelection();
        strengthSlider.setDisable(true);
        updateStrengthControlsVisibility();
        if (engineReady) {
            engine.newGame();
        }
        if (pegasusBridge != null) {
            pegasusBridge.resetForNewGame();
        }
        scrollMoveHistoryToEnd = true;
        refresh();
        syncPegasusPosition();
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
        } else if (mode == Mode.HUMAN_VS_MAIA) {
            String you = Messages.get("pgn_you");
            String maia = "Maia " + currentMaiaRating; // a proper noun + a number, not localized
            white = humanSide == Side.WHITE ? you : maia;
            black = humanSide == Side.WHITE ? maia : you;
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
        if (maiaEngine != null) {
            maiaEngine.shutdown();
        }
        if (pegasusBridge != null) {
            pegasusBridge.shutdown();
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
        applyEngineReply(bestMoveUci);
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
        // engine.stop() (see abandonPendingSearches()) is fire-and-forget - Stockfish can still
        // emit a few more "info" lines for the search just abandoned before it finally replies
        // with "bestmove" (which onBestMove() already discards via this same check). Without this,
        // one of those stale lines could land here after analysisSideToMove has already moved on
        // to a new position (e.g. one reached by undo/redo/a history click) and get displayed - and
        // fed into recordPositionEval() below - as if it were a fresh eval for that new position.
        PendingSearch activeSearch = pendingSearches.peek();
        if (activeSearch == null || activeSearch.generation() != searchGeneration) {
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

    // ---- Opening trainer adapters ---------------------------------------------------------

    /** The Pegasus bridge as {@link TrainingFlow.Board}; every method null-guards the bridge. */
    private final class TrainingBoardAdapter implements TrainingFlow.Board {
        @Override
        public boolean isConnected() {
            return pegasusBridge != null
                    && pegasusBridge.getConnectionState() == ConnectionState.CONNECTED;
        }

        @Override
        public boolean isInSync() {
            return pegasusBridge != null && pegasusBridge.isBoardInSync();
        }

        @Override
        public boolean isGuideActive() {
            return pegasusBridge != null && pegasusBridge.isGuideActive();
        }

        @Override
        public String trackedFen() {
            return pegasusBridge == null ? "" : pegasusBridge.trackedFen();
        }

        @Override
        public void guideMove(String uci, boolean showLeds) {
            if (pegasusBridge != null) {
                pegasusBridge.guideEngineMove(uci, showLeds);
            }
        }

        @Override
        public void syncToPosition(String fen) {
            if (pegasusBridge != null) {
                pegasusBridge.syncBoardToPosition(fen);
            }
        }
    }

    /** Screen effects for {@link TrainingFlow}. */
    private final class TrainingHostAdapter implements TrainingFlow.Host {
        private PauseTransition pendingBookMove;

        @Override
        public void moveApplied(String uci, boolean wasCapture, boolean withSound) {
            Square from = Square.fromValue(uci.substring(0, 2).toUpperCase(Locale.ROOT));
            Square to = Square.fromValue(uci.substring(2, 4).toUpperCase(Locale.ROOT));
            boardCanvas.setLastMove(from, to);
            if (withSound) {
                GameController.this.playMoveSound(wasCapture);
            }
            scrollMoveHistoryToEnd = true;
        }

        @Override
        public void playMoveSound(boolean wasCapture) {
            GameController.this.playMoveSound(wasCapture);
        }

        @Override
        public void historyRewritten() {
            boardCanvas.setLastMove(null, null);
        }

        @Override
        public void refresh() {
            GameController.this.refresh();
        }

        @Override
        public void showTrainingComplete() {
            // Deferred: reaching completion on the book side's own (auto-played) last move means
            // this runs from a PauseTransition's onFinished handler, i.e. while JavaFX is still
            // processing that animation - showAndWait() (inside showTrainingCompleteDialog) throws
            // IllegalStateException ("not allowed during animation or layout processing") if called
            // synchronously there, silently killing the dialog. Platform.runLater pushes it to a
            // fresh pulse, after the animation has finished processing.
            Platform.runLater(GameController.this::showTrainingCompleteDialog);
        }

        @Override
        public void scheduleBookMove(Runnable action, long delayMs) {
            cancelScheduledBookMove();
            pendingBookMove = new PauseTransition(Duration.millis(delayMs));
            pendingBookMove.setOnFinished(e -> action.run());
            pendingBookMove.play();
        }

        @Override
        public void cancelScheduledBookMove() {
            if (pendingBookMove != null) {
                pendingBookMove.stop();
                pendingBookMove = null;
            }
        }

        @Override
        public void log(String message) {
            LOG.log(Level.INFO, message);
        }
    }
}
