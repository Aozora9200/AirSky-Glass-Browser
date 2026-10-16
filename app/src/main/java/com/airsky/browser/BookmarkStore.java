package com.airsky.browser;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * ブックマークの保存先。フォルダを入れ子にでき、一番上には次の 3 つの決まったフォルダがある。
 * <ul>
 *   <li>ブックマーク({@link #ROOT_BOOKMARKS}) … 普段のブックマーク。保存先の初期値</li>
 *   <li>その他のブックマーク({@link #ROOT_OTHER}) … 整理用の置き場</li>
 *   <li>リーディングリスト({@link #ROOT_READING}) … 後で読むページ。フォルダは作れず、既読/未読を持つ</li>
 * </ul>
 * 以前のブックマーク(saved_bm.db)は、初めて開いた時に「ブックマーク」へ移す。
 */
public final class BookmarkStore extends SQLiteOpenHelper {

    /** 3 つのフォルダの親(画面上の一番上。実際のフォルダではない)。 */
    public static final long ROOT = 0;
    public static final long ROOT_BOOKMARKS = 1;
    public static final long ROOT_OTHER = 2;
    public static final long ROOT_READING = 3;

    public static final int SORT_NEWEST = 0;
    public static final int SORT_OLDEST = 1;
    public static final int SORT_NAME = 2;

    private static final String DB_NAME = "bookmarks_v2.db";
    private static final int DB_VERSION = 1;
    private static final String OLD_DB_NAME = "saved_bm.db";
    private static final String T = "nodes";

    /** ブックマーク 1 件、またはフォルダ 1 つ。 */
    public static final class Node {
        public final long id;
        public final long parent;
        public final boolean folder;
        public final String title;
        @Nullable public final String url;
        public final long created;
        @Nullable public final String thumb;
        public final boolean read;

        Node(long id, long parent, boolean folder, String title, @Nullable String url, long created,
             @Nullable String thumb, boolean read) {
            this.id = id;
            this.parent = parent;
            this.folder = folder;
            this.title = title != null ? title : "";
            this.url = url;
            this.created = created;
            this.thumb = thumb;
            this.read = read;
        }

        public boolean isRoot() {
            return parent == ROOT;
        }
    }

    private static BookmarkStore instance;
    private final Context appContext;

    public static synchronized BookmarkStore get(@NonNull Context context) {
        if (instance == null) instance = new BookmarkStore(context.getApplicationContext());
        return instance;
    }

    /** データベースを閉じて、次の get() で開き直す(データをすべて消して入れ替える時)。 */
    static synchronized void closeInstance() {
        if (instance != null) {
            instance.close();
            instance = null;
        }
    }

    private BookmarkStore(Context appContext) {
        super(appContext, DB_NAME, null, DB_VERSION);
        this.appContext = appContext;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + T + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "parent INTEGER NOT NULL, "
                + "folder INTEGER NOT NULL DEFAULT 0, "
                + "title TEXT, "
                + "url TEXT, "
                + "created INTEGER NOT NULL DEFAULT 0, "
                + "thumb TEXT, "
                + "read INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX nodes_parent ON " + T + "(parent)");
        db.execSQL("CREATE INDEX nodes_url ON " + T + "(url)");
        long now = System.currentTimeMillis();
        insertRoot(db, ROOT_BOOKMARKS, Lang.t("ブックマーク"), now);
        insertRoot(db, ROOT_OTHER, Lang.t("その他のブックマーク"), now);
        insertRoot(db, ROOT_READING, Lang.t("リーディングリスト"), now);
        migrateOld(db);
    }

    private static void insertRoot(SQLiteDatabase db, long id, String title, long now) {
        ContentValues v = new ContentValues();
        v.put("id", id);
        v.put("parent", ROOT);
        v.put("folder", 1);
        v.put("title", title);
        v.put("created", now);
        db.insert(T, null, v);
    }

    /** 以前のブックマーク(1 段だけの一覧)を「ブックマーク」に移す。 */
    private void migrateOld(SQLiteDatabase db) {
        File old = appContext.getDatabasePath(OLD_DB_NAME);
        if (!old.exists()) return;
        try (SQLiteDatabase src = SQLiteDatabase.openDatabase(old.getPath(), null, SQLiteDatabase.OPEN_READONLY);
             Cursor c = src.rawQuery("SELECT url, title, screenshot_path FROM pages ORDER BY id", null)) {
            long time = System.currentTimeMillis() - c.getCount() * 1000L;
            while (c.moveToNext()) {
                String url = c.getString(0);
                if (url == null || url.isEmpty()) continue;
                ContentValues v = new ContentValues();
                v.put("parent", ROOT_BOOKMARKS);
                v.put("folder", 0);
                v.put("title", c.getString(1));
                v.put("url", url);
                v.put("thumb", c.getString(2));
                // 並び順(新しい順)が以前と同じになるよう、少しずつ時刻をずらす
                v.put("created", time += 1000L);
                db.insert(T, null, v);
            }
        } catch (Exception ignored) {
            // 古いデータが読めなくても新しいブックマークは使えるようにする
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }

    // ------------------------------------------------------------------
    // 読む
    // ------------------------------------------------------------------

    private static Node read(Cursor c) {
        return new Node(c.getLong(0), c.getLong(1), c.getInt(2) != 0, c.getString(3), c.getString(4),
                c.getLong(5), c.getString(6), c.getInt(7) != 0);
    }

    private static final String COLS = "id, parent, folder, title, url, created, thumb, read";

    @Nullable
    public Node node(long id) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT " + COLS + " FROM " + T + " WHERE id = ?", new String[]{String.valueOf(id)})) {
            return c.moveToFirst() ? read(c) : null;
        }
    }

    /** フォルダの中身。フォルダを先に、その後にブックマークを並べる。 */
    @NonNull
    public List<Node> children(long parent, int sort) {
        String order;
        switch (sort) {
            case SORT_OLDEST:
                order = "folder DESC, created ASC";
                break;
            case SORT_NAME:
                order = "folder DESC, title COLLATE NOCASE ASC";
                break;
            default:
                order = "folder DESC, created DESC";
                break;
        }
        // 一番上の 3 つは決まった順
        if (parent == ROOT) order = "id ASC";
        List<Node> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT " + COLS + " FROM " + T + " WHERE parent = ? ORDER BY " + order,
                new String[]{String.valueOf(parent)})) {
            while (c.moveToNext()) out.add(read(c));
        }
        return out;
    }

    /** フォルダの中のフォルダだけ(移動先を選ぶ画面用)。 */
    @NonNull
    public List<Node> folders(long parent) {
        List<Node> out = new ArrayList<>();
        for (Node n : children(parent, SORT_NAME)) if (n.folder) out.add(n);
        return out;
    }

    /** フォルダに直接入っている数(フォルダ + ブックマーク)。 */
    public int count(long parent) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM " + T + " WHERE parent = ?", new String[]{String.valueOf(parent)})) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    /** 名前か URL に文字を含むブックマーク(フォルダは除く)。 */
    @NonNull
    public List<Node> search(@NonNull String query, long within) {
        String q = "%" + query.toLowerCase(Locale.ROOT).replace("%", "\\%").replace("_", "\\_") + "%";
        List<Node> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT " + COLS + " FROM " + T + " WHERE folder = 0 AND (LOWER(title) LIKE ? ESCAPE '\\' "
                        + "OR LOWER(url) LIKE ? ESCAPE '\\') ORDER BY created DESC", new String[]{q, q})) {
            while (c.moveToNext()) {
                Node n = read(c);
                if (within == ROOT || isInside(n.id, within)) out.add(n);
            }
        }
        return out;
    }

    /** その URL のブックマーク(リーディングリストは除く)。無ければ null。 */
    @Nullable
    public Node findBookmark(@Nullable String url) {
        return findUrl(url, false);
    }

    /** リーディングリストにある、その URL。無ければ null。 */
    @Nullable
    public Node findReading(@Nullable String url) {
        return findUrl(url, true);
    }

    @Nullable
    private Node findUrl(@Nullable String url, boolean reading) {
        if (url == null || url.isEmpty()) return null;
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT " + COLS + " FROM " + T + " WHERE folder = 0 AND url = ? ORDER BY created DESC",
                new String[]{url})) {
            while (c.moveToNext()) {
                Node n = read(c);
                if ((rootOf(n.id) == ROOT_READING) == reading) return n;
            }
        }
        return null;
    }

    /** そのノードが入っている一番上のフォルダ(ROOT_BOOKMARKS など)。 */
    public long rootOf(long id) {
        long cur = id;
        for (int guard = 0; guard < 64; guard++) {
            Node n = node(cur);
            if (n == null) return ROOT;
            if (n.parent == ROOT) return n.id;
            cur = n.parent;
        }
        return ROOT;
    }

    /** id が folder の中(folder 自身も含む)にあるか。 */
    public boolean isInside(long id, long folder) {
        long cur = id;
        for (int guard = 0; guard < 64 && cur != ROOT; guard++) {
            if (cur == folder) return true;
            Node n = node(cur);
            if (n == null) return false;
            cur = n.parent;
        }
        return false;
    }

    /** 一番上からそのフォルダまでの名前(「ブックマーク / 旅行 / 京都」)。 */
    @NonNull
    public List<Node> pathTo(long folder) {
        List<Node> out = new ArrayList<>();
        long cur = folder;
        for (int guard = 0; guard < 64 && cur != ROOT; guard++) {
            Node n = node(cur);
            if (n == null) break;
            out.add(n);
            cur = n.parent;
        }
        Collections.reverse(out);
        return out;
    }

    // ------------------------------------------------------------------
    // 書く
    // ------------------------------------------------------------------

    public long addBookmark(long parent, @NonNull String title, @NonNull String url, @Nullable String thumb) {
        return addBookmark(parent, title, url, thumb, System.currentTimeMillis());
    }

    /** 作った時刻を指定して追加する(ほかのブラウザから移す時に並び順を保つため)。 */
    public long addBookmark(long parent, @NonNull String title, @NonNull String url, @Nullable String thumb,
                            long created) {
        ContentValues v = new ContentValues();
        v.put("parent", parent);
        v.put("folder", 0);
        v.put("title", title);
        v.put("url", url);
        v.put("thumb", thumb);
        v.put("created", created);
        return getWritableDatabase().insert(T, null, v);
    }

    public long addFolder(long parent, @NonNull String title) {
        if (parent == ROOT || parent == ROOT_READING) return -1; // 一番上・リーディングリストには作れない
        ContentValues v = new ContentValues();
        v.put("parent", parent);
        v.put("folder", 1);
        v.put("title", title);
        v.put("created", System.currentTimeMillis());
        return getWritableDatabase().insert(T, null, v);
    }

    public void update(long id, @NonNull String title, @Nullable String url) {
        if (id <= ROOT_READING) return; // 決まったフォルダの名前は変えない
        ContentValues v = new ContentValues();
        v.put("title", title);
        if (url != null) v.put("url", url);
        getWritableDatabase().update(T, v, "id = ?", new String[]{String.valueOf(id)});
    }

    public void setThumb(long id, @Nullable String thumb) {
        ContentValues v = new ContentValues();
        v.put("thumb", thumb);
        getWritableDatabase().update(T, v, "id = ?", new String[]{String.valueOf(id)});
    }

    public void setRead(long id, boolean read) {
        ContentValues v = new ContentValues();
        v.put("read", read ? 1 : 0);
        getWritableDatabase().update(T, v, "id = ?", new String[]{String.valueOf(id)});
    }

    /**
     * 別のフォルダへ移す。フォルダを自分の中へは移せない。フォルダはリーディングリストへは移せない。
     * @return 移せたか
     */
    public boolean move(long id, long newParent) {
        Node n = node(id);
        if (n == null || n.isRoot() || newParent == ROOT) return false;
        if (n.folder && (newParent == ROOT_READING || isInside(newParent, id))) return false;
        ContentValues v = new ContentValues();
        v.put("parent", newParent);
        // リーディングリストへ入れたものは未読から始める
        if (newParent == ROOT_READING) v.put("read", 0);
        getWritableDatabase().update(T, v, "id = ?", new String[]{String.valueOf(id)});
        return true;
    }

    /** 消す(フォルダなら中身ごと)。決まったフォルダは消さない。 */
    public void delete(long id) {
        if (id <= ROOT_READING) return;
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            deleteRecursive(db, id);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private void deleteRecursive(SQLiteDatabase db, long id) {
        List<Long> kids = new ArrayList<>();
        try (Cursor c = db.rawQuery("SELECT id, thumb FROM " + T + " WHERE parent = ?",
                new String[]{String.valueOf(id)})) {
            while (c.moveToNext()) kids.add(c.getLong(0));
        }
        for (long k : kids) deleteRecursive(db, k);
        Node n = node(id);
        if (n != null && n.thumb != null && !n.thumb.isEmpty()) {
            // 他のブックマークが同じ画像を使っていなければ、画像も消す
            try (Cursor c = db.rawQuery("SELECT COUNT(*) FROM " + T + " WHERE thumb = ? AND id != ?",
                    new String[]{n.thumb, String.valueOf(id)})) {
                if (c.moveToFirst() && c.getInt(0) == 0) {
                    //noinspection ResultOfMethodCallIgnored
                    new File(n.thumb).delete();
                }
            }
        }
        db.delete(T, "id = ?", new String[]{String.valueOf(id)});
    }

    // ------------------------------------------------------------------
    // 表示の補助
    // ------------------------------------------------------------------

    /** 決まったフォルダのアイコン。普通のフォルダはフォルダのアイコン。 */
    public static int iconOf(@NonNull Node folder) {
        if (folder.id == ROOT_BOOKMARKS) return R.drawable.ic_pm_bookmark;
        if (folder.id == ROOT_READING) return R.drawable.ic_bm_reading;
        return R.drawable.ic_bm_folder;
    }

    /** 最後に保存したフォルダ(次に保存する時の保存先)。 */
    public long lastFolder() {
        long id = appContext.getSharedPreferences("BookmarkPrefs", Context.MODE_PRIVATE)
                .getLong("lastFolder", ROOT_BOOKMARKS);
        Node n = node(id);
        return n != null && n.folder && id != ROOT_READING ? id : ROOT_BOOKMARKS;
    }

    public void setLastFolder(long id) {
        if (id == ROOT_READING || id == ROOT) return;
        appContext.getSharedPreferences("BookmarkPrefs", Context.MODE_PRIVATE)
                .edit().putLong("lastFolder", id).apply();
    }

    public int sortMode() {
        return appContext.getSharedPreferences("BookmarkPrefs", Context.MODE_PRIVATE).getInt("sort", SORT_NEWEST);
    }

    public void setSortMode(int sort) {
        appContext.getSharedPreferences("BookmarkPrefs", Context.MODE_PRIVATE).edit().putInt("sort", sort).apply();
    }
}
