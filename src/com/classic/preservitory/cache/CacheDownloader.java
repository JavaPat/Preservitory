package com.classic.preservitory.cache;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class CacheDownloader {

    public interface ProgressListener {
        void onProgress(int percent, String status);
    }

    public static void init(ProgressListener listener) {
        try {
            new File(CacheConfig.ROOT_DIR).mkdirs();
            new File(CacheConfig.CACHE_DIR).mkdirs();

            try {
                Files.setAttribute(Paths.get(CacheConfig.ROOT_DIR), "dos:hidden", true);
            } catch (Exception ignored) {}

            if (!isCacheValid()) {
                listener.onProgress(0, "Downloading cache...");
                deleteCache();
                downloadCache(listener);
                listener.onProgress(80, "Extracting cache...");
                extractCache(listener);
                new File(CacheConfig.CACHE_ZIP).delete();
                writeVersionFile();
                listener.onProgress(100, "Done");
            } else {
                listener.onProgress(100, "Done");
            }

        } catch (Exception e) {
            e.printStackTrace();
            listener.onProgress(0, "Error: " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    //  Download
    // -----------------------------------------------------------------------

    private static void downloadCache(ProgressListener listener) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(CacheConfig.CACHE_URL).openConnection();
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(30_000);
        connection.setInstanceFollowRedirects(true);
        connection.connect();

        long contentLength = connection.getContentLengthLong();

        try (InputStream in = connection.getInputStream();
             OutputStream out = Files.newOutputStream(Paths.get(CacheConfig.CACHE_ZIP))) {

            byte[] buffer = new byte[8192];
            long downloaded = 0;
            int len;

            while ((len = in.read(buffer)) > 0) {
                out.write(buffer, 0, len);
                downloaded += len;

                if (contentLength > 0) {
                    int percent = (int) (downloaded * 78 / contentLength);
                    listener.onProgress(percent, "Downloading... " + (downloaded / 1024) + " / " + (contentLength / 1024) + " KB");
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    //  Extraction
    // -----------------------------------------------------------------------

    private static void extractCache(ProgressListener listener) throws IOException {
        File zipFile   = new File(CacheConfig.CACHE_ZIP);
        File cacheDir  = new File(CacheConfig.CACHE_DIR);
        File tmpDir    = new File(CacheConfig.ROOT_DIR + "cache_tmp");

        // Wipe any leftover temp dir from a previous failed extract
        if (tmpDir.exists()) deleteDir(tmpDir);
        tmpDir.mkdirs();

        // Detect the common root prefix inside the zip so we can strip it.
        // e.g. if every entry starts with "cache/" or ".preservitory/cache/", we remove that
        // prefix and always land files directly under our cache dir.
        String stripPrefix = detectStripPrefix(zipFile);

        int totalEntries = countEntries(zipFile);
        int processed    = 0;

        // Canonical path used for directory-traversal guard
        String canonicalCache = tmpDir.getCanonicalPath();

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {

                String name = sanitizeName(entry.getName(), stripPrefix);
                if (name.isEmpty()) {
                    zis.closeEntry();
                    continue;
                }

                File dest = new File(tmpDir, name);

                // Directory traversal guard — reject anything that escapes the tmp dir
                if (!dest.getCanonicalPath().startsWith(canonicalCache + File.separator)
                        && !dest.getCanonicalPath().equals(canonicalCache)) {
                    System.err.println("Skipping unsafe entry: " + entry.getName());
                    zis.closeEntry();
                    continue;
                }

                if (entry.isDirectory()) {
                    dest.mkdirs();
                } else {
                    dest.getParentFile().mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(dest)) {
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                    System.out.println("Extracted: " + name);
                }

                processed++;
                if (totalEntries > 0) {
                    int percent = 80 + (processed * 19 / totalEntries);
                    listener.onProgress(percent, "Extracting... " + processed + "/" + totalEntries);
                }

                zis.closeEntry();
            }
        }

        // Atomic swap: rename tmp → cache (avoids a half-written cache on crash)
        deleteDir(cacheDir);
        Files.move(tmpDir.toPath(), cacheDir.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Detects a common root prefix shared by all zip entries so it can be stripped.
     *
     * Examples of what this handles:
     *   "cache/login_screen/bg.png"         → strips "cache/"
     *   ".preservitory/cache/login_screen/" → strips ".preservitory/cache/"
     *   "./login_screen/bg.png"             → strips "./"
     *   "login_screen/bg.png"               → strips nothing (already flat)
     */
    private static String detectStripPrefix(File zipFile) throws IOException {
        String prefix = null;

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = normalizeSlashes(entry.getName());
                if (name.isEmpty() || name.equals("/")) continue;

                // The prefix candidate is everything up to and including the first '/'
                int slash = name.indexOf('/');
                String candidate = (slash >= 0) ? name.substring(0, slash + 1) : "";

                if (prefix == null) {
                    prefix = candidate;
                } else if (!name.startsWith(prefix)) {
                    // Entries disagree on root — don't strip anything
                    return "";
                }
                zis.closeEntry();
            }
        }

        return prefix != null ? prefix : "";
    }

    /**
     * Normalizes a raw zip entry name:
     *  1. Unifies separators to '/'
     *  2. Strips the detected common prefix
     *  3. Strips any remaining leading '/' or './'
     *  4. Rejects entries containing '..' (directory traversal)
     *
     * Returns an empty string for entries that should be skipped.
     */
    private static String sanitizeName(String raw, String stripPrefix) {
        String name = normalizeSlashes(raw);

        // Strip the detected common root prefix
        if (!stripPrefix.isEmpty() && name.startsWith(stripPrefix)) {
            name = name.substring(stripPrefix.length());
        }

        // Strip any remaining leading slashes or ./
        while (name.startsWith("/") || name.startsWith("./")) {
            name = name.startsWith("./") ? name.substring(2) : name.substring(1);
        }

        // Reject directory traversal
        if (name.contains("..")) return "";

        return name.trim();
    }

    private static String normalizeSlashes(String path) {
        return path.replace('\\', '/');
    }

    private static int countEntries(File zipFile) throws IOException {
        int count = 0;
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            while (zis.getNextEntry() != null) count++;
        }
        return count;
    }

    // -----------------------------------------------------------------------
    //  Version check
    // -----------------------------------------------------------------------

    private static boolean isCacheValid() {
        File versionFile = new File(CacheConfig.VERSION_FILE);
        if (!versionFile.exists()) return false;
        try {
            String stored = new String(Files.readAllBytes(versionFile.toPath()), StandardCharsets.UTF_8).trim();
            return CacheConfig.CACHE_VERSION.equals(stored);
        } catch (IOException e) {
            return false;
        }
    }

    private static void writeVersionFile() throws IOException {
        Files.write(Paths.get(CacheConfig.VERSION_FILE),
                CacheConfig.CACHE_VERSION.getBytes(StandardCharsets.UTF_8));
    }

    // -----------------------------------------------------------------------
    //  Helpers
    // -----------------------------------------------------------------------

    private static void deleteCache() {
        File cacheDir = new File(CacheConfig.CACHE_DIR);
        if (cacheDir.exists()) deleteDir(cacheDir);
        cacheDir.mkdirs();
    }

    private static void deleteDir(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) deleteDir(f);
                else f.delete();
            }
        }
        dir.delete();
    }
}
