/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.desktop;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

/** JavaFX entry point for the desktop build of MoveAPiece. */
public class DesktopApp extends Application {

    private GameController controller;

    @Override
    public void start(Stage stage) {
        stage.setTitle("MoveAPiece");
        // Same artwork as the Android app's launcher icon (see
        // desktop/packaging.gradle's header comment) - jpackage sets the
        // packaged app's icon separately, this covers the window/dock icon
        // for a plain `:desktop:run`.
        stage.getIcons().add(new Image(DesktopApp.class.getResourceAsStream("icon.png")));
        controller = new GameController(stage);
        BorderPane root = controller.buildView();
        Scene scene = new Scene(root, 900, 640);
        Styles.apply(scene);
        stage.setScene(scene);
        stage.show();
    }

    /**
     * JavaFX calls this once, automatically, when the last window closes (implicit exit) - a
     * separate {@code setOnCloseRequest} handler calling {@code controller.shutdown()} too used to
     * make that happen twice, crashing the second call (see StockfishEngine#shutdown's Javadoc).
     */
    @Override
    public void stop() {
        if (controller != null) {
            controller.shutdown();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
