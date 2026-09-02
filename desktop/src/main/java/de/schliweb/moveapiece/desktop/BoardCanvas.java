/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.desktop;

import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.Square;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;

/**
 * Renders an 8x8 chess board and turns clicks into move requests. Holds no chess rules itself: it
 * asks {@link MoveSource} for legal destinations and reports chosen moves to {@link
 * OnMoveListener}, both driven by whoever owns the {@link de.schliweb.moveapiece.logic.ChessGame}
 * ({@link GameController}). Ported from the Android app's {@code ui.BoardView} (same
 * square/highlight model, same piece artwork), redrawn with JavaFX's Canvas/GraphicsContext instead
 * of {@code android.graphics.Canvas}.
 */
public class BoardCanvas extends Canvas {

    public interface MoveSource {
        List<Square> legalDestinationsFrom(Square from);

        boolean hasOwnPieceOn(Square square);
    }

    public interface OnMoveListener {
        void onMoveChosen(Square from, Square to);
    }

    // Same colors as app/src/main/res/values/colors.xml, converted from
    // Android's #AARRGGBB to JavaFX's #RRGGBBAA where an alpha channel is used.
    private static final Color LIGHT = Color.web("#F0D9B5");
    private static final Color DARK = Color.web("#B58863");
    private static final Color SELECTED = Color.web("#F6F669");
    private static final Color LEGAL_MOVE = Color.web("#6DC46E8A");
    private static final Color LAST_MOVE = Color.web("#CCCC33A6");
    private static final Color CHECK = Color.web("#553030E0");
    private static final Color TRAINING_HINT = Color.web("#3399CCA6");

    private static final EnumMap<Piece, Image> PIECE_IMAGES = loadPieceImages();

    private Piece[] boardState = new Piece[64];
    private boolean flipped = false;
    private Square selected = null;
    private List<Square> legalDestinations = new ArrayList<>();
    private Square lastMoveFrom = null;
    private Square lastMoveTo = null;
    private Square checkedKingSquare = null;
    private Square trainingHintFrom = null;
    private Square trainingHintTo = null;

    private MoveSource moveSource;
    private OnMoveListener onMoveListener;
    private boolean interactive = true;

    public BoardCanvas() {
        for (int i = 0; i < boardState.length; i++) {
            boardState[i] = Piece.NONE;
        }
        setOnMouseClicked(this::handleClick);
    }

    private static EnumMap<Piece, Image> loadPieceImages() {
        EnumMap<Piece, Image> images = new EnumMap<>(Piece.class);
        images.put(Piece.WHITE_KING, loadImage("piece_wk.png"));
        images.put(Piece.WHITE_QUEEN, loadImage("piece_wq.png"));
        images.put(Piece.WHITE_ROOK, loadImage("piece_wr.png"));
        images.put(Piece.WHITE_BISHOP, loadImage("piece_wb.png"));
        images.put(Piece.WHITE_KNIGHT, loadImage("piece_wn.png"));
        images.put(Piece.WHITE_PAWN, loadImage("piece_wp.png"));
        images.put(Piece.BLACK_KING, loadImage("piece_bk.png"));
        images.put(Piece.BLACK_QUEEN, loadImage("piece_bq.png"));
        images.put(Piece.BLACK_ROOK, loadImage("piece_br.png"));
        images.put(Piece.BLACK_BISHOP, loadImage("piece_bb.png"));
        images.put(Piece.BLACK_KNIGHT, loadImage("piece_bn.png"));
        images.put(Piece.BLACK_PAWN, loadImage("piece_bp.png"));
        return images;
    }

    private static Image loadImage(String resourceName) {
        return new Image(BoardCanvas.class.getResourceAsStream("pieces/" + resourceName));
    }

    public void setMoveSource(MoveSource moveSource) {
        this.moveSource = moveSource;
    }

    public void setOnMoveListener(OnMoveListener listener) {
        this.onMoveListener = listener;
    }

    public void setInteractive(boolean interactive) {
        this.interactive = interactive;
        if (!interactive) {
            clearSelection();
        }
    }

    public void setFlipped(boolean flipped) {
        this.flipped = flipped;
        draw();
    }

    public void setLastMove(Square from, Square to) {
        this.lastMoveFrom = from;
        this.lastMoveTo = to;
        draw();
    }

    public void setCheckedKingSquare(Square square) {
        this.checkedKingSquare = square;
        draw();
    }

    /** The opening trainer's next expected move, or {@code null, null} to clear it. */
    public void setTrainingHint(Square from, Square to) {
        this.trainingHintFrom = from;
        this.trainingHintTo = to;
        draw();
    }

    /**
     * @param pieces indexed by {@link Square#ordinal()}, {@link Piece#NONE} for empty squares
     */
    public void setBoard(Piece[] pieces) {
        this.boardState = pieces;
        draw();
    }

    public void clearSelection() {
        selected = null;
        legalDestinations = new ArrayList<>();
        draw();
    }

    /**
     * Resizes the (always square) canvas to {@code size}x{@code size} device pixels and redraws.
     */
    public void setSize(double size) {
        setWidth(size);
        setHeight(size);
        draw();
    }

    @Override
    public boolean isResizable() {
        return false;
    }

    private void draw() {
        double squareSize = getWidth() / 8.0;
        if (squareSize <= 0) {
            return;
        }
        GraphicsContext gc = getGraphicsContext2D();
        gc.clearRect(0, 0, getWidth(), getHeight());
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                Square square = squareAt(row, col);
                boolean isLight = (row + col) % 2 == 0;
                double left = col * squareSize;
                double top = row * squareSize;

                gc.setFill(isLight ? LIGHT : DARK);
                gc.fillRect(left, top, squareSize, squareSize);

                if (square == lastMoveFrom || square == lastMoveTo) {
                    gc.setFill(LAST_MOVE);
                    gc.fillRect(left, top, squareSize, squareSize);
                }
                if (square == trainingHintFrom || square == trainingHintTo) {
                    gc.setFill(TRAINING_HINT);
                    gc.fillRect(left, top, squareSize, squareSize);
                }
                if (square == checkedKingSquare) {
                    gc.setFill(CHECK);
                    gc.fillRect(left, top, squareSize, squareSize);
                }
                if (square == selected) {
                    gc.setFill(SELECTED);
                    gc.fillRect(left, top, squareSize, squareSize);
                }

                Piece piece = boardState[square.ordinal()];
                if (piece != null && piece != Piece.NONE) {
                    Image img = PIECE_IMAGES.get(piece);
                    if (img != null) {
                        gc.drawImage(img, left, top, squareSize, squareSize);
                    }
                }

                if (legalDestinations.contains(square)) {
                    double cx = left + squareSize / 2;
                    double cy = top + squareSize / 2;
                    boolean capture = boardState[square.ordinal()] != Piece.NONE;
                    if (capture) {
                        double radius = squareSize * 0.46;
                        gc.setStroke(LEGAL_MOVE);
                        gc.setLineWidth(squareSize * 0.08);
                        gc.strokeOval(cx - radius, cy - radius, radius * 2, radius * 2);
                    } else {
                        double radius = squareSize * 0.16;
                        gc.setFill(LEGAL_MOVE);
                        gc.fillOval(cx - radius, cy - radius, radius * 2, radius * 2);
                    }
                }
            }
        }
    }

    private void handleClick(javafx.scene.input.MouseEvent event) {
        double squareSize = getWidth() / 8.0;
        if (!interactive || squareSize <= 0) {
            return;
        }
        int col = (int) (event.getX() / squareSize);
        int row = (int) (event.getY() / squareSize);
        if (col < 0 || col > 7 || row < 0 || row > 7) {
            return;
        }
        handleTap(squareAt(row, col));
    }

    private void handleTap(Square tapped) {
        if (selected != null && legalDestinations.contains(tapped)) {
            Square from = selected;
            clearSelection();
            if (onMoveListener != null) {
                onMoveListener.onMoveChosen(from, tapped);
            }
            return;
        }

        if (moveSource != null && moveSource.hasOwnPieceOn(tapped)) {
            selected = tapped;
            legalDestinations = moveSource.legalDestinationsFrom(tapped);
        } else {
            clearSelection();
        }
        draw();
    }

    private Square squareAt(int row, int col) {
        int file = flipped ? 7 - col : col;
        int rank = flipped ? row : 7 - row;
        int ordinal = rank * 8 + file;
        return Square.squareAt(ordinal);
    }
}
