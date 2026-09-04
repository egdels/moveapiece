/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.desktop;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Locale;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import javax.imageio.ImageIO;

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
        stage.getIcons().add(dockIcon());
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

    private static Image dockIcon() {
        try (InputStream in = DesktopApp.class.getResourceAsStream("icon.png")) {
            BufferedImage source = ImageIO.read(in);
            BufferedImage icon = isMac() ? maskToMacSquircle(source) : source;
            ByteArrayOutputStream pngBytes = new ByteArrayOutputStream();
            ImageIO.write(icon, "png", pngBytes);
            return new Image(new ByteArrayInputStream(pngBytes.toByteArray()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean isMac() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
    }

    /**
     * Pads and rounds a plain square icon to approximate macOS's Big Sur+ "squircle" app-icon
     * shape. The packaged .app's .icns gets that treatment automatically from Finder/Dock whenever
     * the app isn't running, but a Stage icon set at runtime becomes the Dock tile via
     * NSApplication.setApplicationIconImage, which shows exactly the pixels it's given - unmasked -
     * so without this, the running app's Dock icon looks like a plain square next to its own
     * rounded icon everywhere else (Finder, Launchpad, the not-yet-running Dock icon). Uses plain
     * AWT (Graphics2D/BufferedImage), same as packaging.gradle's .icns/.ico generation, rather than
     * an off-screen JavaFX node snapshot - the latter is unreliable here (HiDPI/Retina
     * backing-scale snapshots can come out at the wrong pixel size).
     */
    private static BufferedImage maskToMacSquircle(BufferedImage source) {
        int size = Math.max(source.getWidth(), source.getHeight());
        int padding = Math.round(size * 0.1f);
        int content = size - 2 * padding;
        int arc = Math.round(size * 0.44f);

        BufferedImage result = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = result.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setClip(new RoundRectangle2D.Float(0, 0, size, size, arc, arc));
        g.drawImage(source, padding, padding, content, content, null);
        g.dispose();
        return result;
    }
}
