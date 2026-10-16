package com.airsky.browser;

import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * WebView(Aozora)が saveWebArchive で保存した MHTML を、GeckoView で開ける 1 つの HTML にする。
 *
 * <p>MHTML は HTML・CSS・画像などを MIME の multipart/related でまとめたもの。各部分の
 * Content-Location(または cid:)で参照している所を data: URL に置き換えて埋め込む。</p>
 *
 * <p>文字コードを変えずに済むよう、中身は ISO-8859-1(1 バイト = 1 文字)の文字列として扱う。
 * 置き換えるのは ASCII の URL だけなので、ほかのバイトはそのまま残る。</p>
 */
final class MhtConverter {

    /** フレームの入れ子をたどる深さの上限。 */
    private static final int MAX_DEPTH = 5;

    private static final class Part {
        final Map<String, String> headers;
        final String body;
        @Nullable String decoded;

        Part(@NonNull Map<String, String> headers, @NonNull String body) {
            this.headers = headers;
            this.body = body;
        }

        @NonNull
        String type() {
            String t = headers.get("content-type");
            if (t == null) return "application/octet-stream";
            int semi = t.indexOf(';');
            return (semi >= 0 ? t.substring(0, semi) : t).trim().toLowerCase(Locale.ROOT);
        }

        @Nullable
        String charset() {
            return param(headers.get("content-type"), "charset");
        }

        /** Content-Transfer-Encoding を戻した中身(ISO-8859-1 の文字列)。 */
        @NonNull
        String decoded() {
            if (decoded == null) {
                String enc = headers.get("content-transfer-encoding");
                enc = enc != null ? enc.trim().toLowerCase(Locale.ROOT) : "";
                if ("quoted-printable".equals(enc)) {
                    decoded = decodeQuotedPrintable(body);
                } else if ("base64".equals(enc)) {
                    decoded = new String(decodeBase64(body), StandardCharsets.ISO_8859_1);
                } else {
                    decoded = body;
                }
            }
            return decoded;
        }
    }

    private final List<Part> parts = new ArrayList<>();
    /** Content-Location / cid: → 部分。 */
    private final Map<String, Part> byLocation = new LinkedHashMap<>();
    /** 参照する名前(長い順。短い URL が長い URL の一部を置き換えないように)。 */
    private final List<String> locations = new ArrayList<>();
    private final Map<Part, String> dataUrls = new HashMap<>();
    private final Set<Part> converting = new HashSet<>();

    private MhtConverter() {
    }

    /** MHTML を HTML にする。HTML の部分が見つからなければ null。 */
    @Nullable
    static byte[] toHtml(@NonNull byte[] mht) {
        return new MhtConverter().convert(new String(mht, StandardCharsets.ISO_8859_1));
    }

    @Nullable
    private byte[] convert(@NonNull String text) {
        int[] head = headerEnd(text, 0);
        if (head == null) return null;
        Map<String, String> top = parseHeaders(text.substring(0, head[0]));
        String boundary = param(top.get("content-type"), "boundary");
        if (boundary == null) {
            // multipart でない(HTML が 1 つだけ)
            Part only = new Part(top, text.substring(head[1]));
            return only.type().contains("html") ? only.decoded().getBytes(StandardCharsets.ISO_8859_1) : null;
        }
        split(text, head[1], boundary);

        Part main = null;
        String snapshot = top.get("snapshot-content-location");
        if (snapshot != null) {
            Part p = byLocation.get(snapshot.trim());
            if (p != null && "text/html".equals(p.type())) main = p;
        }
        if (main == null) {
            for (Part p : parts) {
                if ("text/html".equals(p.type())) {
                    main = p;
                    break;
                }
            }
        }
        if (main == null) return null;

        locations.addAll(byLocation.keySet());
        Collections.sort(locations, (a, b) -> b.length() - a.length());

        converting.add(main);
        String html = embed(main.decoded(), main, 0);
        html = addHeadTags(html, main.charset(), snapshot != null ? snapshot.trim() : location(main));
        return html.getBytes(StandardCharsets.ISO_8859_1);
    }

    // ---- 分割 ----

    private void split(@NonNull String text, int from, @NonNull String boundary) {
        String delim = "--" + boundary;
        int pos = text.indexOf(delim, from);
        while (pos >= 0) {
            int lineEnd = text.indexOf('\n', pos);
            if (lineEnd < 0) break;
            // 終わりの区切り(--boundary--)
            if (text.startsWith("--", pos + delim.length())) break;
            int next = text.indexOf("\n" + delim, lineEnd);
            int end = next >= 0 ? next : text.length();
            // 区切りの前の改行は中身に含めない
            if (end > lineEnd && text.charAt(end - 1) == '\r') end--;
            String chunk = text.substring(lineEnd + 1, Math.max(lineEnd + 1, end));
            int[] head = headerEnd(chunk, 0);
            if (head != null) {
                Part p = new Part(parseHeaders(chunk.substring(0, head[0])), chunk.substring(head[1]));
                parts.add(p);
                String loc = p.headers.get("content-location");
                if (loc != null && !loc.trim().isEmpty()) byLocation.put(loc.trim(), p);
                String cid = p.headers.get("content-id");
                if (cid != null) {
                    cid = cid.trim();
                    if (cid.startsWith("<") && cid.endsWith(">")) cid = cid.substring(1, cid.length() - 1);
                    if (!cid.isEmpty()) byLocation.put("cid:" + cid, p);
                }
            }
            pos = next >= 0 ? next + 1 : -1;
        }
    }

    /** ヘッダーの終わりと中身の始まり。空行が無ければ null。 */
    @Nullable
    private static int[] headerEnd(@NonNull String s, int from) {
        int crlf = s.indexOf("\r\n\r\n", from);
        int lf = s.indexOf("\n\n", from);
        if (crlf >= 0 && (lf < 0 || crlf <= lf)) return new int[]{crlf, crlf + 4};
        if (lf >= 0) return new int[]{lf, lf + 2};
        return null;
    }

    /** ヘッダー(折り返しを含む)を 小文字の名前 → 値 にする。 */
    @NonNull
    private static Map<String, String> parseHeaders(@NonNull String block) {
        Map<String, String> map = new HashMap<>();
        String name = null;
        StringBuilder value = new StringBuilder();
        for (String line : block.split("\r?\n")) {
            if (!line.isEmpty() && (line.charAt(0) == ' ' || line.charAt(0) == '\t')) {
                if (name != null) value.append(' ').append(line.trim());
                continue;
            }
            if (name != null) map.put(name, value.toString().trim());
            int colon = line.indexOf(':');
            if (colon <= 0) {
                name = null;
                continue;
            }
            name = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            value.setLength(0);
            value.append(line.substring(colon + 1));
        }
        if (name != null) map.put(name, value.toString().trim());
        return map;
    }

    /** {@code text/html; charset="utf-8"} から charset などの値を取り出す。 */
    @Nullable
    private static String param(@Nullable String header, @NonNull String key) {
        if (header == null) return null;
        for (String piece : header.split(";")) {
            int eq = piece.indexOf('=');
            if (eq < 0 || !piece.substring(0, eq).trim().equalsIgnoreCase(key)) continue;
            String v = piece.substring(eq + 1).trim();
            if (v.length() >= 2 && v.startsWith("\"") && v.endsWith("\"")) v = v.substring(1, v.length() - 1);
            return v.isEmpty() ? null : v;
        }
        return null;
    }

    // ---- 埋め込み ----

    /** 文字列の中で参照している部分を data: URL に置き換える。 */
    @NonNull
    private String embed(@NonNull String text, @NonNull Part self, int depth) {
        String out = text;
        for (String loc : locations) {
            Part p = byLocation.get(loc);
            if (p == null || p == self) continue;
            String escaped = loc.replace("&", "&amp;");
            boolean raw = out.contains(loc);
            boolean esc = !escaped.equals(loc) && out.contains(escaped);
            if (!raw && !esc) continue;
            String data = dataUrl(p, depth + 1);
            if (data == null) continue;
            if (raw) out = out.replace(loc, data);
            if (esc) out = out.replace(escaped, data);
        }
        return embedRelative(out, self, depth);
    }

    /** CSS の url(...) と @import "..."(HTML の style の中も)。 */
    private static final Pattern CSS_REF = Pattern.compile(
            "url\\(\\s*(['\"]?)([^'\")\\s]+)\\1\\s*\\)|@import\\s+(['\"])([^'\"]+)\\3");

    /**
     * CSS の中の相対パス(url(../fonts/a.ttf) など)を、その部分の場所から解決して埋め込む。
     * 絶対 URL のものは {@link #embed} で置き換え済み。
     */
    @NonNull
    private String embedRelative(@NonNull String text, @NonNull Part self, int depth) {
        String base = location(self);
        if (base == null || (!text.contains("url(") && !text.contains("@import"))) return text;
        Matcher m = CSS_REF.matcher(text);
        StringBuilder out = new StringBuilder(text.length());
        int last = 0;
        while (m.find()) {
            boolean isUrl = m.group(2) != null;
            String ref = isUrl ? m.group(2) : m.group(4);
            Part p = ref.startsWith("data:") ? null : byLocation.get(resolve(base, ref));
            String data = p != null && p != self ? dataUrl(p, depth + 1) : null;
            if (data == null) continue;
            out.append(text, last, m.start());
            out.append(isUrl ? "url(\"" + data + "\")" : "@import \"" + data + "\"");
            last = m.end();
        }
        if (last == 0) return text;
        return out.append(text, last, text.length()).toString();
    }

    /** base から見た ref の絶対 URL(# 以降は除く)。解決できなければ ref のまま。 */
    @NonNull
    private static String resolve(@NonNull String base, @NonNull String ref) {
        String r = ref.replace("&amp;", "&");
        int hash = r.indexOf('#');
        if (hash >= 0) r = r.substring(0, hash);
        try {
            return new URI(base).resolve(r).toString();
        } catch (URISyntaxException | IllegalArgumentException e) {
            return r;
        }
    }

    @Nullable
    private String dataUrl(@NonNull Part p, int depth) {
        String cached = dataUrls.get(p);
        if (cached != null) return cached;
        if (depth > MAX_DEPTH || converting.contains(p)) return null;
        String type = p.type();
        String content = p.decoded();
        if ("text/html".equals(type) || "text/css".equals(type)) {
            // フレームの HTML や CSS の中の参照も埋め込む
            converting.add(p);
            content = embed(content, p, depth);
            converting.remove(p);
        }
        StringBuilder sb = new StringBuilder("data:").append(type);
        String charset = p.charset();
        if (charset != null && type.startsWith("text/")) sb.append(";charset=").append(charset);
        sb.append(";base64,").append(Base64.encodeToString(
                content.getBytes(StandardCharsets.ISO_8859_1), Base64.NO_WRAP));
        String url = sb.toString();
        dataUrls.put(p, url);
        return url;
    }

    @Nullable
    private static String location(@NonNull Part p) {
        String loc = p.headers.get("content-location");
        return loc != null ? loc.trim() : null;
    }

    /**
     * ファイルとして開いた時に文字化けしないよう charset を、相対リンクが元のサイトを指すよう base を
     * &lt;head&gt; に入れる(どちらもページに無い時だけ)。
     */
    @NonNull
    private static String addHeadTags(@NonNull String html, @Nullable String charset, @Nullable String base) {
        String lower = html.toLowerCase(Locale.ROOT);
        StringBuilder tags = new StringBuilder();
        if (charset != null && !charset.matches(".*[\"<>].*")
                && !lower.substring(0, Math.min(lower.length(), 4096)).contains("charset")) {
            tags.append("<meta charset=\"").append(charset).append("\">");
        }
        if (base != null && (base.startsWith("http://") || base.startsWith("https://"))
                && !lower.contains("<base")) {
            tags.append("<base href=\"").append(base.replace("&", "&amp;").replace("\"", "&quot;")).append("\">");
        }
        if (tags.length() == 0) return html;
        int head = lower.indexOf("<head");
        int at = head >= 0 ? lower.indexOf('>', head) + 1 : 0;
        if (at <= 0) at = 0;
        return html.substring(0, at) + tags + html.substring(at);
    }

    // ---- デコード ----

    @NonNull
    private static String decodeQuotedPrintable(@NonNull String s) {
        StringBuilder out = new StringBuilder(s.length());
        int n = s.length();
        for (int i = 0; i < n; i++) {
            char c = s.charAt(i);
            if (c != '=') {
                out.append(c);
                continue;
            }
            // =\r\n / =\n は行の折り返し(消す)
            if (i + 1 < n && s.charAt(i + 1) == '\n') {
                i += 1;
            } else if (i + 2 < n && s.charAt(i + 1) == '\r' && s.charAt(i + 2) == '\n') {
                i += 2;
            } else if (i + 2 < n && hex(s.charAt(i + 1)) >= 0 && hex(s.charAt(i + 2)) >= 0) {
                out.append((char) (hex(s.charAt(i + 1)) * 16 + hex(s.charAt(i + 2))));
                i += 2;
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static int hex(char c) {
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'A' && c <= 'F') return c - 'A' + 10;
        if (c >= 'a' && c <= 'f') return c - 'a' + 10;
        return -1;
    }

    @NonNull
    private static byte[] decodeBase64(@NonNull String s) {
        ByteArrayOutputStream clean = new ByteArrayOutputStream(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c > ' ') clean.write(c);
        }
        try {
            return Base64.decode(clean.toByteArray(), Base64.DEFAULT);
        } catch (IllegalArgumentException e) {
            return new byte[0];
        }
    }
}
