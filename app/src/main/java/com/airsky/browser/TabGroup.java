package com.airsky.browser;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * タブグループ(Chrome と同じく、名前と色を付けてタブをまとめる)。
 *
 * 開いているグループのタブは通常のタブとして並び、どのタブがどのグループかは MainActivity が
 * タブ ID ごとに覚える。グループを「閉じる」と、タブを閉じて URL とタイトルだけを {@link #saved} に残し、
 * タブ一覧の「タブグループ」からいつでも開き直せる。
 */
final class TabGroup {

    /** Chrome と同じ 9 色(灰・青・赤・黄・緑・ピンク・紫・シアン・オレンジ)。ライト / ダーク用。 */
    private static final int[] COLORS_LIGHT = {
            0xFF5F6368, 0xFF1A73E8, 0xFFD93025, 0xFFF9AB00, 0xFF1E8E3E,
            0xFFD01884, 0xFF9334E6, 0xFF007B83, 0xFFFA903E
    };
    private static final int[] COLORS_DARK = {
            0xFFDADCE0, 0xFF8AB4F8, 0xFFF28B82, 0xFFFDD663, 0xFF81C995,
            0xFFFF8BCB, 0xFFC58AF9, 0xFF78D9EC, 0xFFFCAD70
    };
    static final String[] COLOR_NAMES = {Lang.t("グレー"), Lang.t("青"), Lang.t("赤"), Lang.t("黄"), Lang.t("緑"), Lang.t("ピンク"), Lang.t("紫"), Lang.t("シアン"), Lang.t("オレンジ")};

    static int colorCount() {
        return COLORS_LIGHT.length;
    }

    /** 色番号の表示色。シークレット(常にダーク)ではダーク用を使う。 */
    static int colorValue(@NonNull Context context, int index, boolean forceDark) {
        int i = Math.max(0, Math.min(index, COLORS_LIGHT.length - 1));
        boolean night = forceDark || (context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        return night ? COLORS_DARK[i] : COLORS_LIGHT[i];
    }

    /** 閉じたグループに残すタブ。 */
    static final class SavedTab {
        final String url;
        final String title;

        SavedTab(@Nullable String url, @Nullable String title) {
            this.url = url != null ? url : "";
            this.title = title != null ? title : "";
        }
    }

    final int id;
    /** 空なら「N 個のタブ」と表示する。 */
    String title = "";
    int color;
    /** 最後に変更した時刻(「1 年前に更新」の表示用)。 */
    long updated;
    /** 閉じて保存している(タブは開いていない)。 */
    boolean closed;
    /** 閉じている時のタブ。 */
    final List<SavedTab> saved = new ArrayList<>();

    TabGroup(int id) {
        this.id = id;
    }

    void touch() {
        updated = System.currentTimeMillis();
    }

    /** 表示用の名前。名前が無ければ「N 個のタブ」。 */
    String displayTitle(int tabCount) {
        return title != null && !title.trim().isEmpty() ? title.trim() : tabCount + Lang.t(" 個のタブ");
    }

    // ------------------------------------------------------------------
    // 保存
    // ------------------------------------------------------------------

    /** すべてのグループ(開いているもの・閉じて保存しているもの)。端末に保存する。 */
    static final class Store {
        private static final String KEY = "tab_groups";

        private final SharedPreferences prefs;
        private final List<TabGroup> groups = new ArrayList<>();
        private int nextId;

        Store(@NonNull SharedPreferences prefs) {
            this.prefs = prefs;
            load();
        }

        private void load() {
            groups.clear();
            try {
                JSONArray array = new JSONArray(prefs.getString(KEY, "[]"));
                for (int i = 0; i < array.length(); i++) {
                    JSONObject o = array.getJSONObject(i);
                    TabGroup g = new TabGroup(o.getInt("id"));
                    g.title = o.optString("title", "");
                    g.color = o.optInt("color", 0);
                    g.updated = o.optLong("updated", 0);
                    g.closed = o.optBoolean("closed", false);
                    JSONArray tabs = o.optJSONArray("tabs");
                    if (tabs != null) {
                        for (int j = 0; j < tabs.length(); j++) {
                            JSONObject t = tabs.getJSONObject(j);
                            g.saved.add(new SavedTab(t.optString("url"), t.optString("title")));
                        }
                    }
                    groups.add(g);
                    nextId = Math.max(nextId, g.id + 1);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        void save() {
            JSONArray array = new JSONArray();
            try {
                for (TabGroup g : groups) {
                    JSONObject o = new JSONObject();
                    o.put("id", g.id);
                    o.put("title", g.title);
                    o.put("color", g.color);
                    o.put("updated", g.updated);
                    o.put("closed", g.closed);
                    if (g.closed) {
                        JSONArray tabs = new JSONArray();
                        for (SavedTab t : g.saved) {
                            tabs.put(new JSONObject().put("url", t.url).put("title", t.title));
                        }
                        o.put("tabs", tabs);
                    }
                    array.put(o);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
            prefs.edit().putString(KEY, array.toString()).apply();
        }

        /** 新しいグループ。色は前のグループの次の色にする(Chrome と同じく順番に変わる)。 */
        TabGroup create() {
            TabGroup g = new TabGroup(nextId++);
            int lastColor = groups.isEmpty() ? -1 : groups.get(groups.size() - 1).color;
            g.color = (lastColor + 1) % colorCount();
            g.touch();
            groups.add(g);
            return g;
        }

        @Nullable
        TabGroup get(int id) {
            for (TabGroup g : groups) {
                if (g.id == id) return g;
            }
            return null;
        }

        void remove(@NonNull TabGroup g) {
            groups.remove(g);
        }

        /** 元に戻す(削除・閉じる操作の取り消し)。 */
        void restore(@NonNull TabGroup g) {
            if (!groups.contains(g)) groups.add(g);
            nextId = Math.max(nextId, g.id + 1);
        }

        /** 新しく更新した順。 */
        List<TabGroup> all() {
            List<TabGroup> list = new ArrayList<>(groups);
            Collections.sort(list, (a, b) -> Long.compare(b.updated, a.updated));
            return list;
        }
    }
}
