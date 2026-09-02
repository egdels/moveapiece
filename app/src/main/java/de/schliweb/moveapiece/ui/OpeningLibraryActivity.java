/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import androidx.appcompat.app.AppCompatActivity;
import de.schliweb.moveapiece.databinding.ActivityOpeningLibraryBinding;
import de.schliweb.moveapiece.training.OpeningLine;
import de.schliweb.moveapiece.training.OpeningRepository;
import de.schliweb.moveapiece.training.OpeningSearch;
import java.util.ArrayList;
import java.util.List;

/**
 * Searchable browser over {@link OpeningRepository#ALL}: tap a name to open {@link
 * OpeningPreviewActivity}. Purely a reference viewer, distinct from the opening trainer (which
 * quizzes a fixed line as part of a live game).
 */
public class OpeningLibraryActivity extends AppCompatActivity {

    private ActivityOpeningLibraryBinding binding;
    private final List<OpeningLine> allLines = OpeningRepository.ALL;
    private final List<String> allDisplayNames = new ArrayList<>();
    private List<OpeningLine> filtered = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityOpeningLibraryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        for (OpeningLine line : allLines) {
            allDisplayNames.add(OpeningNames.displayName(this, line));
        }

        binding.openingListView.setOnItemClickListener(
                (parent, view, position, id) -> {
                    OpeningLine chosen = filtered.get(position);
                    Intent intent = new Intent(this, OpeningPreviewActivity.class);
                    intent.putExtra(OpeningPreviewActivity.EXTRA_OPENING_ID, chosen.id());
                    startActivity(intent);
                });

        binding.searchInput.addTextChangedListener(
                new TextWatcher() {
                    @Override
                    public void beforeTextChanged(
                            CharSequence s, int start, int count, int after) {}

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                        applyFilter(s.toString());
                    }

                    @Override
                    public void afterTextChanged(Editable s) {}
                });

        applyFilter("");
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void applyFilter(String query) {
        filtered = OpeningSearch.filter(allLines, allDisplayNames, query);

        List<String> names = new ArrayList<>();
        for (OpeningLine line : filtered) {
            names.add(OpeningNames.displayName(this, line));
        }
        binding.openingListView.setAdapter(
                new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, names));

        boolean empty = filtered.isEmpty();
        binding.emptyText.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.openingListView.setVisibility(empty ? View.GONE : View.VISIBLE);
    }
}
