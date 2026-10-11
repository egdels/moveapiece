/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.ui;

import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import androidx.activity.ComponentActivity;
import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Edge-to-edge for every screen. Android 15+ enforces it for this targetSdk anyway (the theme's
 * status bar colour is ignored there); switching it on everywhere makes all Android versions look
 * the same. The main screen pads itself with fitsSystemWindows and gets status bar icons that
 * follow day/night; the screens with a toolbar use {@link #applyToolbarScreen}.
 */
public final class SystemBars {

    private SystemBars() {}

    /** For a screen without a toolbar: icons dark on the light theme, light on the dark one. */
    public static void enable(ComponentActivity activity) {
        EdgeToEdge.enable(activity);
    }

    /**
     * For a screen with the green toolbar: the toolbar reaches under the status bar and, in
     * landscape, the display cutout, with white status bar icons on it; {@code content} (everything
     * below the toolbar) is padded clear of the cutout and the navigation bar. A scrolling {@code
     * content} keeps drawing into its padding, so it scrolls under the navigation bar. Call after
     * setContentView. The toolbar must be a direct child of the layout's root, and the layout must
     * not set fitsSystemWindows.
     */
    public static void applyToolbarScreen(ComponentActivity activity, View toolbar, View content) {
        EdgeToEdge.enable(activity, SystemBarStyle.dark(Color.TRANSPARENT));
        int toolbarHeight = toolbar.getLayoutParams().height;
        if (content instanceof ViewGroup) {
            ((ViewGroup) content).setClipToPadding(false);
        }
        ViewCompat.setOnApplyWindowInsetsListener(
                (View) toolbar.getParent(),
                (root, windowInsets) -> {
                    Insets bars =
                            windowInsets.getInsets(
                                    WindowInsetsCompat.Type.systemBars()
                                            | WindowInsetsCompat.Type.displayCutout());
                    ViewGroup.LayoutParams params = toolbar.getLayoutParams();
                    params.height = toolbarHeight + bars.top;
                    toolbar.setLayoutParams(params);
                    toolbar.setPadding(bars.left, bars.top, bars.right, 0);
                    content.setPadding(bars.left, 0, bars.right, bars.bottom);
                    return WindowInsetsCompat.CONSUMED;
                });
    }
}
