package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Small poster loader for Continue watching and the poster screensaver: http(s), content and file URIs,
 * decoded at the size asked for, kept in a small memory cache and on disk in the app's cache directory
 * (which Android trims on its own). Only these features touch the network.
 */
final class Posters {
    private static final int MAX_BYTES = 8 * 1024 * 1024;
    private static final LruCache<String, Bitmap> MEMORY = new LruCache<String, Bitmap>(6 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };
    private static final ExecutorService IO = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private Posters() {}

    /** Loads {@code uri} no larger than about {@code maxW}×{@code maxH}; {@code done} gets null on failure. */
    static void load(Context ctx, String uri, int maxW, int maxH, Consumer<Bitmap> done) {
        if (uri == null || uri.isEmpty()) {
            done.accept(null);
            return;
        }
        String key = uri + "@" + maxW + "x" + maxH;
        Bitmap cached = MEMORY.get(key);
        if (cached != null) {
            done.accept(cached);
            return;
        }
        Context app = ctx.getApplicationContext();
        IO.execute(() -> {
            Bitmap bitmap = decode(bytes(app, uri), maxW, maxH);
            if (bitmap != null) MEMORY.put(key, bitmap);
            MAIN.post(() -> done.accept(bitmap));
        });
    }

    /** Same as {@link #load} but blocking, for a background thread. */
    static Bitmap loadNow(Context ctx, String uri, int maxW, int maxH) {
        if (uri == null || uri.isEmpty()) return null;
        return decode(bytes(ctx.getApplicationContext(), uri), maxW, maxH);
    }

    private static byte[] bytes(Context ctx, String uri) {
        try {
            String scheme = Uri.parse(uri).getScheme();
            if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
                File file = new File(new File(ctx.getCacheDir(), "posters"), hash(uri));
                if (file.isFile() && file.length() > 0) return read(new FileInputStream(file));
                byte[] data = download(uri, 4);
                if (data != null) {
                    File dir = file.getParentFile();
                    if (dir != null && (dir.isDirectory() || dir.mkdirs())) {
                        try (FileOutputStream out = new FileOutputStream(file)) {
                            out.write(data);
                        }
                    }
                }
                return data;
            }
            return read(ctx.getContentResolver().openInputStream(Uri.parse(uri)));
        } catch (IOException | RuntimeException e) {
            android.util.Log.w("TvHome.Posters", "load " + uri + " failed: " + e);
            return null;
        }
    }

    private static byte[] download(String url, int redirects) throws IOException {
        HttpURLConnection http = (HttpURLConnection) new URL(url).openConnection();
        http.setConnectTimeout(8000);
        http.setReadTimeout(10000);
        http.setInstanceFollowRedirects(true);
        http.setRequestProperty("User-Agent", "TvHome");
        try {
            int code = http.getResponseCode();
            // HttpURLConnection does not follow http <-> https on its own.
            if (code >= 300 && code < 400 && redirects > 0) {
                String location = http.getHeaderField("Location");
                if (location != null) return download(new URL(new URL(url), location).toString(), redirects - 1);
            }
            if (code != 200) {
                android.util.Log.w("TvHome.Posters", "HTTP " + code + " for " + url);
                return null;
            }
            return read(http.getInputStream());
        } finally {
            http.disconnect();
        }
    }

    private static byte[] read(InputStream in) throws IOException {
        if (in == null) return null;
        try (InputStream input = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[16 * 1024];
            int n;
            while ((n = input.read(buf)) > 0) {
                out.write(buf, 0, n);
                if (out.size() > MAX_BYTES) return null;
            }
            return out.toByteArray();
        }
    }

    private static Bitmap decode(byte[] data, int maxW, int maxH) {
        if (data == null) return null;
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
        int sample = 1;
        while (bounds.outWidth / (sample * 2) >= maxW && bounds.outHeight / (sample * 2) >= maxH) sample *= 2;
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        opts.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeByteArray(data, 0, data.length, opts);
    }

    private static String hash(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-1").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }
}
