package com.airsky.browser;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.LruCache;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Python の GUI が使う画像(ファイル・base64 のデータ・URL)を読み込んで覚えておく。
 * 画像の 1 ピクセルは 1dp として表示する(tkinter の PhotoImage などと見た目の大きさをそろえる)。
 */
final class PyImages {

    private static final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(24 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };
    private static final Map<String, List<Runnable>> loading = new HashMap<>();
    private static final ExecutorService io = Executors.newFixedThreadPool(2);
    private static final Handler ui = new Handler(Looper.getMainLooper());

    private PyImages() {}

    private static String key(JSONObject ref) {
        String base = ref.has("path") ? "p:" + ref.optString("path")
                : ref.has("url") ? "u:" + ref.optString("url")
                : "d:" + ref.optString("data").hashCode() + ":" + ref.optString("data").length();
        return base + "@" + ref.optDouble("scale", 1);
    }

    /**
     * 画像を返す。まだ読み込んでいなければ null を返し、読み込みが終わったら loaded を呼ぶ。
     */
    @Nullable
    static Bitmap get(@NonNull Context c, @NonNull JSONObject ref, @Nullable Runnable loaded) {
        String k = key(ref);
        Bitmap b = cache.get(k);
        if (b != null) return b;
        synchronized (loading) {
            List<Runnable> waiting = loading.get(k);
            if (waiting != null) {
                if (loaded != null) waiting.add(loaded);
                return null;
            }
            waiting = new ArrayList<>();
            if (loaded != null) waiting.add(loaded);
            loading.put(k, waiting);
        }
        float density = c.getResources().getDisplayMetrics().density;
        io.execute(() -> {
            Bitmap bmp = null;
            try {
                bmp = decode(ref);
                if (bmp != null) {
                    float s = (float) ref.optDouble("scale", 1) * density;
                    int w = Math.max(1, Math.round(bmp.getWidth() * s)), h = Math.max(1, Math.round(bmp.getHeight() * s));
                    if (w != bmp.getWidth() || h != bmp.getHeight()) {
                        // 小さな絵(ドット絵など)はぼかさない
                        boolean pixel = bmp.getWidth() <= 64 && bmp.getHeight() <= 64;
                        bmp = Bitmap.createScaledBitmap(bmp, w, h, !pixel);
                    }
                    cache.put(k, bmp);
                }
            } catch (Exception | OutOfMemoryError ignored) {
            }
            List<Runnable> waiting;
            synchronized (loading) {
                waiting = loading.remove(k);
            }
            if (waiting != null && bmp != null) {
                ui.post(() -> {
                    for (Runnable r : waiting) r.run();
                });
            }
        });
        return null;
    }

    @Nullable
    private static Bitmap decode(JSONObject ref) throws Exception {
        if (ref.has("path")) {
            return BitmapFactory.decodeFile(ref.optString("path"));
        }
        if (ref.has("data")) {
            byte[] raw = Base64.decode(ref.optString("data"), Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(raw, 0, raw.length);
        }
        if (ref.has("url")) {
            HttpURLConnection conn = (HttpURLConnection) new URL(ref.optString("url")).openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(20000);
            try (InputStream in = conn.getInputStream()) {
                return BitmapFactory.decodeStream(in);
            } finally {
                conn.disconnect();
            }
        }
        return null;
    }
}
