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
import de.schliweb.moveapiece.training.OpeningLine;
import de.schliweb.moveapiece.training.OpeningRepository;
import de.schliweb.moveapiece.training.TrainingSession;
import de.schliweb.moveapiece.ui.BoardView;
import de.schliweb.moveapiece.ui.MoveSoundPlayer;
import de.schliweb.moveapiece.ui.OpeningLibraryActivity;
import de.schliweb.moveapiece.ui.OpeningNames;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;

public class MainActivity extends AppCompatActivity
        implements BoardView.MoveSource, BoardView.OnMoveListener, EngineListener {

    private static final int ELO_MIN = 1320;
    private static final int ELO_MAX = 3190;
    private static final int ENGINE_MOVETIME_MS = 1200;
    private static final int ANALYSIS_MOVETIME_MS = 1500;

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
    private ActivityResultLauncher<String> pgnImportLauncher;

    private GameMode mode = GameMode.ENGINE;
    private Side engineSide = Side.BLACK;
    private int engineElo = 1500;
    private boolean engineReady = false;
    private boolean waitingForEngineMove = false;
    private volatile NnueAssets.Paths nnuePaths;

    // ---- Evaluation display state --------------------------------------------
    private boolean evaluationEnabled = true;

    /**
     * Who was to move in the position the most recent (engine-move or analysis) search was started
     * for.
     */
    private Side analysisSideToMove;

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
    private static final class PendingSearch {
        final boolean isRealMove;
        final int generation;

        PendingSearch(boolean isRealMove, int generation) {
            this.isRealMove = isRealMove;
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
        pgnImportLauncher =
                registerForActivityResult(
                        new ActivityResultContracts.GetContent(), this::onPgnFileSelected);
        bindViews();
        startEngine();
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
        setSupportActionBar(binding.toolbar);

        binding.boardView.setMoveSource(this);
        binding.boardView.setOnMoveListener(this);
        binding.boardView.setFlipped(boardFlipped);
        binding.boardView.setLastMove(lastMoveFrom, lastMoveTo);

        binding.newGameButton.setOnClickListener(v -> showNewGameDialog());
        binding.undoButton.setOnClickListener(v -> undo());
        binding.flipBoardButton.setOnClickListener(v -> setBoardFlipped(!boardFlipped));
        binding.openingLibraryButton.setOnClickListener(
                v -> startActivity(new Intent(this, OpeningLibraryActivity.class)));
        binding.exportPgnButton.setOnClickListener(v -> exportGamePgn());
        binding.importPgnButton.setOnClickListener(v -> pgnImportLauncher.launch("*/*"));

        binding.evaluationSwitch.setChecked(evaluationEnabled);
        binding.evaluationSwitch.setOnCheckedChangeListener(
                (btn, checked) -> {
                    evaluationEnabled = checked;
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

    /**
     * Applies a move already confirmed as legal by the Stockfish engine — used by {@link
     * #onBestMove}. Human taps go through {@link #applyHumanMove} instead, since promotion choice
     * there comes from a dialog, not a UCI move string.
     */
    private void applyConfirmedMove(String uci, boolean isEngineMove) {
        if (applyUciToGame(uci) && !isEngineMove) {
            maybeTriggerEngineMove();
        }
        refreshBoard();
    }

    /**
     * Parses and applies a UCI move string to {@link #game} (last-move highlight + sound included),
     * without any of the mode-specific follow-up ({@link #applyConfirmedMove}'s next-move dispatch)
     * — shared by that method and the training-mode move paths.
     */
    private boolean applyUciToGame(String uci) {
        Square from = Square.valueOf(uci.substring(0, 2).toUpperCase(Locale.ROOT));
        Square to = Square.valueOf(uci.substring(2, 4).toUpperCase(Locale.ROOT));
        boolean wasCapture = game.pieceAt(to) != Piece.NONE;
        if (!game.applyUciMove(uci)) {
            return false;
        }
        setLastMove(from, to);
        playMoveSound(wasCapture);
        return true;
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
        trainingSession =
                mode == GameMode.TRAINING
                        ? new TrainingSession(chosenOpening, chosenSide, hintsEnabled)
                        : null;
        binding.undoButton.setEnabled(true);

        game.reset();
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
    }

    private void updatePgnButtonsVisibility() {
        int visibility =
                mode == GameMode.TRAINING ? android.view.View.GONE : android.view.View.VISIBLE;
        binding.exportPgnButton.setVisibility(visibility);
        binding.importPgnButton.setVisibility(visibility);
        binding.exportPgnButton.setEnabled(game.moveCount() > 0);
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
     * Highlights the trainee's own next expected move on-screen - skipped when hints are disabled
     * (the "quiz" mode toggle).
     */
    private void updateTrainingHintHighlight() {
        if (mode != GameMode.TRAINING
                || trainingSession == null
                || trainingSession.isComplete()
                || !trainingSession.isHumanTurnNow()
                || !trainingSession.hintsEnabled()) {
            binding.boardView.setTrainingHint(null, null);
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
        game.undoLastMove();
        if (mode == GameMode.ENGINE && game.moveCount() > 0 && game.sideToMove() == engineSide) {
            game.undoLastMove();
        }
        setLastMove(null, null);
        refreshBoard();
    }

    /**
     * Steps the training line back to the trainee's own previous move so they can retry it -
     * mirroring ENGINE-mode undo, which likewise always lands back on the human's turn rather than
     * the opponent's. Rolls back an unconfirmed optimistic book-move apply first (see {@link
     * #trainingBookMovePending}) so {@link #game} and {@link #trainingSession} never disagree.
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
        if (engineReady) {
            engine.newGame();
            engine.setFullStrength();
        }
        refreshBoard();
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
        pendingSearches.add(new PendingSearch(isRealMove, searchGeneration));
        analysisSideToMove = game.sideToMove();
        engine.setPosition(game.toUciMoveList());
        engine.go(movetimeMs);
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

    /**
     * Applies a training move played automatically (book side) or confirmed by an on-screen tap.
     */
    private void applyTrainingMove(String uci) {
        if (!applyUciToGame(uci)) {
            refreshBoard();
            return;
        }
        trainingSession.advance();
        refreshBoard();
        maybeAdvanceTraining();
    }

    /**
     * Drives the training line forward: waits for the trainee's own tap-matched move on the
     * trainee's turn, or plays out the book side's move automatically after a short delay so it
     * doesn't feel instantaneous.
     */
    private void maybeAdvanceTraining() {
        if (mode != GameMode.TRAINING || trainingSession == null || game.isGameOver()) {
            return;
        }
        if (trainingSession.isComplete()) {
            showTrainingCompleteDialog();
            return;
        }
        if (trainingSession.isHumanTurnNow()) {
            // The board is already interactive and waits for a matching tap.
            refreshBoard();
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
     * Stockfish, without resetting {@link #game} - the whole point is picking up play from the
     * position the opening line ended at, not starting over.
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
     * Promotion picker UI: shows the four promotion pieces as images (queen/rook/bishop/knight, in
     * that order) in the color of the promoting side, rather than a plain text list.
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

    private void applyHumanMove(Square from, Square to, Piece promotion) {
        boolean wasCapture = game.pieceAt(to) != Piece.NONE;
        if (!game.applyMove(from, to, promotion)) {
            refreshBoard();
            return;
        }
        setLastMove(from, to);
        playMoveSound(wasCapture);
        refreshBoard();
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
        if (!search.isRealMove) {
            // Analysis-only search; onInfo() already streamed eval updates for it.
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
        if (!evaluationEnabled || mode == GameMode.TRAINING || analysisSideToMove == null) {
            return;
        }
        OptionalInt mate = UciInfoParser.parseScoreMate(infoLine);
        if (mate.isPresent()) {
            int whiteRelativeMate =
                    analysisSideToMove == Side.BLACK ? -mate.getAsInt() : mate.getAsInt();
            String side =
                    getString(whiteRelativeMate >= 0 ? R.string.color_white : R.string.color_black);
            binding.evaluationText.setText(
                    getString(R.string.evaluation_mate_format, Math.abs(whiteRelativeMate), side));
            return;
        }
        OptionalInt cp = UciInfoParser.parseScoreCp(infoLine);
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
        super.onDestroy();
    }
}
