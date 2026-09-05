/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.pegasus.bluetooth;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.content.ContextCompat;

/**
 * Version-dependent runtime permissions for BLE (matrix: docs/ANDROID_BLE.md).
 *
 * <p>API 31+: BLUETOOTH_SCAN (neverForLocation) + BLUETOOTH_CONNECT. API 26–30:
 * ACCESS_FINE_LOCATION (required for BLE scan results).
 */
public final class BlePermissions {

    private BlePermissions() {}

    public static String[] required() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return new String[] {
                Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT
            };
        }
        return new String[] {Manifest.permission.ACCESS_FINE_LOCATION};
    }

    public static boolean allGranted(Context context) {
        for (String permission : required()) {
            if (ContextCompat.checkSelfPermission(context, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }
}
