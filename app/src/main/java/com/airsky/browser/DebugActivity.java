package com.airsky.browser;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Debug Menu(設定 › アプリ情報 から開く開発者向けの画面)。
 * ブラウザの細かい切り替え・データの消去・フラグの初期化と、クラス名を入れて任意の画面(アクティビティ)を開く機能。
 */
public class DebugActivity extends PixelSettingsActivity {

    private static final String PREFS = "AppPrefs";
    /** 最近開いたアクティビティ(新しい順、JSON 配列)。 */
    private static final String KEY_RECENT_ACTIVITIES = "debugRecentActivities";
    private static final int RECENT_MAX = 6;

    @Nullable private TextInputEditText activityField;
    /** 作り直した時も入力中の文字を残す。 */
    private String activityText = "";

    @NonNull
    @Override
    protected CharSequence pageTitle() {
        return "Debug Menu";
    }

    @Override
    protected void onBuild() {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);

        // ---- 任意のアクティビティを起動 ----
        header(Lang.t("アクティビティを起動"));
        card(buildLauncher());
        List<String> recent = recentActivities();
        for (String name : recent) {
            row(R.drawable.ic_ps_history, shortName(name), name, () -> launchActivity(name)).chevron();
        }
        row(R.drawable.ic_ps_list, Lang.t("アクティビティの一覧から選ぶ"),
                Lang.t("このアプリの画面をすべて表示します"), this::pickActivity).chevron();
        note(Lang.t("クラス名は「com.airsky.browser.SettingsActivity」「.SettingsActivity」「SettingsActivity」のどれでも入力できます。ほかのアプリは「パッケージ名/クラス名」で指定します。"));

        // ---- ブラウザ ----
        header(Lang.t("ブラウザ"));
        prefSwitch(p, R.drawable.ic_ps_safety, Lang.t("セキュリティ警告"), "securityAlert", true);
        prefSwitch(p, R.drawable.ic_si_cookie, Lang.t("Cookieを受け入れる"), "acceptCookies", true);
        prefSwitch(p, R.drawable.ic_ps_location, Lang.t("位置情報を有効にする"), "geoLocation", true);
        prefSwitch(p, R.drawable.ic_ps_code, Lang.t("JavaScriptを有効にする"), "javaScript", true);
        // 保存しているのは「ブロックしない」なので、表示は反対にする
        switchRow(R.drawable.ic_ps_visibility_off, Lang.t("ポップアップをブロック"), null,
                !p.getBoolean("popupBlock", false), on -> p.edit().putBoolean("popupBlock", !on).apply());
        prefSwitch(p, R.drawable.ic_ps_touch, Lang.t("タップを視覚化"), "touchEffect", true);

        // ---- データ ----
        header(Lang.t("データ"));
        row(R.drawable.ic_ps_storage, Lang.t("キャッシュを消去"), null, () -> {
            GeckoEngine.get(this).clearCache();
            Toast.makeText(this, Lang.t("キャッシュを削除しました"), Toast.LENGTH_SHORT).show();
        });
        row(R.drawable.ic_ps_delete, Lang.t("Cookieをすべて消去"), null,
                () -> confirm(Lang.t("Cookieをすべて消去"), Lang.t("すべてのCookieを削除しますか？"), "OK",
                        () -> GeckoEngine.get(this).clearCookies()));
        row(R.drawable.ic_ps_location, Lang.t("位置情報アクセスをクリア"), null,
                () -> confirm(Lang.t("位置情報アクセスをクリア"),
                        Lang.t("ウェブサイトの現在地情報へのアクセスをクリアしますか？"), "OK",
                        () -> getSharedPreferences("GeoPermissionStore", MODE_PRIVATE).edit().clear().apply()));

        // ---- 画面 ----
        header(Lang.t("画面"));
        row(R.drawable.ic_ps_browser, Lang.t("ウェブサイト設定"), null,
                () -> open(WebSiteSettingsActivity.class)).chevron();
        row(R.drawable.ic_ps_location, Lang.t("位置情報の権限設定"), null,
                () -> open(GeoSettingsActivity.class)).chevron();
        row(R.drawable.ic_ps_info, Lang.t("ライセンス"), null, () -> open(OssLicensesActivity.class)).chevron();

        // ---- フラグ ----
        header(Lang.t("フラグ"));
        row(R.drawable.ic_ps_refresh, Lang.t("AZ_MOVフラグの初期化(isFirstMov)"),
                Lang.t("次に起動した時、最初の動画をもう一度再生します"), () -> {
                    p.edit().putBoolean("isFirstMov", true).apply();
                    Toast.makeText(this, Lang.t("初期化しました"), Toast.LENGTH_SHORT).show();
                });
    }

    private void prefSwitch(@NonNull SharedPreferences p, int icon, @NonNull CharSequence title,
                            @NonNull String key, boolean def) {
        switchRow(icon, title, null, p.getBoolean(key, def), on -> p.edit().putBoolean(key, on).apply());
    }

    // ------------------------------------------------------------------
    // 任意のアクティビティを起動
    // ------------------------------------------------------------------

    /** クラス名の入力欄と「起動」ボタン。 */
    @NonNull
    private LinearLayout buildLauncher() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(dp(16), dp(12), dp(12), dp(16));

        TextInputLayout layout = new TextInputLayout(this, null,
                com.google.android.material.R.attr.textInputOutlinedStyle);
        layout.setHint(Lang.t("クラス名"));
        layout.setPlaceholderText(".SettingsActivity");
        layout.setEndIconMode(TextInputLayout.END_ICON_CLEAR_TEXT);
        TextInputEditText et = new TextInputEditText(layout.getContext());
        et.setSingleLine(true);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                | InputType.TYPE_TEXT_VARIATION_URI);
        et.setImeOptions(EditorInfo.IME_ACTION_GO);
        et.setTypeface(android.graphics.Typeface.MONOSPACE);
        et.setText(activityText);
        et.setSelection(et.length());
        et.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                activityText = s.toString();
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
            }
        });
        et.setOnEditorActionListener((v, actionId, event) -> {
            boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN;
            if (actionId == EditorInfo.IME_ACTION_GO || enter) {
                launchActivity(activityText);
                return true;
            }
            return false;
        });
        layout.addView(et);
        activityField = et;
        box.addView(layout, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        MaterialButton go = new MaterialButton(this);
        go.setText(Lang.t("起動する"));
        go.setIconResource(R.drawable.ic_ps_play);
        go.setOnClickListener(v -> launchActivity(activityText));
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        glp.setMarginStart(dp(10));
        glp.topMargin = dp(4);
        box.addView(go, glp);
        return box;
    }

    /** 入力されたクラス名の画面を開く。 */
    private void launchActivity(@Nullable String input) {
        String text = input == null ? "" : input.trim();
        if (text.isEmpty()) {
            if (activityField != null) activityField.setError(Lang.t("クラス名を入力してください"));
            return;
        }
        ComponentName component = resolve(text);
        Intent intent = new Intent().setComponent(component);
        try {
            startActivity(intent);
            remember(component.getPackageName().equals(getPackageName())
                    ? component.getClassName() : component.flattenToString());
        } catch (ActivityNotFoundException e) {
            showLaunchError(component, Lang.t("この名前のアクティビティは見つかりません。"));
        } catch (SecurityException e) {
            showLaunchError(component, Lang.t("このアクティビティは外から開けません(exported=false など)。"));
        } catch (Exception e) {
            showLaunchError(component, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /**
     * 「パッケージ/クラス」「.クラス」「クラス」「完全なクラス名」を ComponentName にする。
     * パッケージを書いていない時はこのアプリの画面とみなす。
     */
    @NonNull
    private ComponentName resolve(@NonNull String text) {
        String pkg = getPackageName();
        String cls = text;
        int slash = text.indexOf('/');
        if (slash > 0) {
            pkg = text.substring(0, slash);
            cls = text.substring(slash + 1);
        }
        if (cls.startsWith(".")) {
            cls = pkg + cls;
        } else if (!cls.contains(".")) {
            cls = pkg + "." + cls;
        }
        return new ComponentName(pkg, cls);
    }

    private void showLaunchError(@NonNull ComponentName component, @NonNull String message) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(Lang.t("起動できませんでした"))
                .setMessage(component.flattenToShortString() + "\n\n" + message)
                .setPositiveButton("OK", null)
                .show();
    }

    /** このアプリのアクティビティを一覧から選んで入力欄に入れる。 */
    private void pickActivity() {
        String[] names;
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), PackageManager.GET_ACTIVITIES);
            ActivityInfo[] acts = info.activities != null ? info.activities : new ActivityInfo[0];
            names = new String[acts.length];
            for (int i = 0; i < acts.length; i++) names[i] = shortName(acts[i].name);
        } catch (PackageManager.NameNotFoundException e) {
            return;
        }
        Arrays.sort(names, String.CASE_INSENSITIVE_ORDER);
        new MaterialAlertDialogBuilder(this)
                .setTitle(Lang.t("アクティビティ") + " (" + names.length + ")")
                .setItems(names, (d, which) -> {
                    activityText = names[which];
                    if (activityField != null) {
                        activityField.setText(activityText);
                        activityField.setSelection(activityField.length());
                        activityField.requestFocus();
                    }
                })
                .setNegativeButton(Lang.t("キャンセル"), null)
                .show();
    }

    /** このアプリの画面は「.クラス名」で短く見せる。 */
    @NonNull
    private String shortName(@NonNull String name) {
        String pkg = getPackageName() + ".";
        return name.startsWith(pkg) ? name.substring(pkg.length() - 1) : name;
    }

    @NonNull
    private List<String> recentActivities() {
        List<String> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(getSharedPreferences(PREFS, MODE_PRIVATE)
                    .getString(KEY_RECENT_ACTIVITIES, "[]"));
            for (int i = 0; i < a.length(); i++) out.add(a.getString(i));
        } catch (Exception ignored) {
        }
        return out;
    }

    private void remember(@NonNull String name) {
        List<String> list = recentActivities();
        list.remove(name);
        list.add(0, name);
        while (list.size() > RECENT_MAX) list.remove(list.size() - 1);
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString(KEY_RECENT_ACTIVITIES, new JSONArray(list).toString()).apply();
    }
}
