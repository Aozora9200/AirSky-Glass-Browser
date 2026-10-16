package com.airsky.browser;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.chip.ChipGroup;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * アプリ内のお問い合わせ。
 *
 * <p>送信は 2 通り:</p>
 * <ul>
 *   <li><b>送信</b>(既定): 開発者のフィードバック用フォームの受付窓口へ HTTPS で直接送る。
 *       アプリにメールのパスワードや API キーを入れずに済む(APK から抜き出されて悪用される
 *       心配が無い)ので、アプリから直接メールを送る方式の代わりにこちらを使う。</li>
 *   <li><b>メールで送る</b>: 端末のメールアプリで、開発者のアドレス宛てのメールを作る。
 *       送信はメールアプリが行うので、アプリに認証情報は要らない。</li>
 * </ul>
 */
public class ContactActivity extends AppCompatActivity {

    /**
     * メールで送る時の宛先。空ならメールのボタンは出さない。
     * アプリに入れた文字列は誰でも読めるので、公開して差し支えないアドレスにすること。
     */
    static final String CONTACT_EMAIL = "";

    /** フィードバック用フォームの受付窓口(以前ブラウザで開いていた forms.gle/BEmR3Gms7LazZvPs7)。 */
    private static final String FORM_ENDPOINT =
            "https://docs.google.com/forms/d/e/1FAIpQLSc_pbKfCf8fAVvBx_2fIQs3V59oUzp0d_DPEZy04tq87IxBqg/formResponse";
    private static final String ENTRY_TYPE = "entry.1591633300";
    private static final String ENTRY_MESSAGE = "entry.326955045";
    private static final String ENTRY_SUGGESTION = "entry.1696159737";
    private static final String ENTRY_NAME = "entry.485428648";
    private static final String ENTRY_EMAIL = "entry.879531967";

    /** 連続送信を防ぐ間隔。 */
    private static final long SEND_INTERVAL_MS = 60_000;
    private static final String PREFS = "AppPrefs";
    private static final String KEY_LAST_SENT = "contactLastSentAt";

    @NonNull
    static Intent intent(@NonNull Context context) {
        return new Intent(context, ContactActivity.class);
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private ChipGroup typeGroup;
    private TextInputLayout messageLayout, emailLayout;
    private TextInputEditText message, suggestion, name, email;
    private MaterialSwitch attachDevice;
    private TextView deviceInfo;
    private Button sendButton, mailButton;
    private View form, done;
    private boolean sending;
    private boolean sent;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contact);
        TouchEffectView.attach(getWindow());

        typeGroup = findViewById(R.id.contactType);
        messageLayout = findViewById(R.id.contactMessageLayout);
        emailLayout = findViewById(R.id.contactEmailLayout);
        message = findViewById(R.id.contactMessage);
        suggestion = findViewById(R.id.contactSuggestion);
        name = findViewById(R.id.contactName);
        email = findViewById(R.id.contactEmail);
        attachDevice = findViewById(R.id.contactAttachDevice);
        deviceInfo = findViewById(R.id.contactDeviceInfo);
        sendButton = findViewById(R.id.contactSend);
        mailButton = findViewById(R.id.contactMail);
        form = findViewById(R.id.contactForm);
        done = findViewById(R.id.contactDone);

        deviceInfo.setText(deviceInfo());
        attachDevice.setOnCheckedChangeListener((b, checked) ->
                deviceInfo.setVisibility(checked ? View.VISIBLE : View.GONE));
        ((TextView) findViewById(R.id.contactPrivacy)).setText(
                Lang.t("「送信」を押すと、入力した内容(と、オンの場合は端末の情報)が開発者のフィードバック用フォーム(Google フォーム)へ暗号化された通信で送られます。"
                        + "パスワードなどの個人情報は書かないでください。"));

        message.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (s.length() > 0) messageLayout.setError(null);
            }
        });
        email.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}

            @Override
            public void afterTextChanged(Editable s) {
                emailLayout.setError(null);
            }
        });

        sendButton.setOnClickListener(v -> {
            if (sent) close();
            else send();
        });
        mailButton.setVisibility(CONTACT_EMAIL.isEmpty() ? View.GONE : View.VISIBLE);
        mailButton.setOnClickListener(v -> sendByMail());

        ToolChrome.setup(this, Lang.t("お問い合わせ"));
    }

    // ------------------------------------------------------------------
    // 入力
    // ------------------------------------------------------------------

    @NonNull
    private String typeLabel() {
        int id = typeGroup.getCheckedChipId();
        if (id == R.id.typeQuestion) return Lang.t("質問");
        if (id == R.id.typeBug) return Lang.t("バグの報告");
        if (id == R.id.typeFeature) return Lang.t("機能のリクエスト");
        return Lang.t("コメント");
    }

    @NonNull
    private static String textOf(@Nullable TextInputEditText e) {
        return e == null || e.getText() == null ? "" : e.getText().toString().trim();
    }

    /** 入力を確かめる。問題があれば欄にエラーを出して false。 */
    private boolean validate() {
        boolean ok = true;
        if (textOf(message).isEmpty()) {
            messageLayout.setError(Lang.t("お問い合わせ内容を入力してください"));
            ok = false;
        }
        String mail = textOf(email);
        if (!mail.isEmpty() && !Patterns.EMAIL_ADDRESS.matcher(mail).matches()) {
            emailLayout.setError(Lang.t("メールアドレスの形式が正しくありません"));
            ok = false;
        }
        return ok;
    }

    /** フォームの「フィードバック」欄へ入れる本文(端末の情報を付ける場合は末尾に足す)。 */
    @NonNull
    private String messageBody() {
        String body = textOf(message);
        if (attachDevice.isChecked()) body += "\n\n---\n" + deviceInfo();
        return body;
    }

    @NonNull
    private String deviceInfo() {
        String version = "?";
        long code = 0;
        try {
            PackageInfo p = getPackageManager().getPackageInfo(getPackageName(), 0);
            version = p.versionName;
            code = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ? p.getLongVersionCode() : p.versionCode;
        } catch (Exception ignored) {}
        String abi = Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "?";
        return "AirSky Browser " + version + " (" + code + ")\n"
                + "Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")\n"
                + Build.MANUFACTURER + " " + Build.MODEL + " / " + abi;
    }

    // ------------------------------------------------------------------
    // 送信
    // ------------------------------------------------------------------

    private void send() {
        if (sending || !validate()) return;
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        long last = prefs.getLong(KEY_LAST_SENT, 0);
        long wait = SEND_INTERVAL_MS - (System.currentTimeMillis() - last);
        if (wait > 0 && last <= System.currentTimeMillis()) {
            Toast.makeText(this, Lang.t("続けて送信するには、あと ") + ((wait + 999) / 1000) + Lang.t(" 秒お待ちください"),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        hideKeyboard();
        setSending(true);

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put(ENTRY_TYPE, typeLabel());
        fields.put(ENTRY_MESSAGE, messageBody());
        String s = textOf(suggestion);
        if (!s.isEmpty()) fields.put(ENTRY_SUGGESTION, s);
        String n = textOf(name);
        if (!n.isEmpty()) fields.put(ENTRY_NAME, n);
        String m = textOf(email);
        if (!m.isEmpty()) fields.put(ENTRY_EMAIL, m);

        executor.execute(() -> {
            boolean ok = postForm(fields);
            handler.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                setSending(false);
                if (ok) {
                    prefs.edit().putLong(KEY_LAST_SENT, System.currentTimeMillis()).apply();
                    showDone();
                } else {
                    showSendFailed();
                }
            });
        });
    }

    /** フォームの受付窓口へ送る。受け付けられたら true。 */
    private static boolean postForm(@NonNull Map<String, String> fields) {
        HttpURLConnection conn = null;
        try {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> e : fields.entrySet()) {
                if (sb.length() > 0) sb.append('&');
                sb.append(URLEncoder.encode(e.getKey(), "UTF-8")).append('=')
                        .append(URLEncoder.encode(e.getValue(), "UTF-8"));
            }
            byte[] body = sb.toString().getBytes(StandardCharsets.UTF_8);
            conn = (HttpURLConnection) new URL(FORM_ENDPOINT).openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setDoOutput(true);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            conn.setFixedLengthStreamingMode(body.length);
            try (OutputStream out = conn.getOutputStream()) {
                out.write(body);
            }
            int code = conn.getResponseCode();
            return code >= 200 && code < 400;
        } catch (Exception e) {
            return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private void showSendFailed() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder b =
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                        .setTitle(Lang.t("送信できませんでした"))
                        .setMessage(Lang.t("ネットワークに接続されているかご確認のうえ、もう一度お試しください。"))
                        .setPositiveButton(Lang.t("再試行"), (d, w) -> send())
                        .setNegativeButton(Lang.t("閉じる"), null);
        if (!CONTACT_EMAIL.isEmpty()) b.setNeutralButton(Lang.t("メールで送る"), (d, w) -> sendByMail());
        b.show();
    }

    /** 端末のメールアプリで送る(宛先・件名・本文を入れた状態で開く)。 */
    private void sendByMail() {
        if (CONTACT_EMAIL.isEmpty() || !validate()) return;
        String subject = "[AirSky] " + typeLabel();
        StringBuilder body = new StringBuilder(messageBody());
        String s = textOf(suggestion);
        if (!s.isEmpty()) body.append(Lang.t("\n\n【改善の提案】\n")).append(s);
        String n = textOf(name);
        if (!n.isEmpty()) body.append(Lang.t("\n\nお名前: ")).append(n);
        Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
                .putExtra(Intent.EXTRA_EMAIL, new String[]{CONTACT_EMAIL})
                .putExtra(Intent.EXTRA_SUBJECT, subject)
                .putExtra(Intent.EXTRA_TEXT, body.toString());
        try {
            startActivity(Intent.createChooser(intent, Lang.t("メールアプリを選択")));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, Lang.t("メールアプリが見つかりません"), Toast.LENGTH_SHORT).show();
        }
    }

    private void setSending(boolean on) {
        sending = on;
        sendButton.setEnabled(!on);
        mailButton.setEnabled(!on);
        sendButton.setText(on ? Lang.t("送信中...") : Lang.t("送信"));
    }

    private void showDone() {
        sent = true;
        form.setVisibility(View.GONE);
        mailButton.setVisibility(View.GONE);
        done.setVisibility(View.VISIBLE);
        done.setAlpha(0f);
        done.setTranslationY(24 * getResources().getDisplayMetrics().density);
        done.animate().alpha(1f).translationY(0f).setDuration(420).start();
        sendButton.setText(Lang.t("閉じる"));
        ((com.google.android.material.button.MaterialButton) sendButton).setIcon(null);
    }

    private void hideKeyboard() {
        View f = getCurrentFocus();
        if (f == null) return;
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(f.getWindowToken(), 0);
    }

    @Override
    public void onBackPressed() {
        boolean dirty = !sent && (!TextUtils.isEmpty(textOf(message)) || !TextUtils.isEmpty(textOf(suggestion)));
        if (dirty) {
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle(Lang.t("入力内容を破棄しますか？"))
                    .setPositiveButton(Lang.t("破棄"), (d, w) -> close())
                    .setNegativeButton(Lang.t("キャンセル"), null)
                    .show();
            return;
        }
        close();
    }

    private void close() {
        finish();
        overridePendingTransition(R.anim.no_animation, R.anim.slide_out_down_low);
    }

    @Override
    protected void onDestroy() {
        executor.shutdown();
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
