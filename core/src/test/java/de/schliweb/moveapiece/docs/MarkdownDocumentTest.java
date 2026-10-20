/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.docs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import de.schliweb.moveapiece.docs.MarkdownDocument.Block;
import de.schliweb.moveapiece.docs.MarkdownDocument.BlockType;
import de.schliweb.moveapiece.docs.MarkdownDocument.Span;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;

public class MarkdownDocumentTest {

    @Test
    public void titleHeadingsParagraphsAndBullets() {
        MarkdownDocument doc =
                MarkdownDocument.parse(
                        "# Help\n\nFirst line\nsecond line.\n\n## Playing\n\n"
                                + "- one\n  continued\n- two\nafter\n",
                        "fallback");
        assertEquals("Help", doc.title());
        List<Block> blocks = doc.blocks();
        assertEquals(5, blocks.size());
        assertBlock(blocks.get(0), BlockType.PARAGRAPH, "First line second line.");
        assertBlock(blocks.get(1), BlockType.HEADING, "Playing");
        assertBlock(blocks.get(2), BlockType.BULLET, "one continued");
        assertBlock(blocks.get(3), BlockType.BULLET, "two");
        assertBlock(blocks.get(4), BlockType.PARAGRAPH, "after");
    }

    @Test
    public void fallbackTitleWithoutHashLine() {
        assertEquals("fallback", MarkdownDocument.parse("text", "fallback").title());
    }

    @Test
    public void windowsLineEndings() {
        MarkdownDocument doc = MarkdownDocument.parse("# T\r\n\r\n- a\r\n  b\r\n", "x");
        assertEquals("T", doc.title());
        assertBlock(doc.blocks().get(0), BlockType.BULLET, "a b");
    }

    @Test
    public void inlineStylesNest() {
        List<Span> spans = MarkdownDocument.inline("a **bold** *it `c` x* `code`");
        assertEquals(
                List.of(
                        new Span("a ", false, false, false),
                        new Span("bold", true, false, false),
                        new Span(" ", false, false, false),
                        new Span("it ", false, true, false),
                        new Span("c", false, true, true),
                        new Span(" x", false, true, false),
                        new Span(" ", false, false, false),
                        new Span("code", false, false, true)),
                spans);
    }

    @Test
    public void fencedBlockStaysPlainAndReflows() {
        MarkdownDocument doc =
                MarkdownDocument.parse(
                        "## License\n\n```\n  # not a heading\n- not a bullet, `show w'\n\n"
                                + "THE  SOFTWARE *IS*   PROVIDED\n```\n\nafter **bold**\n",
                        "x");
        List<Block> blocks = doc.blocks();
        assertEquals(4, blocks.size());
        assertBlock(blocks.get(0), BlockType.HEADING, "License");
        assertBlock(blocks.get(1), BlockType.VERBATIM, "# not a heading - not a bullet, `show w'");
        assertBlock(blocks.get(2), BlockType.VERBATIM, "THE SOFTWARE *IS* PROVIDED");
        assertEquals(
                List.of(new Span("THE SOFTWARE *IS* PROVIDED", false, false, false)),
                blocks.get(2).spans());
        assertEquals(BlockType.PARAGRAPH, blocks.get(3).type());
        assertTrue(blocks.get(3).spans().get(1).bold());
    }

    /**
     * Every shipped document parses into a title and content, and no Markdown the parser does not
     * know (links, tables, deeper headings, stray emphasis markers) slips into the visible text.
     * License texts in fenced blocks are exempt: they are shown exactly as written.
     */
    @Test
    public void shippedDocumentsUseOnlyTheSupportedSubset() throws IOException {
        Path root = Path.of("..").toAbsolutePath().normalize();
        List<Path> files = new java.util.ArrayList<>();
        files.add(root.resolve("PRIVACY.md"));
        files.add(root.resolve("OPEN-SOURCE-LICENSES.md"));
        try (DirectoryStream<Path> help = Files.newDirectoryStream(root.resolve("help"), "*.md")) {
            help.forEach(files::add);
        }
        assertEquals(8, files.size());
        for (Path file : files) {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            MarkdownDocument doc = MarkdownDocument.parse(text, "");
            assertTrue(file + ": title", !doc.title().isEmpty());
            assertTrue(file + ": content", doc.blocks().size() > 3);
            for (Block block : doc.blocks()) {
                if (block.type() == BlockType.VERBATIM) {
                    continue;
                }
                for (Span span : block.spans()) {
                    if (span.code()) {
                        continue; // permission names such as BLUETOOTH_SCAN
                    }
                    for (String marker : new String[] {"](", "|", "#", "*", "`", "_"}) {
                        assertTrue(
                                file + ": \"" + marker + "\" in " + span.text(),
                                !span.text().contains(marker));
                    }
                }
            }
        }
    }

    private static void assertBlock(Block block, BlockType type, String text) {
        assertEquals(type, block.type());
        assertEquals(text, plain(block));
    }

    private static String plain(Block block) {
        StringBuilder sb = new StringBuilder();
        for (Span span : block.spans()) {
            sb.append(span.text());
        }
        return sb.toString();
    }
}
