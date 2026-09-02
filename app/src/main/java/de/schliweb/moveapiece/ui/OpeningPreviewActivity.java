/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.ui;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.Square;
import de.schliweb.moveapiece.R;
import de.schliweb.moveapiece.databinding.ActivityOpeningPreviewBinding;
import de.schliweb.moveapiece.logic.ChessGame;
import de.schliweb.moveapiece.training.OpeningLine;
import de.schliweb.moveapiece.training.OpeningLinePlayer;
import de.schliweb.moveapiece.training.OpeningRepository;

/**
 * Read-only step-through viewer for a single {@link OpeningLine}: a non-interactive board plus
 * Previous/Next controls, backed by {@link OpeningLinePlayer}. Reached from {@link
 * OpeningLibraryActivity}.
 */
public class OpeningPreviewActivity extends AppCompatActivity {

    public static final String EXTRA_OPENING_ID = "opening_id";
    private static final String STATE_PLY = "ply";

    private ActivityOpeningPreviewBinding binding;
    private OpeningLinePlayer player;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String openingId = getIntent().getStringExtra(EXTRA_OPENING_ID);
        OpeningLine line = openingId == null ? null : OpeningRepository.byId(openingId);
        if (line == null) {
            finish();
            return;
        }

        binding = ActivityOpeningPreviewBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        setTitle(OpeningNames.displayName(this, line));

        player = new OpeningLinePlayer(line);
        // Unlike MainActivity, this Activity declares no configChanges, so a
        // rotation destroys and recreates it; without this it would silently
        // jump back to the start of the line every time the device rotates.
        if (savedInstanceState != null) {
            int savedPly = savedInstanceState.getInt(STATE_PLY, 0);
            while (player.ply() < savedPly && player.hasNext()) {
                player.next();
            }
        }
        binding.previewBoardView.setInteractive(false);
        binding.moveListText.setText(fullSan(line));

        binding.previousButton.setOnClickListener(
                v -> {
                    player.previous();
                    refresh();
                });
        binding.nextButton.setOnClickListener(
                v -> {
                    player.next();
                    refresh();
                });

        refresh();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_PLY, player.ply());
    }

    private static String fullSan(OpeningLine line) {
        ChessGame game = new ChessGame();
        for (String uci : line.uciMoves()) {
            game.applyUciMove(uci);
        }
        return game.toSan();
    }

    private void refresh() {
        ChessGame game = player.gameAtCurrentPly();
        Piece[] pieces = new Piece[64];
        for (int i = 0; i < 64; i++) {
            pieces[i] = game.pieceAt(Square.squareAt(i));
        }
        binding.previewBoardView.setBoard(pieces);

        Square[] lastMove = player.lastMoveSquares();
        binding.previewBoardView.setLastMove(
                lastMove == null ? null : lastMove[0], lastMove == null ? null : lastMove[1]);

        binding.progressText.setText(
                getString(
                        R.string.opening_preview_progress_format,
                        player.ply(),
                        player.totalPlies()));

        binding.previousButton.setEnabled(player.hasPrevious());
        binding.nextButton.setEnabled(player.hasNext());
    }
}
