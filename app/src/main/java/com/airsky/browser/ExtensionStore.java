package com.airsky.browser;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.LruCache;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Firefox のアドオンストア(addons.mozilla.org)から、Android 向けのおすすめ拡張機能を取ってくる。
 * 結果は 6 時間キャッシュし、オフラインでも前回の一覧を出せるようにする。
 */
final class ExtensionStore {

    private static final String TAG = "ExtensionStore";
    private static final String API = "https://addons.mozilla.org/api/v5/addons/search/"
            + "?app=android&type=extension&sort=users&page_size=40&lang=ja";
    private static final long CACHE_MS = 6 * 60 * 60 * 1000L;

    /** おすすめの 1 件。 */
    static final class Addon {
        final String guid;
        final String name;
        final String summary;
        @Nullable final String iconUrl;
        final double rating;
        final int reviews;
        final int users;
        @Nullable final String downloadUrl;
        @Nullable final String pageUrl;

        Addon(String guid, String name, String summary, @Nullable String iconUrl, double rating, int reviews,
              int users, @Nullable String downloadUrl, @Nullable String pageUrl) {
            this.guid = guid;
            this.name = name;
            this.summary = summary;
            this.iconUrl = iconUrl;
            this.rating = rating;
            this.reviews = reviews;
            this.users = users;
            this.downloadUrl = downloadUrl;
            this.pageUrl = pageUrl;
        }
    }

    interface Callback {
        void onResult(@NonNull List<Addon> addons, boolean fromNetwork);
    }

    interface IconCallback {
        void onIcon(@Nullable Bitmap bitmap);
    }

    private static final ExecutorService IO = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final LruCache<String, Bitmap> ICONS = new LruCache<String, Bitmap>(4 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };

    private ExtensionStore() {}

    /** おすすめを読み込む。キャッシュがあればまずそれを返し、古ければネットから取り直してもう一度返す。 */
    static void loadRecommended(@NonNull Context context, boolean force, @NonNull Callback callback) {
        File cache = new File(context.getCacheDir(), "amo_recommended.json");
        IO.execute(() -> {
            boolean fresh = false;
            if (cache.exists()) {
                List<Addon> cached = parse(readFile(cache));
                if (!cached.isEmpty()) {
                    MAIN.post(() -> callback.onResult(cached, false));
                    fresh = System.currentTimeMillis() - cache.lastModified() < CACHE_MS;
                }
            }
            if (fresh && !force) return;
            // まず Mozilla のおすすめ(推奨)に絞って取り、取れなければ人気順の一覧を使う
            String body = fetch(API + "&promoted=recommended");
            List<Addon> list = parse(body);
            if (list.isEmpty()) {
                body = fetch(API);
                list = parse(body);
            }
            if (list.isEmpty()) {
                if (!cache.exists()) MAIN.post(() -> callback.onResult(new ArrayList<>(), true));
                return;
            }
            writeFile(cache, body);
            List<Addon> result = list;
            MAIN.post(() -> callback.onResult(result, true));
        });
    }

    static void loadIcon(@Nullable String url, @NonNull IconCallback callback) {
        if (url == null) {
            callback.onIcon(null);
            return;
        }
        Bitmap cached = ICONS.get(url);
        if (cached != null) {
            callback.onIcon(cached);
            return;
        }
        IO.execute(() -> {
            Bitmap b = null;
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(10000);
                c.setReadTimeout(15000);
                try (InputStream in = c.getInputStream()) {
                    b = BitmapFactory.decodeStream(in);
                } finally {
                    c.disconnect();
                }
            } catch (Exception e) {
                Log.w(TAG, "アイコンを読めませんでした: " + url);
            }
            if (b != null) ICONS.put(url, b);
            Bitmap result = b;
            MAIN.post(() -> callback.onIcon(result));
        });
    }

    // ------------------------------------------------------------------

    @Nullable
    private static String fetch(String url) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(10000);
            c.setReadTimeout(20000);
            c.setRequestProperty("Accept", "application/json");
            if (c.getResponseCode() != 200) return null;
            try (InputStream in = c.getInputStream()) {
                return readAll(in);
            }
        } catch (Exception e) {
            Log.w(TAG, "おすすめを取得できませんでした", e);
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    @NonNull
    static List<Addon> parse(@Nullable String body) {
        List<Addon> list = new ArrayList<>();
        if (body == null) return list;
        try {
            JSONArray results = new JSONObject(body).optJSONArray("results");
            if (results == null) return list;
            for (int i = 0; i < results.length(); i++) {
                JSONObject o = results.optJSONObject(i);
                if (o == null) continue;
                String guid = o.optString("guid", "");
                String name = text(o.opt("name"));
                if (guid.isEmpty() || name == null) continue;
                String summary = text(o.opt("summary"));
                String icon = o.optString("icon_url", null);
                JSONObject icons = o.optJSONObject("icons");
                if (icons != null) {
                    String big = icons.optString("128", icons.optString("64", null));
                    if (big != null) icon = big;
                }
                JSONObject ratings = o.optJSONObject("ratings");
                double avg = ratings != null ? ratings.optDouble("average", 0) : 0;
                int count = ratings != null ? ratings.optInt("count", 0) : 0;
                String download = null;
                JSONObject version = o.optJSONObject("current_version");
                if (version != null) {
                    JSONObject file = version.optJSONObject("file");
                    if (file != null) download = file.optString("url", null);
                }
                list.add(new Addon(guid, name, summary != null ? summary : "", icon, avg, count,
                        o.optInt("average_daily_users", 0), download, o.optString("url", null)));
            }
        } catch (Exception e) {
            Log.w(TAG, "おすすめの形式が読めませんでした", e);
        }
        return list;
    }

    /** 翻訳つきの文字列(文字列、または {"ja": "...", "_default": "en-US", ...})から日本語を選ぶ。 */
    @Nullable
    private static String text(@Nullable Object v) {
        if (v == null || v == JSONObject.NULL) return null;
        if (v instanceof String) return ((String) v).isEmpty() ? null : (String) v;
        if (v instanceof JSONObject) {
            JSONObject o = (JSONObject) v;
            String ja = str(o, "ja");
            if (ja != null && !ja.isEmpty()) return ja;
            String def = str(o, "_default");
            if (def != null) {
                String d = str(o, def);
                if (d != null && !d.isEmpty()) return d;
            }
            String en = str(o, "en-US");
            if (en != null && !en.isEmpty()) return en;
            Iterator<String> keys = o.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                if (k.startsWith("_")) continue;
                String s = str(o, k);
                if (s != null && !s.isEmpty()) return s;
            }
        }
        return null;
    }

    /** JSON の null は null として返す(optString は "null" という文字列にしてしまう)。 */
    @Nullable
    private static String str(@NonNull JSONObject o, @NonNull String key) {
        if (o.isNull(key)) return null;
        String s = o.optString(key, null);
        return s == null || s.isEmpty() ? null : s;
    }

    private static String readAll(InputStream in) throws java.io.IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[16 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        return bos.toString(StandardCharsets.UTF_8.name());
    }

    @Nullable
    private static String readFile(File f) {
        try (InputStream in = new FileInputStream(f)) {
            return readAll(in);
        } catch (Exception e) {
            return null;
        }
    }

    private static void writeFile(File f, @Nullable String s) {
        if (s == null) return;
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(s.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        }
    }
}
