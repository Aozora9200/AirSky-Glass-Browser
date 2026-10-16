package com.airsky.browser;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * メモに書かれた URL のページを読み、題名・説明・画像(og:title / og:description / og:image)を取ってくる。
 * 画像は小さくしてメモの media フォルダに保存する。
 */
final class LinkPreviewFetcher {

    interface Callback {
        void onDone(@NonNull NoteStore.LinkPreview preview);
    }

    private static final int MAX_HTML = 512 * 1024;
    private static final int MAX_IMAGE = 4 * 1024 * 1024;
    private static final ExecutorService IO = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private LinkPreviewFetcher() {}

    static void fetch(@NonNull NoteStore store, @NonNull String url, @NonNull Callback cb) {
        IO.execute(() -> {
            NoteStore.LinkPreview p = new NoteStore.LinkPreview();
            p.url = url;
            try {
                String html = readHtml(url);
                p.title = meta(html, "og:title");
                if (p.title == null) p.title = meta(html, "twitter:title");
                if (p.title == null) p.title = tag(html, "title");
                p.description = meta(html, "og:description");
                if (p.description == null) p.description = meta(html, "description");
                String image = meta(html, "og:image");
                if (image == null) image = meta(html, "twitter:image");
                if (image != null) {
                    String abs = new URL(new URL(url), image).toString();
                    p.imageFile = downloadImage(store, abs);
                }
                if (p.title == null) p.title = new URL(url).getHost();
            } catch (Exception e) {
                p.failed = true;
                try {
                    p.title = new URL(url).getHost();
                } catch (Exception ignored) {
                    p.title = url;
                }
            }
            MAIN.post(() -> cb.onDone(p));
        });
    }

    private static String readHtml(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36");
        c.setRequestProperty("Accept", "text/html,application/xhtml+xml");
        try {
            String type = c.getContentType();
            if (type != null && !type.contains("html")) throw new IllegalStateException("not html");
            Charset cs = StandardCharsets.UTF_8;
            if (type != null) {
                Matcher m = Pattern.compile("charset=([\\w-]+)", Pattern.CASE_INSENSITIVE).matcher(type);
                if (m.find()) {
                    try {
                        cs = Charset.forName(m.group(1));
                    } catch (Exception ignored) {
                    }
                }
            }
            byte[] data = readLimited(c.getInputStream(), MAX_HTML);
            String html = new String(data, cs);
            // ページの中で文字コードが指定されていれば、それで読み直す
            Matcher mm = Pattern.compile("<meta[^>]+charset=[\"']?([\\w-]+)", Pattern.CASE_INSENSITIVE).matcher(html);
            if (mm.find()) {
                try {
                    Charset declared = Charset.forName(mm.group(1));
                    if (!declared.equals(cs)) html = new String(data, declared);
                } catch (Exception ignored) {
                }
            }
            return html;
        } finally {
            c.disconnect();
        }
    }

    private static byte[] readLimited(InputStream in, int max) throws Exception {
        try (InputStream is = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) != -1) {
                out.write(buf, 0, n);
                if (out.size() >= max) break;
            }
            return out.toByteArray();
        }
    }

    @Nullable
    private static String meta(String html, String name) {
        Pattern[] ps = {
                Pattern.compile("<meta[^>]+(?:property|name)=[\"']" + Pattern.quote(name) + "[\"'][^>]*content=[\"']([^\"']*)[\"']",
                        Pattern.CASE_INSENSITIVE),
                Pattern.compile("<meta[^>]+content=[\"']([^\"']*)[\"'][^>]*(?:property|name)=[\"']" + Pattern.quote(name) + "[\"']",
                        Pattern.CASE_INSENSITIVE)};
        for (Pattern p : ps) {
            Matcher m = p.matcher(html);
            if (m.find()) {
                String v = decode(m.group(1));
                if (!v.isEmpty()) return v;
            }
        }
        return null;
    }

    @Nullable
    private static String tag(String html, String tag) {
        Matcher m = Pattern.compile("<" + tag + "[^>]*>([^<]*)</" + tag + ">", Pattern.CASE_INSENSITIVE).matcher(html);
        if (!m.find()) return null;
        String v = decode(m.group(1));
        return v.isEmpty() ? null : v;
    }

    private static String decode(String s) {
        return Html.fromHtml(s, Html.FROM_HTML_MODE_LEGACY).toString().trim();
    }

    @Nullable
    private static String downloadImage(NoteStore store, String url) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            byte[] data;
            try {
                data = readLimited(c.getInputStream(), MAX_IMAGE);
            } finally {
                c.disconnect();
            }
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, o);
            int sample = 1;
            while (o.outWidth / (sample * 2) >= 480) sample *= 2;
            o = new BitmapFactory.Options();
            o.inSampleSize = sample;
            Bitmap bmp = BitmapFactory.decodeByteArray(data, 0, data.length, o);
            if (bmp == null) return null;
            File f = store.newMediaFile("jpg");
            try (OutputStream out = new FileOutputStream(f)) {
                bmp.compress(Bitmap.CompressFormat.JPEG, 85, out);
            }
            return f.getName();
        } catch (Exception e) {
            return null;
        }
    }
}
