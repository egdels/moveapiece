/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.desktop;

import com.github.bhlangonijr.chesslib.Side;
import de.schliweb.moveapiece.training.OpeningLine;
import de.schliweb.moveapiece.training.OpeningRepository;
import de.schliweb.moveapiece.training.OpeningSearch;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Modal "start the opening trainer" picker: search/select one of {@link OpeningRepository#ALL},
 * choose which side to drill, and whether the trainee's own next move gets a highlighted hint.
 * Opened from {@link GameController}'s "Opening Trainer" mode toggle and its "New Game" button
 * while already training.
 */
final class TrainingSetupDialog {

    private TrainingSetupDialog() {}

    static Optional<TrainingChoice> show(Stage owner) {
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
        listView.getSelectionModel().selectFirst();

        ToggleGroup sideGroup = new ToggleGroup();
        RadioButton whiteRadio = new RadioButton(Messages.get("training_play_white"));
        RadioButton blackRadio = new RadioButton(Messages.get("training_play_black"));
        whiteRadio.setToggleGroup(sideGroup);
        blackRadio.setToggleGroup(sideGroup);
        whiteRadio.setSelected(true);

        CheckBox hintsBox = new CheckBox(Messages.get("dialog_training_hint_checkbox"));
        hintsBox.setSelected(true);

        searchField
                .textProperty()
                .addListener(
                        (obs, old, value) -> {
                            items.setAll(OpeningSearch.filter(allLines, allDisplayNames, value));
                            if (!items.isEmpty()) {
                                listView.getSelectionModel().selectFirst();
                            }
                        });

        VBox content =
                new VBox(8, searchField, listView, new HBox(16, whiteRadio, blackRadio), hintsBox);
        content.setPrefWidth(360);
        content.setPrefHeight(420);
        VBox.setVgrow(listView, Priority.ALWAYS);

        Dialog<TrainingChoice> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle(Messages.get("training_setup_title"));
        dialog.getDialogPane().setContent(content);
        Styles.apply(dialog.getDialogPane());
        ButtonType startType =
                new ButtonType(Messages.get("action_start"), ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(startType, ButtonType.CANCEL);

        Node startButton = dialog.getDialogPane().lookupButton(startType);
        startButton.setDisable(listView.getSelectionModel().getSelectedItem() == null);
        listView.getSelectionModel()
                .selectedItemProperty()
                .addListener((obs, old, val) -> startButton.setDisable(val == null));

        dialog.setResultConverter(
                buttonType -> {
                    if (buttonType != startType) {
                        return null;
                    }
                    OpeningLine selected = listView.getSelectionModel().getSelectedItem();
                    if (selected == null) {
                        return null;
                    }
                    Side side = whiteRadio.isSelected() ? Side.WHITE : Side.BLACK;
                    return new TrainingChoice(selected, side, hintsBox.isSelected());
                });

        return dialog.showAndWait();
    }
}
