package com.airsky.browser;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * メモ(Keep 風)の保存場所。files/notes/notes.json に全部のメモを、
 * files/notes/media/ に写真・動画・録音・手書きのファイルを置く。
 */
final class NoteStore {

    /** チェックリストの 1 行 */
    static final class CheckItem {
        String text;
        boolean checked;

        CheckItem(String text, boolean checked) {
            this.text = text;
            this.checked = checked;
        }
    }

    static final String TYPE_IMAGE = "image";
    static final String TYPE_VIDEO = "video";
    static final String TYPE_AUDIO = "audio";
    static final String TYPE_DRAWING = "drawing";

    /** 添付(写真・動画・録音・手書き)。file は media フォルダの中の名前 */
    static final class Attachment {
        String type;
        String file;
        long durationMs;

        Attachment(String type, String file) {
            this.type = type;
            this.file = file;
        }
    }

    /** URL のプレビュー(ページの題名・説明・画像) */
    static final class LinkPreview {
        String url;
        String title;
        String description;
        @Nullable String imageFile;
        boolean failed;
        /** 利用者が × で消したプレビュー(URL が本文に残っていても出さない) */
        boolean hidden;
    }

    static final class Note {
        String id = UUID.randomUUID().toString();
        String title = "";
        String body = "";
        int color = 0;
        boolean pinned;
        boolean checklist;
        boolean markdown = true;
        long created = System.currentTimeMillis();
        long updated = created;
        final List<CheckItem> items = new ArrayList<>();
        final List<Attachment> attachments = new ArrayList<>();
        final Map<String, LinkPreview> links = new LinkedHashMap<>();

        boolean isEmpty() {
            if (!title.trim().isEmpty() || !body.trim().isEmpty() || !attachments.isEmpty()) return false;
            for (CheckItem i : items) if (!i.text.trim().isEmpty()) return false;
            return true;
        }

        /** チェックリストを本文の文字に直したもの(共有・書き出し・検索用) */
        String plainText() {
            if (!checklist) return body;
            StringBuilder sb = new StringBuilder();
            for (CheckItem i : items) {
                sb.append(i.checked ? "- [x] " : "- [ ] ").append(i.text).append('\n');
            }
            return sb.toString();
        }

        Note copy() {
            Note n = fromJson(toJson());
            n.id = UUID.randomUUID().toString();
            n.created = n.updated = System.currentTimeMillis();
            return n;
        }

        JSONObject toJson() {
            JSONObject o = new JSONObject();
            try {
                o.put("id", id);
                o.put("title", title);
                o.put("body", body);
                o.put("color", color);
                o.put("pinned", pinned);
                o.put("checklist", checklist);
                o.put("markdown", markdown);
                o.put("created", created);
                o.put("updated", updated);
                JSONArray a = new JSONArray();
                for (CheckItem i : items) {
                    a.put(new JSONObject().put("text", i.text).put("checked", i.checked));
                }
                o.put("items", a);
                JSONArray m = new JSONArray();
                for (Attachment at : attachments) {
                    m.put(new JSONObject().put("type", at.type).put("file", at.file).put("duration", at.durationMs));
                }
                o.put("attachments", m);
                JSONObject l = new JSONObject();
                for (LinkPreview p : links.values()) {
                    JSONObject j = new JSONObject();
                    j.put("title", p.title);
                    j.put("description", p.description);
                    j.put("image", p.imageFile);
                    j.put("failed", p.failed);
                    j.put("hidden", p.hidden);
                    l.put(p.url, j);
                }
                o.put("links", l);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return o;
        }

        static Note fromJson(JSONObject o) {
            Note n = new Note();
            n.id = o.optString("id", n.id);
            n.title = o.optString("title", "");
            n.body = o.optString("body", "");
            n.color = o.optInt("color", 0);
            n.pinned = o.optBoolean("pinned", false);
            n.checklist = o.optBoolean("checklist", false);
            n.markdown = o.optBoolean("markdown", true);
            n.created = o.optLong("created", n.created);
            n.updated = o.optLong("updated", n.created);
            JSONArray a = o.optJSONArray("items");
            if (a != null) {
                for (int i = 0; i < a.length(); i++) {
                    JSONObject j = a.optJSONObject(i);
                    if (j != null) n.items.add(new CheckItem(j.optString("text", ""), j.optBoolean("checked")));
                }
            }
            JSONArray m = o.optJSONArray("attachments");
            if (m != null) {
                for (int i = 0; i < m.length(); i++) {
                    JSONObject j = m.optJSONObject(i);
                    if (j == null) continue;
                    Attachment at = new Attachment(j.optString("type"), j.optString("file"));
                    at.durationMs = j.optLong("duration", 0);
                    n.attachments.add(at);
                }
            }
            JSONObject l = o.optJSONObject("links");
            if (l != null) {
                Iterator<String> keys = l.keys();
                while (keys.hasNext()) {
                    String url = keys.next();
                    JSONObject j = l.optJSONObject(url);
                    if (j == null) continue;
                    LinkPreview p = new LinkPreview();
                    p.url = url;
                    p.title = j.optString("title", null);
                    p.description = j.optString("description", null);
                    p.imageFile = j.isNull("image") ? null : j.optString("image", null);
                    p.failed = j.optBoolean("failed", false);
                    p.hidden = j.optBoolean("hidden", false);
                    n.links.put(url, p);
                }
            }
            return n;
        }
    }

    private static NoteStore instance;

    private final File dir;
    private final File mediaDir;
    private final File file;
    private final List<Note> notes = new ArrayList<>();

    private NoteStore(Context ctx) {
        dir = new File(ctx.getFilesDir(), "notes");
        mediaDir = new File(dir, "media");
        //noinspection ResultOfMethodCallIgnored
        mediaDir.mkdirs();
        file = new File(dir, "notes.json");
        load();
    }

    static synchronized NoteStore get(Context ctx) {
        if (instance == null) instance = new NoteStore(ctx.getApplicationContext());
        return instance;
    }

    File mediaDir() {
        return mediaDir;
    }

    File media(String name) {
        return new File(mediaDir, name);
    }

    /** media フォルダに新しいファイル名を作る */
    File newMediaFile(String ext) {
        return new File(mediaDir, UUID.randomUUID().toString().replace("-", "") + "." + ext);
    }

    synchronized List<Note> all() {
        return new ArrayList<>(notes);
    }

    @Nullable
    synchronized Note find(String id) {
        for (Note n : notes) if (n.id.equals(id)) return n;
        return null;
    }

    /** 新しいメモは先頭に、あるメモは置き換える */
    synchronized void put(@NonNull Note note) {
        for (int i = 0; i < notes.size(); i++) {
            if (notes.get(i).id.equals(note.id)) {
                notes.set(i, note);
                save();
                return;
            }
        }
        notes.add(0, note);
        save();
    }

    synchronized void delete(@NonNull Note note) {
        for (int i = 0; i < notes.size(); i++) {
            if (notes.get(i).id.equals(note.id)) {
                notes.remove(i);
                break;
            }
        }
        // 添付ファイルも消す
        for (Attachment a : note.attachments) {
            //noinspection ResultOfMethodCallIgnored
            media(a.file).delete();
        }
        for (LinkPreview p : note.links.values()) {
            if (p.imageFile != null) {
                //noinspection ResultOfMethodCallIgnored
                media(p.imageFile).delete();
            }
        }
        save();
    }

    /** 並べ替え(一覧でドラッグした時) */
    synchronized void reorder(List<Note> order) {
        notes.clear();
        notes.addAll(order);
        save();
    }

    private void load() {
        notes.clear();
        if (!file.isFile()) return;
        try (InputStream in = new FileInputStream(file)) {
            byte[] buf = new byte[(int) file.length()];
            int off = 0;
            while (off < buf.length) {
                int n = in.read(buf, off, buf.length - off);
                if (n < 0) break;
                off += n;
            }
            JSONArray a = new JSONArray(new String(buf, 0, off, StandardCharsets.UTF_8));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o != null) notes.add(Note.fromJson(o));
            }
        } catch (IOException | JSONException e) {
            e.printStackTrace();
        }
    }

    private void save() {
        JSONArray a = new JSONArray();
        for (Note n : notes) a.put(n.toJson());
        File tmp = new File(dir, "notes.json.tmp");
        try (OutputStream out = new FileOutputStream(tmp)) {
            out.write(a.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        // 書き込みの途中で落ちても前のデータが残るよう、書き終えてから置き換える
        if (!tmp.renameTo(file)) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
            //noinspection ResultOfMethodCallIgnored
            tmp.renameTo(file);
        }
    }
}
