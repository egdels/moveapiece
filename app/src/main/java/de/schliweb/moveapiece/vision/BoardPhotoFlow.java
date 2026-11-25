/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.vision;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Toast;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.github.bhlangonijr.chesslib.Piece;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import de.schliweb.chessnut.core.game.InvalidPositionException;
import de.schliweb.moveapiece.R;
import de.schliweb.moveapiece.ui.BoardView;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Takes a position over from a board that only senses which squares are occupied (the Pegasus), by
 * photographing it: camera ({@link BoardCameraActivity}), recognition ({@link
 * BoardPhotoRecognizer}), then a board on which the user checks the result and corrects pieces
 * before it becomes the game position.
 *
 * <p>How the network was trained and how well it reads is described in {@code training/}. A photo
 * is read on the device and not kept. Only a debug build keeps it ({@link #keepsRecords}): there
 * every use leaves a small folder with the photos, what the board reported, what was recognised and
 * what the user made of it, in the app's own files directory - the material to make the recognition
 * better; the folders have to be copied off by hand.
 */
public final class BoardPhotoFlow {

    private static final String TAG = "BoardPhotoFlow";
    private static final String MODEL_ASSET = "vision/piece_recogniser.onnx";
    private static final String PHOTO_FOLDER = "board-photos";

    /** Photos are decoded at no more than about this many pixels on the longer side. */
    private static final int MAX_SIDE = 2200;

    private static final String SYMBOLS = "KQRBNPkqrbnp";

    /** The pieces as the board of the app draws them, in the order of {@link #SYMBOLS}. */
    private static final int[] PIECE_IMAGES = {
        R.drawable.piece_wk, R.drawable.piece_wq, R.drawable.piece_wr,
        R.drawable.piece_wb, R.drawable.piece_wn, R.drawable.piece_wp,
        R.drawable.piece_bk, R.drawable.piece_bq, R.drawable.piece_br,
        R.drawable.piece_bb, R.drawable.piece_bn, R.drawable.piece_bp
    };

    private static final int PIECE_GROUND = 0xFFE9E4D8;

    private static final String[] GLYPHS = {
        "♔", "♕", "♖", "♗", "♘", "♙", "♚", "♛", "♜", "♝", "♞", "♟"
    };

    /** What the flow needs from the screen it runs on. */
    public interface Host {
        /** The position the user confirmed, as a FEN the game can load. */
        void onPositionRead(String fen);

        /** The confirmed pieces do not make a playable position; tell the user why. */
        void onInvalidPosition(InvalidPositionException e);

        /**
         * The camera opens or has closed: while it is open the board should not light anything, its
         * lights mislead the recognition.
         */
        void onCamera(boolean open);

        /**
         * Which squares the board reports as occupied right now, {@code [rank][file]}, or {@code
         * null} if it cannot tell (not connected any more). Asked again for every photo: between
         * two photos the pieces may have been moved (it has happened: a new position set up and
         * "new photo" pressed - it was read with the occupancy of the position before).
         */
        boolean[][] currentOccupancy();
    }

    private final AppCompatActivity activity;
    private final Host host;
    private final ActivityResultLauncher<Intent> camera;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private BoardPhotoRecognizer recognizer;
    private Dialog dialog;

    /** The dialog asking for the piece of a square, while it is open. */
    private Dialog chooser;

    /** Shows the check dialog again as it stands, while it is open; see {@link #onRotated}. */
    private Runnable showAgain;

    /**
     * The photos of the position being read and what the network made of each: a second photo from
     * another side can be added, and all of them are then read together.
     */
    private final List<byte[]> photos = new ArrayList<>();

    private final List<BoardPhotoRecognizer.Reading> readings = new ArrayList<>();

    private boolean[][] occupancy;
    private boolean whiteToMove;
    private boolean flipped;

    /** How the last photo was taken: by the camera itself, and how long its live search took. */
    private boolean automatic;

    private long liveMillis;

    /** Must be created while the activity is being created, like every result launcher. */
    public BoardPhotoFlow(AppCompatActivity activity, Host host) {
        this.activity = activity;
        this.host = host;
        this.camera =
                activity.registerForActivityResult(
                        new ActivityResultContracts.StartActivityForResult(), this::onCameraDone);
    }

    /** Whether reading a position from a photo is offered: with the network and a camera. */
    public static boolean isAvailable(android.content.Context context) {
        if (!context.getPackageManager()
                .hasSystemFeature(android.content.pm.PackageManager.FEATURE_CAMERA_ANY)) {
            return false;
        }
        try (InputStream in = context.getAssets().open(MODEL_ASSET)) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Whether this build keeps the photos with what was read from them: debug builds only (see
     * {@code res/values/developer.xml}).
     */
    public static boolean keepsRecords(android.content.Context context) {
        return context.getResources().getBoolean(R.bool.developer_records);
    }

    /**
     * Opens the camera for a photo of the board.
     *
     * @param occupancy which squares the board reports as occupied, {@code [rank][file]}
     * @param whiteToMove who is to move in the position on the board
     * @param flipped whether the board on screen is shown from Black's side
     */
    public void start(boolean[][] occupancy, boolean whiteToMove, boolean flipped) {
        photos.clear();
        readings.clear();
        takePhoto(occupancy, whiteToMove, flipped);
    }

    /** Opens the camera for one more photo of the same position. */
    private void takePhoto(boolean[][] occupancy, boolean whiteToMove, boolean flipped) {
        this.occupancy = occupancy;
        this.whiteToMove = whiteToMove;
        this.flipped = flipped;
        File photo = photoFile();
        photo.getParentFile().mkdirs();
        photo.delete();
        host.onCamera(true);
        camera.launch(
                new Intent(activity, BoardCameraActivity.class)
                        .putExtra(BoardCameraActivity.EXTRA_FILE, photo.getPath()));
    }

    /** Closes whatever dialog of the flow is open, e.g. when the activity goes away. */
    public void dismiss() {
        if (chooser != null && chooser.isShowing()) {
            chooser.dismiss();
        }
        chooser = null;
        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
        }
        dialog = null;
        showAgain = null;
    }

    /**
     * To be called when the screen has been turned. The activity is not created anew then, and a
     * dialog that is open keeps the layout and the measures of the other orientation, so the check
     * dialog is built again - with the photos and with what the user has changed so far.
     */
    public void onRotated() {
        Runnable again = showAgain;
        if (again != null) {
            dismiss();
            again.run();
        }
    }

    public void shutdown() {
        dismiss();
        worker.shutdown();
    }

    private File photoFile() {
        return new File(new File(activity.getCacheDir(), PHOTO_FOLDER), "photo.jpg");
    }

    private void onCameraDone(ActivityResult done) {
        host.onCamera(false);
        if (done.getResultCode() != Activity.RESULT_OK || occupancy == null) {
            return;
        }
        // What the board reports now counts, not what it reported when the flow began. If it has
        // changed, the photos taken before show another position and are dropped.
        boolean[][] now = host.currentOccupancy();
        if (now != null) {
            if (!java.util.Arrays.deepEquals(now, occupancy)) {
                photos.clear();
                readings.clear();
            }
            occupancy = now;
        }
        Intent data = done.getData();
        automatic =
                data != null && data.getBooleanExtra(BoardCameraActivity.EXTRA_AUTOMATIC, false);
        liveMillis = data == null ? 0 : data.getLongExtra(BoardCameraActivity.EXTRA_LIVE_MILLIS, 0);
        dialog =
                new MaterialAlertDialogBuilder(activity)
                        .setMessage(R.string.board_photo_reading)
                        .setCancelable(false)
                        .show();
        final boolean[][] occupied = occupancy;
        worker.execute(
                () -> {
                    byte[] jpeg = null;
                    BoardPhotoRecognizer.Reading reading = null;
                    int sample = 1;
                    try {
                        jpeg = Files.readAllBytes(photoFile().toPath());
                        // From here on the photo is only held in memory, until the position is
                        // confirmed or given up.
                        photoFile().delete();
                        BitmapFactory.Options bounds = new BitmapFactory.Options();
                        bounds.inJustDecodeBounds = true;
                        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length, bounds);
                        while (Math.max(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE) {
                            sample *= 2;
                        }
                        BitmapFactory.Options options = new BitmapFactory.Options();
                        options.inSampleSize = sample;
                        Bitmap bitmap =
                                BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length, options);
                        int w = bitmap.getWidth();
                        int h = bitmap.getHeight();
                        int[] pixels = new int[w * h];
                        bitmap.getPixels(pixels, 0, w, 0, 0, w, h);
                        bitmap.recycle();
                        Photo photo = Photo.upright(w, h, pixels, Photo.jpegOrientation(jpeg));
                        reading = recognizer().read(photo);
                    } catch (Exception | OutOfMemoryError e) {
                        Log.w(TAG, "reading the photo failed", e);
                    }
                    final byte[] photoBytes = jpeg;
                    final BoardPhotoRecognizer.Reading read = reading;
                    final int sampled = sample;
                    main.post(() -> onRead(photoBytes, read, occupied, sampled));
                });
    }

    private BoardPhotoRecognizer recognizer() throws Exception {
        if (recognizer == null) {
            try (InputStream in = activity.getAssets().open(MODEL_ASSET)) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] buffer = new byte[1 << 16];
                for (int n = in.read(buffer); n > 0; n = in.read(buffer)) {
                    bytes.write(buffer, 0, n);
                }
                recognizer = new BoardPhotoRecognizer(bytes.toByteArray());
            }
        }
        return recognizer;
    }

    private void onRead(
            byte[] jpeg, BoardPhotoRecognizer.Reading reading, boolean[][] occupied, int sampled) {
        dismiss();
        if (activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        if (reading == null) {
            // Kept by itself; the photos read so far stay as they are.
            saveRecord(
                    java.util.Collections.singletonList(jpeg),
                    occupied,
                    null,
                    sampled,
                    null,
                    "not_found");
            Toast.makeText(activity, R.string.board_photo_not_found, Toast.LENGTH_LONG).show();
            if (readings.isEmpty()) {
                return;
            }
        } else {
            photos.add(jpeg);
            readings.add(reading);
        }
        showCheckDialog(
                new ArrayList<>(photos),
                recognizer.recognize(new ArrayList<>(readings), occupied),
                occupied,
                sampled);
    }

    /**
     * The recognised position on a board; every piece can be changed. No square is marked as
     * uncertain: the network is often sure of a piece it reads wrong, so the marks held only about
     * half of the wrong squares and made the others look checked.
     */
    private void showCheckDialog(
            List<byte[]> jpeg,
            BoardPhotoRecognizer.Result result,
            boolean[][] occupied,
            int sampled) {
        showCheckDialog(jpeg, result, occupied, sampled, piecesOf(result.placement));
    }

    private void showCheckDialog(
            List<byte[]> jpeg,
            BoardPhotoRecognizer.Result result,
            boolean[][] occupied,
            int sampled,
            Piece[] pieces) {
        View content = activity.getLayoutInflater().inflate(R.layout.dialog_board_photo, null);
        BoardView boardView = content.findViewById(R.id.photoBoardView);
        android.widget.TextView count = content.findViewById(R.id.photoCount);
        count.setText(
                activity.getResources()
                        .getQuantityString(R.plurals.board_photo_count, jpeg.size(), jpeg.size()));
        content.findViewById(R.id.photoAdd)
                .setOnClickListener(
                        v -> {
                            // What was changed by hand is read anew with the next photo.
                            dismiss();
                            takePhoto(occupied, whiteToMove, flipped);
                        });
        boardView.setFlipped(flipped);
        boardView.setBoard(pieces);
        boardView.setInteractive(true);
        boardView.setOnSquareTapListener(
                square -> {
                    // Any square can be changed, also emptied or filled against what the board
                    // reports: the user sees the board, and a sensor can be wrong.
                    choosePiece(
                            pieces[square.ordinal()],
                            chosen -> {
                                pieces[square.ordinal()] = chosen;
                                boardView.setBoard(pieces);
                            });
                });
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity);
        if (content.findViewById(R.id.photoTitle) == null) {
            // Lying on its side the layout has the title beside the board, to leave it the height.
            builder.setTitle(R.string.board_photo_check_title);
        }
        AlertDialog check =
                builder.setView(content)
                        .setPositiveButton(R.string.action_ok, null)
                        .setNegativeButton(
                                R.string.action_cancel,
                                (d, which) ->
                                        saveRecord(
                                                jpeg, occupied, result, sampled, null, "cancelled"))
                        .setNeutralButton(
                                R.string.board_photo_retake,
                                (d, which) -> {
                                    saveRecord(jpeg, occupied, result, sampled, null, "retaken");
                                    start(occupied, whiteToMove, flipped);
                                })
                        .setCancelable(false)
                        .create();
        check.show();
        // Set after show(), so an unplayable position keeps the dialog open for correcting.
        check.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(
                        v -> {
                            boolean[][] now = host.currentOccupancy();
                            if (now != null && !java.util.Arrays.deepEquals(now, occupied)) {
                                // Pieces were moved while the dialog was open: what is shown
                                // is not what stands on the board any more.
                                Toast.makeText(
                                                activity,
                                                R.string.board_photo_changed,
                                                Toast.LENGTH_LONG)
                                        .show();
                                return;
                            }
                            String placement = placementOf(pieces);
                            String fen;
                            try {
                                fen = BoardPhotoRecognizer.fen(placement, whiteToMove);
                            } catch (InvalidPositionException e) {
                                host.onInvalidPosition(e);
                                return;
                            }
                            saveRecord(jpeg, occupied, result, sampled, placement, "accepted");
                            check.dismiss();
                            host.onPositionRead(fen);
                        });
        check.setOnDismissListener(
                d -> {
                    if (dialog == check) {
                        showAgain = null;
                    }
                });
        dialog = check;
        showAgain = () -> showCheckDialog(jpeg, result, occupied, sampled, pieces);
    }

    /**
     * Asks which piece stands on a square: the six pieces of each colour as they look on the board
     * of the app, the one standing there now set off, and a button to empty the square.
     */
    private void choosePiece(Piece current, java.util.function.Consumer<Piece> chosen) {
        float dp = activity.getResources().getDisplayMetrics().density;
        android.widget.LinearLayout content = new android.widget.LinearLayout(activity);
        content.setOrientation(android.widget.LinearLayout.VERTICAL);
        content.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        int pad = Math.round(16 * dp);
        content.setPadding(pad, Math.round(8 * dp), pad, 0);
        final AlertDialog[] open = new AlertDialog[1];
        int highlight =
                com.google.android.material.color.MaterialColors.getColor(
                        content, androidx.appcompat.R.attr.colorPrimary);
        for (int colour = 0; colour < 2; colour++) {
            android.widget.LinearLayout row = new android.widget.LinearLayout(activity);
            row.setGravity(android.view.Gravity.CENTER);
            for (int kind = 0; kind < 6; kind++) {
                int index = colour * 6 + kind;
                Piece piece = Piece.fromFenSymbol(String.valueOf(SYMBOLS.charAt(index)));
                android.widget.ImageButton button = new android.widget.ImageButton(activity);
                button.setImageResource(PIECE_IMAGES[index]);
                button.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
                button.setContentDescription(GLYPHS[index]);
                int inner = Math.round(4 * dp);
                button.setPadding(inner, inner, inner, inner);
                android.graphics.drawable.GradientDrawable back =
                        new android.graphics.drawable.GradientDrawable();
                back.setCornerRadius(8 * dp);
                // A light ground like a square of the board: the black pieces would be lost on
                // the dark dialog of the night theme, the white ones keep their outline on it.
                back.setColor(PIECE_GROUND);
                if (piece == current) {
                    back.setStroke(Math.round(3 * dp), highlight);
                }
                button.setBackground(back);
                android.widget.LinearLayout.LayoutParams size =
                        new android.widget.LinearLayout.LayoutParams(0, Math.round(52 * dp), 1f);
                int gap = Math.round(2 * dp);
                size.setMargins(gap, gap, gap, gap);
                row.addView(button, size);
                button.setOnClickListener(
                        v -> {
                            open[0].dismiss();
                            chosen.accept(piece);
                        });
            }
            content.addView(
                    row,
                    new android.widget.LinearLayout.LayoutParams(
                            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));
        }
        open[0] =
                new MaterialAlertDialogBuilder(activity)
                        .setTitle(R.string.board_photo_piece_title)
                        .setView(content)
                        .setNeutralButton(
                                R.string.board_photo_empty, (d, which) -> chosen.accept(Piece.NONE))
                        .setNegativeButton(R.string.action_cancel, null)
                        .show();
        chooser = open[0];
    }

    private String appVersion() {
        try {
            return activity.getPackageManager()
                    .getPackageInfo(activity.getPackageName(), 0)
                    .versionName;
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            return "?";
        }
    }

    private static Piece[] piecesOf(String placement) {
        Piece[] pieces = new Piece[64];
        java.util.Arrays.fill(pieces, Piece.NONE);
        String[] rows = placement.split("/");
        for (int i = 0; i < 8; i++) {
            int file = 0;
            for (char c : rows[i].toCharArray()) {
                if (Character.isDigit(c)) {
                    file += c - '0';
                } else {
                    pieces[(7 - i) * 8 + file++] = Piece.fromFenSymbol(String.valueOf(c));
                }
            }
        }
        return pieces;
    }

    private static String placementOf(Piece[] pieces) {
        StringBuilder out = new StringBuilder();
        for (int rank = 7; rank >= 0; rank--) {
            int gap = 0;
            for (int file = 0; file < 8; file++) {
                Piece piece = pieces[rank * 8 + file];
                if (piece == null || piece == Piece.NONE) {
                    gap++;
                    continue;
                }
                if (gap > 0) {
                    out.append(gap);
                    gap = 0;
                }
                out.append(piece.getFenSymbol());
            }
            if (gap > 0) {
                out.append(gap);
            }
            if (rank > 0) {
                out.append('/');
            }
        }
        return out.toString();
    }

    /**
     * In a debug build, keeps what happened: the photo and a small text file next to it. {@code
     * corrected} is the placement the user confirmed, {@code null} if they did not.
     */
    private void saveRecord(
            List<byte[]> jpeg,
            boolean[][] occupied,
            BoardPhotoRecognizer.Result result,
            int sampled,
            String corrected,
            String outcome) {
        if (!keepsRecords(activity) || jpeg == null || jpeg.isEmpty() || jpeg.get(0) == null) {
            return;
        }
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.ROOT).format(new Date());
        File base = activity.getExternalFilesDir(null);
        if (base == null) {
            return;
        }
        File folder = new File(new File(base, PHOTO_FOLDER), stamp);
        StringBuilder squares = new StringBuilder();
        for (int rank = 7; rank >= 0; rank--) {
            for (int file = 0; file < 8; file++) {
                squares.append(occupied[rank][file] ? '1' : '0');
            }
        }
        StringBuilder json = new StringBuilder("{\n");
        json.append("  \"app\": \"").append(appVersion()).append("\",\n");
        json.append("  \"outcome\": \"").append(outcome).append("\",\n");
        json.append("  \"white_to_move\": ").append(whiteToMove).append(",\n");
        // photo.jpg, photo_2.jpg, ...: read together; what follows on the view is of the last one
        json.append("  \"photos\": ").append(jpeg.size()).append(",\n");
        json.append(
                "  \"reading\": \"mean of each window as it is and with its neighbours hidden\",\n");
        json.append("  \"occupancy\": \"").append(squares).append("\",\n");
        json.append("  \"photo_shrunk_by\": ").append(sampled).append(",\n");
        json.append("  \"taken\": \"").append(automatic ? "automatic" : "button").append("\",\n");
        json.append("  \"live_search_millis\": ").append(liveMillis).append(",\n");
        if (result != null) {
            json.append("  \"recognised\": \"").append(result.placement).append("\",\n");
            json.append("  \"uncertain\": \"")
                    .append(String.join(" ", result.uncertainSquares))
                    .append("\",\n");
            json.append("  \"white_side_uncertain\": ")
                    .append(result.whiteSideUncertain)
                    .append(",\n");
            json.append("  \"corners\": [");
            for (int i = 0; i < 4; i++) {
                json.append(i > 0 ? ", " : "")
                        .append(
                                String.format(
                                        Locale.ROOT,
                                        "[%.1f, %.1f]",
                                        result.corners[i][0],
                                        result.corners[i][1]));
            }
            json.append("],\n");
            json.append("  \"camera_degrees_above_table\": ")
                    .append(String.format(Locale.ROOT, "%.1f", result.guide.elevation))
                    .append(",\n");
            json.append("  \"board_share_of_photo\": ")
                    .append(String.format(Locale.ROOT, "%.2f", result.guide.share))
                    .append(",\n");
            json.append("  \"camera_advice\": \"").append(result.guide.advice).append("\",\n");
            json.append("  \"millis_area_windows_pieces\": [")
                    .append(result.millis[0])
                    .append(", ")
                    .append(result.millis[1])
                    .append(", ")
                    .append(result.millis[2])
                    .append("],\n");
        }
        json.append("  \"corrected\": ")
                .append(corrected == null ? "null" : "\"" + corrected + "\"")
                .append("\n}\n");
        final String text = json.toString();
        worker.execute(
                () -> {
                    try {
                        if (!folder.mkdirs()) {
                            return;
                        }
                        for (int i = 0; i < jpeg.size(); i++) {
                            String name = i == 0 ? "photo.jpg" : "photo_" + (i + 1) + ".jpg";
                            Files.write(new File(folder, name).toPath(), jpeg.get(i));
                        }
                        Files.write(
                                new File(folder, "info.json").toPath(),
                                text.getBytes(StandardCharsets.UTF_8));
                    } catch (IOException e) {
                        Log.w(TAG, "saving the record failed", e);
                    }
                });
    }
}
