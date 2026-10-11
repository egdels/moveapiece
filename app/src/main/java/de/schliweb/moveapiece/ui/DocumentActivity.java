/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.BulletSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.text.style.URLSpan;
import android.text.util.Linkify;
import android.util.TypedValue;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import de.schliweb.moveapiece.databinding.ActivityDocumentBinding;
import de.schliweb.moveapiece.docs.MarkdownDocument;
import de.schliweb.moveapiece.docs.MarkdownDocument.Block;
import de.schliweb.moveapiece.docs.MarkdownDocument.Span;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * Shows one Markdown file from the assets: the help in the device's language (the asset name is the
 * {@code help_asset} string, so Android's own resource lookup picks the language) or the privacy
 * policy. app/build.gradle copies both from the repository root, so the text is maintained in one
 * place for GitHub, this app and the desktop app. Google Play wants the privacy policy reachable
 * from inside the app; as text it keeps the app offline.
 */
public class DocumentActivity extends AppCompatActivity {

    public static final String PRIVACY_POLICY = "PRIVACY.md";

    private static final String EXTRA_ASSET = "asset";

    /**
     * A web address with a path, such as github.com/egdels/moveapiece, without punctuation that
     * ends the sentence. Not Patterns.WEB_URL: that takes file names like CHANGELOG.md for a domain
     * (.md is Moldova's) and keeps a full stop after the address.
     */
    private static final Pattern WEB_ADDRESS =
            Pattern.compile("(https://)?[\\w-]+(\\.[\\w-]+)+/[^\\s]*[^\\s.,;:)]");

    public static void start(Context context, String asset) {
        context.startActivity(
                new Intent(context, DocumentActivity.class).putExtra(EXTRA_ASSET, asset));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityDocumentBinding binding = ActivityDocumentBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        SystemBars.applyToolbarScreen(this, binding.toolbar, binding.screenContent);
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        String asset = getIntent().getStringExtra(EXTRA_ASSET);
        MarkdownDocument document = MarkdownDocument.parse(readAsset(asset), "MoveAPiece");
        setTitle(document.title());
        for (Block block : document.blocks()) {
            binding.documentContent.addView(blockView(block));
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private String readAsset(String asset) {
        // Not InputStream.readAllBytes: that needs API 33, minSdk is 26.
        try (InputStream in = getAssets().open(asset)) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            for (int n; (n = in.read(buffer)) != -1; ) {
                bytes.write(buffer, 0, n);
            }
            return bytes.toString(StandardCharsets.UTF_8.name());
        } catch (IOException e) {
            // The files are part of the APK (see app/build.gradle); missing means a broken build.
            throw new UncheckedIOException(e);
        }
    }

    private TextView blockView(Block block) {
        TextView view = new TextView(this);
        SpannableStringBuilder text = styled(block);
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
        switch (block.type()) {
            case HEADING:
                view.setTextAppearance(
                        com.google.android.material.R.style.TextAppearance_Material3_TitleMedium);
                params.topMargin = dp(20);
                params.bottomMargin = dp(4);
                break;
            case BULLET:
                view.setTextAppearance(
                        com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
                text.setSpan(
                        new BulletSpan(dp(10)), 0, text.length(), Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
                params.leftMargin = dp(4);
                params.topMargin = dp(6);
                break;
            default:
                view.setTextAppearance(
                        com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
                params.topMargin = dp(10);
                break;
        }
        view.setLayoutParams(params);
        // The files name their web addresses as plain text; make them tappable.
        Linkify.addLinks(text, WEB_ADDRESS, "https://");
        if (text.getSpans(0, text.length(), URLSpan.class).length > 0) {
            view.setMovementMethod(LinkMovementMethod.getInstance());
        }
        view.setText(text);
        return view;
    }

    private static SpannableStringBuilder styled(Block block) {
        SpannableStringBuilder text = new SpannableStringBuilder();
        for (Span span : block.spans()) {
            int start = text.length();
            text.append(span.text());
            int end = text.length();
            int style = (span.bold() ? Typeface.BOLD : 0) | (span.italic() ? Typeface.ITALIC : 0);
            if (style != 0) {
                text.setSpan(new StyleSpan(style), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            if (span.code()) {
                text.setSpan(
                        new TypefaceSpan("monospace"),
                        start,
                        end,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        return text;
    }

    private int dp(int value) {
        return Math.round(
                TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()));
    }
}
