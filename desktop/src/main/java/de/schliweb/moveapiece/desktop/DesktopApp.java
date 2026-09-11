/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.desktop;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Taskbar;
import java.awt.geom.Path2D;
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
     * Apple's documented Big Sur+ icon template: a 1024x1024 canvas, an 824x824 icon tile (100px
     * margin per side - ~9.77%), 185.4 corner radius. The same fractions drive {@code
     * packaging.gradle}'s {@code generateIcons} task, which bakes this shape into the bundled
     * .icns - see {@link #maskToMacSquircle} for why both places need it independently.
     */
    private static final double ICON_PADDING_FRACTION = 100.0 / 1024.0;

    private static final double ICON_RADIUS_FRACTION = 185.4 / 824.0;

    /** Apple's corners have continuous curvature, not a circular arc - this is a reasonable fit. */
    private static final double ICON_SUPERELLIPSE_N = 5.0;

    /**
     * Pads and clips a plain square icon to approximate macOS's Big Sur+ "continuous corner"
     * app-icon shape (a superellipse-cornered rounded square - flat edges, smoothly curved
     * corners; not a plain circular-arc round-rect, which looks noticeably more geometric/angular
     * by comparison). {@code java.awt.Taskbar}'s runtime Dock tile (see {@link #setDockIcon}) shows
     * exactly the pixels it's given, unmasked - unlike a small handful of macOS surfaces that apply
     * their own cosmetic framing to a *pinned, not-running* app tile, nothing softens a *running*
     * app's Dock icon at all. Without this, it would look like a plain square next to the properly
     * shaped one shown everywhere else once the icon isn't running.
     */
    private static BufferedImage maskToMacSquircle(BufferedImage source) {
        int size = Math.max(source.getWidth(), source.getHeight());
        double padding = size * ICON_PADDING_FRACTION;
        double content = size - 2 * padding;

        BufferedImage result = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = result.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setClip(appleSquirclePath(size));
        g.drawImage(
                source,
                (int) Math.round(padding),
                (int) Math.round(padding),
                (int) Math.round(content),
                (int) Math.round(content),
                null);
        g.dispose();
        return result;
    }

    /**
     * Traces Apple's icon-tile outline (see {@link #maskToMacSquircle}) at the given canvas size:
     * the 100px-margin content box, superellipse-cornered rather than circular-arc-cornered. Each
     * corner is a quarter of the superellipse {@code |x/r|^n + |y/r|^n = 1}, parametrized as
     * {@code (r*cos(t)^(2/n), r*sin(t)^(2/n))} for {@code t} in {@code [0, pi/2]} - flatter/more
     * "continuous" than a quarter-circle (which this formula reduces to at n=2) - swept per corner
     * in clockwise path order and connected by the tile's straight edges.
     */
    private static Path2D appleSquirclePath(double size) {
        double padding = size * ICON_PADDING_FRACTION;
        double content = size - 2 * padding;
        double r = content * ICON_RADIUS_FRACTION;
        int steps = 24;

        // {cornerX, cornerY, signX, signY, thetaStart, thetaEnd} - corner center in content-local
        // coordinates, which quadrant its curve bulges into, and its clockwise sweep direction
        // (from the tangent point on the incoming edge to the one on the outgoing edge).
        double[][] corners = {
            {content - r, r, +1, -1, Math.PI / 2, 0}, // top-right
            {content - r, content - r, +1, +1, 0, Math.PI / 2}, // bottom-right
            {r, content - r, -1, +1, Math.PI / 2, 0}, // bottom-left
            {r, r, -1, -1, 0, Math.PI / 2}, // top-left
        };

        Path2D.Double path = new Path2D.Double();
        boolean first = true;
        for (double[] corner : corners) {
            double cx = corner[0];
            double cy = corner[1];
            double signX = corner[2];
            double signY = corner[3];
            double t0 = corner[4];
            double t1 = corner[5];
            for (int i = 0; i <= steps; i++) {
                double t = t0 + (t1 - t0) * i / steps;
                double dx = r * Math.pow(Math.cos(t), 2.0 / ICON_SUPERELLIPSE_N);
                double dy = r * Math.pow(Math.sin(t), 2.0 / ICON_SUPERELLIPSE_N);
                double x = padding + cx + signX * dx;
                double y = padding + cy + signY * dy;
                if (first) {
                    path.moveTo(x, y);
                    first = false;
                } else {
                    path.lineTo(x, y);
                }
            }
        }
        path.closePath();
        return path;
    }
}
