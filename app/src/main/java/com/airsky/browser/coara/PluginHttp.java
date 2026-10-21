package com.airsky.browser.coara;

import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class PluginHttp {
    static final int MAX_BODY = 8 * 1024 * 1024;
    private static final int MAX_REDIRECTS = 8;
    private static final int DEFAULT_TIMEOUT = 30000;
    private static final int MAX_TIMEOUT = 120000;

    static final class Call {
        volatile boolean aborted;
        volatile HttpURLConnection conn;

        void abort() {
            aborted = true;
            HttpURLConnection c = conn;
            if (c != null) {
                try {
                    c.disconnect();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private PluginHttp() {
    }

    private static long parseLooseIpv4(String h) {
        if (h.isEmpty()) {
            return -1L;
        }
        String[] parts = h.split("\\.", -1);
        if (parts.length > 4) {
            return -1L;
        }
        long[] v = new long[parts.length];
        for (int i = 0; i < parts.length; i++) {
            String t = parts[i];
            if (t.isEmpty()) {
                return -1L;
            }
            try {
                if (t.startsWith("0x") || t.startsWith("0X")) {
                    if (t.length() == 2) {
                        return -1L;
                    }
                    v[i] = Long.parseLong(t.substring(2), 16);
                } else if (t.length() > 1 && t.charAt(0) == '0') {
                    v[i] = Long.parseLong(t.substring(1), 8);
                } else {
                    v[i] = Long.parseLong(t, 10);
                }
            } catch (NumberFormatException e) {
                return -1L;
            }
            if (v[i] < 0) {
                return -1L;
            }
        }
        long result;
        switch (parts.length) {
            case 1:
                if (v[0] > 0xFFFFFFFFL) {
                    return -1L;
                }
                result = v[0];
                break;
            case 2:
                if (v[0] > 0xFF || v[1] > 0xFFFFFFL) {
                    return -1L;
                }
                result = (v[0] << 24) | v[1];
                break;
            case 3:
                if (v[0] > 0xFF || v[1] > 0xFF || v[2] > 0xFFFFL) {
                    return -1L;
                }
                result = (v[0] << 24) | (v[1] << 16) | v[2];
                break;
            default:
                if (v[0] > 0xFF || v[1] > 0xFF || v[2] > 0xFF || v[3] > 0xFF) {
                    return -1L;
                }
                result = (v[0] << 24) | (v[1] << 16) | (v[2] << 8) | v[3];
                break;
        }
        return result;
    }

    private static boolean isPrivateIpv4(long ip) {
        int a = (int) ((ip >> 24) & 0xFF);
        int b = (int) ((ip >> 16) & 0xFF);
        return a == 10 || a == 127 || a == 0 || (a == 169 && b == 254) || (a == 172 && b >= 16 && b <= 31)
                || (a == 192 && b == 168) || (a == 100 && b >= 64 && b <= 127) || a >= 224;
    }

    static boolean isPrivateHost(String host) {
        if (host == null) {
            return true;
        }
        String h = host.toLowerCase(Locale.US);
        while (h.endsWith(".")) {
            h = h.substring(0, h.length() - 1);
        }
        if (h.isEmpty() || h.startsWith("[") || h.indexOf(':') >= 0) {
            return true;
        }
        if (h.equals("localhost") || h.endsWith(".localhost") || h.endsWith(".local") || h.endsWith(".internal")
                || h.endsWith(".lan") || h.endsWith(".home.arpa") || h.indexOf('.') < 0 && parseLooseIpv4(h) < 0) {
            return true;
        }
        long ip = parseLooseIpv4(h);
        if (ip >= 0) {
            return isPrivateIpv4(ip);
        }
        return false;
    }

    private static boolean isIpLiteral(String host) {
        return host.indexOf(':') >= 0 || host.startsWith("[") || parseLooseIpv4(host) >= 0;
    }

    private static boolean resolvesToPrivate(String host) {
        if (isIpLiteral(host)) {
            return false;
        }
        try {
            java.net.InetAddress[] addrs = java.net.InetAddress.getAllByName(host);
            for (java.net.InetAddress a : addrs) {
                if (a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress()
                        || a.isSiteLocalAddress() || a.isMulticastAddress()) {
                    return true;
                }
                byte[] raw = a.getAddress();
                if (raw.length == 4) {
                    long ip = ((raw[0] & 0xFFL) << 24) | ((raw[1] & 0xFFL) << 16) | ((raw[2] & 0xFFL) << 8) | (raw[3] & 0xFFL);
                    if (isPrivateIpv4(ip)) {
                        return true;
                    }
                } else if (raw.length == 16 && (raw[0] & 0xFE) == 0xFC) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    static boolean hostAllowed(List<String> connects, String url, String originHost) {
        String host;
        String scheme;
        try {
            URL u = new URL(url);
            host = u.getHost();
            scheme = u.getProtocol();
            if (u.getUserInfo() != null) {
                return false;
            }
        } catch (Exception e) {
            return false;
        }
        if (host == null || host.isEmpty()) {
            return false;
        }
        host = host.toLowerCase(Locale.US);
        while (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }
        boolean secure = "https".equalsIgnoreCase(scheme);
        String origin = originHost == null ? "" : originHost.toLowerCase(Locale.US);
        boolean priv = isPrivateHost(host);
        if (host.equals(origin)) {
            return true;
        }
        if (connects != null) {
            for (String c : connects) {
                String e = c.trim().toLowerCase(Locale.US);
                if (e.isEmpty()) {
                    continue;
                }
                if (e.equals("*")) {
                    if (secure && !priv && !resolvesToPrivate(host)) {
                        return true;
                    }
                    continue;
                }
                if (host.equals(e)) {
                    return true;
                }
                if (!priv && host.endsWith("." + e) && (secure || !isIpLiteral(host)) && !resolvesToPrivate(host)) {
                    return true;
                }
            }
        }
        return false;
    }

    static JSONObject errorPayload(String message) {
        JSONObject o = new JSONObject();
        try {
            o.put("status", 0);
            o.put("error", message == null ? "error" : message);
        } catch (JSONException ignored) {
        }
        return o;
    }

    static JSONObject execute(JSONObject opts, List<String> connects, Call call) {
        try {
            String url = opts.optString("url", "");
            String origin = opts.optString("origin", "");
            String method = opts.optString("method", "GET").toUpperCase(Locale.US);
            if (!method.matches("[A-Z]{3,10}")) {
                return errorPayload("invalid method");
            }
            String lower = url.toLowerCase(Locale.US);
            if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
                return errorPayload("unsupported scheme");
            }
            boolean anonymous = opts.optBoolean("anonymous", false);
            int timeout = Math.max(1000, Math.min(MAX_TIMEOUT, opts.optInt("timeout", DEFAULT_TIMEOUT)));
            if (opts.optInt("timeout", 0) <= 0) {
                timeout = DEFAULT_TIMEOUT;
            }
            JSONObject headers = opts.optJSONObject("headers");
            byte[] body = null;
            if (opts.has("dataB64")) {
                body = Base64.decode(opts.optString("dataB64", ""), Base64.DEFAULT);
            } else if (opts.has("data") && !opts.isNull("data")) {
                body = opts.optString("data", "").getBytes(StandardCharsets.UTF_8);
            }
            String responseType = opts.optString("responseType", "");
            String user = opts.optString("user", "");
            String password = opts.optString("password", "");

            String current = url;
            int redirects = 0;
            while (true) {
                if (call.aborted) {
                    return abortedPayload();
                }
                if (!hostAllowed(connects, current, origin)) {
                    return errorPayload("blocked host (not in @connect): " + hostOf(current));
                }
                HttpURLConnection c = (HttpURLConnection) new URL(current).openConnection();
                call.conn = c;
                try {
                    c.setConnectTimeout(Math.min(timeout, 20000));
                    c.setReadTimeout(timeout);
                    c.setInstanceFollowRedirects(false);
                    c.setRequestMethod(method);
                    boolean hasCookie = false;
                    boolean hasUa = false;
                    if (headers != null) {
                        Iterator<String> it = headers.keys();
                        while (it.hasNext()) {
                            String k = it.next();
                            String kl = k.toLowerCase(Locale.US);
                            if (kl.equals("host") || kl.equals("content-length") || kl.equals("connection")
                                    || kl.equals("transfer-encoding")) {
                                continue;
                            }
                            if (kl.equals("cookie")) {
                                hasCookie = true;
                            }
                            if (kl.equals("user-agent")) {
                                hasUa = true;
                            }
                            c.setRequestProperty(k, headers.optString(k, ""));
                        }
                    }
                    if (!hasUa) {
                        c.setRequestProperty("User-Agent", opts.optString("ua", "Mozilla/5.0 (Linux; Android 10) AirSky"));
                    }
                    // GeckoView の Cookie はアプリから読み書きできないので、ページの Cookie は送らない
                    if (!user.isEmpty()) {
                        String cred = Base64.encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
                        c.setRequestProperty("Authorization", "Basic " + cred);
                    }
                    if (body != null && body.length > 0 && !method.equals("GET") && !method.equals("HEAD")) {
                        c.setDoOutput(true);
                        c.setFixedLengthStreamingMode(body.length);
                        c.getOutputStream().write(body);
                        c.getOutputStream().flush();
                    }
                    int code = c.getResponseCode();
                    if (code >= 300 && code < 400 && code != 304 && code != 300) {
                        String loc = c.getHeaderField("Location");
                        if (loc != null && !loc.isEmpty() && redirects < MAX_REDIRECTS && !opts.optBoolean("noRedirect", false)) {
                            redirects++;
                            String next = new URL(new URL(current), loc).toString();
                            if (current.regionMatches(true, 0, "https://", 0, 8) && !next.regionMatches(true, 0, "https://", 0, 8)) {
                                return errorPayload("blocked https to http redirect: " + hostOf(next));
                            }
                            current = next;
                            if (code == 301 || code == 302 || code == 303) {
                                if (!method.equals("HEAD")) {
                                    method = "GET";
                                }
                                body = null;
                            }
                            continue;
                        }
                    }
                    InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
                    byte[] data = new byte[0];
                    if (in != null && !method.equals("HEAD")) {
                        try (InputStream is = in) {
                            ByteArrayOutputStream bos = new ByteArrayOutputStream();
                            byte[] buf = new byte[16384];
                            int n;
                            long total = 0L;
                            while ((n = is.read(buf)) > 0) {
                                if (call.aborted) {
                                    return abortedPayload();
                                }
                                total += n;
                                if (total > MAX_BODY) {
                                    return errorPayload("response too large");
                                }
                                bos.write(buf, 0, n);
                            }
                            data = bos.toByteArray();
                        }
                    }
                    JSONObject out = new JSONObject();
                    out.put("status", code);
                    out.put("statusText", c.getResponseMessage() == null ? "" : c.getResponseMessage());
                    out.put("finalUrl", current);
                    out.put("headers", headerString(c));
                    String ctype = c.getContentType();
                    if (responseType.equals("arraybuffer") || responseType.equals("blob") || opts.optBoolean("binary", false)) {
                        out.put("b64", Base64.encodeToString(data, Base64.NO_WRAP));
                        out.put("mime", ctype == null ? "" : ctype);
                    } else {
                        out.put("text", new String(data, charsetOf(ctype, opts.optString("charset", ""))));
                    }
                    return out;
                } finally {
                    try {
                        c.disconnect();
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Throwable t) {
            if (call.aborted) {
                return abortedPayload();
            }
            return errorPayload(t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    private static JSONObject abortedPayload() {
        JSONObject o = errorPayload("aborted");
        try {
            o.put("aborted", true);
        } catch (JSONException ignored) {
        }
        return o;
    }

    private static String hostOf(String url) {
        try {
            return new URL(url).getHost();
        } catch (Exception e) {
            return url;
        }
    }

    private static String headerString(HttpURLConnection c) {
        StringBuilder sb = new StringBuilder();
        Map<String, List<String>> hf = c.getHeaderFields();
        if (hf == null) {
            return "";
        }
        for (Map.Entry<String, List<String>> e : hf.entrySet()) {
            if (e.getKey() == null) {
                continue;
            }
            for (String v : e.getValue()) {
                sb.append(e.getKey()).append(": ").append(v).append("\r\n");
            }
        }
        return sb.toString();
    }

    private static Charset charsetOf(String contentType, String override) {
        String name = override;
        if ((name == null || name.isEmpty()) && contentType != null) {
            int i = contentType.toLowerCase(Locale.US).indexOf("charset=");
            if (i >= 0) {
                name = contentType.substring(i + 8).split(";")[0].trim().replace("\"", "");
            }
        }
        if (name != null && !name.isEmpty()) {
            try {
                return Charset.forName(name);
            } catch (Exception ignored) {
            }
        }
        return StandardCharsets.UTF_8;
    }
}
