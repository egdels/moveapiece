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
 * Installs the NNUE evaluation network into a plain install directory, where Stockfish can open it
 * as a plain file via "setoption name EvalFile value &lt;path&gt;". See app/stockfish.gradle
 * (NNUE_EMBEDDING_OFF) for why the network is shipped this way instead of embedded in the engine
 * binary.
 *
 * <p>Where the raw bytes come from is platform-specific (an Android APK asset, a file sitting next
 * to a desktop-built Stockfish binary, ...), so that part is abstracted behind {@link NetSource}.
 */
public final class NnueAssets {

    private static final String ASSET_DIR = "nnue";
    public static final String NET = "nn-1a298aa575a0.nnue";

    private NnueAssets() {}

    /** Opens the raw bytes of an NNUE net, addressed relative to {@link #ASSET_DIR}. */
    public interface NetSource {
        InputStream open(String relativePath) throws IOException;
    }

    /**
     * Blocking (copies ~99 MB on first run, or a no-op if the file is already present in {@code
     * installDir}); call off the main thread.
     *
     * @return absolute path of the installed net
     */
    public static String extractIfNeeded(NetSource source, File installDir) throws IOException {
        File net = extractOne(source, installDir, NET);
        deleteStaleNets(installDir);
        return net.getAbsolutePath();
    }

    /**
     * Removes nets an earlier app version installed (other Stockfish releases need other nets), so
     * an update does not leave them behind in {@code installDir} for good.
     */
    private static void deleteStaleNets(File installDir) {
        File[] stale =
                installDir.listFiles(
                        (dir, name) ->
                                name.startsWith("nn-")
                                        && (name.endsWith(".nnue") || name.endsWith(".nnue.tmp"))
                                        && !name.equals(NET));
        if (stale == null) {
            return;
        }
        for (File file : stale) {
            // Best effort: a read-only install dir just keeps the old file.
            file.delete();
        }
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
