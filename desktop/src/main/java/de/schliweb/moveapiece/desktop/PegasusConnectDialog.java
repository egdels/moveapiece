/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.desktop;

import de.schliweb.moveapiece.desktop.pegasus.DesktopPegasusGameBridge;
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
import javafx.stage.Stage;

/**
 * Modal device-scan/pick dialog for the Pegasus board, following {@link GameSetupDialog}'s "static
 * utility returning an {@code Optional}" shape. Starts a scan as soon as it's shown and stops it
 * once closed either way - mirrors the Android app's {@code MainActivity.showPegasusScanDialog},
 * minus the runtime-permission step (macOS has no Android-style BLE permission prompt).
 */
final class PegasusConnectDialog {

    private static final long SCAN_TIMEOUT_MS = 15000;

    private PegasusConnectDialog() {}

    static Optional<String> show(Stage owner, DesktopPegasusGameBridge bridge) {
        ObservableList<DiscoveredDevice> items = FXCollections.observableArrayList();
        ListView<DiscoveredDevice> listView = new ListView<>(items);
        listView.setPlaceholder(new Label(Messages.get("pegasus_scan_empty")));
        listView.setCellFactory(
                lv ->
                        new ListCell<>() {
                            @Override
                            protected void updateItem(DiscoveredDevice item, boolean empty) {
                                super.updateItem(item, empty);
                                setText(empty || item == null ? null : item.toString());
                            }
                        });
        listView.setPrefSize(360, 240);

        Dialog<String> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle(Messages.get("dialog_pegasus_title"));
        dialog.getDialogPane().setContent(listView);
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
                            // Unnamed BLE devices clutter the list and are never the
                            // Pegasus board, which always advertises a name.
                            return;
                        }
                        Platform.runLater(
                                () -> {
                                    if (!items.contains(device)) {
                                        items.add(device);
                                    }
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
                                    showScanError(owner, error);
                                });
                    }
                };
        bridge.startScan(scanListener, SCAN_TIMEOUT_MS);

        dialog.setResultConverter(
                buttonType -> {
                    if (buttonType != connectType) {
                        return null;
                    }
                    DiscoveredDevice selected = listView.getSelectionModel().getSelectedItem();
                    return selected == null ? null : selected.getAddress();
                });

        Optional<String> result = dialog.showAndWait();
        bridge.stopScan();
        return result;
    }

    private static void showScanError(Stage owner, TransportError error) {
        String messageKey =
                error == TransportError.PERMISSION_DENIED
                        ? "pegasus_permission_required"
                        : "pegasus_scan_failed";
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initOwner(owner);
        alert.setTitle(Messages.get("dialog_error_title"));
        alert.setHeaderText(null);
        alert.setContentText(Messages.get(messageKey));
        Styles.apply(alert.getDialogPane());
        alert.showAndWait();
    }
}
