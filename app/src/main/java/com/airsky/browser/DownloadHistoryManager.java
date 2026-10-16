package com.airsky.browser;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class DownloadHistoryManager {

    private static final String PREF_NAME = "AdvancedBrowserPrefs";
    private static final String KEY_DOWNLOAD_HISTORY = "download_history";

    public static void addDownloadHistory(Context context, long downloadId, String fileName, String filePath) {
        addDownloadHistory(context, downloadId, fileName, filePath, null);
    }

    /**
     * @param contentUri MediaStore の URI(Android 10 以降の保存先)。Android 10 では公開フォルダの
     *                   ファイルをパスで確認できないため、存在確認・表示・削除はこちらで行う。
     */
    public static void addDownloadHistory(Context context, long downloadId, String fileName,
                                          String filePath, @Nullable String contentUri) {
        addDownloadHistory(context, downloadId, fileName, filePath, contentUri, null);
    }

    /**
     * @param sourceUrl ダウンロード元の URL(ダウンロード画面にドメインを出す)
     */
    public static synchronized void addDownloadHistory(Context context, long downloadId, String fileName,
                                                       String filePath, @Nullable String contentUri,
                                                       @Nullable String sourceUrl) {
        SharedPreferences pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        try {
            JSONArray array = new JSONArray(pref.getString(KEY_DOWNLOAD_HISTORY, "[]"));
            JSONObject obj = new JSONObject();
            obj.put("id", downloadId);
            obj.put("fileName", fileName);
            obj.put("filePath", filePath);
            if (contentUri != null) obj.put("uri", contentUri);
            if (sourceUrl != null) obj.put("url", sourceUrl);
            // ダウンロード画面で日付ごとに分けるための時刻
            obj.put("time", System.currentTimeMillis());
            array.put(obj);
            pref.edit().putString(KEY_DOWNLOAD_HISTORY, array.toString()).apply();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /** 履歴から 1 件消す(ファイルはそのまま)。 */
    public static synchronized void removeDownloadHistory(Context context, long downloadId) {
        SharedPreferences pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        try {
            JSONArray array = new JSONArray(pref.getString(KEY_DOWNLOAD_HISTORY, "[]"));
            JSONArray kept = new JSONArray();
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                if (obj.optLong("id") != downloadId) kept.put(obj);
            }
            pref.edit().putString(KEY_DOWNLOAD_HISTORY, kept.toString()).apply();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /** ウイルスが見つかってダウンロードを中止した(ファイルは削除済み)ことを記録する。 */
    public static synchronized void markThreat(Context context, long downloadId, String threat) {
        SharedPreferences pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        try {
            JSONArray array = new JSONArray(pref.getString(KEY_DOWNLOAD_HISTORY, "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                if (obj.optLong("id") != downloadId) continue;
                obj.put("threat", threat);
                pref.edit().putString(KEY_DOWNLOAD_HISTORY, array.toString()).apply();
                return;
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /** ダウンロード完了時に、確定したファイル名・パスへ書き換える。 */
    public static synchronized void updateDownloadHistory(Context context, long downloadId, String fileName,
                                                          String filePath, @Nullable String contentUri) {
        SharedPreferences pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        try {
            JSONArray array = new JSONArray(pref.getString(KEY_DOWNLOAD_HISTORY, "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                if (obj.optLong("id") != downloadId) continue;
                obj.put("fileName", fileName);
                obj.put("filePath", filePath);
                if (contentUri != null) obj.put("uri", contentUri);
                pref.edit().putString(KEY_DOWNLOAD_HISTORY, array.toString()).apply();
                return;
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}
