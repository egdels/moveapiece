/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.docs;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The help, the privacy policy and the open-source licenses (HELP*.md in help/, PRIVACY.md and
 * OPEN-SOURCE-LICENSES.md in the repository root), parsed into blocks of styled spans that each app
 * renders with its own widgets. Only the subset of Markdown those files use: a "#" title, "##"
 * headings, paragraphs, "-" bullets with indented continuation lines, inline {@code `code`}, {@code
 * **bold**} and {@code *italic*}, and fenced blocks between two {@code ```} lines. A fenced block
 * holds text that must stay as written, such as a license: each of its paragraphs becomes a {@link
 * BlockType#VERBATIM} block, reflowed but without any inline markup. The files deliberately use no
 * tables or link syntax so that this stays small; a URL is plain text.
 */
public record MarkdownDocument(String title, List<Block> blocks) {

    public enum BlockType {
        HEADING,
        PARAGRAPH,
        BULLET,
        /** A paragraph from a fenced block: plain text, shown like a paragraph. */
        VERBATIM
    }

    public record Block(BlockType type, List<Span> spans) {}

    /** A run of text with one style; {@code code} text is shown in a monospace font. */
    public record Span(String text, boolean bold, boolean italic, boolean code) {}

    private static final Pattern INLINE =
            Pattern.compile("`([^`]+)`|\\*\\*([^*]+)\\*\\*|\\*([^*]+)\\*");

    /** {@code fallbackTitle} is used when the text has no "#" line. */
    public static MarkdownDocument parse(String markdown, String fallbackTitle) {
        Parser parser = new Parser();
        String title = fallbackTitle;
        for (String raw : markdown.split("\\r?\\n", -1)) {
            String line = raw.stripTrailing();
            if (line.strip().startsWith("```")) {
                parser.flush();
                parser.fenced = !parser.fenced;
                continue;
            }
            if (parser.fenced) {
                if (line.isBlank()) {
                    parser.flush();
                } else {
                    parser.verbatim.add(line.strip());
                }
                continue;
            }
            if (line.isBlank()) {
                parser.flush();
            } else if (line.startsWith("# ")) {
                parser.flush();
                title = line.substring(2).trim();
            } else if (line.startsWith("## ")) {
                parser.flush();
                parser.blocks.add(new Block(BlockType.HEADING, inline(line.substring(3).trim())));
            } else if (line.startsWith("- ")) {
                parser.flush();
                parser.bullet = new StringBuilder(line.substring(2).trim());
            } else if (parser.bullet != null && raw.startsWith("  ")) {
                parser.bullet.append(' ').append(line.trim());
            } else {
                if (parser.bullet != null) {
                    parser.flush();
                }
                if (parser.paragraph.length() > 0) {
                    parser.paragraph.append(' ');
                }
                parser.paragraph.append(line.trim());
            }
        }
        parser.flush();
        return new MarkdownDocument(title, List.copyOf(parser.blocks));
    }

    private static final class Parser {
        final List<Block> blocks = new ArrayList<>();
        final StringBuilder paragraph = new StringBuilder();
        StringBuilder bullet;
        final List<String> verbatim = new ArrayList<>();
        boolean fenced;

        void flush() {
            if (bullet != null) {
                blocks.add(new Block(BlockType.BULLET, inline(bullet.toString())));
                bullet = null;
            }
            if (paragraph.length() > 0) {
                blocks.add(new Block(BlockType.PARAGRAPH, inline(paragraph.toString())));
                paragraph.setLength(0);
            }
            if (!verbatim.isEmpty()) {
                // Reflowed like any paragraph; runs of spaces only lined text up in the file.
                String text = String.join(" ", verbatim).replaceAll("\\s+", " ");
                blocks.add(
                        new Block(
                                BlockType.VERBATIM, List.of(new Span(text, false, false, false))));
                verbatim.clear();
            }
        }
    }

    static List<Span> inline(String text) {
        List<Span> spans = new ArrayList<>();
        appendInline(text, false, false, spans);
        return List.copyOf(spans);
    }

    /** Styled spans may nest (code inside italic), so the inner text is parsed again. */
    private static void appendInline(String text, boolean bold, boolean italic, List<Span> out) {
        Matcher m = INLINE.matcher(text);
        int last = 0;
        while (m.find()) {
            addPlain(text.substring(last, m.start()), bold, italic, out);
            if (m.group(1) != null) {
                out.add(new Span(m.group(1), bold, italic, true));
            } else if (m.group(2) != null) {
                appendInline(m.group(2), true, italic, out);
            } else {
                appendInline(m.group(3), bold, true, out);
            }
            last = m.end();
        }
        addPlain(text.substring(last), bold, italic, out);
    }

    private static void addPlain(String text, boolean bold, boolean italic, List<Span> out) {
        if (!text.isEmpty()) {
            out.add(new Span(text, bold, italic, false));
        }
    }
}
