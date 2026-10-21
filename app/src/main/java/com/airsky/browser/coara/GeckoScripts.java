package com.airsky.browser.coara;

import android.content.Context;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Coara Browser 拡張機能(プラグイン)を、AirSky の組み込み拡張(coara_cs.js)へ渡す形にまとめる。
 *
 * <p>Coara の WebView 版はページを開くたびに JavascriptInterface で同期的にデータを取りに来るが、
 * Gecko のコンテンツスクリプトからアプリへは非同期でしか聞けない。そのため、プラグインのコード・
 * リソース・翻訳文は先にまとめて送っておき、ページ側からの問い合わせはコンテンツスクリプトが答える。</p>
 */
public final class GeckoScripts {

    private static final String RUNTIME_ASSET = "coara/plugin_runtime.js";
    /** 1 つのプラグインのリソース(GM_getResourceText など)として先に送る量の上限。 */
    private static final int RESOURCE_BUDGET = 512 * 1024;
    private static final int RESOURCE_URL_BUDGET = 512 * 1024;

    private static String runtimeTemplate;
    private static final java.util.Map<String, String> PRELUDES = new java.util.HashMap<>();

    private GeckoScripts() {
    }

    /** 有効なプラグインを、組み込み拡張へ送る形(プラグインごとの JSON)にする。 */
    public static JSONArray buildAll(Context context) {
        PluginManager manager = PluginManager.get(context);
        manager.reloadIfChanged();
        JSONArray out = new JSONArray();
        for (PluginInfo p : manager.getEnabledPlugins()) {
            try {
                JSONObject o = build(context, manager, p);
                if (o != null) {
                    out.put(o);
                }
            } catch (Throwable t) {
                manager.log("E", p.id, "スクリプト生成に失敗: " + t);
            }
        }
        return out;
    }

    /** そのプラグインの保存データ(キー → 文字列)。 */
    public static JSONObject storeOf(Context context, String id) {
        PluginManager manager = PluginManager.get(context);
        JSONObject o = new JSONObject();
        try {
            JSONArray keys = new JSONArray(manager.storageKeys(id));
            for (int i = 0; i < keys.length(); i++) {
                String k = keys.optString(i);
                String v = manager.storageGet(id, k);
                if (v != null) {
                    o.put(k, v);
                }
            }
        } catch (Exception ignored) {
        }
        return o;
    }

    private static JSONObject build(Context context, PluginManager manager, PluginInfo info) throws Exception {
        JSONArray entries = new JSONArray();
        boolean allFrames = false;
        List<PluginEntry> list = info.entries();
        for (int i = 0; i < list.size(); i++) {
            PluginEntry entry = list.get(i);
            String code = buildEntryScript(context, manager, info, entry, i);
            if (code == null) {
                continue;
            }
            JSONObject e = new JSONObject();
            e.put("key", manager.keyFor(info.id + "#" + i));
            e.put("allFrames", entry.allFrames);
            e.put("matches", new JSONArray(PluginPatterns.toRegexList(PluginPatterns.effectiveMatches(entry.matches))));
            e.put("excludes", new JSONArray(PluginPatterns.toRegexList(entry.excludes)));
            e.put("code", code);
            entries.put(e);
            allFrames |= entry.allFrames;
        }
        if (entries.length() == 0) {
            return null;
        }
        JSONObject o = new JSONObject();
        o.put("id", info.id);
        o.put("title", info.title);
        o.put("allFrames", allFrames);
        o.put("perms", new JSONArray(info.permissions));
        o.put("uilang", Locale.getDefault().toLanguageTag());
        o.put("entries", entries);
        putResources(manager, info, o);
        o.put("msgs", messages(manager, info));
        return o;
    }

    /** GM_getResourceText / GM_getResourceURL / runtime.getURL で読まれるファイル。 */
    private static void putResources(PluginManager manager, PluginInfo p, JSONObject out) throws Exception {
        JSONObject res = new JSONObject();
        JSONObject urls = new JSONObject();
        int textUsed = 0;
        int urlUsed = 0;
        // 名前(GM の @resource 名・ファイル名) → 実際のファイル
        Set<String[]> names = new LinkedHashSet<>();
        for (java.util.Map.Entry<String, String> e : p.resourceMap.entrySet()) {
            names.add(new String[]{e.getKey(), e.getValue()});
        }
        for (String r : p.resources) {
            names.add(new String[]{r, r});
        }
        for (String a : p.assets) {
            names.add(new String[]{a, a});
        }
        Set<String> seen = new HashSet<>();
        for (String[] n : names) {
            if (!seen.add(n[0])) {
                continue;
            }
            String path = n[1];
            if (p.resources.contains(path) && textUsed < RESOURCE_BUDGET) {
                String text = manager.readText(p, path);
                if (text != null && textUsed + text.length() <= RESOURCE_BUDGET) {
                    res.put(n[0], text);
                    textUsed += text.length();
                }
            }
            if (urlUsed < RESOURCE_URL_BUDGET) {
                byte[] data = manager.readBytes(p, path, RESOURCE_URL_BUDGET - urlUsed);
                if (data != null) {
                    String url = "data:" + mimeOf(path) + ";base64," + Base64.encodeToString(data, Base64.NO_WRAP);
                    urls.put(n[0], url);
                    urlUsed += url.length();
                }
            }
        }
        out.put("res", res);
        out.put("resUrl", urls);
    }

    /** _locales の翻訳文(キーは小文字にそろえる。chrome.i18n はキーの大文字小文字を区別しない)。 */
    private static JSONObject messages(PluginManager manager, PluginInfo p) {
        JSONObject out = new JSONObject();
        File locales = new File(manager.pluginDir(p.id), "_locales");
        File[] dirs = locales.listFiles();
        if (dirs == null) {
            return out;
        }
        Set<String> available = new HashSet<>();
        for (File d : dirs) {
            if (d.isDirectory() && new File(d, "messages.json").isFile()) {
                available.add(d.getName());
            }
        }
        String chosen = WebExtParser.pickLocale(available, p.defaultLocale);
        if (chosen == null) {
            return out;
        }
        try {
            File f = new File(new File(locales, chosen), "messages.json");
            if (f.length() > 1024 * 1024) {
                return out;
            }
            JSONObject data = new JSONObject(WebExtParser.decode(readAll(f)));
            Iterator<String> it = data.keys();
            while (it.hasNext()) {
                String k = it.next();
                JSONObject entry = data.optJSONObject(k);
                if (entry == null) {
                    continue;
                }
                JSONObject copy = new JSONObject();
                copy.put("message", entry.optString("message", ""));
                JSONObject ph = entry.optJSONObject("placeholders");
                if (ph != null) {
                    JSONObject lower = new JSONObject();
                    Iterator<String> pit = ph.keys();
                    while (pit.hasNext()) {
                        String pk = pit.next();
                        lower.put(pk.toLowerCase(Locale.US), ph.opt(pk));
                    }
                    copy.put("placeholders", lower);
                }
                out.put(k.toLowerCase(Locale.US), copy);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    /**
     * ページ側で動かすコード。CH(コンテンツスクリプトとの連絡用の名前)を受け取る関数の式になっていて、
     * コンテンツスクリプトが {@code コード("名前");} の形にして動かす。
     */
    private static String buildEntryScript(Context context, PluginManager manager, PluginInfo info,
                                           PluginEntry entry, int index) throws Exception {
        String template = loadAsset(context, RUNTIME_ASSET);
        if (template == null) {
            return null;
        }
        StringBuilder body = new StringBuilder();
        for (String name : entry.scripts) {
            String src = manager.readText(info, name);
            if (src == null) {
                manager.log("W", info.id, "スクリプトを読み込めません: " + name);
                continue;
            }
            body.append(src).append("\n;\n");
        }
        JSONArray css = new JSONArray();
        for (String name : entry.styles) {
            String text = manager.readText(info, name);
            if (text == null) {
                manager.log("W", info.id, "スタイルを読み込めません: " + name);
                continue;
            }
            css.put(text);
        }
        if (body.length() == 0 && css.length() == 0) {
            return null;
        }
        JSONObject meta = new JSONObject();
        meta.put("id", info.id);
        meta.put("title", info.title);
        meta.put("version", info.version);
        meta.put("description", info.description);
        meta.put("author", info.author);
        meta.put("homepage", info.homepage);
        meta.put("format", info.format);
        meta.put("runAt", entry.runAt);
        meta.put("allFrames", entry.allFrames);
        meta.put("permissions", new JSONArray(info.permissions));
        meta.put("rawMatches", new JSONArray(entry.matches));
        meta.put("rawExcludes", new JSONArray(entry.excludes));
        meta.put("matches", new JSONArray(PluginPatterns.toRegexList(PluginPatterns.effectiveMatches(entry.matches))));
        meta.put("excludes", new JSONArray(PluginPatterns.toRegexList(entry.excludes)));

        String prelude = "";
        String kind = info.apiKind();
        if (kind.equals("gm")) {
            prelude = loadAsset(context, "coara/plugin_api_gm.js");
        } else if (kind.equals("webext")) {
            prelude = loadAsset(context, "coara/plugin_api_webext.js");
        }
        if (prelude == null) {
            prelude = "";
        }

        int idx = template.indexOf("%BODY%");
        if (idx < 0) {
            return null;
        }
        String head = template.substring(0, idx);
        String tail = template.substring(idx + "%BODY%".length());
        List<String[]> values = new ArrayList<>();
        values.add(new String[]{"%META%", meta.toString()});
        values.add(new String[]{"%CSS%", css.toString()});
        values.add(new String[]{"%KEY%", manager.keyFor(info.id + "#" + index)});
        values.add(new String[]{"%PRELUDE%", prelude});
        String built = fill(head, values) + body + tail;
        // 最後の改行・空白を落とす(コンテンツスクリプトがすぐ後ろに ("名前") を付ける)
        int end = built.length();
        while (end > 0 && Character.isWhitespace(built.charAt(end - 1))) {
            end--;
        }
        return built.substring(0, end);
    }

    private static String fill(String template, List<String[]> values) {
        StringBuilder out = new StringBuilder(template.length() + 256);
        int i = 0;
        int n = template.length();
        while (i < n) {
            char c = template.charAt(i);
            if (c == '%') {
                boolean replaced = false;
                for (String[] kv : values) {
                    if (template.startsWith(kv[0], i)) {
                        out.append(kv[1]);
                        i += kv[0].length();
                        replaced = true;
                        break;
                    }
                }
                if (replaced) {
                    continue;
                }
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    private static synchronized String loadAsset(Context context, String asset) {
        if (RUNTIME_ASSET.equals(asset) && runtimeTemplate != null) {
            return runtimeTemplate;
        }
        String cached = PRELUDES.get(asset);
        if (cached != null) {
            return cached;
        }
        try (InputStream in = context.getAssets().open(asset)) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            String text = new String(bos.toByteArray(), StandardCharsets.UTF_8);
            if (RUNTIME_ASSET.equals(asset)) {
                runtimeTemplate = text;
            } else {
                PRELUDES.put(asset, text);
            }
            return text;
        } catch (Exception e) {
            return null;
        }
    }

    private static byte[] readAll(File f) throws Exception {
        try (InputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            return bos.toByteArray();
        }
    }

    private static String mimeOf(String path) {
        String ext = PluginInstaller.extOf(path == null ? "" : path);
        switch (ext) {
            case "png":
                return "image/png";
            case "jpg":
            case "jpeg":
                return "image/jpeg";
            case "gif":
                return "image/gif";
            case "webp":
                return "image/webp";
            case "svg":
                return "image/svg+xml";
            case "ico":
                return "image/x-icon";
            case "woff":
                return "font/woff";
            case "woff2":
                return "font/woff2";
            case "ttf":
                return "font/ttf";
            case "otf":
                return "font/otf";
            case "css":
                return "text/css";
            case "js":
            case "mjs":
                return "text/javascript";
            case "json":
                return "application/json";
            case "html":
            case "htm":
                return "text/html";
            case "xml":
                return "text/xml";
            case "txt":
            case "md":
            case "csv":
                return "text/plain";
            default:
                return "application/octet-stream";
        }
    }
}
