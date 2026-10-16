package com.airsky.browser;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * ダウンロードしたファイルのウイルススキャン(端末内で完結するシグネチャ方式)。
 *
 * <ul>
 *   <li>書き込み中のデータを {@link Session#update} に流し、既知のパターンが見つかった時点で
 *       ダウンロードを止められるようにする(チャンクの境目にまたがるパターンも検出する)</li>
 *   <li>書き込み後に {@link Session#finish} で SHA-256 を既知の不正ファイルの一覧と照合する</li>
 *   <li>ZIP 形式(zip / apk / jar / docx など)は {@link #scanArchive} で中身も調べる(入れ子は 3 段まで)</li>
 * </ul>
 *
 * 外部の検索サービスには何も送らない。シグネチャは EICAR テストファイルと {@link #KNOWN_BAD_SHA256}
 * の一覧なので、市販のウイルス対策ソフトの代わりにはならない。
 */
public final class VirusScanner {

    static final String PREFS = AppDownloader.PREFS;
    static final String KEY_ENABLED = "virusScanEnabled";

    /** 中身を調べる ZIP 形式の拡張子。 */
    private static final String[] ARCHIVE_EXTENSIONS = {
            ".zip", ".apk", ".apks", ".xapk", ".jar", ".aab",
            ".docx", ".xlsx", ".pptx", ".odt", ".ods", ".odp", ".epub"
    };
    private static final int MAX_ARCHIVE_DEPTH = 3;
    /** 圧縮爆弾対策: 展開して調べるのはアーカイブ 1 つにつきこの量まで。 */
    private static final long MAX_ARCHIVE_BYTES = 512L * 1024 * 1024;

    /** 内容に含まれていたら不正と判断するバイト列。 */
    private static final Signature[] SIGNATURES = {
            // ウイルス対策ソフトの動作確認用の標準テストファイル(実害は無い)
            new Signature("EICAR-Test-File",
                    "X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*"),
    };

    /** SHA-256(小文字の 16 進) → 検出名。 */
    private static final Map<String, String> KNOWN_BAD_SHA256;

    static {
        Map<String, String> m = new HashMap<>();
        // eicar.com (68 バイトの標準テストファイル)
        m.put("275a021bbfb6489e54d471899f7db9d1663fc695ec2fe2a2c4538aabf651fd0f", "EICAR-Test-File");
        KNOWN_BAD_SHA256 = Collections.unmodifiableMap(m);
    }

    private static final int MAX_SIGNATURE_LENGTH;

    static {
        int max = 0;
        for (Signature s : SIGNATURES) max = Math.max(max, s.bytes.length);
        MAX_SIGNATURE_LENGTH = max;
    }

    private VirusScanner() {}

    /** 設定で有効になっているか(既定は有効)。 */
    public static boolean isEnabled(@NonNull Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true);
    }

    public static void setEnabled(@NonNull Context context, boolean enabled) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    /** この名前のファイルは中身(ZIP の各ファイル)も調べるか。 */
    public static boolean isArchive(@Nullable String fileName) {
        if (fileName == null) return false;
        String lower = fileName.toLowerCase(Locale.ROOT);
        for (String ext : ARCHIVE_EXTENSIONS) {
            if (lower.endsWith(ext)) return true;
        }
        return false;
    }

    /** 書き込みと並行してデータを調べる。1 つのダウンロードにつき 1 つ作る。スレッドセーフではない。 */
    public static final class Session {
        @Nullable private final MessageDigest sha256;
        /** 前のチャンクの末尾(パターンがチャンクの境目にまたがっても見つけるため)。 */
        private final byte[] tail = new byte[Math.max(0, MAX_SIGNATURE_LENGTH - 1)];
        private int tailLength;
        private byte[] window = new byte[0];
        @Nullable private String threat;

        public Session() {
            MessageDigest md;
            try {
                md = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException e) {
                md = null;
            }
            sha256 = md;
        }

        /**
         * 書き込んだデータを渡す。
         * @return 不正なデータが見つかったら検出名。以降も同じ名前を返す
         */
        @Nullable
        public String update(@NonNull byte[] buf, int off, int len) {
            if (threat != null || len <= 0) return threat;
            if (sha256 != null) sha256.update(buf, off, len);

            int total = tailLength + len;
            if (window.length < total) window = new byte[total];
            System.arraycopy(tail, 0, window, 0, tailLength);
            System.arraycopy(buf, off, window, tailLength, len);
            threat = matchSignatures(window, total);

            // 次回のために末尾を残す
            int keep = Math.min(tail.length, total);
            System.arraycopy(window, total - keep, tail, 0, keep);
            tailLength = keep;
            return threat;
        }

        /** 全データを渡し終えた後の判定(ハッシュの照合)。 */
        @Nullable
        public String finish() {
            if (threat != null) return threat;
            if (sha256 == null) return null;
            String hex = toHex(sha256.digest());
            threat = KNOWN_BAD_SHA256.get(hex);
            return threat;
        }
    }

    /**
     * ZIP 形式のファイルの中身を調べる。壊れた・ZIP ではないデータなら、そこまでで終える(不正とはしない)。
     * @return 見つかったら「中のファイル名: 検出名」
     */
    @Nullable
    public static String scanArchive(@NonNull InputStream in) {
        long[] budget = {MAX_ARCHIVE_BYTES};
        try {
            return scanZip(in, 1, budget);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    @Nullable
    private static String scanZip(@NonNull InputStream in, int depth, long[] budget) throws IOException {
        // 閉じても渡されたストリーム(外側の ZIP の中のファイルなど)は閉じない
        try (ZipInputStream zip = new ZipInputStream(new NonClosingInputStream(in))) {
            return scanEntries(zip, depth, budget);
        }
    }

    @Nullable
    private static String scanEntries(@NonNull ZipInputStream zip, int depth, long[] budget) throws IOException {
        byte[] buf = new byte[64 * 1024];
        ZipEntry entry;
        while ((entry = zip.getNextEntry()) != null) {
            if (entry.isDirectory()) continue;
            String name = entry.getName();
            if (depth < MAX_ARCHIVE_DEPTH && isArchive(name)) {
                String inner = scanZip(zip, depth + 1, budget);
                if (inner != null) return name + " > " + inner;
                continue;
            }
            Session session = new Session();
            int n;
            while ((n = zip.read(buf)) != -1) {
                budget[0] -= n;
                if (budget[0] < 0) return null; // 大きすぎる(圧縮爆弾の可能性)ので、ここまでにする
                String found = session.update(buf, 0, n);
                if (found != null) return name + ": " + found;
            }
            String found = session.finish();
            if (found != null) return name + ": " + found;
        }
        return null;
    }

    @Nullable
    private static String matchSignatures(byte[] data, int length) {
        for (Signature s : SIGNATURES) {
            if (indexOf(data, length, s.bytes) >= 0) return s.name;
        }
        return null;
    }

    private static int indexOf(byte[] data, int length, byte[] pattern) {
        int last = length - pattern.length;
        byte first = pattern[0];
        outer:
        for (int i = 0; i <= last; i++) {
            if (data[i] != first) continue;
            for (int j = 1; j < pattern.length; j++) {
                if (data[i + j] != pattern[j]) continue outer;
            }
            return i;
        }
        return -1;
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format(Locale.ROOT, "%02x", b));
        return sb.toString();
    }

    private static final class Signature {
        final String name;
        final byte[] bytes;

        Signature(String name, String ascii) {
            this.name = name;
            this.bytes = ascii.getBytes(StandardCharsets.US_ASCII);
        }
    }

    /** 入れ子の ZIP を読む時、内側を読み終えても外側の ZIP を閉じないようにする。 */
    private static final class NonClosingInputStream extends java.io.FilterInputStream {
        NonClosingInputStream(InputStream in) {
            super(in);
        }

        @Override
        public void close() {
        }
    }
}
