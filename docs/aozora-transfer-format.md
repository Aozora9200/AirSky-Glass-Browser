# Aozora → AirSky 引っ越しファイル(.az)の形式

AirSky は初回起動時、Aozora から次の条件で起動されると、最初の動画(AZ_MOV)の後で
「Aozora からデータを引っ越ししますか？」と尋ねます。

- 起動 Intent の extra `Aozora_to_AirSky` が `true`(boolean。文字列 `"true"` も可)
- AirSky のセットアップがまだ済んでいない(`AppPrefs.isFirstRun_v2 == true`)
- まだ引っ越しをしていない(`AppPrefs.aozoraTransferDone != true`)

「はい」を押すと、`ダウンロード/Aozora/.Aozora-西暦.az` を読み込みます
(複数あれば西暦がいちばん新しいもの)。

| 結果 | AirSky の動き |
| --- | --- |
| 成功 | 「引っ越しが完了しました」→ `.az` を削除 → 再起動してセットアップへ |
| 失敗 | 理由を表示 → AirSky のデータをすべて削除 → 再起動(`.az` は残す) |

### 初回起動でない時(AirSky を使っている途中)

`Aozora_to_AirSky = true` で起動されたのがセットアップ後だった場合は、次の順に警告します。

1. 「Aozora 以外からの引っ越しの誘導にご注意下さい！」と、ほかのブラウザからの引っ越しで損害が出ても
   一切責任を負えない旨の警告 →［次へ］／［キャンセル］
2. 「引っ越しの際、既存のデータを削除してデータが上書きされます！」→［同意して引っ越す］(赤)／［キャンセル］

［キャンセル］(とファイルへのアクセスを断った場合)はアプリを終了します。［同意して引っ越す］では
`.az` を読み込んで中身を確かめてから既存のデータをすべて削除し、Aozora のデータを書き込みます。
ファイルが無い・壊れているなど、削除の前に失敗した場合は既存のデータを残したまま終了します。
削除の後に失敗した場合は、すべてのデータを削除して再起動します。成功後は再起動してセットアップへ進みます。

AirSky がすでに起動していると、ランチャー用の Intent はそのタスクを前に出すだけで extra が届かないことがあります。
Aozora からは `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK` を付けて起動してください(下の例を参照)。

Android 11 以降は「すべてのファイルへのアクセス」の許可を求めます。許可されない場合は、
ファイルを自分で選んでもらうこともできます。

## ファイルの中身

`.az` は ZIP です。Aozora のアプリ領域(`/data/data/com.aozora.aozora/`)の
`shared_prefs/` と `databases/` を、そのままのファイル名で入れてください。

```
manifest.json                         (任意)
shared_prefs/AppPrefs.xml
shared_prefs/WebViewTabs.xml
shared_prefs/AdvancedBrowserPrefs.xml
shared_prefs/theme_prefs.xml
shared_prefs/popup_prefs.xml
shared_prefs/arrow_prefs.xml
shared_prefs/GeoPermissionStore.xml
databases/history.db
databases/saved_bm.db
databases/saved_pages.db              (任意。保存したページ)
saved_pages/page_*.mht                (任意。saved_pages.db の url のファイル)
saved_pages/screenshot_*.png          (任意。saved_pages.db の screenshot_path のファイル)
tab_history.json                      (任意。タブの戻る/進む履歴)
```

`tab_history.json` は、Aozora のタブの id ごとの戻る/進む履歴です(`index` は今のページ、0 から数える)。
WebView の `tab_state_<id>.dat` は GeckoView では読めないので、Aozora 側で URL の一覧にして入れます。

```json
{ "3": { "index": 1, "entries": [ { "url": "https://a.example/", "title": "A" }, { "url": "https://b.example/", "title": "B" } ] } }
```

`saved_pages/` のファイルは、`saved_pages.db` に書かれたパスのファイル名で探します。

`manifest.json` を入れる場合は `format` を `"aozora-airsky-transfer"` にします
(違う値だと形式エラーで失敗します)。

```json
{ "format": "aozora-airsky-transfer", "version": 1, "app": "com.aozora.aozora", "versionName": "2.1", "created": 1791812168454 }
```

パスの前に余分なフォルダがあっても(`data/shared_prefs/AppPrefs.xml` など)読めます。
1 ファイル 64MB、合計 256MB までです(`saved_pages/` は別に合計 512MB まで。超えた分は移さずに続けます)。

## AirSky に移すもの

| Aozora | AirSky |
| --- | --- |
| `WebViewTabs.bookmarks`(JSON)と `saved_bm.db` の `pages` | ブックマーク(「ブックマーク」フォルダ。同じ URL は 1 つだけ) |
| `WebViewTabs.history`(JSON)+ `history.db` の時刻 | 履歴 |
| `WebViewTabs.saved_tabs` / `current_tab_id` | 開いていたタブ |
| `tab_history.json` | タブの戻る/進む履歴(Gecko のセッション状態 `tab_state_<id>.json` にする) |
| `saved_pages.db` の `pages` と `saved_pages/` | 保存したページ(.mht は画像・CSS を埋め込んだ HTML に変える) |
| `AdvancedBrowserPrefs.download_history` | ダウンロード履歴 |
| `AppPrefs` の表示・動作の設定(`bootScreen`, `zoomButton`, `touchEffect`, `hidebottom` など) | 同じ名前の設定 |
| `AdvancedBrowserPrefs` の `dark_mode`, `ua_enabled` など | 同じ名前の設定 |
| `theme_prefs` / `popup_prefs` / `arrow_prefs` | テーマ・メニューボタン・矢印ボタン |
| `GeoPermissionStore` | サイトの位置情報の許可 |

移さないもの: `password_prefs`(アプリのロック)、初回起動のフラグ(`isFirstRun_v2`, `isFirstMov`)、
`file:///android_asset/...` などの内蔵ページの URL。

## Aozora 側の書き出しの例

```java
// ダウンロード/Aozora/.Aozora-<西暦>.az に書き出してから AirSky を起動する
void exportToAirSky(Activity a) throws IOException, JSONException {
    File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Aozora");
    dir.mkdirs();
    int year = Calendar.getInstance().get(Calendar.YEAR);
    File out = new File(dir, ".Aozora-" + year + ".az");
    File data = new File(a.getApplicationInfo().dataDir);
    try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(out))) {
        JSONObject m = new JSONObject();
        m.put("format", "aozora-airsky-transfer");
        m.put("version", 1);
        m.put("app", a.getPackageName());
        m.put("created", System.currentTimeMillis());
        zip.putNextEntry(new ZipEntry("manifest.json"));
        zip.write(m.toString().getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
        for (String folder : new String[]{"shared_prefs", "databases"}) {
            File[] files = new File(data, folder).listFiles();
            if (files == null) continue;
            for (File f : files) {
                if (!f.isFile() || f.getName().endsWith("-journal") || f.getName().endsWith("-wal")) continue;
                zip.putNextEntry(new ZipEntry(folder + "/" + f.getName()));
                try (FileInputStream in = new FileInputStream(f)) {
                    byte[] buf = new byte[16384];
                    int n;
                    while ((n = in.read(buf)) > 0) zip.write(buf, 0, n);
                }
                zip.closeEntry();
            }
        }
    }
    Intent i = a.getPackageManager().getLaunchIntentForPackage("com.airsky.browser");
    if (i != null) {
        i.putExtra("Aozora_to_AirSky", true);
        // AirSky が起動中でも、起動画面から始め直して extra を受け取れるようにする
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        a.startActivity(i);
    }
}
```

書き出す前に `SharedPreferences` の `apply()` が終わっているよう、保存は `commit()` にしておくと確実です。
データベースは書き出しの間だけ閉じておくと(または `PRAGMA wal_checkpoint` 後に)中身が揃います。
