/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.desktop;

import de.schliweb.moveapiece.training.OpeningLine;
import de.schliweb.moveapiece.training.OpeningRepository;
import de.schliweb.moveapiece.training.OpeningSearch;
import java.util.ArrayList;
import java.util.List;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Searchable browser over {@link OpeningRepository#ALL}: pick a name to open {@link
 * OpeningPreviewWindow}. A reference viewer, distinct from the in-game opening trainer (which
 * quizzes a fixed line as part of a live game - not part of this first desktop release, see the
 * project plan). Ported from the Android app's {@code ui.OpeningLibraryActivity}.
 */
final class OpeningLibraryWindow {

    private OpeningLibraryWindow() {}

    static void show(Stage owner) {
        List<OpeningLine> allLines = OpeningRepository.ALL;
        List<String> allDisplayNames = new ArrayList<>();
        for (OpeningLine line : allLines) {
            allDisplayNames.add(OpeningNames.displayName(line));
        }

        TextField searchField = new TextField();
        searchField.setPromptText(Messages.get("opening_library_search_hint"));

        ObservableList<OpeningLine> items = FXCollections.observableArrayList(allLines);
        ListView<OpeningLine> listView = new ListView<>(items);
        listView.setPlaceholder(new Label(Messages.get("opening_library_empty")));
        listView.setCellFactory(
                lv ->
                        new ListCell<>() {
                            @Override
                            protected void updateItem(OpeningLine item, boolean empty) {
                                super.updateItem(item, empty);
                                setText(
                                        empty || item == null
                                                ? null
                                                : OpeningNames.displayName(item));
                            }
                        });

        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.setTitle(Messages.get("opening_library_title"));

        searchField
                .textProperty()
                .addListener(
                        (obs, old, value) ->
                                items.setAll(
                                        OpeningSearch.filter(allLines, allDisplayNames, value)));

        Runnable openSelected =
                () -> {
                    OpeningLine selected = listView.getSelectionModel().getSelectedItem();
                    if (selected != null) {
                        OpeningPreviewWindow.show(stage, selected);
                    }
                };
        listView.setOnMouseClicked(
                e -> {
                    if (e.getClickCount() == 2) {
                        openSelected.run();
                    }
                });
        listView.setOnKeyPressed(
                e -> {
                    if (e.getCode() == KeyCode.ENTER) {
                        openSelected.run();
                    }
                });

        VBox root = new VBox(8, searchField, listView);
        VBox.setVgrow(listView, Priority.ALWAYS);
        root.getStyleClass().add("card");

        Scene scene = new Scene(root, 380, 520);
        Styles.apply(scene);
        stage.setScene(scene);
        stage.show();
        searchField.requestFocus();
    }
}
