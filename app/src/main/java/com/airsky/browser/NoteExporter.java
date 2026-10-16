package com.airsky.browser;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** メモをファイルに書き出す(Markdown・テキスト・添付ファイルごとの ZIP) */
final class NoteExporter {

    private NoteExporter() {}

    /** 書き出す時のファイル名(題名か本文の最初の行) */
    static String fileName(@NonNull NoteStore.Note n, String ext) {
        String base = n.title.trim();
        if (base.isEmpty()) {
            String first = n.plainText().trim();
            int nl = first.indexOf('\n');
            base = nl >= 0 ? first.substring(0, nl) : first;
        }
        base = base.replaceAll("[\\\\/:*?\"<>|\\n\\r\\t]", "_").trim();
        if (base.length() > 40) base = base.substring(0, 40);
        if (base.isEmpty()) base = Lang.t("メモ_") + new SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(new Date(n.updated));
        return base + "." + ext;
    }

    /** Markdown にする。mediaPrefix を渡すと添付の画像を ![](...) で参照する */
    static String toMarkdown(@NonNull NoteStore.Note n, String mediaPrefix) {
        StringBuilder sb = new StringBuilder();
        if (!n.title.trim().isEmpty()) sb.append("# ").append(n.title.trim()).append("\n\n");
        if (mediaPrefix != null) {
            for (NoteStore.Attachment a : n.attachments) {
                if (NoteStore.TYPE_IMAGE.equals(a.type) || NoteStore.TYPE_DRAWING.equals(a.type)) {
                    sb.append("![](").append(mediaPrefix).append(a.file).append(")\n\n");
                } else {
                    sb.append("[").append(label(a.type)).append("](").append(mediaPrefix).append(a.file).append(")\n\n");
                }
            }
        }
        sb.append(n.plainText());
        return sb.toString();
    }

    static String toText(@NonNull NoteStore.Note n) {
        StringBuilder sb = new StringBuilder();
        if (!n.title.trim().isEmpty()) sb.append(n.title.trim()).append("\n\n");
        if (n.checklist) {
            for (NoteStore.CheckItem i : n.items) sb.append(i.checked ? "☑ " : "☐ ").append(i.text).append('\n');
        } else {
            sb.append(n.markdown ? Markdown.strip(n.body) : n.body);
        }
        return sb.toString();
    }

    private static String label(String type) {
        switch (type) {
            case NoteStore.TYPE_AUDIO: return Lang.t("録音");
            case NoteStore.TYPE_VIDEO: return Lang.t("動画");
            case NoteStore.TYPE_DRAWING: return Lang.t("図形描画");
            default: return Lang.t("画像");
        }
    }

    static void writeText(Context ctx, Uri uri, String text) throws IOException {
        try (OutputStream out = ctx.getContentResolver().openOutputStream(uri)) {
            if (out == null) throw new IOException("cannot open");
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    /** メモ(複数可)を、1 つずつの .md と media フォルダにまとめた ZIP にする */
    static void writeZip(Context ctx, Uri uri, List<NoteStore.Note> notes) throws IOException {
        NoteStore store = NoteStore.get(ctx);
        try (OutputStream raw = ctx.getContentResolver().openOutputStream(uri)) {
            if (raw == null) throw new IOException("cannot open");
            try (ZipOutputStream zip = new ZipOutputStream(raw)) {
                Set<String> names = new HashSet<>();
                for (NoteStore.Note n : notes) {
                    String name = fileName(n, "md");
                    String unique = name;
                    for (int i = 2; names.contains(unique); i++) unique = name.replace(".md", " (" + i + ").md");
                    names.add(unique);
                    zip.putNextEntry(new ZipEntry(unique));
                    zip.write(toMarkdown(n, "media/").getBytes(StandardCharsets.UTF_8));
                    zip.closeEntry();
                    for (NoteStore.Attachment a : n.attachments) {
                        File f = store.media(a.file);
                        if (!f.isFile() || names.contains("media/" + a.file)) continue;
                        names.add("media/" + a.file);
                        zip.putNextEntry(new ZipEntry("media/" + a.file));
                        try (InputStream in = new FileInputStream(f)) {
                            byte[] buf = new byte[16384];
                            int r;
                            while ((r = in.read(buf)) != -1) zip.write(buf, 0, r);
                        }
                        zip.closeEntry();
                    }
                }
            }
        }
    }
}
