/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.vision;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.WindowManager;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.core.resolutionselector.AspectRatioStrategy;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.core.resolutionselector.ResolutionStrategy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.common.util.concurrent.ListenableFuture;
import de.schliweb.moveapiece.R;
import de.schliweb.moveapiece.databinding.ActivityBoardCameraBinding;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The camera for a photo of the board. It looks for the playing area in the live picture, draws it
 * and tells the user how to hold the phone ({@link PhotoGuide}); once the board is seen well and
 * the phone is held still, it takes the photo by itself. The release button takes it at any time.
 *
 * <p>The photo is written to the file named in {@link #EXTRA_FILE}. Nothing else is kept, and
 * nothing leaves the device.
 */
public final class BoardCameraActivity extends AppCompatActivity {

    /** In: path of the file the photo is written to. */
    public static final String EXTRA_FILE = "file";

    /** Out: whether the photo was taken by the camera itself rather than with the button. */
    public static final String EXTRA_AUTOMATIC = "automatic";

    /** Out: average milliseconds the search for the board took in a live picture. */
    public static final String EXTRA_LIVE_MILLIS = "live_millis";

    private static final String TAG = "BoardCamera";

    /** The live pictures searched for the board are about this large. */
    private static final Size LIVE_SIZE = new Size(640, 480);

    /** The photo is taken after this many good pictures in a row ... */
    private static final int STEADY_FRAMES = 3;

    /** ... between which no corner moved more than this share of the picture's diagonal. */
    private static final double STEADY = 0.03;

    private final ExecutorService searcher = Executors.newSingleThreadExecutor();
    private ActivityBoardCameraBinding binding;
    private ActivityResultLauncher<String> permission;
    private ImageCapture capture;
    private boolean taking;

    // Only touched by the searcher thread.
    private double[][] lastCorners;
    private int steadyFrames;
    private long searchNanos;
    private int searches;

    private volatile long liveMillis;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this, SystemBarStyle.dark(Color.TRANSPARENT));
        binding = ActivityBoardCameraBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        ViewCompat.setOnApplyWindowInsetsListener(
                binding.cameraControls,
                (view, windowInsets) -> {
                    Insets bars =
                            windowInsets.getInsets(
                                    WindowInsetsCompat.Type.systemBars()
                                            | WindowInsetsCompat.Type.displayCutout());
                    view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                    return WindowInsetsCompat.CONSUMED;
                });
        // The whole picture, so that the outline drawn over it fits (see BoardOutlineView).
        binding.cameraPreview.setScaleType(PreviewView.ScaleType.FIT_CENTER);
        binding.cameraShutter.setOnClickListener(v -> takePhoto(false));
        binding.cameraShutter.setEnabled(false);

        permission =
                registerForActivityResult(
                        new ActivityResultContracts.RequestPermission(),
                        granted -> {
                            if (granted) {
                                startCamera();
                            } else {
                                giveUp(R.string.board_camera_denied);
                            }
                        });
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            permission.launch(Manifest.permission.CAMERA);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        searcher.shutdown();
    }

    private void giveUp(int message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        setResult(RESULT_CANCELED);
        finish();
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> provider = ProcessCameraProvider.getInstance(this);
        provider.addListener(
                () -> {
                    try {
                        bind(provider.get());
                    } catch (Exception e) {
                        Log.w(TAG, "no camera", e);
                        giveUp(R.string.board_camera_denied);
                    }
                },
                ContextCompat.getMainExecutor(this));
    }

    private void bind(ProcessCameraProvider provider) {
        // Preview, live pictures and photo all 4:3, so that they show the same part of the scene.
        ResolutionSelector full =
                new ResolutionSelector.Builder()
                        .setAspectRatioStrategy(
                                AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                        .build();
        ResolutionSelector small =
                new ResolutionSelector.Builder()
                        .setAspectRatioStrategy(
                                AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                        .setResolutionStrategy(
                                new ResolutionStrategy(
                                        LIVE_SIZE,
                                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                        .build();
        Preview preview = new Preview.Builder().setResolutionSelector(full).build();
        preview.setSurfaceProvider(binding.cameraPreview.getSurfaceProvider());
        ImageAnalysis live =
                new ImageAnalysis.Builder()
                        .setResolutionSelector(small)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();
        live.setAnalyzer(searcher, this::search);
        capture =
                new ImageCapture.Builder()
                        .setResolutionSelector(full)
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                        .build();
        provider.unbindAll();
        provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, live, capture);
        binding.cameraShutter.setEnabled(true);
    }

    /** Looks for the board in one live picture. Runs on the searcher thread. */
    private void search(@NonNull ImageProxy image) {
        try {
            long start = System.nanoTime();
            Photo photo = upright(image);
            BoardFrameFinder.Result found = BoardFrameFinder.find(photo);
            searchNanos += System.nanoTime() - start;
            searches++;
            liveMillis = searchNanos / searches / 1_000_000;

            PhotoGuide guide = null;
            float[] outline = null;
            if (found != null) {
                guide =
                        PhotoGuide.of(
                                found.corners, photo.width, photo.height, lastCorners != null);
                outline = new float[8];
                for (int i = 0; i < 4; i++) {
                    outline[2 * i] = (float) (found.corners[i][0] / photo.width);
                    outline[2 * i + 1] = (float) (found.corners[i][1] / photo.height);
                }
            }
            boolean good = guide != null && guide.advice == PhotoGuide.Advice.GOOD;
            if (good && lastCorners != null && moved(found.corners, photo) < STEADY) {
                steadyFrames++;
            } else {
                steadyFrames = good ? 1 : 0;
            }
            lastCorners = good ? found.corners : null;

            // One line per live picture, to follow from a computer what the camera sees.
            Log.i(
                    TAG,
                    guide == null
                            ? "no board, " + (System.nanoTime() - start) / 1_000_000 + " ms"
                            : String.format(
                                    java.util.Locale.ROOT,
                                    "%s, %.1f degrees, board %.0f %% of the picture, fit %.1f, %d"
                                            + " ms, steady %d",
                                    guide.advice,
                                    guide.elevation,
                                    100 * guide.share,
                                    found.fit,
                                    (System.nanoTime() - start) / 1_000_000,
                                    steadyFrames));

            final PhotoGuide shown = guide;
            final float[] corners = outline;
            final float aspect = (float) photo.width / photo.height;
            final boolean take = steadyFrames >= STEADY_FRAMES;
            runOnUiThread(() -> show(shown, corners, aspect, take));
        } catch (RuntimeException e) {
            Log.w(TAG, "live picture failed", e);
        } finally {
            image.close();
        }
    }

    private double moved(double[][] corners, Photo photo) {
        double worst = 0;
        for (int i = 0; i < 4; i++) {
            worst =
                    Math.max(
                            worst,
                            Math.hypot(
                                    corners[i][0] - lastCorners[i][0],
                                    corners[i][1] - lastCorners[i][1]));
        }
        return worst / Math.hypot(photo.width, photo.height);
    }

    /** The brightness of a live picture as a grey photo, turned upright. */
    private static Photo upright(ImageProxy image) {
        ImageProxy.PlaneProxy plane = image.getPlanes()[0];
        ByteBuffer buffer = plane.getBuffer();
        int stride = plane.getRowStride();
        int step = plane.getPixelStride();
        int w = image.getWidth();
        int h = image.getHeight();
        int turn = image.getImageInfo().getRotationDegrees();
        boolean sideways = turn == 90 || turn == 270;
        int outW = sideways ? h : w;
        int outH = sideways ? w : h;
        int[] rgb = new int[outW * outH];
        byte[] row = new byte[(w - 1) * step + 1];
        for (int y = 0; y < h; y++) {
            buffer.position(y * stride);
            buffer.get(row, 0, Math.min(row.length, buffer.remaining()));
            for (int x = 0; x < w; x++) {
                int v = row[x * step] & 0xff;
                int outX;
                int outY;
                switch (turn) {
                    case 90:
                        outX = h - 1 - y;
                        outY = x;
                        break;
                    case 180:
                        outX = w - 1 - x;
                        outY = h - 1 - y;
                        break;
                    case 270:
                        outX = y;
                        outY = w - 1 - x;
                        break;
                    default:
                        outX = x;
                        outY = y;
                        break;
                }
                rgb[outY * outW + outX] = 0xff000000 | (v << 16) | (v << 8) | v;
            }
        }
        return new Photo(outW, outH, rgb);
    }

    private void show(PhotoGuide guide, float[] corners, float aspect, boolean take) {
        if (taking || isFinishing() || isDestroyed()) {
            return;
        }
        boolean good = guide != null && guide.advice == PhotoGuide.Advice.GOOD;
        binding.cameraOutline.show(corners, aspect, good);
        int hint = R.string.board_camera_searching;
        if (guide != null) {
            switch (guide.advice) {
                case STEEPER:
                    hint = R.string.board_camera_steeper;
                    break;
                case FLATTER:
                    hint = R.string.board_camera_flatter;
                    break;
                case CLOSER:
                    hint = R.string.board_camera_closer;
                    break;
                case FARTHER:
                    hint = R.string.board_camera_farther;
                    break;
                default:
                    hint = R.string.board_camera_hold;
                    break;
            }
        }
        binding.cameraHint.setText(hint);
        if (take) {
            takePhoto(true);
        }
    }

    private void takePhoto(boolean automatic) {
        if (taking || capture == null) {
            return;
        }
        taking = true;
        binding.cameraShutter.setEnabled(false);
        File file = new File(getIntent().getStringExtra(EXTRA_FILE));
        capture.takePicture(
                new ImageCapture.OutputFileOptions.Builder(file).build(),
                ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults results) {
                        setResult(
                                RESULT_OK,
                                new Intent()
                                        .putExtra(EXTRA_AUTOMATIC, automatic)
                                        .putExtra(EXTRA_LIVE_MILLIS, liveMillis));
                        finish();
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException e) {
                        Log.w(TAG, "taking the photo failed", e);
                        taking = false;
                        binding.cameraShutter.setEnabled(true);
                    }
                });
    }
}
