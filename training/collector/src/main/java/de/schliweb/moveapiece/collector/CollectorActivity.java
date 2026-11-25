/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.collector;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.InputType;
import android.util.Log;
import android.util.Size;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExposureState;
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
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.common.util.concurrent.ListenableFuture;
import de.schliweb.moveapiece.collector.databinding.ActivityCollectorBinding;
import de.schliweb.moveapiece.vision.BoardFrameFinder;
import de.schliweb.moveapiece.vision.Photo;
import de.schliweb.moveapiece.vision.PhotoGuide;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Collects training photos for the board vision.
 *
 * <p>Board: the positions of {@link Positions}, one after the other. The playing area is found in
 * the live picture and the view judged as in the app ({@link PhotoGuide}); a photo is taken by
 * itself when the view is good and held still, until every band of camera angles has its photos.
 * The position shown is written next to each photo, so nothing has to be labelled afterwards -
 * provided the board really shows it.
 *
 * <p>Pieces: one piece alone, photographed with the button or in a series. A series takes one photo
 * by itself at each band of angles the phone is tilted through and held still at; then the piece is
 * turned a quarter and the next series shows its next side. The tilt of the phone, from its gravity
 * sensor, stands in for the camera angle and is written next to each photo.
 *
 * <p>Board and pieces are kept apart, each under a name with its measures, so that later any set of
 * pieces can be put on any board: {@code boards/<board>/surroundings_<n>/<position>/} and {@code
 * pieces/<set>/<piece>/} under {@code files/collection} of the app on the phone; nothing leaves it.
 */
public final class CollectorActivity extends AppCompatActivity implements SensorEventListener {

    private static final String TAG = "Collector";

    /**
     * The camera angles to cover, in degrees above the table: lower limits of the bands and the
     * upper limit of the last one. They span the views the app accepts.
     */
    static final int[] BANDS = {50, 55, 60, 65, 70};

    /** Photos per band and position that are taken by themselves. */
    private static final int PER_BAND = 2;

    /**
     * The same for the empty board. Its photos are what the pieces are put on later, so each is the
     * background of many training pictures - and they cost no setting up.
     */
    private static final int PER_BAND_EMPTY = 10;

    /**
     * The angles to cover with a single piece. They reach further than those of the board: a piece
     * on a square near the camera is seen from much more steeply than the board as a whole, one on
     * a far square from much flatter. In 77 photos of the empty board from 50 to 70 degrees the
     * squares were seen from 37 to 88 degrees. The angle reckoned here from the tilt came out some
     * 4 degrees higher than the one measured on the cut-out pieces (reckoned 50 to 85, measured 47
     * to 79), so the bands run from 40 to 95.
     */
    static final int[] PIECE_BANDS = {40, 45, 50, 55, 60, 65, 70, 75, 80, 85, 90, 95};

    /**
     * Sides a piece is photographed from, a quarter turn of the piece apart; a series takes one
     * photo per band of each. A knight looks different from each, and the light falls differently
     * on every piece.
     */
    private static final int SIDES = 4;

    /** How much larger the live picture is shown while a single piece is photographed. */
    private static final float PIECE_ZOOM = 3f;

    /**
     * How much darker than the camera would choose a white piece is photographed, in stops. The
     * camera sets its exposure by the mat and the table, and a white piece from board distance came
     * out burnt: about half of each white piece seen from above was plain white, with nothing left
     * of its shading (cut-outs from 70 degrees up, all three colours at their end). With two stops
     * (white king, one side) nothing was burnt in all three colours, but seen from straight above 4
     * % of the piece still had its blue at the end: the white balance for the dark table makes the
     * piece blue. Three stops leave room for a lit side.
     */
    private static final float WHITE_PIECE_STOPS_DARKER = 3f;

    /** Side of the small grey picture two live pictures are compared by. */
    /**
     * Photos of one film of a board: taken one after the other while the phone is moved slowly, to
     * see what reading several views of one position together is worth (four photos of a played
     * position from different places read together were right nearly always, one alone in half to
     * two thirds of the cases).
     */
    private static final int FILM_SHOTS = 12;

    private static final int THUMB = 32;

    /**
     * Two live pictures that differ by less than this on average (of 255) show the same: the phone
     * is held still. In a first series, with a limit of 3.0, the difference was 1.4 in the middle
     * and 3.7 at the ninth tenth, and the phone counted as still in two of three pictures, moving
     * or not. What a hand really holding still gives is not measured yet; the values are logged.
     */
    private static final double STILL = 1.5;

    /** The live picture has to stay the same this long before a series takes a photo. */
    private static final long STILL_MILLIS = 600;

    /**
     * A series takes the next photo only with the phone tilted this many degrees differently: not
     * the same view twice where two bands meet.
     */
    private static final double TILT_ON = 3.0;

    /**
     * The colour at a point of the frame around the piece may differ this much from that of most of
     * the frame (both colour channels of the live picture added, of 255); more, and something other
     * than the surface is there. Of 42 photos of the white king on the green mat, the 4 whose
     * cut-out failed all had about 31 at the worst point (the white of the piece or of what stood
     * behind it); the photos with nothing but mat on the frame had at most 12.
     */
    private static final double OFF_SURFACE = 20.0;

    /** The board and set this was first made for; its measures are known. */
    private static final String PEGASUS = "Pegasus";

    private static final double PEGASUS_SQUARE = 40;
    private static final double[] PEGASUS_FEET = {30, 27, 24, 24, 24, 20};

    private static final Size LIVE_SIZE = new Size(640, 480);
    private static final int STEADY_FRAMES = 3;
    private static final double STEADY = 0.03;

    /**
     * Between two photos taken by themselves a corner of the board has to move by this share of the
     * picture: another angle, distance or side, not the same photo twice.
     */
    private static final double MOVE_ON = 0.08;

    /**
     * A piece standing on the line of {@link OutlineView#TARGET_Y} is below the middle of the
     * picture and so seen this many degrees more steeply than the phone is tilted. (Upright 3:4
     * picture, focal length 0.6 of its diagonal as everywhere here: the diagonal is 5/3 of the
     * width, the focal length equals the width, the height is 4/3 of it.)
     */
    private static final double BELOW_MIDDLE =
            Math.toDegrees(Math.atan((OutlineView.TARGET_Y - 0.5) * 4.0 / 3.0));

    private static final String MODEL_ASSET = "piece_recogniser.fp16.onnx";

    /**
     * A board photo in which more squares than this look differently occupied than in the position
     * shown is reported: the board then probably shows another position. The recogniser gets a
     * square or two wrong by itself, so fewer are not reported - and a single forgotten move, which
     * changes two squares, is not noticed this way.
     */
    private static final int MAY_DIFFER = 2;

    /**
     * The camera may look at the board from this many degrees higher or lower in the photo than in
     * the live picture it was taken on; more, and the phone moved on while the photo was taken. Of
     * the first 40 photos of the empty board 36 were within 1.3 degrees; the others were 2.9, 2.9,
     * 4.4 and 14.4 off.
     */
    private static final double MAY_MOVE = 3.0;

    /** What the check of a board photo says instead of a count of squares. */
    private static final int NO_BOARD = -1;

    private static final int MOVED = -2;

    /** End of the name of a board photo that does not seem to show its position. */
    private static final String DIFFERS = "_differs";

    private static final String TYPES = "KQRBNP";
    private static final String[] GLYPHS = {"♚", "♛", "♜", "♝", "♞", "♟"};

    private final ExecutorService searcher = Executors.newSingleThreadExecutor();
    private final ExecutorService writer = Executors.newSingleThreadExecutor();
    private ActivityCollectorBinding binding;
    private ImageCapture capture;
    private Camera camera;
    private float stopsDarker;
    private SensorManager sensors;
    private List<Positions.Position> positions;

    private volatile boolean boardMode = true;
    private volatile double tilt = Double.NaN;
    private int position;

    /** Names of the board and of the set of pieces being photographed; also their folders. */
    private String board;

    private String pieceSet;

    /**
     * Which way the phone points, in degrees about the vertical; only differences mean anything.
     */
    private volatile double heading = Double.NaN;

    /** A series of photos of a piece is running. */
    private boolean series;

    /** A film of the board is running: photos one after the other, into a folder of their own. */
    private boolean film;

    private File filmFolder;
    private int filmShots;
    private long filmStarted;

    /** Tilt of the phone at the last photo of the piece chosen; NaN before the first. */
    private double shotTilt = Double.NaN;

    /** Photos of the piece chosen, per side and band; kept up by {@link #showCounts()}. */
    private int[][] pieceCounts = new int[SIDES][PIECE_BANDS.length - 1];

    /** The side of the piece the photo being taken belongs to. */
    private int sideOfShot;

    /** Whether the frame around the piece shows nothing but the surface; main thread. */
    private boolean frameClear = true;

    private android.media.MediaActionSound sounds;

    /** Checks the board photos against the position; {@code null} without the model. */
    private de.schliweb.moveapiece.vision.BoardPhotoRecognizer recognizer;

    /**
     * What the last check of a board photo found wrong, or {@code null}; shown until it is right.
     */
    private String warning;

    /**
     * The surroundings the board is being photographed in, counted from 1. Each has its own folder
     * and its own count of photos per position, so that all positions can be gone through again
     * somewhere else.
     */
    private int surroundings;

    private boolean taking;

    // What the live picture last showed; main thread.
    private PhotoGuide lastGuide;
    private float[] lastOutline;

    /** Where the board was in the last photo of this position, or {@code null}. */
    private float[] lastShot;

    // Only touched by the searcher thread.
    private double[][] lastCorners;
    private int steadyFrames;
    private int[] lastThumb;
    private long stillSince;
    private long lastLogged;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityCollectorBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        ViewCompat.setOnApplyWindowInsetsListener(
                binding.root,
                (view, windowInsets) -> {
                    Insets bars =
                            windowInsets.getInsets(
                                    WindowInsetsCompat.Type.systemBars()
                                            | WindowInsetsCompat.Type.displayCutout());
                    view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                    return WindowInsetsCompat.CONSUMED;
                });
        binding.preview.setScaleType(PreviewView.ScaleType.FIT_CENTER);
        // A TextureView, so that the live picture can be shown enlarged (see zoomOnPiece).
        binding.preview.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
        binding.outline.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> zoomOnPiece());
        sensors = (SensorManager) getSystemService(SENSOR_SERVICE);
        positions = Positions.all();
        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        position = prefs.getInt("position", 0) % positions.size();
        board = prefs.getString("board", PEGASUS);
        pieceSet = prefs.getString("piece_set", PEGASUS);
        surroundings = prefs.getInt("surroundings_" + board, 1);
        binding.surroundings.setOnClickListener(v -> askForNewSurroundings());
        binding.boardName.setOnClickListener(v -> choose(true));
        binding.setName.setOnClickListener(v -> choose(false));
        binding.series.setOnClickListener(
                v -> {
                    if (boardMode) {
                        setFilm(!film);
                    } else {
                        setSeries(!series);
                    }
                });
        binding.discard.setOnClickListener(v -> askToDiscard());
        sounds = new android.media.MediaActionSound();
        sounds.load(android.media.MediaActionSound.SHUTTER_CLICK);
        sounds.load(android.media.MediaActionSound.STOP_VIDEO_RECORDING);
        showNames();
        writeMeasures(true);
        writeMeasures(false);

        addButtons(binding.pieceColour, new String[] {"♔", "♚"});
        addButtons(binding.pieceType, GLYPHS);
        binding.pieceColour.addOnButtonCheckedListener((group, id, checked) -> pieceChosen());
        binding.pieceType.addOnButtonCheckedListener((group, id, checked) -> pieceChosen());

        binding.mode.check(binding.modeBoard.getId());
        binding.mode.addOnButtonCheckedListener(
                (group, id, checked) -> {
                    if (checked) {
                        setMode(id == binding.modeBoard.getId());
                    }
                });
        // The diagram is small next to the camera picture: a tap shows it large over the picture,
        // another tap there puts it away again. No photos are taken meanwhile.
        binding.diagram.setOnClickListener(
                v -> binding.diagramLargePanel.setVisibility(View.VISIBLE));
        binding.diagramLargePanel.setOnClickListener(v -> v.setVisibility(View.GONE));
        binding.previous.setOnClickListener(v -> showPosition(position - 1));
        binding.next.setOnClickListener(v -> showPosition(position + 1));
        binding.shutter.setOnClickListener(v -> takePhoto(false));
        showPosition(position);
        setMode(true);

        ActivityResultLauncher<String> permission =
                registerForActivityResult(
                        new ActivityResultContracts.RequestPermission(),
                        granted -> {
                            if (granted) {
                                startCamera();
                            } else {
                                Toast.makeText(this, R.string.no_camera, Toast.LENGTH_LONG).show();
                            }
                        });
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            permission.launch(Manifest.permission.CAMERA);
        }
    }

    private void addButtons(MaterialButtonToggleGroup group, String[] labels) {
        for (String label : labels) {
            MaterialButton button =
                    new MaterialButton(
                            this,
                            null,
                            com.google.android.material.R.attr.materialButtonOutlinedStyle);
            button.setId(View.generateViewId());
            button.setText(label);
            button.setTextSize(22);
            button.setMinWidth(0);
            button.setMinimumWidth(0);
            // Six buttons side by side leave little room: without the side padding of a text
            // button, and never shortened to dots.
            button.setPaddingRelative(0, button.getPaddingTop(), 0, button.getPaddingBottom());
            button.setEllipsize(null);
            button.setMaxLines(1);
            group.addView(
                    button,
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        }
        group.check(group.getChildAt(0).getId());
    }

    @Override
    protected void onResume() {
        super.onResume();
        Sensor gravity = sensors.getDefaultSensor(Sensor.TYPE_GRAVITY);
        if (gravity == null) {
            gravity = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }
        if (gravity != null) {
            sensors.registerListener(this, gravity, SensorManager.SENSOR_DELAY_UI);
        }
        Sensor turning = sensors.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);
        if (turning != null) {
            sensors.registerListener(this, turning, SensorManager.SENSOR_DELAY_UI);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        setFilm(false);
        sensors.unregisterListener(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        searcher.shutdown();
        writer.shutdown();
        sounds.release();
    }

    /**
     * How steeply the camera on the back looks down: 0 degrees with the phone upright, looking at
     * the horizon, 90 with the phone lying flat, looking straight down. With the object in the
     * middle of the picture this is the angle above the table the camera sees it from.
     */
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_GAME_ROTATION_VECTOR) {
            float[] rotation = new float[9];
            float[] angles = new float[3];
            SensorManager.getRotationMatrixFromVector(rotation, event.values);
            SensorManager.getOrientation(rotation, angles);
            heading = Math.toDegrees(angles[0]);
            return;
        }
        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];
        double length = Math.sqrt(x * x + y * y + z * z);
        if (length < 1e-3) {
            return;
        }
        double now = Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, z / length))));
        // Smoothed a little; the accelerometer, where there is no gravity sensor, jitters.
        tilt = Double.isNaN(tilt) ? now : 0.8 * tilt + 0.2 * now;
        if (!boardMode) {
            showPieceHint();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    // --- what is being photographed ---------------------------------------------------------

    private void setMode(boolean board) {
        boardMode = board;
        binding.boardPanel.setVisibility(board ? View.VISIBLE : View.GONE);
        binding.piecePanel.setVisibility(board ? View.GONE : View.VISIBLE);
        setSeries(false);
        setFilm(false);
        binding.diagramLargePanel.setVisibility(View.GONE);
        binding.outline.show(null, 0.75f, false);
        binding.outline.showTarget(!board);
        zoomOnPiece();
        setExposure();
        lastGuide = null;
        lastOutline = null;
        lastShot = null;
        binding.hint.setText(board ? R.string.hint_searching : R.string.hint_piece);
        binding.info.setText("");
        showCounts();
    }

    /**
     * Shows the live picture enlarged around the frame for a single piece: the frame is small, so
     * that the piece is photographed from afar, and hard to aim at in its true size. Only what is
     * shown is enlarged; the photo and the picture the frame is checked in stay whole.
     */
    private void zoomOnPiece() {
        float zoom = boardMode ? 1f : PIECE_ZOOM;
        float[] middle = binding.outline.middleOfFrame();
        for (View view : new View[] {binding.preview, binding.outline}) {
            view.setPivotX(middle[0]);
            view.setPivotY(middle[1]);
            view.setScaleX(zoom);
            view.setScaleY(zoom);
        }
        // Pulled to the middle of what is seen of the camera, where the frame would else sit low.
        float down = boardMode ? 0f : binding.outline.getHeight() / 2f - middle[1];
        binding.preview.setTranslationY(down);
        binding.outline.setTranslationY(down);
        binding.outline.invalidate();
    }

    private void askForNewSurroundings() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.surroundings_title)
                .setMessage(getString(R.string.surroundings_text, surroundings + 1))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(
                        R.string.surroundings_start,
                        (dialog, which) -> {
                            surroundings++;
                            getPreferences(MODE_PRIVATE)
                                    .edit()
                                    .putInt("surroundings_" + board, surroundings)
                                    .apply();
                            showPosition(0);
                        })
                .show();
    }

    private void showPosition(int index) {
        setFilm(false);
        position = Math.floorMod(index, positions.size());
        lastShot = null;
        warning = null;
        binding.warning.setVisibility(View.GONE);
        getPreferences(MODE_PRIVATE).edit().putInt("position", position).apply();
        Positions.Position p = positions.get(position);
        binding.diagram.show(p.placement);
        binding.diagramLarge.show(p.placement);
        binding.positionTitle.setText((position + 1) + "/" + positions.size() + "  " + p.title);
        binding.surroundings.setText(getString(R.string.surroundings, surroundings));
        binding.positionMoves.setText(
                p.moves.isEmpty() ? getString(R.string.set_up) : getString(R.string.play, p.moves));
        showCounts();
    }

    /** The piece chosen in the piece mode, e.g. {@code wK} or {@code bN}. */
    private String piece() {
        int colour = indexOfChecked(binding.pieceColour);
        int type = indexOfChecked(binding.pieceType);
        return (colour == 0 ? "w" : "b") + TYPES.charAt(Math.max(0, type));
    }

    private static int indexOfChecked(MaterialButtonToggleGroup group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            if (group.getChildAt(i).getId() == group.getCheckedButtonId()) {
                return i;
            }
        }
        return 0;
    }

    /** Where the photos of what is being photographed now go. */
    private File folder() {
        return boardMode
                ? new File(
                        new File(home(true), "surroundings_" + surroundings),
                        positions.get(position).id)
                : new File(home(false), piece());
    }

    /** The folder of the board or of the set of pieces chosen. */
    private File home(boolean ofBoard) {
        File base = new File(getExternalFilesDir(null), "collection");
        return ofBoard
                ? new File(new File(base, "boards"), board)
                : new File(new File(base, "pieces"), pieceSet);
    }

    /** The bands of angles of what is being photographed now. */
    private int[] bands() {
        return boardMode ? BANDS : PIECE_BANDS;
    }

    /** The band an angle falls into, or -1 if it is outside all of them. */
    static int band(int[] bands, double degrees) {
        for (int i = 0; i + 1 < bands.length; i++) {
            if (degrees >= bands[i] && degrees < bands[i + 1]) {
                return i;
            }
        }
        return -1;
    }

    private static String bandName(int[] bands, int band) {
        return bands[band] + "–" + bands[band + 1] + "°";
    }

    /** How many photos per band the position shown gets by itself. */
    private int perBand() {
        return positions.get(position).placement.matches(".*[A-Za-z].*")
                ? PER_BAND
                : PER_BAND_EMPTY;
    }

    /** Photos already taken of the current position, per band. */
    private int[] counts() {
        int[] bands = BANDS;
        int[] counts = new int[bands.length - 1];
        String[] names = folder().list();
        if (names != null) {
            for (String name : names) {
                for (int i = 0; i < counts.length; i++) {
                    if (name.endsWith("_" + bands[i] + ".jpg")) {
                        counts[i]++;
                    }
                }
            }
        }
        return counts;
    }

    private static final java.util.regex.Pattern PIECE_PHOTO =
            java.util.regex.Pattern.compile(".*_s(\\d+)_(\\d+)\\.jpg");

    /** Reads the photos of the piece chosen into {@link #pieceCounts}. */
    private void countPiecePhotos() {
        int[][] counts = new int[SIDES][PIECE_BANDS.length - 1];
        String[] names = new File(home(false), piece()).list();
        if (names != null) {
            for (String name : names) {
                java.util.regex.Matcher m = PIECE_PHOTO.matcher(name);
                if (!m.matches()) {
                    continue;
                }
                int side = Integer.parseInt(m.group(1));
                int from = Integer.parseInt(m.group(2));
                for (int i = 0; i < counts[0].length; i++) {
                    if (side >= 1 && side <= SIDES && PIECE_BANDS[i] == from) {
                        counts[side - 1][i]++;
                    }
                }
            }
        }
        pieceCounts = counts;
    }

    /**
     * The side of the piece chosen that still lacks photos, counted from 1; {@code SIDES + 1} when
     * all have theirs.
     */
    private int side() {
        for (int s = 0; s < SIDES; s++) {
            for (int count : pieceCounts[s]) {
                if (count == 0) {
                    return s + 1;
                }
            }
        }
        return SIDES + 1;
    }

    private void showCounts() {
        StringBuilder text = new StringBuilder();
        if (boardMode) {
            int[] counts = counts();
            for (int i = 0; i < counts.length; i++) {
                text.append(i > 0 ? "   " : "")
                        .append(bandName(BANDS, i))
                        .append(": ")
                        .append(counts[i])
                        .append("/")
                        .append(perBand());
            }
        } else {
            countPiecePhotos();
            int side = Math.min(side(), SIDES);
            text.append(getString(R.string.side_of, side, SIDES)).append("  ");
            for (int i = 0; i < pieceCounts[0].length; i++) {
                text.append("  ")
                        .append(PIECE_BANDS[i])
                        .append("° ")
                        .append(pieceCounts[side - 1][i] > 0 ? "✓" : "–");
            }
        }
        binding.bands.setText(text);
    }

    // --- camera -----------------------------------------------------------------------------

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> provider = ProcessCameraProvider.getInstance(this);
        provider.addListener(
                () -> {
                    try {
                        bind(provider.get());
                    } catch (Exception e) {
                        Log.w(TAG, "no camera", e);
                        Toast.makeText(this, R.string.no_camera, Toast.LENGTH_LONG).show();
                    }
                },
                ContextCompat.getMainExecutor(this));
    }

    private void bind(ProcessCameraProvider provider) {
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
        preview.setSurfaceProvider(binding.preview.getSurfaceProvider());
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
        camera =
                provider.bindToLifecycle(
                        this, CameraSelector.DEFAULT_BACK_CAMERA, preview, live, capture);
        setExposure();
    }

    /** Looks for the board in one live picture. Runs on the searcher thread. */
    private void search(@NonNull ImageProxy image) {
        try {
            if (!boardMode) {
                lastCorners = null;
                steadyFrames = 0;
                int[] now = thumb(image);
                long time = SystemClock.elapsedRealtime();
                double moved = lastThumb == null ? 255 : difference(now, lastThumb);
                if (moved > STILL) {
                    stillSince = 0;
                } else if (stillSince == 0) {
                    stillSince = time;
                }
                final boolean still = stillSince != 0 && time - stillSince >= STILL_MILLIS;
                final double off = offSurface(image);
                lastThumb = now;
                if (time - lastLogged > 1000) {
                    lastLogged = time;
                    Log.i(
                            TAG,
                            String.format(
                                    Locale.ROOT,
                                    "piece live: differs from the picture before %.1f, still"
                                            + " %b, frame off the surface by %.1f",
                                    moved,
                                    still,
                                    off));
                }
                runOnUiThread(() -> pieceLive(still, off <= OFF_SURFACE));
                return;
            }
            lastThumb = null;
            Photo photo = upright(image);
            BoardFrameFinder.Result found = BoardFrameFinder.find(photo);
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
            final PhotoGuide shown = guide;
            final float[] corners = outline;
            final float aspect = (float) photo.width / photo.height;
            final boolean steady = steadyFrames >= STEADY_FRAMES;
            runOnUiThread(() -> showBoard(shown, corners, aspect, steady));
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

    /**
     * The brightness of a live picture, shrunk to a few cells: enough to see whether it changed.
     */
    private static int[] thumb(ImageProxy image) {
        ImageProxy.PlaneProxy plane = image.getPlanes()[0];
        ByteBuffer buffer = plane.getBuffer();
        int stride = plane.getRowStride();
        int step = plane.getPixelStride();
        int cellW = image.getWidth() / THUMB;
        int cellH = image.getHeight() / THUMB;
        int[] out = new int[THUMB * THUMB];
        for (int j = 0; j < THUMB; j++) {
            for (int i = 0; i < THUMB; i++) {
                int sum = 0;
                // Sixteen pixels spread over the cell, so that the noise of single ones evens out.
                for (int v = 0; v < 4; v++) {
                    int y = j * cellH + (2 * v + 1) * cellH / 8;
                    for (int u = 0; u < 4; u++) {
                        int x = i * cellW + (2 * u + 1) * cellW / 8;
                        sum += buffer.get(y * stride + x * step) & 0xff;
                    }
                }
                out[j * THUMB + i] = sum / 16;
            }
        }
        return out;
    }

    /**
     * How far the colour on the frame around the piece is, at its worst point, from the colour of
     * most of the frame: small when the frame shows the surface all the way round. Only then the
     * whole piece stands in front of the surface and can be cut out. Looks at the top of the frame
     * and its sides down to the line for the foot (the frame of {@link OutlineView}).
     */
    private static double offSurface(ImageProxy image) {
        if (image.getPlanes().length < 3) {
            return 0;
        }
        int w = image.getWidth();
        int h = image.getHeight();
        int turn = image.getImageInfo().getRotationDegrees();
        boolean sideways = turn == 90 || turn == 270;
        double width = sideways ? h : w;
        double height = sideways ? w : h;
        double foot = OutlineView.TARGET_Y * height;
        double left = (0.5 - OutlineView.FRAME_HALF_WIDTH) * width;
        double right = (0.5 + OutlineView.FRAME_HALF_WIDTH) * width;
        double top = foot - OutlineView.FRAME_HEIGHT * width;
        final int across = 12;
        final int down = 12;
        double[][] at = new double[across + 2 * down][];
        for (int i = 0; i < across; i++) {
            at[i] = new double[] {left + (right - left) * i / (across - 1), top};
        }
        for (int i = 1; i <= down; i++) {
            double y = top + (foot - top) * i / down;
            at[across + 2 * i - 2] = new double[] {left, y};
            at[across + 2 * i - 1] = new double[] {right, y};
        }
        int[][] colour = new int[2][at.length];
        for (int c = 0; c < 2; c++) {
            ImageProxy.PlaneProxy plane = image.getPlanes()[c + 1];
            ByteBuffer buffer = plane.getBuffer();
            int stride = plane.getRowStride();
            int step = plane.getPixelStride();
            for (int k = 0; k < at.length; k++) {
                // From the upright picture back into the one the camera delivers (see upright()).
                double x;
                double y;
                switch (turn) {
                    case 90:
                        x = at[k][1];
                        y = h - 1 - at[k][0];
                        break;
                    case 180:
                        x = w - 1 - at[k][0];
                        y = h - 1 - at[k][1];
                        break;
                    case 270:
                        x = w - 1 - at[k][1];
                        y = at[k][0];
                        break;
                    default:
                        x = at[k][0];
                        y = at[k][1];
                        break;
                }
                int sum = 0;
                int n = 0;
                for (int v = -2; v < 2; v++) {
                    for (int u = -2; u < 2; u++) {
                        int cx = (int) (x / 2) + u;
                        int cy = (int) (y / 2) + v;
                        int index = cy * stride + cx * step;
                        if (cx >= 0
                                && cy >= 0
                                && cx < w / 2
                                && cy < h / 2
                                && index < buffer.limit()) {
                            sum += buffer.get(index) & 0xff;
                            n++;
                        }
                    }
                }
                colour[c][k] = n == 0 ? -1 : sum / n;
            }
        }
        int[] usual = new int[2];
        for (int c = 0; c < 2; c++) {
            int[] sorted = colour[c].clone();
            java.util.Arrays.sort(sorted);
            usual[c] = sorted[sorted.length / 2];
        }
        double worst = 0;
        for (int k = 0; k < at.length; k++) {
            if (colour[0][k] >= 0 && colour[1][k] >= 0) {
                worst =
                        Math.max(
                                worst,
                                Math.abs(colour[0][k] - usual[0])
                                        + Math.abs(colour[1][k] - usual[1]));
            }
        }
        return worst;
    }

    private static double difference(int[] a, int[] b) {
        long sum = 0;
        for (int i = 0; i < a.length; i++) {
            sum += Math.abs(a[i] - b[i]);
        }
        return (double) sum / a.length;
    }

    /**
     * The brightness of a live picture as a grey photo, turned upright (as in BoardCameraActivity
     * of the app).
     */
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

    // --- hints and taking photos ------------------------------------------------------------

    private void showBoard(PhotoGuide guide, float[] corners, float aspect, boolean steady) {
        if (!boardMode || (taking && !film) || isFinishing() || isDestroyed()) {
            return;
        }
        lastGuide = guide;
        lastOutline = corners;
        if (film) {
            // The film takes its photos by itself; the live picture only says where the board is.
            binding.outline.show(corners, aspect, false);
            if (guide != null) {
                binding.info.setText(
                        getString(R.string.info_board, tilt, guide.elevation, 100 * guide.share));
            }
            return;
        }
        boolean good = guide != null && guide.advice == PhotoGuide.Advice.GOOD;
        int[] counts = counts();
        int band = guide == null ? -1 : band(BANDS, guide.elevation);
        boolean wanted =
                good
                        && band >= 0
                        && counts[band] < perBand()
                        && movedOn(corners)
                        && binding.diagramLargePanel.getVisibility() != View.VISIBLE;
        binding.outline.show(corners, aspect, wanted);
        String hint = getString(R.string.hint_searching);
        if (guide != null) {
            binding.info.setText(
                    getString(R.string.info_board, tilt, guide.elevation, 100 * guide.share));
            switch (guide.advice) {
                case STEEPER:
                    hint = getString(R.string.hint_steeper);
                    break;
                case FLATTER:
                    hint = getString(R.string.hint_flatter);
                    break;
                case CLOSER:
                    hint = getString(R.string.hint_closer);
                    break;
                case FARTHER:
                    hint = getString(R.string.hint_farther);
                    break;
                default:
                    if (wanted) {
                        hint = getString(R.string.hint_hold);
                    } else if (band >= 0 && counts[band] < perBand()) {
                        hint = getString(R.string.hint_move_on);
                    } else {
                        hint = openBands(BANDS, counts, band, perBand());
                    }
                    break;
            }
        } else {
            binding.info.setText(getString(R.string.info_tilt, tilt));
        }
        binding.hint.setText(hint);
        if (wanted && steady) {
            takePhoto(true);
        }
    }

    private boolean movedOn(float[] corners) {
        if (lastShot == null || corners == null) {
            return true;
        }
        double worst = 0;
        for (int i = 0; i < 4; i++) {
            worst =
                    Math.max(
                            worst,
                            Math.hypot(
                                    corners[2 * i] - lastShot[2 * i],
                                    corners[2 * i + 1] - lastShot[2 * i + 1]));
        }
        return worst > MOVE_ON;
    }

    /** What to say when the view is good but its band of angles already has its photos. */
    private String openBands(int[] bands, int[] counts, int band, int each) {
        List<String> open = new ArrayList<>();
        for (int i = 0; i < counts.length; i++) {
            if (counts[i] < each) {
                open.add(bandName(bands, i));
            }
        }
        if (open.isEmpty()) {
            return getString(boardMode ? R.string.hint_position_done : R.string.hint_piece_done);
        }
        return getString(
                R.string.hint_band_full,
                band < 0 ? "" : bandName(bands, band),
                String.join(", ", open));
    }

    private void showPieceHint() {
        if (taking || binding == null) {
            return;
        }
        double angle = tilt + BELOW_MIDDLE;
        binding.info.setText(getString(R.string.info_piece, tilt, angle));
        int band = band(PIECE_BANDS, angle);
        if (angle < PIECE_BANDS[0]) {
            binding.hint.setText(R.string.hint_steeper);
        } else if (band < 0) {
            binding.hint.setText(R.string.hint_flatter);
        } else if (!frameClear) {
            binding.hint.setText(R.string.hint_frame);
        } else if (!series) {
            int side = side();
            if (side > SIDES) {
                binding.hint.setText(R.string.hint_piece_done);
            } else if (side > 1 && untouched(side)) {
                binding.hint.setText(getString(R.string.hint_turn, side, SIDES));
            } else {
                binding.hint.setText(R.string.hint_piece);
            }
        }
        binding.shutter.setEnabled(band >= 0);
    }

    /** Whether a side of the piece chosen has no photo yet. */
    private boolean untouched(int side) {
        for (int count : pieceCounts[side - 1]) {
            if (count > 0) {
                return false;
            }
        }
        return true;
    }

    /** Another piece was chosen: its photos start afresh, and no series runs on by itself. */
    /** Darker for a white piece, as the camera chooses for everything else. */
    private void setExposure() {
        stopsDarker = 0f;
        if (camera == null) {
            return;
        }
        ExposureState state = camera.getCameraInfo().getExposureState();
        if (!state.isExposureCompensationSupported()) {
            return;
        }
        boolean white = !boardMode && indexOfChecked(binding.pieceColour) == 0;
        float step = state.getExposureCompensationStep().floatValue();
        int index = white ? Math.round(-WHITE_PIECE_STOPS_DARKER / step) : 0;
        index = Math.max(state.getExposureCompensationRange().getLower(), index);
        stopsDarker = -index * step;
        camera.getCameraControl().setExposureCompensationIndex(index);
    }

    private void pieceChosen() {
        setExposure();
        setSeries(false);
        shotTilt = Double.NaN;
        showCounts();
    }

    private void setSeries(boolean on) {
        series = on;
        if (!boardMode) {
            binding.series.setText(on ? R.string.series_stop : R.string.series_start);
        }
    }

    /**
     * Starts or stops a film of the board: {@link #FILM_SHOTS} photos one after the other, as fast
     * as the camera takes them, while the phone is moved slowly. They go into a folder of their own
     * below the position (film_ and the time), numbered, each with its note, and are neither
     * counted for the angles nor checked against the position.
     */
    private void setFilm(boolean on) {
        if (on && (capture == null || taking)) {
            return;
        }
        boolean was = film;
        film = on;
        if (boardMode) {
            binding.series.setText(on ? R.string.film_stop : R.string.film_start);
        }
        binding.shutter.setEnabled(!on && !taking);
        if (on) {
            String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(new Date());
            filmFolder = new File(folder(), "film_" + stamp);
            filmShots = 0;
            filmStarted = android.os.SystemClock.elapsedRealtime();
            if (!filmFolder.isDirectory() && !filmFolder.mkdirs()) {
                film = false;
                binding.series.setText(R.string.film_start);
                binding.shutter.setEnabled(true);
                Toast.makeText(this, R.string.save_failed, Toast.LENGTH_LONG).show();
                return;
            }
            sounds.play(android.media.MediaActionSound.START_VIDEO_RECORDING);
            takeFilmShot();
        } else if (was) {
            sounds.play(android.media.MediaActionSound.STOP_VIDEO_RECORDING);
            binding.hint.setText(getString(R.string.film_done, filmShots));
        }
    }

    private void takeFilmShot() {
        if (!film || taking || capture == null) {
            return;
        }
        final File folder = filmFolder;
        final int shot = filmShots + 1;
        final String name = String.format(Locale.ROOT, "%03d", shot);
        StringBuilder json = new StringBuilder("{\n");
        json.append(boardNote(lastGuide, lastOutline));
        json.append("  \"film\": \"").append(folder.getName()).append("\",\n");
        json.append("  \"shot\": ").append(shot).append(",\n");
        json.append("  \"millis_since_start\": ")
                .append(android.os.SystemClock.elapsedRealtime() - filmStarted)
                .append(",\n");
        json.append(number("tilt_of_phone", tilt));
        json.append(number("heading_of_phone", heading));
        json.append("  \"taken\": \"film\"\n}\n");
        taking = true;
        File photo = new File(folder, name + ".jpg");
        capture.takePicture(
                new ImageCapture.OutputFileOptions.Builder(photo).build(),
                ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults results) {
                        writer.execute(
                                () -> {
                                    try {
                                        Files.write(
                                                new File(folder, name + ".json").toPath(),
                                                json.toString().getBytes(StandardCharsets.UTF_8));
                                    } catch (java.io.IOException e) {
                                        Log.w(TAG, "saving the note failed", e);
                                    }
                                });
                        taking = false;
                        filmShots = shot;
                        if (!film) {
                            binding.shutter.setEnabled(true);
                        } else if (shot >= FILM_SHOTS) {
                            setFilm(false);
                        } else {
                            binding.hint.setText(
                                    getString(R.string.film_running, shot, FILM_SHOTS));
                            takeFilmShot();
                        }
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException e) {
                        Log.w(TAG, "taking the photo of the film failed", e);
                        taking = false;
                        setFilm(false);
                        binding.hint.setText(R.string.save_failed);
                    }
                });
    }

    /** What the note of a board photo says about the board, the position and the view. */
    private String boardNote(PhotoGuide guide, float[] outline) {
        StringBuilder json = new StringBuilder();
        Positions.Position p = positions.get(position);
        json.append("  \"kind\": \"board\",\n");
        json.append("  \"board\": \"").append(this.board).append("\",\n");
        if (p.placement.matches(".*[A-Za-z].*")) {
            json.append("  \"piece_set\": \"").append(pieceSet).append("\",\n");
        }
        json.append("  \"surroundings\": ").append(surroundings).append(",\n");
        json.append("  \"position\": \"").append(p.id).append("\",\n");
        json.append("  \"fen\": \"").append(p.placement).append("\",\n");
        if (guide != null) {
            json.append(number("camera_degrees_above_table", guide.elevation));
            json.append(number("board_share_of_photo", guide.share));
            json.append("  \"camera_advice\": \"").append(guide.advice).append("\",\n");
        }
        if (outline != null) {
            json.append("  \"corners_in_live_picture\": [");
            for (int i = 0; i < 4; i++) {
                json.append(i > 0 ? ", " : "")
                        .append(
                                String.format(
                                        Locale.ROOT,
                                        "[%.4f, %.4f]",
                                        outline[2 * i],
                                        outline[2 * i + 1]));
            }
            json.append("],\n");
        }
        return json.toString();
    }

    /**
     * Called for every live picture of a piece. Shows whether the frame is clear and, in a series,
     * takes a photo when the phone is at an angle that this side of the piece still lacks, the
     * frame shows nothing but the surface and the phone is held still.
     */
    private void pieceLive(boolean still, boolean clear) {
        if (boardMode || isFinishing() || isDestroyed()) {
            return;
        }
        frameClear = clear;
        binding.outline.targetGood(clear);
        if (!series || taking) {
            return;
        }
        int side = side();
        if (side > SIDES) {
            setSeries(false);
            return;
        }
        int band = band(PIECE_BANDS, tilt + BELOW_MIDDLE);
        if (band < 0 || !clear) {
            return; // showPieceHint says what is wrong
        }
        if (pieceCounts[side - 1][band] > 0) {
            binding.hint.setText(openBands(PIECE_BANDS, pieceCounts[side - 1], band, 1));
        } else if (!Double.isNaN(shotTilt) && Math.abs(tilt - shotTilt) < TILT_ON) {
            binding.hint.setText(R.string.hint_series_move);
        } else if (!still) {
            binding.hint.setText(R.string.hint_hold);
        } else {
            takePhoto(true);
        }
    }

    // --- throwing photos away -----------------------------------------------------------------

    /** Offers to delete all photos of the position or piece shown: a try that went wrong. */
    private void askToDiscard() {
        File folder = folder();
        File[] files = folder.listFiles();
        int photos = 0;
        if (files != null) {
            for (File file : files) {
                photos += file.getName().endsWith(".jpg") ? 1 : 0;
            }
        }
        if (photos == 0) {
            Toast.makeText(this, R.string.discard_nothing, Toast.LENGTH_SHORT).show();
            return;
        }
        setSeries(false);
        String what =
                boardMode
                        ? positions.get(position).title
                                + ", "
                                + getString(R.string.surroundings_short, surroundings)
                        : piece() + ", " + pieceSet;
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.discard_title)
                .setMessage(getString(R.string.discard_text, photos, what))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(
                        R.string.discard,
                        (dialog, which) -> {
                            for (File file : files) {
                                if (!file.delete()) {
                                    Log.w(TAG, "could not delete " + file);
                                }
                            }
                            shotTilt = Double.NaN;
                            lastShot = null;
                            warning = null;
                            binding.warning.setVisibility(View.GONE);
                            showCounts();
                        })
                .show();
    }

    // --- boards and sets of pieces, by name ---------------------------------------------------

    private void showNames() {
        binding.boardName.setText(getString(R.string.board_name, board));
        binding.setName.setText(getString(R.string.set_name, pieceSet));
    }

    private Set<String> names(boolean ofBoard) {
        Set<String> names =
                new TreeSet<>(
                        getPreferences(MODE_PRIVATE)
                                .getStringSet(
                                        ofBoard ? "boards" : "piece_sets",
                                        Collections.singleton(PEGASUS)));
        names.add(ofBoard ? board : pieceSet);
        return names;
    }

    /** Lets the user pick another board or set, name a new one or change the measures. */
    private void choose(boolean ofBoard) {
        List<String> names = new ArrayList<>(names(ofBoard));
        List<String> items = new ArrayList<>(names);
        items.add(getString(ofBoard ? R.string.new_board : R.string.new_set));
        items.add(getString(R.string.measures, ofBoard ? board : pieceSet));
        new MaterialAlertDialogBuilder(this)
                .setTitle(ofBoard ? R.string.boards_title : R.string.sets_title)
                .setItems(
                        items.toArray(new String[0]),
                        (dialog, which) -> {
                            if (which < names.size()) {
                                use(ofBoard, names.get(which));
                            } else if (which == names.size()) {
                                askForName(ofBoard);
                            } else {
                                askForMeasures(ofBoard);
                            }
                        })
                .show();
    }

    private void askForName(boolean ofBoard) {
        EditText name = new EditText(this);
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        name.setHint(R.string.name_hint);
        new MaterialAlertDialogBuilder(this)
                .setTitle(ofBoard ? R.string.new_board : R.string.new_set)
                .setView(padded(name))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(
                        R.string.ok,
                        (dialog, which) -> {
                            // The name is also the name of the folder.
                            String clean =
                                    name.getText()
                                            .toString()
                                            .trim()
                                            .replaceAll("[^\\p{L}\\p{N}-]+", "_")
                                            .replaceAll("^_+|_+$", "");
                            if (!clean.isEmpty()) {
                                use(ofBoard, clean);
                                askForMeasures(ofBoard);
                            }
                        })
                .show();
    }

    private void use(boolean ofBoard, String name) {
        Set<String> names = names(ofBoard);
        names.add(name);
        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        SharedPreferences.Editor edit = prefs.edit();
        edit.putStringSet(ofBoard ? "boards" : "piece_sets", names);
        edit.putString(ofBoard ? "board" : "piece_set", name);
        edit.apply();
        if (ofBoard) {
            board = name;
            surroundings = prefs.getInt("surroundings_" + board, 1);
            showPosition(position);
        } else {
            pieceSet = name;
            pieceChosen();
        }
        showNames();
        writeMeasures(ofBoard);
    }

    /** What is measured of a board (the square) or of a set (the foot of each kind of piece). */
    private String[] measureKeys(boolean ofBoard) {
        if (ofBoard) {
            return new String[] {"square_mm_" + board};
        }
        String[] keys = new String[TYPES.length()];
        for (int i = 0; i < keys.length; i++) {
            keys[i] = "foot_mm_" + pieceSet + "_" + TYPES.charAt(i);
        }
        return keys;
    }

    /** The measures in millimetres, NaN where none was entered. */
    private double[] measures(boolean ofBoard) {
        String[] keys = measureKeys(ofBoard);
        boolean known = PEGASUS.equals(ofBoard ? board : pieceSet);
        double[] out = new double[keys.length];
        for (int i = 0; i < keys.length; i++) {
            float fallback =
                    !known ? Float.NaN : (float) (ofBoard ? PEGASUS_SQUARE : PEGASUS_FEET[i]);
            out[i] = getPreferences(MODE_PRIVATE).getFloat(keys[i], fallback);
        }
        return out;
    }

    private void askForMeasures(boolean ofBoard) {
        String[] keys = measureKeys(ofBoard);
        double[] now = measures(ofBoard);
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        EditText[] fields = new EditText[keys.length];
        for (int i = 0; i < keys.length; i++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            TextView label = new TextView(this);
            label.setText(
                    ofBoard
                            ? getString(R.string.square_mm)
                            : getString(R.string.foot_mm, GLYPHS[i]));
            label.setTextSize(16);
            fields[i] = new EditText(this);
            fields[i].setInputType(
                    InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            if (!Double.isNaN(now[i])) {
                fields[i].setText(String.format(Locale.ROOT, "%.1f", now[i]));
            }
            row.addView(
                    label,
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 2));
            row.addView(
                    fields[i],
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            rows.addView(row);
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.measures, ofBoard ? board : pieceSet))
                .setMessage(ofBoard ? R.string.measures_board : R.string.measures_set)
                .setView(padded(rows))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(
                        R.string.ok,
                        (dialog, which) -> {
                            SharedPreferences.Editor edit = getPreferences(MODE_PRIVATE).edit();
                            for (int i = 0; i < keys.length; i++) {
                                try {
                                    edit.putFloat(
                                            keys[i],
                                            Float.parseFloat(
                                                    fields[i]
                                                            .getText()
                                                            .toString()
                                                            .trim()
                                                            .replace(',', '.')));
                                } catch (NumberFormatException e) {
                                    edit.remove(keys[i]);
                                }
                            }
                            edit.apply();
                            writeMeasures(ofBoard);
                        })
                .show();
    }

    private View padded(View view) {
        int pad = Math.round(20 * getResources().getDisplayMetrics().density);
        LinearLayout box = new LinearLayout(this);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(
                view,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return box;
    }

    /**
     * Writes the measures next to the photos: board.json in the board's folder, set.json in the
     * set's.
     */
    private void writeMeasures(boolean ofBoard) {
        double[] values = measures(ofBoard);
        StringBuilder json = new StringBuilder("{\n");
        if (ofBoard) {
            json.append("  \"board\": \"").append(board).append("\",\n");
            json.append("  \"square_mm\": ").append(measure(values[0])).append("\n}\n");
        } else {
            json.append("  \"piece_set\": \"").append(pieceSet).append("\",\n");
            json.append("  \"foot_mm\": {");
            for (int i = 0; i < values.length; i++) {
                json.append(i > 0 ? ", " : "")
                        .append('"')
                        .append(TYPES.charAt(i))
                        .append("\": ")
                        .append(measure(values[i]));
            }
            json.append("}\n}\n");
        }
        File file = new File(home(ofBoard), ofBoard ? "board.json" : "set.json");
        writer.execute(
                () -> {
                    File folder = file.getParentFile();
                    if (folder == null || (!folder.isDirectory() && !folder.mkdirs())) {
                        Log.w(TAG, "no folder for the measures");
                        return;
                    }
                    try {
                        Files.write(
                                file.toPath(), json.toString().getBytes(StandardCharsets.UTF_8));
                    } catch (java.io.IOException e) {
                        Log.w(TAG, "saving the measures failed", e);
                    }
                });
    }

    private static String measure(double value) {
        return Double.isNaN(value) ? "null" : String.format(Locale.ROOT, "%.1f", value);
    }

    private void takePhoto(boolean automatic) {
        if (taking || capture == null) {
            return;
        }
        final boolean board = boardMode;
        final PhotoGuide guide = lastGuide;
        final float[] outline = lastOutline;
        final double sensorTilt = tilt;
        // The angle the photo is filed under: seen from the board where there is one.
        double angle;
        if (board) {
            angle = guide != null ? guide.elevation : sensorTilt;
        } else {
            angle = sensorTilt + BELOW_MIDDLE;
        }
        final int[] bands = bands();
        int band = band(bands, angle);
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.ROOT).format(new Date());
        final int side = Math.min(side(), SIDES);
        String name =
                stamp
                        + (board ? "" : "_s" + side)
                        + "_"
                        + (band < 0 ? "x" : String.valueOf(bands[band]));
        File folder = folder();
        if (!folder.isDirectory() && !folder.mkdirs()) {
            Toast.makeText(this, R.string.save_failed, Toast.LENGTH_LONG).show();
            return;
        }
        StringBuilder json = new StringBuilder("{\n");
        if (board) {
            json.append(boardNote(guide, outline));
        } else {
            json.append("  \"kind\": \"piece\",\n");
            json.append("  \"piece_set\": \"").append(pieceSet).append("\",\n");
            json.append("  \"piece\": \"").append(piece()).append("\",\n");
            json.append(number("line_for_the_foot_at", OutlineView.TARGET_Y));
            json.append(number("stops_darker", stopsDarker));
            json.append("  \"side\": ").append(side).append(",\n");
            json.append(number("heading_of_phone", heading));
            shotTilt = sensorTilt;
            sideOfShot = side;
        }
        json.append(number("tilt_of_phone", sensorTilt));
        json.append("  \"taken\": \"").append(automatic ? "automatic" : "button").append("\"\n}\n");

        taking = true;
        lastShot = board ? outline : lastShot;
        binding.shutter.setEnabled(false);
        File photo = new File(folder, name + ".jpg");
        capture.takePicture(
                new ImageCapture.OutputFileOptions.Builder(photo).build(),
                ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults results) {
                        final String placement = board ? positions.get(position).placement : null;
                        writer.execute(
                                () -> {
                                    String text = json.toString();
                                    String noted = name;
                                    if (placement != null) {
                                        double[] seenFrom = {Double.NaN};
                                        int differ =
                                                check(
                                                        photo,
                                                        placement,
                                                        guide == null
                                                                ? Double.NaN
                                                                : guide.elevation,
                                                        seenFrom);
                                        text =
                                                text.replace(
                                                        "  \"taken\"",
                                                        number(
                                                                        "camera_degrees_in_photo",
                                                                        seenFrom[0])
                                                                + "  \"taken\"");
                                        text =
                                                text.replace(
                                                        "  \"taken\"",
                                                        "  \"squares_that_differ\": "
                                                                + differ
                                                                + ",\n  \"taken\"");
                                        if (differ > MAY_DIFFER || differ < 0) {
                                            // Kept, but under a name that is not counted: the
                                            // angle is still open once the board is put right.
                                            // The same for a photo the board is not found in or
                                            // that shows it differently than the live picture.
                                            noted = name + DIFFERS;
                                            if (!photo.renameTo(new File(folder, noted + ".jpg"))) {
                                                noted = name;
                                            }
                                        }
                                        runOnUiThread(() -> checked(differ));
                                    }
                                    try {
                                        Files.write(
                                                new File(folder, noted + ".json").toPath(),
                                                text.getBytes(StandardCharsets.UTF_8));
                                    } catch (java.io.IOException e) {
                                        Log.w(TAG, "saving the note failed", e);
                                    }
                                });
                        if (automatic) {
                            sounds.play(android.media.MediaActionSound.SHUTTER_CLICK);
                            binding.root.performHapticFeedback(
                                    android.view.HapticFeedbackConstants.LONG_PRESS);
                        }
                        done(getString(R.string.saved, band < 0 ? "–" : bandName(bands, band)));
                        if (!board && series && side() != sideOfShot) {
                            // This side of the piece has all its photos: the piece is turned on
                            // (or done) before the next series.
                            setSeries(false);
                            sounds.play(android.media.MediaActionSound.STOP_VIDEO_RECORDING);
                        }
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException e) {
                        Log.w(TAG, "taking the photo failed", e);
                        done(getString(R.string.save_failed));
                    }
                });
    }

    /**
     * Checks a board photo. First the photo itself: the board has to be found in it and seen as the
     * live picture showed it - a good view from nearly the same angle - or the phone moved while it
     * was taken ({@link #NO_BOARD}, {@link #MOVED}). Then in how many squares the board looks
     * differently occupied than the position, with White on the side where it fits best; 0 if that
     * cannot be told for lack of the model. Runs on the writer thread.
     *
     * @param live degrees above the table in the live picture, or NaN
     * @param seenFrom gets the degrees above the table in the photo
     */
    private int check(File file, String placement, double live, double[] seenFrom) {
        Photo upright;
        try {
            byte[] jpeg = Files.readAllBytes(file.toPath());
            android.graphics.BitmapFactory.Options options =
                    new android.graphics.BitmapFactory.Options();
            options.inSampleSize = 2;
            android.graphics.Bitmap bitmap =
                    android.graphics.BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length, options);
            int w = bitmap.getWidth();
            int h = bitmap.getHeight();
            int[] pixels = new int[w * h];
            bitmap.getPixels(pixels, 0, w, 0, 0, w, h);
            bitmap.recycle();
            upright = Photo.upright(w, h, pixels, Photo.jpegOrientation(jpeg));
            BoardFrameFinder.Result found = BoardFrameFinder.find(upright);
            if (found == null) {
                return NO_BOARD;
            }
            PhotoGuide guide = PhotoGuide.of(found.corners, upright.width, upright.height);
            seenFrom[0] = guide.elevation;
            if (guide.advice != PhotoGuide.Advice.GOOD
                    || Math.abs(guide.elevation - live) > MAY_MOVE) {
                return MOVED;
            }
        } catch (Exception | OutOfMemoryError e) {
            Log.w(TAG, "checking the photo failed", e);
            return 0;
        }
        try {
            if (recognizer == null) {
                try (java.io.InputStream in = getAssets().open(MODEL_ASSET)) {
                    java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
                    byte[] buffer = new byte[1 << 16];
                    for (int n = in.read(buffer); n > 0; n = in.read(buffer)) {
                        bytes.write(buffer, 0, n);
                    }
                    recognizer =
                            new de.schliweb.moveapiece.vision.BoardPhotoRecognizer(
                                    bytes.toByteArray());
                }
            }
            boolean[][] seen = recognizer.seenOccupied(upright);
            if (seen == null) {
                return NO_BOARD;
            }
            return de.schliweb.moveapiece.vision.PositionCheck.squaresThatDiffer(seen, placement);
        } catch (Exception | OutOfMemoryError e) {
            Log.w(TAG, "checking the position failed", e);
            return 0;
        }
    }

    private void checked(int differ) {
        warning =
                differ > MAY_DIFFER
                        ? getString(R.string.warning_position, differ)
                        : differ == MOVED
                                ? getString(R.string.warning_moved)
                                : differ == NO_BOARD ? getString(R.string.warning_no_board) : null;
        binding.warning.setText(warning == null ? "" : warning);
        binding.warning.setVisibility(warning == null ? View.GONE : View.VISIBLE);
        showCounts();
    }

    private void done(String message) {
        taking = false;
        binding.shutter.setEnabled(!film);
        binding.hint.setText(message);
        showCounts();
    }

    private static String number(String name, double value) {
        return Double.isNaN(value)
                ? ""
                : String.format(Locale.ROOT, "  \"%s\": %.2f,\n", name, value);
    }
}
