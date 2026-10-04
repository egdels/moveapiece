/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.desktop;

import de.schliweb.moveapiece.board.PhysicalBoardBridge;
import de.schliweb.moveapiece.logic.BoardType;
import de.schliweb.pegasus.core.transport.DiscoveredDevice;
import de.schliweb.pegasus.core.transport.ScanListener;
import de.schliweb.pegasus.core.transport.TransportError;
import java.util.Optional;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Modal device-scan/pick dialog for the physical board, following {@link GameSetupDialog}'s "static
 * utility returning an {@code Optional}" shape. Starts a scan as soon as it's shown and stops it
 * once closed either way - mirrors the Android app's {@code MainActivity.showPegasusScanDialog},
 * minus the runtime-permission step (macOS has no Android-style BLE permission prompt).
 *
 * <p>There is nothing to configure: the dialog scans with the bridge it is given, lists every named
 * device in range (recognised boards first) and returns the one picked. Working out whether that
 * device is a DGT Pegasus or a Chessnut is the caller's job ({@code
 * GameController.connectWithDetection}).
 */
final class BoardConnectDialog {

    private static final long SCAN_TIMEOUT_MS = 15000;

    private BoardConnectDialog() {}

    static Optional<DiscoveredDevice> show(Stage owner, PhysicalBoardBridge bridge) {
        ObservableList<DiscoveredDevice> items = FXCollections.observableArrayList();
        // How many entries at the top of the list are recognised boards (by name).
        int[] boardCount = {0};
        ListView<DiscoveredDevice> listView = new ListView<>(items);
        listView.setPlaceholder(new Label(Messages.get("pegasus_scan_empty")));
        listView.setCellFactory(
                lv ->
                        new ListCell<>() {
                            @Override
                            protected void updateItem(DiscoveredDevice item, boolean empty) {
                                super.updateItem(item, empty);
                                // Just the advertised name (the Chessnut Air sends it with a
                                // trailing newline): address and signal strength are developer
                                // detail.
                                setText(
                                        empty || item == null || item.getName() == null
                                                ? null
                                                : item.getName().trim());
                            }
                        });
        listView.setPrefSize(360, 240);

        // No board type to choose: the caller works it out from the device picked.
        VBox content = new VBox(8, listView);

        Dialog<DiscoveredDevice> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle(Messages.get("dialog_board_connect"));
        dialog.getDialogPane().setContent(content);
        Styles.apply(dialog.getDialogPane());
        ButtonType connectType =
                new ButtonType(Messages.get("action_connect"), ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(connectType, ButtonType.CANCEL);

        Node connectButtonNode = dialog.getDialogPane().lookupButton(connectType);
        connectButtonNode.setDisable(true);
        listView.getSelectionModel()
                .selectedItemProperty()
                .addListener((obs, old, val) -> connectButtonNode.setDisable(val == null));
        listView.setOnMouseClicked(
                e -> {
                    if (e.getClickCount() == 2
                            && listView.getSelectionModel().getSelectedItem() != null) {
                        ((Button) connectButtonNode).fire();
                    }
                });

        ScanListener scanListener =
                new ScanListener() {
                    @Override
                    public void onDeviceFound(DiscoveredDevice device) {
                        if (device.getName() == null || device.getName().isBlank()) {
                            // Unnamed BLE devices clutter the list and are never a
                            // supported board; both always advertise a name.
                            return;
                        }
                        // Recognised boards (by name or advertised service) go to the top of the
                        // list. The desktop transports report no advertised services yet, so
                        // here it is the name alone; Android already passes them.
                        boolean isBoard =
                                BoardType.guessFromAdvertisement(
                                                device.getName(),
                                                device.getAdvertisedServiceUuids())
                                        != null;
                        Platform.runLater(
                                () -> {
                                    if (items.contains(device)) {
                                        return;
                                    }
                                    items.add(isBoard ? boardCount[0]++ : items.size(), device);
                                });
                    }

                    @Override
                    public void onScanFinished() {
                        // List just stops growing; no action needed.
                    }

                    @Override
                    public void onScanFailed(TransportError error, String detail) {
                        Platform.runLater(
                                () -> {
                                    dialog.setResult(null);
                                    dialog.close();
                                    showScanError(owner, error, detail);
                                });
                    }
                };
        if (bridge != null) {
            bridge.startScan(scanListener, SCAN_TIMEOUT_MS);
        }

        dialog.setResultConverter(
                buttonType -> {
                    if (buttonType != connectType) {
                        return null;
                    }
                    return listView.getSelectionModel().getSelectedItem();
                });

        Optional<DiscoveredDevice> result = dialog.showAndWait();
        if (bridge != null) {
            bridge.stopScan();
        }
        return result;
    }

    private static void showScanError(Stage owner, TransportError error, String detail) {
        String messageKey =
                error == TransportError.PERMISSION_DENIED
                        ? "pegasus_permission_required"
                        : "pegasus_scan_failed";
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initOwner(owner);
        alert.setTitle(Messages.get("dialog_error_title"));
        alert.setHeaderText(null);
        alert.setContentText(
                Messages.get(messageKey)
                        + " ("
                        + error
                        + (detail == null || detail.isBlank() ? "" : ": " + detail)
                        + ")");
        Styles.apply(alert.getDialogPane());
        alert.showAndWait();
    }
}
