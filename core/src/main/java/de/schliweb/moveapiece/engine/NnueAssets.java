/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package de.schliweb.moveapiece.engine;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Installs the two NNUE evaluation networks into a plain install directory, where Stockfish can
 * open them as plain files via "setoption name EvalFile/EvalFileSmall value &lt;path&gt;". See
 * app/stockfish.gradle (NNUE_EMBEDDING_OFF) for why the networks are shipped this way instead of
 * embedded in the engine binary.
 *
 * <p>Where the raw bytes come from is platform-specific (an Android APK asset, a file sitting next
 * to a desktop-built Stockfish binary, ...), so that part is abstracted behind {@link NetSource}.
 */
public final class NnueAssets {

    private static final String ASSET_DIR = "nnue";
    public static final String BIG_NET = "nn-c288c895ea92.nnue";
    public static final String SMALL_NET = "nn-37f18f62d772.nnue";

    private NnueAssets() {}

    /** Opens the raw bytes of an NNUE net, addressed relative to {@link #ASSET_DIR}. */
    public interface NetSource {
        InputStream open(String relativePath) throws IOException;
    }

    public static final class Paths {
        public final String bigNetPath;
        public final String smallNetPath;

        Paths(String bigNetPath, String smallNetPath) {
            this.bigNetPath = bigNetPath;
            this.smallNetPath = smallNetPath;
        }
    }

    /**
     * Blocking (copies ~112 MB on first run, or a no-op if the files are already present in {@code
     * installDir}); call off the main thread.
     */
    public static Paths extractIfNeeded(NetSource source, File installDir) throws IOException {
        File bigNet = extractOne(source, installDir, BIG_NET);
        File smallNet = extractOne(source, installDir, SMALL_NET);
        return new Paths(bigNet.getAbsolutePath(), smallNet.getAbsolutePath());
    }

    private static File extractOne(NetSource source, File installDir, String name)
            throws IOException {
        File dest = new File(installDir, name);
        if (dest.exists() && dest.length() > 0) {
            return dest;
        }
        File tmp = new File(installDir, name + ".tmp");
        try (InputStream in = source.open(ASSET_DIR + "/" + name);
                OutputStream out = new FileOutputStream(tmp)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
        if (!tmp.renameTo(dest)) {
            throw new IOException("Failed to install " + name);
        }
        return dest;
    }
}
