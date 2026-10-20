/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.desktop;

import de.schliweb.moveapiece.docs.MarkdownDocument;
import de.schliweb.moveapiece.docs.MarkdownDocument.Block;
import de.schliweb.moveapiece.docs.MarkdownDocument.Span;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Stage;

/**
 * Shows one Markdown file from the resources: the help in the host's language (the file name is the
 * {@code help_file} message, so the resource bundle picks the language), the privacy policy or the
 * open-source licenses. desktop/build.gradle copies them from the repository, so the text is
 * maintained in one place for GitHub, this app and the Android app (its {@code
 * ui.DocumentActivity}).
 */
final class DocumentWindow {

    static final String PRIVACY_POLICY = "PRIVACY.md";
    static final String LICENSES = "OPEN-SOURCE-LICENSES.md";

    /** One window per file: opening it again brings the open one to the front. */
    private static final Map<String, Stage> OPEN = new HashMap<>();

    private DocumentWindow() {}

    static void show(Stage owner, String file) {
        Stage open = OPEN.get(file);
        if (open != null) {
            open.toFront();
            return;
        }
        MarkdownDocument document = MarkdownDocument.parse(read(file), "MoveAPiece");

        VBox content = new VBox();
        content.getStyleClass().add("document");
        for (Block block : document.blocks()) {
            content.getChildren().add(blockNode(block));
        }
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("document-scroll");

        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.setTitle(document.title());
        Scene scene = new Scene(scroll, 560, 640);
        Styles.apply(scene);
        stage.setScene(scene);
        stage.setOnHidden(e -> OPEN.remove(file));
        OPEN.put(file, stage);
        stage.show();
    }

    private static String read(String file) {
        try (InputStream in = DocumentWindow.class.getResourceAsStream("docs/" + file)) {
            if (in == null) {
                // Copied in by desktop/build.gradle; missing means a broken build.
                throw new IllegalStateException("docs/" + file + " missing from the resources");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Node blockNode(Block block) {
        TextFlow flow = new TextFlow();
        for (Span span : block.spans()) {
            Text text = new Text(span.text());
            text.getStyleClass().add("document-text");
            if (span.bold()) {
                text.getStyleClass().add("document-bold");
            }
            if (span.italic()) {
                text.getStyleClass().add("document-italic");
            }
            if (span.code()) {
                text.getStyleClass().add("document-code");
            }
            flow.getChildren().add(text);
        }
        switch (block.type()) {
            case HEADING:
                flow.getStyleClass().add("document-heading");
                return flow;
            case BULLET:
                Text bullet = new Text("•");
                bullet.getStyleClass().add("document-text");
                flow.setMinWidth(0);
                HBox row = new HBox(8, bullet, flow);
                HBox.setHgrow(flow, Priority.ALWAYS);
                row.getStyleClass().add("document-bullet");
                return row;
            default:
                flow.getStyleClass().add("document-paragraph");
                return flow;
        }
    }
}
