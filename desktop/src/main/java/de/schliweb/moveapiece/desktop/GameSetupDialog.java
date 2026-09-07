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
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Modal "New Game" picker: opponent (human, Stockfish, or the opening trainer), which color the
 * human plays, and - for the opening trainer - which line to drill and whether the trainee's own
 * next move gets a highlighted hint. Mirrors the Android app's single {@code showNewGameDialog}
 * (one dialog for all three modes) - the one deliberate difference is the engine's playing
 * strength, which stays a live, always-visible sidebar slider on desktop (see {@link
 * GameController}) rather than a dialog-only setting, since unlike the Android dialog's SeekBar it
 * can be adjusted mid-game.
 */
final class GameSetupDialog {

    enum Opponent {
        HUMAN,
        STOCKFISH,
        TRAINER
    }

    record Choice(Opponent opponent, Side side, OpeningLine opening, boolean hintsEnabled) {}

    private GameSetupDialog() {}

    static Optional<Choice> show(Stage owner) {
        List<OpeningLine> allLines = OpeningRepository.ALL;
        List<String> allDisplayNames = new ArrayList<>();
        for (OpeningLine line : allLines) {
            allDisplayNames.add(OpeningNames.displayName(line));
        }

        ToggleGroup opponentGroup = new ToggleGroup();
        RadioButton humanRadio = new RadioButton(Messages.get("mode_human_vs_human"));
        RadioButton stockfishRadio = new RadioButton(Messages.get("mode_human_vs_stockfish"));
        RadioButton trainerRadio = new RadioButton(Messages.get("mode_opening_trainer"));
        humanRadio.setToggleGroup(opponentGroup);
        stockfishRadio.setToggleGroup(opponentGroup);
        trainerRadio.setToggleGroup(opponentGroup);
        stockfishRadio.setSelected(true);

        ToggleGroup sideGroup = new ToggleGroup();
        RadioButton whiteRadio = new RadioButton(Messages.get("training_play_white"));
        RadioButton blackRadio = new RadioButton(Messages.get("training_play_black"));
        whiteRadio.setToggleGroup(sideGroup);
        blackRadio.setToggleGroup(sideGroup);
        whiteRadio.setSelected(true);
        HBox colorBox = new HBox(16, whiteRadio, blackRadio);

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
        // Fixed rather than grown to fill the dialog - unlike the old opening-trainer-only dialog
        // this content isn't given an explicit height (see below), so nothing forces the list to
        // be any taller than this on its own.
        listView.setPrefHeight(240);
        searchField
                .textProperty()
                .addListener(
                        (obs, old, value) -> {
                            items.setAll(OpeningSearch.filter(allLines, allDisplayNames, value));
                            if (!items.isEmpty()) {
                                listView.getSelectionModel().selectFirst();
                            }
                        });
        VBox openingBox = new VBox(8, searchField, listView);

        CheckBox hintsBox = new CheckBox(Messages.get("dialog_training_hint_checkbox"));
        hintsBox.setSelected(true);

        VBox content =
                new VBox(
                        8,
                        humanRadio,
                        stockfishRadio,
                        trainerRadio,
                        colorBox,
                        openingBox,
                        hintsBox);
        // No explicit prefHeight: unlike the old opening-trainer-only dialog, this one's height
        // must vary with which opponent is selected (the search list below is only shown for the
        // trainer) - an explicit height would otherwise reserve that list's space even when hidden.
        content.setPrefWidth(360);

        // A JavaFX Dialog's window is only auto-sized to its content once, right before it's first
        // shown - toggling visible/managed afterwards (i.e. every call here but the first, which
        // runs before the Dialog below even exists) changes content's preferred size but leaves the
        // already-shown window at its old size, so it must be resized explicitly.
        Runnable updateVisibility =
                () -> {
                    boolean isHuman = humanRadio.isSelected();
                    boolean isTrainer = trainerRadio.isSelected();
                    colorBox.setVisible(!isHuman);
                    colorBox.setManaged(!isHuman);
                    openingBox.setVisible(isTrainer);
                    openingBox.setManaged(isTrainer);
                    hintsBox.setVisible(isTrainer);
                    hintsBox.setManaged(isTrainer);
                    if (content.getScene() != null) {
                        content.getScene().getWindow().sizeToScene();
                    }
                };
        opponentGroup
                .selectedToggleProperty()
                .addListener((obs, old, val) -> updateVisibility.run());
        updateVisibility.run();

        Dialog<Choice> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle(Messages.get("menu_new_game"));
        dialog.getDialogPane().setContent(content);
        Styles.apply(dialog.getDialogPane());
        ButtonType startType =
                new ButtonType(Messages.get("action_start"), ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(startType, ButtonType.CANCEL);

        Node startButton = dialog.getDialogPane().lookupButton(startType);
        Runnable updateStartDisabled =
                () ->
                        startButton.setDisable(
                                trainerRadio.isSelected()
                                        && listView.getSelectionModel().getSelectedItem() == null);
        listView.getSelectionModel()
                .selectedItemProperty()
                .addListener((obs, old, val) -> updateStartDisabled.run());
        opponentGroup
                .selectedToggleProperty()
                .addListener((obs, old, val) -> updateStartDisabled.run());
        updateStartDisabled.run();

        dialog.setResultConverter(
                buttonType -> {
                    if (buttonType != startType) {
                        return null;
                    }
                    Opponent opponent =
                            humanRadio.isSelected()
                                    ? Opponent.HUMAN
                                    : stockfishRadio.isSelected()
                                            ? Opponent.STOCKFISH
                                            : Opponent.TRAINER;
                    Side side = whiteRadio.isSelected() ? Side.WHITE : Side.BLACK;
                    OpeningLine opening =
                            opponent == Opponent.TRAINER
                                    ? listView.getSelectionModel().getSelectedItem()
                                    : null;
                    if (opponent == Opponent.TRAINER && opening == null) {
                        return null;
                    }
                    return new Choice(opponent, side, opening, hintsBox.isSelected());
                });

        return dialog.showAndWait();
    }
}
