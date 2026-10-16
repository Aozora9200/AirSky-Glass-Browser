package com.airsky.browser;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * assets/LICENSE.MD を読み、法的情報の画面で使う形にする。
 *
 * <p>ファイルは「===」の行で区切ったブロックの並び。ライブラリのブロックは
 * {@code ** group: xxx **} か {@code ** 名前 **} で始まり、{@code name: / copyrightHolder: / license: /
 * licenseUrl: / url:} の行と自由な説明文が続く。{@code ## 見出し} で始まる部分は文章のまま扱う。</p>
 */
final class LicenseCatalog {

    /** ライブラリ 1 つ分。 */
    static final class Entry {
        @Nullable String group;
        @Nullable String name;
        @Nullable String copyright;
        @Nullable String license;
        @Nullable String licenseUrl;
        @Nullable String url;
        final StringBuilder note = new StringBuilder();

        /** 一覧に出す名前(name があれば name、なければ group)。 */
        @NonNull
        String title() {
            if (name != null && !name.isEmpty()) return name;
            if (group != null) return group;
            return "?";
        }
    }

    /** 「## 見出し」で始まる文章の部分(サードパーティのアセット・Special Thanks・商標通知)。 */
    static final class Section {
        final String title;
        final StringBuilder body = new StringBuilder();

        Section(String title) {
            this.title = title;
        }
    }

    final List<Entry> entries = new ArrayList<>();
    final List<Section> sections = new ArrayList<>();
    /** 最後の「---」以降などの締めの文章(著作権表示)。 */
    final StringBuilder footer = new StringBuilder();

    private static LicenseCatalog cache;

    @NonNull
    static synchronized LicenseCatalog get(@NonNull Context c) {
        if (cache == null) cache = load(c.getApplicationContext());
        return cache;
    }

    @NonNull
    private static LicenseCatalog load(@NonNull Context c) {
        LicenseCatalog cat = new LicenseCatalog();
        List<String> lines = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                c.getAssets().open("LICENSE.MD"), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) lines.add(line);
        } catch (IOException e) {
            return cat;
        }

        Entry entry = null;
        Section section = null;
        for (String raw : lines) {
            String line = raw.trim();
            if (line.startsWith("===")) {
                entry = null;
                continue;
            }
            if (line.startsWith("## ")) {
                entry = null;
                section = new Section(line.substring(3).trim());
                cat.sections.add(section);
                continue;
            }
            if (line.startsWith("# ")) continue; // ファイル全体の見出し
            if (section != null) {
                // 見出しのあとは文章。** で始まるブロックも Special Thanks のように文章の一部として残す
                section.body.append(raw).append('\n');
                continue;
            }
            if (line.startsWith("**") && line.endsWith("**") && line.length() > 4) {
                entry = new Entry();
                String head = line.substring(2, line.length() - 2).trim();
                if (head.startsWith("group:")) {
                    entry.group = head.substring(6).trim();
                } else {
                    entry.name = head;
                }
                cat.entries.add(entry);
                continue;
            }
            if (entry == null || line.isEmpty()) continue;
            String value;
            if ((value = field(line, "name:")) != null) entry.name = value;
            else if ((value = field(line, "copyrightHolder:")) != null) entry.copyright = value;
            else if ((value = field(line, "licenseUrl:")) != null) entry.licenseUrl = value;
            else if ((value = field(line, "license:")) != null) entry.license = value;
            else if ((value = field(line, "url:")) != null) entry.url = value;
            else {
                if (entry.note.length() > 0) entry.note.append('\n');
                entry.note.append(line);
            }
        }

        // 最後の見出し(商標通知)の後ろにある「---」以降は全体の締めの文章として分ける
        if (!cat.sections.isEmpty()) {
            Section last = cat.sections.get(cat.sections.size() - 1);
            String body = last.body.toString();
            int cut = body.indexOf("\n---");
            if (cut >= 0) {
                cat.footer.append(body.substring(cut).replace("---", "").trim());
                last.body.setLength(0);
                last.body.append(body, 0, cut);
            }
        }
        Collections.sort(cat.entries, (a, b) -> a.title().compareToIgnoreCase(b.title()));
        return cat;
    }

    @Nullable
    private static String field(String line, String key) {
        if (!line.startsWith(key)) return null;
        return line.substring(key.length()).trim();
    }

    /** Markdown の飾り(**太字** など)を外して画面に出せる文章にする。 */
    @NonNull
    static String plain(@NonNull CharSequence md) {
        return md.toString()
                .replaceAll("\\*\\*(.*?)\\*\\*", "$1")
                .replaceAll("(?m)^-{3,}\\s*$", "")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    /** URL を Aozora のタブで開く。 */
    static void openUrl(@NonNull android.app.Activity from, @NonNull String url) {
        Intent intent = new Intent(from, MainActivity.class);
        intent.putExtra("url", url);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        from.startActivity(intent);
    }
}
