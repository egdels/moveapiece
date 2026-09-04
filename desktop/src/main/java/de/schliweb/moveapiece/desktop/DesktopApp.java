/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.desktop;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Taskbar;
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
        BufferedImage icon = loadIcon();
        // Same artwork as the Android app's launcher icon (see
        // desktop/packaging.gradle's header comment) - jpackage sets the
        // packaged app's icon separately, this covers the window icon for a
        // plain `:desktop:run` (title bar on Windows/Linux; macOS windows
        // don't show one).
        stage.getIcons().add(toFxImage(icon));
        setDockIcon(icon);
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

    private static BufferedImage loadIcon() {
        try (InputStream in = DesktopApp.class.getResourceAsStream("icon.png")) {
            BufferedImage source = ImageIO.read(in);
            return isMac() ? maskToMacSquircle(source) : source;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Image toFxImage(BufferedImage awtImage) {
        try {
            ByteArrayOutputStream pngBytes = new ByteArrayOutputStream();
            ImageIO.write(awtImage, "png", pngBytes);
            return new Image(new ByteArrayInputStream(pngBytes.toByteArray()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * {@code Stage.getIcons()} alone does not actually set the Dock tile on macOS - a long-standing
     * JavaFX bug (JDK-8095033) that still applies as of this JavaFX version: the app just keeps
     * showing the default Java icon there. {@code java.awt.Taskbar} (JDK 9+, toolkit-agnostic - it
     * doesn't require or start a Swing/AWT UI) is the mechanism that actually works, on macOS and
     * anywhere else that supports it; {@code isSupported} guards make this a no-op elsewhere (e.g.
     * Windows, which doesn't support runtime taskbar-icon changes through this API at all).
     */
    private static void setDockIcon(BufferedImage icon) {
        if (!Taskbar.isTaskbarSupported()) {
            return;
        }
        Taskbar taskbar = Taskbar.getTaskbar();
        if (taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
            taskbar.setIconImage(icon);
        }
    }

    private static boolean isMac() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
    }

    /**
     * Pads and rounds a plain square icon to approximate macOS's Big Sur+ "squircle" app-icon
     * shape. The packaged .app's .icns gets that treatment automatically from Finder/Dock whenever
     * the app isn't running; the Dock tile set at runtime (see {@link #setDockIcon}) shows exactly
     * the pixels it's given - unmasked - so without this, the running app's Dock icon would look
     * like a plain square next to its own rounded icon everywhere else (Finder, Launchpad, the
     * not-yet-running Dock icon).
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
