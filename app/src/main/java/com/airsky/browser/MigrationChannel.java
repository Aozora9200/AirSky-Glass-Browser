package com.airsky.browser;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 端末どうしの引っ越しファイルの受け渡し(Wi-Fi・Bluetooth 共通の送り方)。
 *
 * <p>送る側は「見出し(件数などの JSON)→ データの塊 … → 終わり(SHA-256)」の順に枠(frame)で送り、
 * 受け取る側は最後に「受け取った」の枠を返す。枠は [種類 1 バイト][長さ 4 バイト][中身]。</p>
 *
 * <p>QR コード(Wi-Fi)では、QR コードに入れた 256 ビットの鍵で枠ごとに AES-GCM で暗号化する
 * (QR コードを読んだ端末しか中身を読めず、書き換えも見つかる)。Bluetooth はペア設定した端末どうしの
 * 暗号化された接続なので、鍵は使わず SHA-256 で壊れていないかだけ確かめる。</p>
 *
 * <p>どちらの方法でも、アプリの署名(署名証明書の SHA-256)が同じ相手とだけやり取りする。
 * QR コードと見出しに送る側の署名を入れ、受け取る側は見出しを読んだらすぐ自分の署名を返す。
 * 送る側はそれを確かめてからデータを送るので、作り替えられた AirSky などにはデータを渡さない。</p>
 */
final class MigrationChannel {

    /** Bluetooth(RFCOMM)のサービスの UUID。 */
    static final UUID BT_UUID = UUID.fromString("5c3a6e2d-91b4-4f7a-8d2e-a1b5c0de2026");
    static final String BT_NAME = "AirSky Migration";
    /** QR コードの中身の始まり。 */
    static final String QR_SCHEME = "airsky-migrate";

    private static final byte T_HEADER = 1;
    private static final byte T_DATA = 2;
    private static final byte T_END = 3;
    private static final byte T_ACK = 4;
    /** 受け取る側の署名(見出しのすぐ後に返す)。 */
    private static final byte T_HELLO = 5;
    private static final int CHUNK = 64 * 1024;
    private static final int MAX_FRAME = CHUNK + 1024;
    private static final String MAGIC = "AIRSKY-MIG/1";

    private MigrationChannel() {}

    interface Progress {
        void onProgress(long done, long total);
    }

    /** 受け取った見出しを見て、続けるかどうか(false で止める)。 */
    interface HeaderCheck {
        boolean onHeader(@NonNull DeviceMigration.Summary summary);
    }

    // ------------------------------------------------------------------
    // QR コード
    // ------------------------------------------------------------------

    /** QR コードに入れる接続先。 */
    static final class Ticket {
        final List<String> hosts = new ArrayList<>();
        int port;
        byte[] key;
        String device = "";
        /** 送る側のアプリの署名({@link #appSignature})。 */
        String signature = "";

        @NonNull
        String toQr() {
            StringBuilder sb = new StringBuilder(QR_SCHEME).append(":1?h=");
            for (int i = 0; i < hosts.size(); i++) {
                if (i > 0) sb.append(',');
                sb.append(hosts.get(i));
            }
            sb.append("&p=").append(port);
            sb.append("&k=").append(android.util.Base64.encodeToString(key,
                    android.util.Base64.URL_SAFE | android.util.Base64.NO_PADDING | android.util.Base64.NO_WRAP));
            sb.append("&n=").append(Uri.encode(device));
            sb.append("&s=").append(signature);
            return sb.toString();
        }

        /** QR コードの文字を読む。引っ越しの QR コードでなければ null。 */
        @Nullable
        static Ticket parse(@Nullable String text) {
            if (text == null || !text.startsWith(QR_SCHEME + ":1?")) return null;
            try {
                Uri u = Uri.parse("x://y/" + text.substring(text.indexOf('?')));
                Ticket t = new Ticket();
                String h = u.getQueryParameter("h");
                if (h != null) for (String s : h.split(",")) if (!s.isEmpty()) t.hosts.add(s);
                t.port = Integer.parseInt(u.getQueryParameter("p"));
                t.key = android.util.Base64.decode(u.getQueryParameter("k"),
                        android.util.Base64.URL_SAFE | android.util.Base64.NO_PADDING);
                String n = u.getQueryParameter("n");
                t.device = n != null ? n : "";
                String sig = u.getQueryParameter("s");
                t.signature = sig != null ? sig : "";
                if (t.hosts.isEmpty() || t.port <= 0 || t.port > 65535 || t.key.length != 32) return null;
                return t;
            } catch (Exception e) {
                return null;
            }
        }
    }

    static boolean isMigrationQr(@Nullable String text) {
        return text != null && text.startsWith(QR_SCHEME + ":");
    }

    /**
     * このアプリの署名証明書の SHA-256(URL セーフな Base64)。同じ開発者が署名したアプリなら同じになる。
     * 読めなければ空文字(どの相手とも一致しない)。
     */
    @NonNull
    static String appSignature(@NonNull Context context) {
        String cached = signatureCache;
        if (cached != null) return cached;
        String result = "";
        try {
            PackageManager pm = context.getPackageManager();
            Signature[] signers;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageInfo pi = pm.getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
                signers = pi.signingInfo != null ? pi.signingInfo.getApkContentsSigners() : null;
            } else {
                //noinspection deprecation
                PackageInfo pi = pm.getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNATURES);
                //noinspection deprecation
                signers = pi.signatures;
            }
            if (signers != null && signers.length > 0) {
                // 複数の署名者がいても並び順に左右されないよう、証明書ごとのハッシュを並べ替えてからまとめる
                String[] each = new String[signers.length];
                for (int i = 0; i < signers.length; i++) each[i] = b64(sha256().digest(signers[i].toByteArray()));
                Arrays.sort(each);
                result = b64(sha256().digest(String.join(",", each).getBytes(StandardCharsets.US_ASCII)));
            }
        } catch (Exception ignored) {
        }
        if (!result.isEmpty()) signatureCache = result;
        return result;
    }

    @Nullable private static volatile String signatureCache;

    /** 相手の署名が自分と同じか。どちらかが空なら同じとはみなさない。 */
    static boolean sameSignature(@NonNull String mine, @Nullable String theirs) {
        if (mine.isEmpty() || theirs == null || theirs.isEmpty()) return false;
        return MessageDigest.isEqual(mine.getBytes(StandardCharsets.US_ASCII), theirs.getBytes(StandardCharsets.US_ASCII));
    }

    @NonNull
    private static String b64(@NonNull byte[] b) {
        return android.util.Base64.encodeToString(b,
                android.util.Base64.URL_SAFE | android.util.Base64.NO_PADDING | android.util.Base64.NO_WRAP);
    }

    @NonNull
    static byte[] newKey() {
        byte[] k = new byte[32];
        new SecureRandom().nextBytes(k);
        return k;
    }

    /** この端末の LAN(Wi-Fi・テザリング)の IPv4 アドレス。 */
    @NonNull
    static List<String> localAddresses() {
        List<String> out = new ArrayList<>();
        List<String> other = new ArrayList<>();
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                String name = ni.getName() != null ? ni.getName() : "";
                // モバイル通信(rmnet など)は相手から届かないので使わない
                if (name.startsWith("rmnet") || name.startsWith("ccmni") || name.startsWith("dummy")) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (!(a instanceof Inet4Address) || a.isLoopbackAddress()) continue;
                    String ip = a.getHostAddress();
                    if (ip == null) continue;
                    if (name.startsWith("wlan") || name.startsWith("ap") || name.startsWith("swlan")) out.add(ip);
                    else if (a.isSiteLocalAddress()) other.add(ip);
                }
            }
        } catch (Exception ignored) {
        }
        out.addAll(other);
        return out.size() > 4 ? out.subList(0, 4) : out;
    }

    // ------------------------------------------------------------------
    // 送る
    // ------------------------------------------------------------------

    /**
     * 引っ越しファイルを送る。相手が「受け取った」を返すまで待つ。
     *
     * @param key       Wi-Fi(QR コード)の時は暗号化の鍵、Bluetooth の時は null
     * @param signature このアプリの署名({@link #appSignature})。相手の署名が違えば送らない
     */
    static void send(@NonNull InputStream rawIn, @NonNull OutputStream rawOut, @Nullable byte[] key,
                     @NonNull String signature, @NonNull File file, @NonNull DeviceMigration.Summary summary,
                     @Nullable Progress progress) throws IOException {
        DataOutputStream out = new DataOutputStream(new java.io.BufferedOutputStream(rawOut, CHUNK + 64));
        DataInputStream in = new DataInputStream(rawIn);
        Sealer seal = new Sealer(key, (byte) 0);
        Sealer open = new Sealer(key, (byte) 1);

        out.write(MAGIC.getBytes(StandardCharsets.US_ASCII));
        JSONObject header = summary.toJson();
        try {
            header.put("bytes", file.length());
            header.put("signature", signature);
        } catch (Exception ignored) {
        }
        writeFrame(out, seal, T_HEADER, header.toString().getBytes(StandardCharsets.UTF_8), 0, -1);
        out.flush();

        // 受け取る側の署名を確かめてから中身を送る
        Frame hello = readFrame(in, open);
        if (hello.type == T_ACK) throw new RemoteRejected(new String(hello.data, StandardCharsets.UTF_8));
        if (hello.type != T_HELLO) throw new IOException("unexpected frame " + hello.type);
        if (!sameSignature(signature, new String(hello.data, StandardCharsets.UTF_8))) throw new SignatureMismatch();

        MessageDigest sha = sha256();
        long total = file.length();
        long done = 0;
        byte[] buf = new byte[CHUNK];
        try (InputStream fin = new FileInputStream(file)) {
            int n;
            while ((n = fin.read(buf)) > 0) {
                sha.update(buf, 0, n);
                writeFrame(out, seal, T_DATA, buf, 0, n);
                done += n;
                if (progress != null) progress.onProgress(done, total);
            }
        }
        writeFrame(out, seal, T_END, sha.digest(), 0, -1);
        out.flush();

        // 受け取った側の返事を待つ
        Frame ack = readFrame(in, open);
        if (ack.type != T_ACK) throw new IOException("unexpected frame " + ack.type);
        String reply = new String(ack.data, StandardCharsets.UTF_8);
        if (!"ok".equals(reply)) throw new RemoteRejected(reply);
    }

    /** 相手のアプリの署名がこのアプリと違う(作り替えられたアプリ・別の開発者のビルドなど)。 */
    static final class SignatureMismatch extends IOException {
    }

    /** 相手が受け取りを断った(内容の確認でキャンセルした・空き容量が無い・署名が違うなど)。 */
    static final class RemoteRejected extends IOException {
        RemoteRejected(String reason) {
            super(reason);
        }
    }

    // ------------------------------------------------------------------
    // 受け取る
    // ------------------------------------------------------------------

    /**
     * 引っ越しファイルを受け取って {@code out} に書く。見出しを受け取った時に {@code check} を呼ぶ。
     *
     * @return 見出しの件数
     */
    @NonNull
    static DeviceMigration.Summary receive(@NonNull InputStream rawIn, @NonNull OutputStream rawOut,
                                           @Nullable byte[] key, @NonNull String signature, @NonNull File outFile,
                                           @Nullable HeaderCheck check, @Nullable Progress progress)
            throws IOException {
        DataInputStream in = new DataInputStream(new java.io.BufferedInputStream(rawIn, CHUNK + 64));
        DataOutputStream out = new DataOutputStream(rawOut);
        Sealer open = new Sealer(key, (byte) 0);
        Sealer seal = new Sealer(key, (byte) 1);

        byte[] magic = new byte[MAGIC.length()];
        in.readFully(magic);
        if (!MAGIC.equals(new String(magic, StandardCharsets.US_ASCII))) throw new IOException("bad magic");

        Frame h = readFrame(in, open);
        if (h.type != T_HEADER) throw new IOException("no header");
        DeviceMigration.Summary summary;
        String theirs;
        try {
            JSONObject header = new JSONObject(new String(h.data, StandardCharsets.UTF_8));
            summary = DeviceMigration.Summary.fromJson(header);
            theirs = header.optString("signature");
        } catch (Exception e) {
            throw new IOException("bad header", e);
        }
        if (!sameSignature(signature, theirs)) {
            sendAck(out, seal, "signature");
            throw new SignatureMismatch();
        }
        writeFrame(out, seal, T_HELLO, signature.getBytes(StandardCharsets.US_ASCII), 0, -1);
        out.flush();
        if (summary.bytes <= 0 || summary.bytes > DeviceMigration.MAX_SIZE) throw new IOException("bad size");
        if (check != null && !check.onHeader(summary)) {
            sendAck(out, seal, "cancel");
            throw new java.io.InterruptedIOException("cancelled");
        }
        if (outFile.getParentFile() != null && outFile.getParentFile().getUsableSpace() < summary.bytes + 8L * 1024 * 1024) {
            sendAck(out, seal, "nospace");
            throw new NoSpace();
        }

        MessageDigest sha = sha256();
        long done = 0;
        try (OutputStream fout = new FileOutputStream(outFile)) {
            while (true) {
                Frame f = readFrame(in, open);
                if (f.type == T_DATA) {
                    done += f.data.length;
                    if (done > summary.bytes) throw new IOException("too much data");
                    sha.update(f.data);
                    fout.write(f.data);
                    if (progress != null) progress.onProgress(done, summary.bytes);
                } else if (f.type == T_END) {
                    if (!MessageDigest.isEqual(sha.digest(), f.data) || done != summary.bytes) {
                        sendAck(out, seal, "corrupt");
                        throw new IOException("checksum mismatch");
                    }
                    break;
                } else {
                    throw new IOException("unexpected frame " + f.type);
                }
            }
        }
        sendAck(out, seal, "ok");
        return summary;
    }

    /** 受け取る端末の空き容量が足りない。 */
    static final class NoSpace extends IOException {
    }

    private static void sendAck(@NonNull DataOutputStream out, @NonNull Sealer seal, @NonNull String text) {
        try {
            writeFrame(out, seal, T_ACK, text.getBytes(StandardCharsets.UTF_8), 0, -1);
            out.flush();
        } catch (IOException ignored) {
        }
    }

    // ------------------------------------------------------------------
    // 枠
    // ------------------------------------------------------------------

    private static final class Frame {
        byte type;
        byte[] data;
    }

    private static void writeFrame(@NonNull DataOutputStream out, @NonNull Sealer seal, byte type,
                                   @NonNull byte[] data, int off, int len) throws IOException {
        if (len < 0) len = data.length - off;
        byte[] payload = seal.seal(type, data, off, len);
        out.writeByte(type);
        out.writeInt(payload.length);
        out.write(payload);
    }

    @NonNull
    private static Frame readFrame(@NonNull DataInputStream in, @NonNull Sealer open) throws IOException {
        Frame f = new Frame();
        f.type = in.readByte();
        int len = in.readInt();
        if (len < 0 || len > MAX_FRAME) throw new IOException("bad frame length");
        byte[] payload = new byte[len];
        in.readFully(payload);
        f.data = open.open(f.type, payload);
        return f;
    }

    /**
     * 枠ごとの AES-GCM。nonce は「向き(送る側 0 / 返事 1)+ 通し番号」なので、同じ鍵で同じ nonce を
     * 使うことはなく、枠を入れ替えたり抜いたりすると読めなくなる。鍵が無ければそのまま通す。
     */
    private static final class Sealer {
        @Nullable private final SecretKeySpec key;
        private final byte direction;
        private long counter;

        Sealer(@Nullable byte[] key, byte direction) {
            this.key = key != null ? new SecretKeySpec(key, "AES") : null;
            this.direction = direction;
        }

        @NonNull
        byte[] seal(byte type, @NonNull byte[] data, int off, int len) throws IOException {
            if (key == null) {
                byte[] copy = new byte[len];
                System.arraycopy(data, off, copy, 0, len);
                return copy;
            }
            try {
                Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
                c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce()));
                c.updateAAD(new byte[]{type});
                return c.doFinal(data, off, len);
            } catch (Exception e) {
                throw new IOException("encrypt", e);
            }
        }

        @NonNull
        byte[] open(byte type, @NonNull byte[] payload) throws IOException {
            if (key == null) return payload;
            try {
                Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
                c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, nonce()));
                c.updateAAD(new byte[]{type});
                return c.doFinal(payload);
            } catch (Exception e) {
                throw new WrongKey();
            }
        }

        @NonNull
        private byte[] nonce() {
            ByteBuffer b = ByteBuffer.allocate(12);
            b.put(direction).put(new byte[3]).putLong(counter++);
            return b.array();
        }
    }

    /** 鍵が合わない(別の QR コードの相手・書き換えられたデータ)。 */
    static final class WrongKey extends IOException {
    }

    @NonNull
    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
