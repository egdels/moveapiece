/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece;

import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.PieceType;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.Square;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import de.schliweb.moveapiece.databinding.ActivityMainBinding;
import de.schliweb.moveapiece.databinding.DialogContinueFreePlayBinding;
import de.schliweb.moveapiece.databinding.DialogNewGameBinding;
import de.schliweb.moveapiece.databinding.DialogPromotionBinding;
import de.schliweb.moveapiece.engine.EngineListener;
import de.schliweb.moveapiece.engine.NnueAssets;
import de.schliweb.moveapiece.engine.StockfishEngine;
import de.schliweb.moveapiece.engine.UciInfoParser;
import de.schliweb.moveapiece.logic.ChessGame;
import de.schliweb.moveapiece.logic.PgnGames;
import de.schliweb.moveapiece.pegasus.PegasusGameBridge;
import de.schliweb.moveapiece.training.OpeningLine;
import de.schliweb.moveapiece.training.OpeningRepository;
import de.schliweb.moveapiece.training.TrainingSession;
import de.schliweb.moveapiece.ui.BoardView;
import de.schliweb.moveapiece.ui.MoveSoundPlayer;
import de.schliweb.moveapiece.ui.OpeningLibraryActivity;
import de.schliweb.moveapiece.ui.OpeningNames;
import de.schliweb.pegasus.bluetooth.AndroidPegasusBleTransport;
import de.schliweb.pegasus.bluetooth.BlePermissions;
import de.schliweb.pegasus.core.transport.ConnectionState;
import de.schliweb.pegasus.core.transport.DiscoveredDevice;
import de.schliweb.pegasus.core.transport.ScanListener;
import de.schliweb.pegasus.core.transport.TransportError;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;

public class MainActivity extends AppCompatActivity
        implements BoardView.MoveSource,
                BoardView.OnMoveListener,
                EngineListener,
                PegasusGameBridge.Listener {

    private static final int ELO_MIN = 1320;
    private static final int ELO_MAX = 3190;
    private static final int ENGINE_MOVETIME_MS = 1200;
    private static final int ANALYSIS_MOVETIME_MS = 1500;
    private static final int HINT_MOVETIME_MS = 1500;
    private static final int HINT_MULTI_PV_LINES = 3;
    private static final long PEGASUS_SCAN_TIMEOUT_MS = 15000;

    /**
     * How the human's moves are matched: against a second human, Stockfish, or a fixed opening
     * line.
     */
    private enum GameMode {
        HUMAN,
        ENGINE,
        TRAINING
    }

    private ActivityMainBinding binding;
    private final ChessGame game = new ChessGame();
    private StockfishEngine engine;
    private MoveSoundPlayer soundPlayer;
	private Settings settings;
    private PegasusGameBridge pegasusBridge;
    private ActivityResultLauncher<String[]> blePermissionLauncher;
    private ActivityResultLauncher<String> pgnImportLauncher;

    private GameMode mode = GameMode.ENGINE;
    private Side engineSide = Side.BLACK;
    private int engineElo = 1500;
    private boolean engineReady = false;
    private boolean waitingForEngineMove = false;
    private boolean waitingForHint = false;
    private volatile NnueAssets.Paths nnuePaths;

    // ---- Evaluation display state --------------------------------------------
    private boolean evaluationEnabled = true;

    /**
     * Who was to move in the position the most recent (engine-move or analysis) search was started
     * for.
     */
    private Side analysisSideToMove;

    // ---- Move-quality (blunder check) state ------------------------------------
    /**
     * Freshest known eval of the position currently on the board, from the side-to-move's own
     * perspective (raw UCI score, unlike {@link #onInfo}'s white-relative display value) - updated
     * from every "info" line regardless of which search it belongs to, since a deeper/more accurate
     * number for the same position is always welcome. {@link #lastPositionEvalMoveCount} pins it to
     * a specific ply ({@link de.schliweb.moveapiece.logic.ChessGame#moveCount()}); -1 means "none
     * yet" (requires {@link #evaluationEnabled}, so a fresh game or a toggle-off leaves it stale
     * until the next search completes).
     */
    private int lastPositionEvalCp;

    private int lastPositionEvalMoveCount = -1;

    /**
     * Snapshot of {@link #lastPositionEvalCp}/{@link #lastPositionEvalMoveCount} taken by {@link
     * #recordMoveQualityBaseline} right before a graded move is applied - compared against the eval
     * of the resulting position once that comes in (see {@link #maybeFinalizeMoveQuality}) to
     * classify the move's centipawn loss. -1 means "not currently grading a move".
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

    /** Latest raw score seen for the position currently being searched during post-game analysis. */
    private int postGameLiveScoreCp;

    private static final int POST_GAME_MOVETIME_MS = 400;

    // ---- Engine search bookkeeping --------------------------------------------
    /**
     * Analysis (evaluation display) and real-move searches share one Stockfish process, so a "go"
     * for one can still be in flight when the other wants to search. Every {@link
     * #startEngineSearch} call stops whatever's running first and records its purpose here;
     * Stockfish always answers a stopped search with its own "bestmove" too, so replies arrive in
     * exactly the order the searches were started in - {@link #onBestMove} pops this queue instead
     * of relying on a single "am I waiting for a move" flag, which can't tell a stale analysis
     * reply from the real one.
     */
    private enum SearchPurpose {
        REAL_MOVE,
        ANALYSIS,
        HINT,
        POST_GAME
    }

    private static final class PendingSearch {
        final SearchPurpose purpose;
        final int generation;

        PendingSearch(SearchPurpose purpose, int generation) {
            this.purpose = purpose;
            this.generation = generation;
        }
    }

    private final ArrayDeque<PendingSearch> pendingSearches = new ArrayDeque<>();

    /**
     * Bumped whenever outstanding searches are deliberately abandoned (new game, PGN import) so a
     * cancelled search's eventual reply - still in {@link #pendingSearches} since it was stopped,
     * not un-queued - is recognized as belonging to a position we've since left, even though its
     * queue slot and purpose look otherwise valid.
     */
    private int searchGeneration = 0;

    // ---- Training mode state ------------------------------------------------
    private TrainingSession trainingSession;

    /** True while a guide shows the trainee's own next move, not yet applied to {@link #game}. */
    private boolean guidingTrainingHumanMove = false;

    private boolean waitingForTrainingAutoMove = false;

    /**
     * True while a connected board's book-move reply has already been applied to {@link #game} but
     * not yet advanced in {@link #trainingSession} - physical confirmation is still pending (see
     * {@link #onEngineMoveGuidanceComplete}). Undo needs to know this to roll the optimistic apply
     * back too, or {@link #game} and {@link #trainingSession} end up one ply out of step.
     */
    private boolean trainingBookMovePending = false;

    private final Handler trainingHandler = new Handler(Looper.getMainLooper());
    private static final long TRAINING_AUTO_MOVE_DELAY_MS = 600;

    // Mirrors state that otherwise lives only inside the (portrait- or
    // landscape-specific) BoardView instance, so it survives that instance
    // being torn down and recreated in bindViews() below.
    private boolean boardFlipped = false;
    private Square lastMoveFrom;
    private Square lastMoveTo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        soundPlayer = new MoveSoundPlayer(getApplicationContext());
        settings = new Settings(getApplicationContext());
        engineElo = settings.getEngineElo(engineElo);
        evaluationEnabled = settings.isEvaluationDisplayEnabled(evaluationEnabled);
        pegasusBridge =
                new PegasusGameBridge(
                        new AndroidPegasusBleTransport(getApplicationContext()), this);
        maybeStartPegasusRecording();
        blePermissionLauncher =
                registerForActivityResult(
                        new ActivityResultContracts.RequestMultiplePermissions(),
                        grantResults -> {
                            if (BlePermissions.allGranted(this)) {
                                showPegasusScanDialog();
                            } else {
                                Toast.makeText(
                                                this,
                                                R.string.pegasus_permission_required,
                                                Toast.LENGTH_SHORT)
                                        .show();
                            }
                        });
        pgnImportLauncher =
                registerForActivityResult(
                        new ActivityResultContracts.GetContent(), this::onPgnFileSelected);
        bindViews();
        startEngine();
    }

    /**
     * Raw BLE traffic recording for hardware-verification sessions. Debug builds only, written to
     * app-specific external storage so it can be pulled without root: {@code adb pull
     * /sdcard/Android/data/de.schliweb.moveapiece/files/pegasus-sessions/}. Non-critical if it
     * fails to start - recording is a debugging aid, not core functionality.
     */
    private void maybeStartPegasusRecording() {
        if ((getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)
                == 0) {
            return;
        }
        File dir = new File(getExternalFilesDir(null), "pegasus-sessions");
        if (!dir.exists() && !dir.mkdirs()) {
            return;
        }
        File file = new File(dir, "session-" + System.currentTimeMillis() + ".ndjson");
        try {
            pegasusBridge.startRecording(file);
        } catch (IOException e) {
            // Non-critical; see javadoc above.
        }
    }

    /**
     * Inflates activity_main.xml (or its layout-land variant) and wires up listeners. The manifest
     * declares configChanges for orientation, so rotating never destroys/recreates the Activity -
     * which is exactly why this needs to be called again from {@link #onConfigurationChanged}:
     * that's the only way the correct orientation-specific layout gets picked up. Reapplies board
     * state ({@link #game}, flip, last move) to the freshly inflated BoardView so nothing resets on
     * rotation.
     */
    private void bindViews() {
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.boardView.setMoveSource(this);
        binding.boardView.setOnMoveListener(this);
        binding.boardView.setFlipped(boardFlipped);
        binding.boardView.setLastMove(lastMoveFrom, lastMoveTo);

        binding.newGameButton.setOnClickListener(v -> showNewGameDialog());
        binding.undoButton.setOnClickListener(v -> undo());
        binding.flipBoardButton.setOnClickListener(v -> setBoardFlipped(!boardFlipped));
        binding.openingLibraryButton.setOnClickListener(
                v -> startActivity(new Intent(this, OpeningLibraryActivity.class)));
        binding.hintButton.setOnClickListener(v -> requestHint());
        binding.pegasusButton.setOnClickListener(v -> onPegasusButtonClicked());
        updatePegasusButtonLabel(pegasusBridge.getConnectionState());
        binding.exportPgnButton.setOnClickListener(v -> exportGamePgn());
        binding.importPgnButton.setOnClickListener(v -> pgnImportLauncher.launch("*/*"));
        binding.analyzeGameButton.setOnClickListener(v -> startPostGameAnalysis());

        binding.evaluationSwitch.setChecked(evaluationEnabled);
        binding.evaluationSwitch.setOnCheckedChangeListener(
                (btn, checked) -> {
                    evaluationEnabled = checked;
                    settings.setEvaluationDisplayEnabled(checked);
                    if (!checked) {
                        binding.evaluationText.setText("");
                    } else {
                        maybeTriggerAnalysis();
                    }
                });

        refreshBoard();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        bindViews();
    }

    private void setBoardFlipped(boolean flipped) {
        boardFlipped = flipped;
        binding.boardView.setFlipped(flipped);
    }

    private void setLastMove(Square from, Square to) {
        lastMoveFrom = from;
        lastMoveTo = to;
        binding.boardView.setLastMove(from, to);
    }

    private void startEngine() {
        String enginePath =
                getApplicationInfo().nativeLibraryDir + File.separator + "libstockfish.so";
        engine = new StockfishEngine(enginePath, new Handler(Looper.getMainLooper())::post);
        engine.setListener(this);

        // The NNUE nets (~112 MB) are shipped as APK assets instead of being
        // embedded in the engine binary (see app/stockfish.gradle); extract
        // them to app-private storage off the main thread before starting
        // the engine process.
        new Thread(
                        () -> {
                            try {
                                NnueAssets.Paths paths =
                                        NnueAssets.extractIfNeeded(
                                                path ->
                                                        getApplicationContext()
                                                                .getAssets()
                                                                .open(path),
                                                getApplicationContext().getFilesDir());
                                runOnUiThread(
                                        () -> {
                                            nnuePaths = paths;
                                            engine.start();
                                        });
                            } catch (IOException e) {
                                runOnUiThread(() -> onEngineError(e));
                            }
                        },
                        "nnue-extract")
                .start();
    }

    // ---- Pegasus board -----------------------------------------------------

    private void onPegasusButtonClicked() {
        if (pegasusBridge.getConnectionState() == ConnectionState.CONNECTED) {
            pegasusBridge.disconnect();
            return;
        }
        if (!BlePermissions.allGranted(this)) {
            blePermissionLauncher.launch(BlePermissions.required());
            return;
        }
        showPegasusScanDialog();
    }

    private void showPegasusScanDialog() {
        List<DiscoveredDevice> found = new ArrayList<>();
        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_pegasus_title)
                .setAdapter(
                        adapter,
                        (dialog, which) -> {
                            pegasusBridge.stopScan();
                            pegasusBridge.connect(found.get(which).getAddress());
                        })
                .setNegativeButton(
                        R.string.action_cancel, (dialog, which) -> pegasusBridge.stopScan())
                .setOnDismissListener(dialog -> pegasusBridge.stopScan())
                .show();

        Toast.makeText(this, R.string.pegasus_scan_empty, Toast.LENGTH_SHORT).show();
        pegasusBridge.startScan(
                new ScanListener() {
                    @Override
                    public void onDeviceFound(DiscoveredDevice device) {
                        if (device.getName() == null || device.getName().trim().isEmpty()) {
                            // Unnamed BLE devices clutter the list and are never the
                            // Pegasus board, which always advertises a name.
                            return;
                        }
                        runOnUiThread(
                                () -> {
                                    if (!found.contains(device)) {
                                        found.add(device);
                                        adapter.add(device.toString());
                                    }
                                });
                    }

                    @Override
                    public void onScanFinished() {
                        // Dialog just stops growing; no action needed.
                    }

                    @Override
                    public void onScanFailed(TransportError error, String detail) {
                        runOnUiThread(
                                () ->
                                        Toast.makeText(
                                                        MainActivity.this,
                                                        R.string.pegasus_scan_failed,
                                                        Toast.LENGTH_SHORT)
                                                .show());
                    }
                },
                PEGASUS_SCAN_TIMEOUT_MS);
    }

    /**
     * The icon-only Pegasus button (see {@code secondaryButtonBar} in the layout) carries its
     * connect/disconnect state via icon + contentDescription instead of visible text - swaps to a
     * green-tinted variant of the same glyph rather than re-tinting the default one at runtime, to
     * avoid fighting whatever default icon tint the button's style applies.
     */
    private void updatePegasusButtonLabel(ConnectionState state) {
        if (binding == null) {
            return;
        }
        boolean connected = state == ConnectionState.CONNECTED;
        binding.pegasusButton.setIconResource(
                connected ? R.drawable.ic_pegasus_connected : R.drawable.ic_pegasus);
        binding.pegasusButton.setContentDescription(
                getString(
                        connected
                                ? R.string.menu_pegasus_disconnect
                                : R.string.menu_pegasus_connect));
    }

    /**
     * Applies a move already confirmed as legal by an external source (the Stockfish engine, or the
     * physical board's own move detector) — shared by {@link #onBestMove} and {@link
     * #onPhysicalMoveConfirmed}. Human taps go through {@link #applyHumanMove} instead, since
     * promotion choice there comes from a dialog, not a UCI move string.
     */
    private void applyConfirmedMove(String uci, boolean isEngineMove) {
        if (applyUciToGame(uci)) {
            if (isEngineMove) {
                if (pegasusBridge.getConnectionState() == ConnectionState.CONNECTED) {
                    pegasusBridge.guideEngineMove(uci);
                }
            } else {
                maybeTriggerEngineMove();
            }
        }
        refreshBoard();
    }

    /**
     * Parses and applies a UCI move string to {@link #game} (last-move highlight + sound included),
     * without any of the mode-specific follow-up ({@link #applyConfirmedMove}'s
     * engine-guidance/next-move dispatch) — shared by that method and the training-mode move paths.
     */
    private boolean applyUciToGame(String uci) {
        Square from = Square.valueOf(uci.substring(0, 2).toUpperCase(Locale.ROOT));
        Square to = Square.valueOf(uci.substring(2, 4).toUpperCase(Locale.ROOT));
        boolean wasCapture = game.pieceAt(to) != Piece.NONE;
        recordMoveQualityBaseline();
        if (!game.applyUciMove(uci)) {
            return false;
        }
        setLastMove(from, to);
        playMoveSound(wasCapture);
        return true;
    }

    // ---- PegasusGameBridge.Listener -----------------------------------------

    @Override
    public void onConnectionStateChanged(ConnectionState state) {
        updatePegasusButtonLabel(state);
        // Real-hardware finding: with the screen off, this Samsung device's
        // power management drops the active BLE GATT connection (status=8)
        // almost exactly at the screen-off timeout, and subsequent reconnect
        // attempts then also fail (status=147). Keep the screen on for the
        // whole connect/connected/reconnecting lifecycle; only a clean
        // DISCONNECTED releases it.
        if (state == ConnectionState.DISCONNECTED) {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        if (state == ConnectionState.CONNECTED) {
            // The bridge only replays moves it actually observed (physical
            // moves, guided engine moves); on-screen play while the board
            // was disconnected leaves its own position stale. Push MoveAPiece's
            // authoritative position on every (re)connect so the board can
            // resume physical play correctly - see PegasusGameBridge
            // .syncBoardToPosition for how a mismatch is then resolved.
            pegasusBridge.syncBoardToPosition(game.toFen());
            if (mode == GameMode.TRAINING) {
                // Re-issues the current hint (or resumes book-side play) if a
                // guide was mid-flight when the board disconnected - without
                // this, the LEDs would stay dark until the next move happens
                // to resolve things on its own.
                maybeAdvanceTraining();
            }
            Toast.makeText(this, R.string.pegasus_connected, Toast.LENGTH_SHORT).show();
        } else if (state == ConnectionState.DISCONNECTED) {
            Toast.makeText(this, R.string.pegasus_disconnected, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onPhysicalMoveConfirmed(String uci) {
        applyConfirmedMove(uci, false);
    }

    @Override
    public void onBoardMismatch(boolean mismatched) {
        // The board itself already shows the mismatched squares via LEDs;
        // no additional on-screen indicator in the MVP.
        if (!mismatched
                && mode == GameMode.TRAINING
                && trainingSession != null
                && !trainingSession.isComplete()
                && trainingSession.isHumanTurnNow()) {
            // Retries a guideEngineMove() that silently no-op'd because the
            // physical board wasn't synced yet when maybeAdvanceTraining()
            // first tried it (e.g. "Wiederholen" pressed while the board
            // still showed the previous line's final position - see
            // guideEngineMove()'s own physicalBoard-sync guard). Safe to
            // call unconditionally here: while a guide is active, physical
            // events are routed to it exclusively (feedDetector()'s
            // syncGuide.isActive() branch), so this callback cannot fire at
            // all unless no guide is currently running.
            maybeAdvanceTraining();
        }
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
    public void onEngineMoveGuidanceComplete() {
        if (mode == GameMode.TRAINING && trainingSession != null) {
            if (guidingTrainingHumanMove) {
                // The trainee's own move was only guided (not yet applied) -
                // the physical board just confirmed it was played correctly.
                guidingTrainingHumanMove = false;
                applyUciToGame(trainingSession.currentExpectedUci());
            }
            trainingBookMovePending = false;
            trainingSession.advance();
            refreshBoard();
            maybeAdvanceTraining();
            return;
        }
        // MoveAPiece's own state was already updated when the engine move was
        // applied in applyConfirmedMove(); nothing further to do here.
    }

    @Override
    public void onTransportError(TransportError error, String detail) {
        Toast.makeText(this, getString(R.string.pegasus_error_format, error), Toast.LENGTH_SHORT)
                .show();
    }

    @Override
    public void onBatteryStatus(int percent) {
        Toast.makeText(this, getString(R.string.pegasus_battery_format, percent), Toast.LENGTH_LONG)
                .show();
    }

    // ---- New game setup ----------------------------------------------------

    private void showNewGameDialog() {
        DialogNewGameBinding dialogBinding = DialogNewGameBinding.inflate(getLayoutInflater());
        dialogBinding.strengthLabel.setText(getString(R.string.dialog_strength_format, engineElo));
        dialogBinding.strengthSeekBar.setMax(ELO_MAX - ELO_MIN);
        dialogBinding.strengthSeekBar.setProgress(engineElo - ELO_MIN);
        dialogBinding.strengthSeekBar.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        dialogBinding.strengthLabel.setText(
                                getString(R.string.dialog_strength_format, ELO_MIN + progress));
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {}

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {}
                });

        List<String> openingNames = new ArrayList<>();
        for (OpeningLine line : OpeningRepository.ALL) {
            openingNames.add(OpeningNames.displayName(this, line));
        }
        ArrayAdapter<String> openingAdapter =
                new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, openingNames);
        openingAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        dialogBinding.openingSpinner.setAdapter(openingAdapter);

        dialogBinding.opponentGroup.setOnCheckedChangeListener(
                (group, checkedId) ->
                        updateDialogVisibility(
                                dialogBinding, modeForCheckedId(dialogBinding, checkedId)));
        updateDialogVisibility(
                dialogBinding,
                modeForCheckedId(
                        dialogBinding, dialogBinding.opponentGroup.getCheckedRadioButtonId()));

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_new_game_title)
                .setView(dialogBinding.getRoot())
                .setPositiveButton(
                        R.string.action_ok,
                        (dialog, which) -> {
                            GameMode chosenMode =
                                    modeForCheckedId(
                                            dialogBinding,
                                            dialogBinding.opponentGroup.getCheckedRadioButtonId());
                            Side chosenColor =
                                    dialogBinding.colorWhite.isChecked() ? Side.WHITE : Side.BLACK;
                            int chosenElo = ELO_MIN + dialogBinding.strengthSeekBar.getProgress();
                            // ENGINE: chosenColor is the human's own color, so the engine
                            // plays the opposite side. TRAINING: chosenColor directly
                            // names the side being trained - do not invert it here.
                            Side chosenSide =
                                    chosenMode == GameMode.ENGINE
                                            ? (chosenColor == Side.WHITE ? Side.BLACK : Side.WHITE)
                                            : chosenColor;
                            OpeningLine chosenOpening =
                                    chosenMode == GameMode.TRAINING
                                            ? OpeningRepository.ALL.get(
                                                    dialogBinding.openingSpinner
                                                            .getSelectedItemPosition())
                                            : null;
                            boolean chosenHints = dialogBinding.hintCheckBox.isChecked();
                            startNewGame(
                                    chosenMode, chosenSide, chosenElo, chosenOpening, chosenHints);
                        })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private GameMode modeForCheckedId(DialogNewGameBinding dialogBinding, int checkedId) {
        if (checkedId == dialogBinding.opponentEngine.getId()) {
            return GameMode.ENGINE;
        }
        if (checkedId == dialogBinding.opponentTraining.getId()) {
            return GameMode.TRAINING;
        }
        return GameMode.HUMAN;
    }

    private void updateDialogVisibility(DialogNewGameBinding dialogBinding, GameMode dialogMode) {
        int colorVisibility =
                dialogMode == GameMode.HUMAN ? android.view.View.GONE : android.view.View.VISIBLE;
        int strengthVisibility =
                dialogMode == GameMode.ENGINE ? android.view.View.VISIBLE : android.view.View.GONE;
        int openingVisibility =
                dialogMode == GameMode.TRAINING
                        ? android.view.View.VISIBLE
                        : android.view.View.GONE;
        dialogBinding.colorLabel.setVisibility(colorVisibility);
        dialogBinding.colorGroup.setVisibility(colorVisibility);
        dialogBinding.strengthLabel.setVisibility(strengthVisibility);
        dialogBinding.strengthSeekBar.setVisibility(strengthVisibility);
        dialogBinding.openingLabel.setVisibility(openingVisibility);
        dialogBinding.openingSpinner.setVisibility(openingVisibility);
        dialogBinding.hintCheckBox.setVisibility(openingVisibility);
    }

    private void startNewGame(
            GameMode chosenMode,
            Side chosenSide,
            int chosenElo,
            OpeningLine chosenOpening,
            boolean hintsEnabled) {
        abandonPendingSearches();
        trainingHandler.removeCallbacksAndMessages(null);
        waitingForTrainingAutoMove = false;
        guidingTrainingHumanMove = false;
        trainingBookMovePending = false;

        mode = chosenMode;
        engineSide = chosenMode == GameMode.ENGINE ? chosenSide : engineSide;
        engineElo = chosenElo;
        settings.setEngineElo(engineElo);
        trainingSession =
                mode == GameMode.TRAINING
                        ? new TrainingSession(chosenOpening, chosenSide, hintsEnabled)
                        : null;
        binding.undoButton.setEnabled(true);

        game.reset();
        pegasusBridge.resetForNewGame();
        boolean flip =
                (mode == GameMode.ENGINE && engineSide == Side.WHITE)
                        || (mode == GameMode.TRAINING && chosenSide == Side.BLACK);
        setBoardFlipped(flip);
        setLastMove(null, null);
        binding.boardView.setCheckedKingSquare(null);
        binding.boardView.setTrainingHint(null, null);
        binding.boardView.clearSelection();

        if (engineReady) {
            engine.newGame();
            if (mode == GameMode.ENGINE) {
                engine.setStrength(engineElo);
            } else {
                engine.setFullStrength();
            }
        }

        refreshBoard();
        if (mode == GameMode.ENGINE) {
            maybeTriggerEngineMove();
        } else if (mode == GameMode.TRAINING) {
            maybeAdvanceTraining();
        }
    }

    // ---- Board <-> game glue -------------------------------------------------

    private void refreshBoard() {
        Piece[] pieces = new Piece[64];
        for (int i = 0; i < 64; i++) {
            pieces[i] = game.pieceAt(Square.squareAt(i));
        }
        binding.boardView.setBoard(pieces);
        binding.boardView.setInteractive(isBoardInteractiveNow());
        binding.boardView.setCheckedKingSquare(findCheckedKingSquare());
        updateStatusText();
        updateMoveHistory();
        updateTrainingProgressText();
        updateEngineStrengthText();
        updateTrainingHintHighlight();
        maybeTriggerAnalysis();
        updatePgnButtonsVisibility();
        updateHintButtonState();
    }

    private void updatePgnButtonsVisibility() {
        int visibility =
                mode == GameMode.TRAINING ? android.view.View.GONE : android.view.View.VISIBLE;
        binding.exportPgnButton.setVisibility(visibility);
        binding.importPgnButton.setVisibility(visibility);
        binding.exportPgnButton.setEnabled(game.moveCount() > 0);
        binding.analyzeGameButton.setVisibility(visibility);
        updateAnalyzeGameButtonState();
    }

    private boolean isBoardInteractiveNow() {
        if (game.isGameOver() || waitingForEngineMove || waitingForTrainingAutoMove) {
            return false;
        }
        switch (mode) {
            case ENGINE:
                return game.sideToMove() != engineSide;
            case TRAINING:
                return trainingSession != null
                        && !trainingSession.isComplete()
                        && trainingSession.isHumanTurnNow();
            default:
                return true;
        }
    }

    private void updateTrainingProgressText() {
        TextView progress = binding.trainingProgressText;
        if (mode != GameMode.TRAINING || trainingSession == null) {
            progress.setVisibility(android.view.View.GONE);
            return;
        }
        progress.setVisibility(android.view.View.VISIBLE);
        progress.setText(
                getString(
                        R.string.status_training_progress_format,
                        OpeningNames.displayName(this, trainingSession.line()),
                        Math.min(trainingSession.plyIndex() + 1, trainingSession.totalPlies()),
                        trainingSession.totalPlies()));
    }

    private void updateEngineStrengthText() {
        TextView strength = binding.engineStrengthText;
        if (mode != GameMode.ENGINE) {
            strength.setVisibility(android.view.View.GONE);
            return;
        }
        strength.setVisibility(android.view.View.VISIBLE);
        strength.setText(getString(R.string.status_engine_strength_format, engineElo));
    }

    /**
     * On-screen equivalent of the physical board's LED guidance: highlights the trainee's own next
     * expected move, so playing without a Pegasus board (or the "quiz" mode with hints off) stays
     * consistent either way.
     */
    private void updateTrainingHintHighlight() {
        if (mode != GameMode.TRAINING
                || trainingSession == null
                || trainingSession.isComplete()
                || !trainingSession.isHumanTurnNow()
                || !trainingSession.hintsEnabled()) {
            binding.boardView.setTrainingHint(null, null);
            binding.hintAlternativesText.setVisibility(android.view.View.GONE);
            return;
        }
        String uci = trainingSession.currentExpectedUci();
        Square from = Square.valueOf(uci.substring(0, 2).toUpperCase(Locale.ROOT));
        Square to = Square.valueOf(uci.substring(2, 4).toUpperCase(Locale.ROOT));
        binding.boardView.setTrainingHint(from, to);
    }

    private void updateMoveHistory() {
        binding.moveListText.setText(game.toSan());
    }

    private Square findCheckedKingSquare() {
        if (!game.isCheck()) {
            return null;
        }
        Piece king = game.sideToMove() == Side.WHITE ? Piece.WHITE_KING : Piece.BLACK_KING;
        for (int i = 0; i < 64; i++) {
            Square sq = Square.squareAt(i);
            if (game.pieceAt(sq) == king) {
                return sq;
            }
        }
        return null;
    }

    private void updateStatusText() {
        TextView status = binding.statusText;
        if (game.isCheckmate()) {
            boolean whiteWon = game.sideToMove() == Side.BLACK;
            status.setText(
                    whiteWon ? R.string.status_checkmate_white : R.string.status_checkmate_black);
        } else if (game.isStalemate()) {
            status.setText(R.string.status_stalemate);
        } else if (game.isDraw()) {
            status.setText(R.string.status_draw);
        } else if (waitingForEngineMove) {
            status.setText(R.string.status_engine_thinking);
        } else if (waitingForTrainingAutoMove) {
            status.setText(R.string.status_training_auto_move);
        } else if (game.isCheck()) {
            status.setText(R.string.status_check);
        } else {
            status.setText(
                    game.sideToMove() == Side.WHITE
                            ? R.string.status_white_to_move
                            : R.string.status_black_to_move);
        }
    }

    private void undo() {
        if (waitingForEngineMove) {
            return;
        }
        if (mode == GameMode.TRAINING) {
            undoTrainingMove();
            return;
        }
        abandonPendingSearches();
        game.undoLastMove();
        if (mode == GameMode.ENGINE && game.moveCount() > 0 && game.sideToMove() == engineSide) {
            game.undoLastMove();
        }
        setLastMove(null, null);
        refreshBoard();
        syncPegasusPosition();
    }

    /**
     * Steps the training line back to the trainee's own previous move so they can retry it -
     * mirroring ENGINE-mode undo, which likewise always lands back on the human's turn rather than
     * the opponent's. Rolls back an unconfirmed optimistic book-move apply first (see {@link
     * #trainingBookMovePending}) so {@link #game} and {@link #trainingSession} never disagree, then
     * resyncs the physical board - any active guide is cancelled there too, and mismatch LEDs light
     * up until the pieces are moved back; once they match, the existing {@link #onBoardMismatch}
     * handler re-issues the hint for the retried move on its own.
     */
    private void undoTrainingMove() {
        if (trainingSession == null
                || (!trainingBookMovePending && trainingSession.plyIndex() == 0)) {
            return;
        }
        trainingHandler.removeCallbacksAndMessages(null);
        waitingForTrainingAutoMove = false;
        guidingTrainingHumanMove = false;
        if (trainingBookMovePending) {
            game.undoLastMove();
            trainingBookMovePending = false;
        }
        if (trainingSession.plyIndex() > 0) {
            game.undoLastMove();
            trainingSession.retreat();
            if (trainingSession.plyIndex() > 0 && !trainingSession.isHumanTurnNow()) {
                game.undoLastMove();
                trainingSession.retreat();
            }
        }
        setLastMove(null, null);
        refreshBoard();
        syncPegasusPosition();
    }

    /**
     * Keeps the Pegasus bridge's own position tracking current whenever the game state changes
     * without it observing the move itself (tap-to-move, undo) — see {@link
     * PegasusGameBridge#syncBoardToPosition} for why this is needed: without it, the bridge only
     * desyncs visibly on the next reconnect instead of immediately, which is confusing while the
     * board is live-connected.
     */
    private void syncPegasusPosition() {
        if (pegasusBridge.getConnectionState() == ConnectionState.CONNECTED) {
            pegasusBridge.syncBoardToPosition(game.toFen());
        }
    }

    // ---- PGN import/export ---------------------------------------------------

    private void exportGamePgn() {
        String white;
        String black;
        if (mode == GameMode.ENGINE) {
            String engineName = getString(R.string.opponent_engine) + " (Elo " + engineElo + ")";
            if (engineSide == Side.WHITE) {
                white = engineName;
                black = getString(R.string.color_black);
            } else {
                white = getString(R.string.color_white);
                black = engineName;
            }
        } else {
            white = getString(R.string.color_white);
            black = getString(R.string.color_black);
        }
        String pgn = game.toPgn(white, black);
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, pgn);
        intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name) + " – PGN");
        startActivity(Intent.createChooser(intent, getString(R.string.action_export_pgn)));
    }

    /**
     * Reading the picked URI can block on network I/O for cloud-backed providers (e.g. WebDAV), and
     * {@link ChessGame#loadPgn} itself can take a while for a pathologically large single game -
     * both would otherwise freeze the UI thread, so both run off it, with a progress dialog so the
     * app doesn't look hung meanwhile. {@link #abandonPendingSearches()} runs first (on the UI
     * thread) so a stray engine reply can't mutate {@link #game} concurrently with the background
     * thread's own mutation of it via {@code loadPgn}.
     *
     * <p>A multi-game file (tournament/opening database) is offered as a pick list instead of
     * silently importing whatever chesslib happens to parse first - see {@link
     * PgnGames#splitGames}, a cheap header-only scan that doesn't pay chesslib's full per-game
     * parse cost just to build the list.
     */
    private void onPgnFileSelected(Uri uri) {
        if (uri == null) {
            return;
        }
        abandonPendingSearches();
        AlertDialog progress =
                new MaterialAlertDialogBuilder(this)
                        .setView(R.layout.dialog_pgn_import_progress)
                        .setCancelable(false)
                        .show();
        new Thread(
                        () -> {
                            String text;
                            try (InputStream in = getContentResolver().openInputStream(uri)) {
                                if (in == null) {
                                    throw new IOException("openInputStream returned null");
                                }
                                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                                byte[] chunk = new byte[8192];
                                int n;
                                while ((n = in.read(chunk)) != -1) {
                                    buffer.write(chunk, 0, n);
                                }
                                text = new String(buffer.toByteArray(), StandardCharsets.UTF_8);
                            } catch (IOException e) {
                                runOnUiThread(
                                        () -> {
                                            progress.dismiss();
                                            Toast.makeText(
                                                            this,
                                                            R.string.pgn_import_failed,
                                                            Toast.LENGTH_SHORT)
                                                    .show();
                                        });
                                return;
                            }
                            List<String> games = PgnGames.splitGames(text);
                            if (games.size() <= 1) {
                                boolean loaded = game.loadPgn(text);
                                runOnUiThread(
                                        () -> {
                                            progress.dismiss();
                                            if (!loaded) {
                                                Toast.makeText(
                                                                this,
                                                                R.string.pgn_import_failed,
                                                                Toast.LENGTH_SHORT)
                                                        .show();
                                                return;
                                            }
                                            finishPgnImport();
                                        });
                            } else {
                                runOnUiThread(
                                        () -> {
                                            progress.dismiss();
                                            showPgnGameSelectionDialog(games);
                                        });
                            }
                        },
                        "pgn-import")
                .start();
    }

    private void showPgnGameSelectionDialog(List<String> games) {
        String[] labels = new String[games.size()];
        for (int i = 0; i < games.size(); i++) {
            labels[i] = PgnGames.summarize(games.get(i));
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.pgn_select_game_title)
                .setItems(
                        labels,
                        (dialog, which) -> {
                            if (!game.loadPgn(games.get(which))) {
                                Toast.makeText(this, R.string.pgn_import_failed, Toast.LENGTH_SHORT)
                                        .show();
                                return;
                            }
                            finishPgnImport();
                        })
                .show();
    }

    private void finishPgnImport() {
        trainingHandler.removeCallbacksAndMessages(null);
        waitingForTrainingAutoMove = false;
        guidingTrainingHumanMove = false;
        trainingBookMovePending = false;
        mode = GameMode.HUMAN;
        trainingSession = null;
        binding.undoButton.setEnabled(true);
        setBoardFlipped(false);
        setLastMove(null, null);
        binding.boardView.setCheckedKingSquare(null);
        binding.boardView.setTrainingHint(null, null);
        binding.boardView.clearSelection();
        pegasusBridge.resetForNewGame();
        if (engineReady) {
            engine.newGame();
            engine.setFullStrength();
        }
        refreshBoard();
        syncPegasusPosition();
    }

    private void maybeTriggerEngineMove() {
        if (mode != GameMode.ENGINE
                || game.isGameOver()
                || game.sideToMove() != engineSide
                || !engineReady) {
            return;
        }
        waitingForEngineMove = true;
        binding.boardView.setInteractive(false);
        updateStatusText();
        startEngineSearch(true, ENGINE_MOVETIME_MS);
    }

    /**
     * Stops whatever the engine is currently doing, queues the purpose of the search being started
     * (see {@link PendingSearch}) and kicks it off from the current position. Shared by the
     * real-move and analysis triggers so neither can silently run into the other's still-active
     * "go".
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
     * (reusing {@link BoardView#setTrainingHint}) without applying it - the human decides whether to
     * play it. Always searches at full strength, regardless of {@link #engineElo}, then restores
     * that strength for whatever search comes next (see {@link #onBestMove}), so a low-Elo opponent
     * doesn't leak into the hint.
     */
    private void requestHint() {
        if (!engineReady
                || mode == GameMode.TRAINING
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
        Square from = Square.valueOf(uci.substring(0, 2).toUpperCase(Locale.ROOT));
        Square to = Square.valueOf(uci.substring(2, 4).toUpperCase(Locale.ROOT));
        binding.boardView.setTrainingHint(from, to);
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
            alternatives.add(
                    getString(
                            R.string.hint_alternative_format, squares, multiPvCpByRank[rank] / 100.0));
        }
        if (alternatives.isEmpty()) {
            binding.hintAlternativesText.setVisibility(android.view.View.GONE);
            return;
        }
        binding.hintAlternativesText.setText(
                getString(R.string.hint_alternatives_label, String.join(", ", alternatives)));
        binding.hintAlternativesText.setVisibility(android.view.View.VISIBLE);
    }

    /**
     * Records one MultiPV line's move+score, indexed by its 1-based {@code multipv} rank (see {@link
     * UciInfoParser#parseMultiPv}) - only called while {@link #multiPvSearchActive}. Later lines for
     * the same rank (deeper iterations) simply overwrite earlier ones, so what's left once the search
     * ends is the converged answer.
     */
    private void captureMultiPvCandidate(String infoLine) {
        OptionalInt multiPv = UciInfoParser.parseMultiPv(infoLine);
        if (multiPv.isEmpty() || multiPv.getAsInt() < 1 || multiPv.getAsInt() > HINT_MULTI_PV_LINES) {
            return;
        }
        Optional<String> pvMove = UciInfoParser.parsePvFirstMove(infoLine);
        if (pvMove.isEmpty()) {
            return;
        }
        OptionalInt mate = UciInfoParser.parseScoreMate(infoLine);
        OptionalInt cp = mate.isPresent() ? OptionalInt.empty() : UciInfoParser.parseScoreCp(infoLine);
        if (mate.isEmpty() && cp.isEmpty()) {
            return;
        }
        int rank = multiPv.getAsInt() - 1;
        multiPvMoveByRank[rank] = pvMove.get();
        multiPvCpByRank[rank] = mate.isPresent() ? mateToCp(mate.getAsInt()) : cp.getAsInt();
    }

    private void updateHintButtonState() {
        int visibility =
                mode == GameMode.TRAINING ? android.view.View.GONE : android.view.View.VISIBLE;
        binding.hintButton.setVisibility(visibility);
        binding.hintButton.setEnabled(
                engineReady && !waitingForHint && postGameUciMoves == null && isBoardInteractiveNow());
    }

    /** Converts a "mate in N" score to a centipawn-scale value that still dominates normal evals. */
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
     * comes in. Called right before any move is committed to {@link #game} (human tap or engine/
     * physical-board move); silently skips grading this move (baseline left at -1) in training mode
     * or when no eval is known for exactly the current position - e.g. right after toggling
     * evaluation display back on, or the game's very first ply before any search has finished.
     */
    private void recordMoveQualityBaseline() {
        if (mode == GameMode.TRAINING || lastPositionEvalMoveCount != game.moveCount()) {
            moveQualityBaselineMoveCount = -1;
            return;
        }
        moveQualityBaselineCp = lastPositionEvalCp;
        moveQualityBaselineMoveCount = game.moveCount();
        binding.moveQualityText.setVisibility(android.view.View.GONE);
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

    /** Move-quality string resource for a given centipawn loss, or 0 if not notable enough to flag. */
    private static int moveQualityLabelRes(int cpLoss) {
        if (cpLoss >= BLUNDER_CP_LOSS) {
            return R.string.move_quality_blunder;
        }
        if (cpLoss >= MISTAKE_CP_LOSS) {
            return R.string.move_quality_mistake;
        }
        if (cpLoss >= INACCURACY_CP_LOSS) {
            return R.string.move_quality_inaccuracy;
        }
        return 0;
    }

    private void showMoveQualityIfNotable(int cpLoss) {
        int labelRes = moveQualityLabelRes(cpLoss);
        if (labelRes == 0) {
            binding.moveQualityText.setVisibility(android.view.View.GONE);
            return;
        }
        binding.moveQualityText.setText(
                getString(R.string.move_quality_format, getString(labelRes), -cpLoss / 100.0));
        binding.moveQualityText.setVisibility(android.view.View.VISIBLE);
    }

    /**
     * Replays the finished game from the start, one ply at a time, grading every move the same way
     * live blunder-check does ({@link #moveQualityLabelRes}) and showing a summary dialog once done.
     * Always searches at full strength, restored afterwards in {@link #advancePostGameAnalysis}.
     * Requires {@link ChessGame#isGameOver()} rather than just some moves played - unlike every other
     * search this method starts, it doesn't call {@link StockfishEngine#newGame()} (which would send
     * "isready" and re-enter {@link #onReadyOk}, undoing the full-strength setting and, if it were
     * still someone's turn in the live game, firing off an unwanted real move), so nothing may still
     * be live for it to accidentally interfere with.
     */
    private void startPostGameAnalysis() {
        if (!engineReady
                || mode == GameMode.TRAINING
                || !game.isGameOver()
                || postGameUciMoves != null) {
            return;
        }
        abandonPendingSearches();
        postGameUciMoves =
                java.util.Arrays.asList(game.toUciMoveList().split(" "));
        postGamePositionEvals = new ArrayList<>(postGameUciMoves.size() + 1);
        updateHintButtonState();
        updateAnalyzeGameButtonState();
        engine.setFullStrength();
        requestPostGameEvalFor(0);
    }

    /**
     * The final replayed position is the live game's own current one - if it's checkmate/stalemate,
     * it has no legal moves for Stockfish to search (in practice: no "info score" line ever arrives,
     * silently leaving {@link #postGameLiveScoreCp} stuck on the previous position's stale value, which
     * would badly mis-grade the final move). Score it directly from the game's own verdict instead:
     * very bad for whoever's mated, neutral for a stalemate.
     */
    private void requestPostGameEvalFor(int positionIndex) {
        if (positionIndex == postGameUciMoves.size() && (game.isCheckmate() || game.isStalemate())) {
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
        if (mode == GameMode.ENGINE) {
            engine.setStrength(engineElo);
        }
        updateHintButtonState();
        updateAnalyzeGameButtonState();
        showPostGameReport(uciMoves, evals);
    }

    /**
     * The icon-only Analyze button (see {@code secondaryButtonBar}) has no visible label to carry
     * progress the way the old text button did - {@link #binding}.analysisProgressText fills that
     * role instead, shown only while an analysis is actually running.
     */
    private void updateAnalyzeGameButtonState() {
        boolean running = postGameUciMoves != null;
        binding.analyzeGameButton.setEnabled(
                !running && engineReady && mode != GameMode.TRAINING && game.isGameOver());
        if (running) {
            binding.analysisProgressText.setText(
                    getString(
                            R.string.action_analyzing_format,
                            postGamePositionEvals.size(),
                            postGameUciMoves.size() + 1));
            binding.analysisProgressText.setVisibility(android.view.View.VISIBLE);
        } else {
            binding.analysisProgressText.setVisibility(android.view.View.GONE);
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
            int labelRes = moveQualityLabelRes(cpLoss);
            if (labelRes == R.string.move_quality_blunder) {
                blunders[side]++;
            } else if (labelRes == R.string.move_quality_mistake) {
                mistakes[side]++;
            } else if (labelRes == R.string.move_quality_inaccuracy) {
                inaccuracies[side]++;
            } else {
                continue;
            }
            String san = ply < sanMoves.size() ? sanMoves.get(ply) : uciMoves.get(ply);
            if (flaggedMoves.length() > 0) {
                flaggedMoves.append('\n');
            }
            flaggedMoves.append(
                    getString(
                            R.string.analysis_flagged_move_format,
                            plyLabel(ply),
                            san,
                            getString(labelRes),
                            -cpLoss / 100.0));
        }
        StringBuilder report = new StringBuilder();
        int[] sideColorRes = {R.string.color_white, R.string.color_black};
        for (int side = 0; side < 2; side++) {
            if (side > 0) {
                report.append('\n');
            }
            report.append(
                    getString(
                            R.string.analysis_side_summary_format,
                            getString(sideColorRes[side]),
                            plies[side] == 0 ? 0.0 : lossSum[side] / plies[side] / 100.0,
                            inaccuracies[side],
                            mistakes[side],
                            blunders[side]));
        }
        report.append("\n\n");
        report.append(
                flaggedMoves.length() > 0
                        ? getString(R.string.analysis_flagged_moves_header) + "\n" + flaggedMoves
                        : getString(R.string.analysis_no_flagged_moves));
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.analysis_dialog_title)
                .setMessage(report.toString())
                .setPositiveButton(R.string.action_ok, null)
                .show();
    }

    /**
     * Cancels any outstanding engine search and marks its eventual reply (and any other
     * already-queued one) as belonging to a position we've since left - used wherever the game is
     * reset out from under a possibly in-flight search (new game, PGN import).
     */
    private void abandonPendingSearches() {
        engine.stop();
        searchGeneration++;
        waitingForEngineMove = false;
        waitingForHint = false;
        if (multiPvSearchActive) {
            // A hint search was interrupted mid-flight (new game/undo/PGN import/post-game analysis
            // starting) - restore MultiPV so the next (unrelated) search's info lines aren't
            // misrouted to captureMultiPvCandidate() forever.
            multiPvSearchActive = false;
            engine.setMultiPv(1);
        }
        lastPositionEvalMoveCount = -1;
        moveQualityBaselineMoveCount = -1;
        postGameUciMoves = null;
        postGamePositionEvals = null;
        binding.moveQualityText.setVisibility(android.view.View.GONE);
        updateAnalyzeGameButtonState();
    }

    /**
     * Starts a dedicated evaluation search when nothing else is already searching the current
     * position. If the engine is about to search for its own reply anyway ({@link
     * #maybeTriggerEngineMove}), that search's "info" stream already covers the evaluation display,
     * so a second, redundant search is skipped.
     */
    private void maybeTriggerAnalysis() {
        int evalVisibility =
                mode == GameMode.TRAINING ? android.view.View.GONE : android.view.View.VISIBLE;
        binding.evaluationSwitch.setVisibility(evalVisibility);
        binding.evaluationText.setVisibility(evalVisibility);
        if (!evaluationEnabled || mode == GameMode.TRAINING || game.isGameOver()) {
            binding.evaluationText.setText("");
            return;
        }
        if (!engineReady) {
            return;
        }
        boolean engineAboutToSearchAnyway =
                mode == GameMode.ENGINE && game.sideToMove() == engineSide;
        if (engineAboutToSearchAnyway) {
            return;
        }
        startEngineSearch(false, ANALYSIS_MOVETIME_MS);
    }

    // ---- Training mode ------------------------------------------------------

    /** Applies a training move confirmed by disconnected auto-play or an on-screen tap. */
    private void applyTrainingMove(String uci) {
        if (!applyUciToGame(uci)) {
            refreshBoard();
            return;
        }
        trainingSession.advance();
        refreshBoard();
        syncPegasusPosition();
        maybeAdvanceTraining();
    }

    /**
     * Drives the training line forward: shows a hint for the trainee's own next move (applied only
     * once the physical board confirms it), or plays out the book side's move immediately - guided
     * physically if connected, after a short delay otherwise so it doesn't feel instantaneous.
     */
    private void maybeAdvanceTraining() {
        if (mode != GameMode.TRAINING || trainingSession == null || game.isGameOver()) {
            return;
        }
        if (trainingSession.isComplete()) {
            showTrainingCompleteDialog();
            return;
        }
        boolean connected = pegasusBridge.getConnectionState() == ConnectionState.CONNECTED;
        if (trainingSession.isHumanTurnNow()) {
            if (connected) {
                guidingTrainingHumanMove = true;
                pegasusBridge.guideEngineMove(
                        trainingSession.currentExpectedUci(), trainingSession.hintsEnabled());
            }
            // Disconnected: the board is already interactive and waits for a matching tap.
            refreshBoard();
        } else if (connected) {
            String uci = trainingSession.currentExpectedUci();
            applyUciToGame(uci);
            refreshBoard();
            trainingBookMovePending = true;
            pegasusBridge.guideEngineMove(uci);
            // trainingSession.advance() happens in onEngineMoveGuidanceComplete, once physically
            // confirmed.
        } else {
            waitingForTrainingAutoMove = true;
            refreshBoard();
            trainingHandler.postDelayed(
                    () -> {
                        waitingForTrainingAutoMove = false;
                        applyTrainingMove(trainingSession.currentExpectedUci());
                    },
                    TRAINING_AUTO_MOVE_DELAY_MS);
        }
    }

    private void showTrainingCompleteDialog() {
        OpeningLine line = trainingSession.line();
        Side side = trainingSession.humanSide();
        boolean hintsEnabled = trainingSession.hintsEnabled();
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_training_complete_title)
                .setMessage(
                        getString(
                                R.string.dialog_training_complete_message_format,
                                OpeningNames.displayName(this, line)))
                .setPositiveButton(
                        R.string.action_repeat,
                        (d, w) ->
                                startNewGame(
                                        GameMode.TRAINING, side, engineElo, line, hintsEnabled))
                .setNegativeButton(R.string.action_pick_opening, (d, w) -> showNewGameDialog())
                .setNeutralButton(
                        R.string.action_continue_free_play,
                        (d, w) -> showContinueFreePlayDialog(side))
                .setCancelable(false)
                .show();
    }

    /**
     * Asks whether to continue past a just-completed training line against a second human or
     * Stockfish, without resetting {@link #game} or the Pegasus bridge - the whole point is picking
     * up play from the position the opening line ended at, not starting over.
     */
    private void showContinueFreePlayDialog(Side trainedSide) {
        DialogContinueFreePlayBinding dialogBinding =
                DialogContinueFreePlayBinding.inflate(getLayoutInflater());
        dialogBinding.strengthLabel.setText(getString(R.string.dialog_strength_format, engineElo));
        dialogBinding.strengthSeekBar.setMax(ELO_MAX - ELO_MIN);
        dialogBinding.strengthSeekBar.setProgress(engineElo - ELO_MIN);
        dialogBinding.strengthSeekBar.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        dialogBinding.strengthLabel.setText(
                                getString(R.string.dialog_strength_format, ELO_MIN + progress));
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {}

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {}
                });
        dialogBinding.opponentGroup.setOnCheckedChangeListener(
                (group, checkedId) -> {
                    int visibility =
                            checkedId == dialogBinding.opponentEngine.getId()
                                    ? android.view.View.VISIBLE
                                    : android.view.View.GONE;
                    dialogBinding.strengthLabel.setVisibility(visibility);
                    dialogBinding.strengthSeekBar.setVisibility(visibility);
                });

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.action_continue_free_play)
                .setView(dialogBinding.getRoot())
                .setPositiveButton(
                        R.string.action_ok,
                        (dialog, which) -> {
                            boolean vsEngine = dialogBinding.opponentEngine.isChecked();
                            trainingSession = null;
                            if (vsEngine) {
                                mode = GameMode.ENGINE;
                                engineSide = trainedSide == Side.WHITE ? Side.BLACK : Side.WHITE;
                                engineElo = ELO_MIN + dialogBinding.strengthSeekBar.getProgress();
                                settings.setEngineElo(engineElo);
                                if (engineReady) {
                                    engine.newGame();
                                    engine.setStrength(engineElo);
                                }
                            } else {
                                mode = GameMode.HUMAN;
                            }
                            binding.undoButton.setEnabled(true);
                            refreshBoard();
                            if (mode == GameMode.ENGINE) {
                                maybeTriggerEngineMove();
                            }
                        })
                // Change of mind: back to the completion dialog rather than
                // leaving the board stuck on an already-finished session.
                .setNegativeButton(
                        R.string.action_cancel, (dialog, which) -> showTrainingCompleteDialog())
                .setCancelable(false)
                .show();
    }

    // ---- BoardView.MoveSource -------------------------------------------------

    @Override
    public List<Square> legalDestinationsFrom(Square from) {
        return game.legalDestinationsFrom(from);
    }

    @Override
    public boolean hasOwnPieceOn(Square square) {
        Piece piece = game.pieceAt(square);
        if (piece == Piece.NONE) {
            return false;
        }
        return piece.getPieceSide() == game.sideToMove();
    }

    // ---- BoardView.OnMoveListener ----------------------------------------------

    @Override
    public void onMoveChosen(Square from, Square to) {
        if (mode == GameMode.TRAINING) {
            onTrainingMoveChosen(from, to);
            return;
        }
        if (game.isPromotion(from, to)) {
            showPromotionDialog(from, to);
        } else {
            applyHumanMove(from, to, null);
        }
    }

    /**
     * On-screen fallback for training mode (no physical board needed): only accepts a tap that
     * matches the line's expected move for the current ply, silently ignoring anything else - no
     * popup for a wrong attempt, matching the guided-physical-move path's "just keep waiting"
     * behavior (see commit 0e4b0ea removing an unreliable move-confirmation Snackbar). None of the
     * curated lines reach a promotion within their depth, so a plain 4-character UCI comparison is
     * enough.
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

    private void showPromotionDialog(Square from, Square to) {
        Side side = game.sideToMove();
        PieceType[] types = {PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT};
        showPromotionPickerDialog(
                side, which -> applyHumanMove(from, to, Piece.make(side, types[which])));
    }

    /**
     * Physical-board promotion: the board can only report that a pawn reached the back rank
     * (occupancy-only, never piece identity), so the choice always comes from here - resolved via
     * {@link PegasusGameBridge#selectPromotion}, not {@link #applyHumanMove}.
     */
    private void showPhysicalPromotionDialog() {
        Side side = game.sideToMove();
        de.schliweb.pegasus.core.chess.PieceType[] types = {
            de.schliweb.pegasus.core.chess.PieceType.QUEEN,
            de.schliweb.pegasus.core.chess.PieceType.ROOK,
            de.schliweb.pegasus.core.chess.PieceType.BISHOP,
            de.schliweb.pegasus.core.chess.PieceType.KNIGHT,
        };
        showPromotionPickerDialog(side, which -> pegasusBridge.selectPromotion(types[which]));
    }

    /**
     * Shared promotion picker UI: shows the four promotion pieces as images
     * (queen/rook/bishop/knight, in that order) in the color of the promoting side, rather than a
     * plain text list - both promotion flows only differ in what happens once a piece is picked.
     */
    private void showPromotionPickerDialog(Side side, java.util.function.IntConsumer onSelected) {
        int[] whiteDrawables = {
            R.drawable.piece_wq, R.drawable.piece_wr, R.drawable.piece_wb, R.drawable.piece_wn
        };
        int[] blackDrawables = {
            R.drawable.piece_bq, R.drawable.piece_br, R.drawable.piece_bb, R.drawable.piece_bn
        };
        int[] drawables = side == Side.WHITE ? whiteDrawables : blackDrawables;

        DialogPromotionBinding dialogBinding = DialogPromotionBinding.inflate(getLayoutInflater());
        dialogBinding.promotionQueen.setImageResource(drawables[0]);
        dialogBinding.promotionRook.setImageResource(drawables[1]);
        dialogBinding.promotionBishop.setImageResource(drawables[2]);
        dialogBinding.promotionKnight.setImageResource(drawables[3]);

        AlertDialog dialog =
                new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.promotion_title)
                        .setView(dialogBinding.getRoot())
                        .setCancelable(false)
                        .show();

        View.OnClickListener pick =
                v -> {
                    int which;
                    if (v == dialogBinding.promotionQueen) {
                        which = 0;
                    } else if (v == dialogBinding.promotionRook) {
                        which = 1;
                    } else if (v == dialogBinding.promotionBishop) {
                        which = 2;
                    } else {
                        which = 3;
                    }
                    dialog.dismiss();
                    onSelected.accept(which);
                };
        dialogBinding.promotionQueen.setOnClickListener(pick);
        dialogBinding.promotionRook.setOnClickListener(pick);
        dialogBinding.promotionBishop.setOnClickListener(pick);
        dialogBinding.promotionKnight.setOnClickListener(pick);
    }

    /**
     * Physical occupancy matched several legal moves that aren't a pure promotion choice —
     * structurally near-unreachable with occupancy-only sensing, but handled defensively rather
     * than silently stalling (see PegasusGameBridge.dispatchDetectionResult). Candidates are shown
     * as plain UCI strings; there's no richer identifying detail available.
     */
    private void showAmbiguousMoveDialog(List<String> candidateUcis) {
        String[] items = candidateUcis.toArray(new String[0]);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.pegasus_ambiguous_title)
                .setItems(items, (dialog, which) -> pegasusBridge.selectCandidate(items[which]))
                .setCancelable(false)
                .show();
    }

    private void applyHumanMove(Square from, Square to, Piece promotion) {
        boolean wasCapture = game.pieceAt(to) != Piece.NONE;
        recordMoveQualityBaseline();
        if (!game.applyMove(from, to, promotion)) {
            refreshBoard();
            return;
        }
        setLastMove(from, to);
        playMoveSound(wasCapture);
        refreshBoard();
        syncPegasusPosition();
        maybeTriggerEngineMove();
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

    // ---- EngineListener ---------------------------------------------------------

    @Override
    public void onUciOk() {
        if (nnuePaths != null) {
            engine.setEvalFiles(nnuePaths.bigNetPath, nnuePaths.smallNetPath);
        }
        engine.newGame();
    }

    @Override
    public void onReadyOk() {
        engineReady = true;
        if (mode == GameMode.ENGINE) {
            engine.setStrength(engineElo);
        }
        maybeTriggerEngineMove();
        maybeTriggerAnalysis();
        updateHintButtonState();
        updateAnalyzeGameButtonState();
    }

    @Override
    public void onBestMove(String bestMoveUci, String ponderUci) {
        PendingSearch search = pendingSearches.poll();
        if (search == null || search.generation != searchGeneration) {
            // Either unexpected (defensive only - every go() we send queues
            // an entry), or a reply for a search abandoned by
            // abandonPendingSearches() (new game, PGN import); discard it.
            return;
        }
        maybeFinalizeMoveQuality();
        if (search.purpose == SearchPurpose.ANALYSIS) {
            // Analysis-only search; onInfo() already streamed eval updates for it.
            return;
        }
        if (search.purpose == SearchPurpose.HINT) {
            waitingForHint = false;
            multiPvSearchActive = false;
            engine.setMultiPv(1);
            if (mode == GameMode.ENGINE) {
                engine.setStrength(engineElo);
            }
            updateHintButtonState();
            if (bestMoveUci != null && !bestMoveUci.equals("(none)")) {
                showHint(bestMoveUci);
            }
            showHintAlternatives();
            return;
        }
        if (search.purpose == SearchPurpose.POST_GAME) {
            advancePostGameAnalysis();
            return;
        }
        waitingForEngineMove = false;
        if (bestMoveUci == null || bestMoveUci.equals("(none)")) {
            refreshBoard();
            return;
        }
        applyConfirmedMove(bestMoveUci, true);
    }

    @Override
    public void onInfo(String infoLine) {
        if (multiPvSearchActive) {
            captureMultiPvCandidate(infoLine);
            return;
        }
        OptionalInt mate = UciInfoParser.parseScoreMate(infoLine);
        OptionalInt cp = mate.isPresent() ? OptionalInt.empty() : UciInfoParser.parseScoreCp(infoLine);
        if (mate.isEmpty() && cp.isEmpty()) {
            return;
        }
        int rawCp = mate.isPresent() ? mateToCp(mate.getAsInt()) : cp.getAsInt();
        if (postGameUciMoves != null) {
            // Post-game analysis replays past positions - never the live eval/move-quality state.
            postGameLiveScoreCp = rawCp;
            return;
        }
        if (!evaluationEnabled || mode == GameMode.TRAINING || analysisSideToMove == null) {
            return;
        }
        recordPositionEval(rawCp);
        if (mate.isPresent()) {
            int whiteRelativeMate =
                    analysisSideToMove == Side.BLACK ? -mate.getAsInt() : mate.getAsInt();
            String side =
                    getString(whiteRelativeMate >= 0 ? R.string.color_white : R.string.color_black);
            binding.evaluationText.setText(
                    getString(R.string.evaluation_mate_format, Math.abs(whiteRelativeMate), side));
            return;
        }
        if (cp.isPresent()) {
            int whiteRelativeCp = analysisSideToMove == Side.BLACK ? -cp.getAsInt() : cp.getAsInt();
            binding.evaluationText.setText(
                    String.format(Locale.ROOT, "%+.1f", whiteRelativeCp / 100.0));
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
        binding.statusText.setText(R.string.status_engine_unavailable);
    }

    @Override
    protected void onDestroy() {
        trainingHandler.removeCallbacksAndMessages(null);
        if (engine != null) {
            engine.shutdown();
        }
        if (soundPlayer != null) {
            soundPlayer.release();
        }
        if (pegasusBridge != null) {
            pegasusBridge.shutdown();
        }
        super.onDestroy();
    }
}
