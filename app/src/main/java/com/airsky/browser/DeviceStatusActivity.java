package com.airsky.browser;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Inet4Address;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 端末の状態(Pixel の「デバイス情報 > ステータス」風)。
 *
 * <p>電池・ネットワーク・端末の情報を項目ごとに並べる。電池とネットワークと稼働時間は
 * 画面を開いている間ずっと更新する。</p>
 */
public class DeviceStatusActivity extends PixelSettingsActivity {

    private final Handler main = new Handler(Looper.getMainLooper());

    @Nullable private Intent battery;
    /** getprop の結果(読み込み中は null)。 */
    @Nullable private Map<String, String> props;

    @Nullable private Row batteryStatusRow, batteryLevelRow, batteryHealthRow, batteryTempRow, batteryVoltageRow;
    @Nullable private Row networkRow, ipRow, uptimeRow;

    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            battery = intent;
            updateBattery();
        }
    };

    private final BroadcastReceiver networkReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateNetwork();
        }
    };

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (uptimeRow != null) uptimeRow.setSummary(uptime());
            main.postDelayed(this, 1000);
        }
    };

    @NonNull
    @Override
    protected CharSequence pageTitle() {
        return Lang.t("端末の状態");
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // getprop は少し時間がかかるので裏で読み、読めたら作り直す
        new Thread(() -> {
            Map<String, String> p = readProps();
            main.post(() -> {
                props = p;
                if (!isFinishing()) rebuild();
            });
        }, "getprop").start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        battery = registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        registerReceiver(networkReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
        updateBattery();
        updateNetwork();
        main.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(batteryReceiver);
        unregisterReceiver(networkReceiver);
        main.removeCallbacks(tick);
    }

    @Override
    protected void onBuild() {
        header(Lang.t("電池"));
        batteryStatusRow = row(R.drawable.ic_ps_battery, Lang.t("電池の状態"), "—", null);
        batteryLevelRow = row(R.drawable.ic_ps_bolt, Lang.t("電池残量"), "—", null);
        batteryHealthRow = row(R.drawable.ic_ps_safety, Lang.t("電池の健全性"), "—", null);
        batteryTempRow = row(R.drawable.ic_ps_sensors, Lang.t("電池の温度"), "—", null);
        batteryVoltageRow = row(R.drawable.ic_ps_speed, Lang.t("電圧"), "—", null);

        header(Lang.t("ネットワーク"));
        networkRow = row(R.drawable.ic_ps_router, Lang.t("ネットワーク接続の状態"), "—", null);
        ipRow = row(R.drawable.ic_ps_dns, Lang.t("IP アドレス"), "—", null);

        header(Lang.t("端末"));
        row(R.drawable.ic_ps_phone, Lang.t("モデル"), Build.MODEL, null);
        row(R.drawable.ic_ps_info, Lang.t("メーカー"), Build.MANUFACTURER, null);
        row(R.drawable.ic_ps_palette, Lang.t("ブランド"), Build.BRAND, null);
        row(R.drawable.ic_ps_update, Lang.t("Android バージョン"), "Android " + Build.VERSION.RELEASE
                + " (API " + Build.VERSION.SDK_INT + ")", null);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            row(R.drawable.ic_ps_safety, Lang.t("セキュリティ アップデート"), Build.VERSION.SECURITY_PATCH, null);
        }
        row(R.drawable.ic_ps_code, Lang.t("ビルド番号"), Build.DISPLAY, null);
        String soc = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? Build.SOC_MODEL : null;
        if (soc == null || soc.isEmpty() || Build.UNKNOWN.equals(soc)) soc = Build.HARDWARE;
        row(R.drawable.ic_ps_tune, "SoC", soc, null);
        row(R.drawable.ic_ps_desktop, Lang.t("画面"), screenInfo(), null);
        row(R.drawable.ic_ps_language, Lang.t("言語"), Locale.getDefault().getDisplayName(), null);
        uptimeRow = row(R.drawable.ic_ps_history, Lang.t("稼働時間"), uptime(), null);

        header(Lang.t("詳細"));
        if (props == null) {
            row(0, Lang.t("読み込んでいます…"), null, null);
        } else {
            String[][] keys = {
                    {"ro.vndk.version", "VNDK"},
                    {"ro.build.type", Lang.t("ビルドの種類")},
                    {"ro.boot.baseband", Lang.t("ベースバンド")},
                    {"ro.boot.slot_suffix", Lang.t("スロット")},
                    {"ro.sf.lcd_density", Lang.t("画面密度 (ro.sf.lcd_density)")},
                    {"ro.product.locale", Lang.t("初期の言語")},
            };
            int shown = 0;
            for (String[] k : keys) {
                String v = props.get(k[0]);
                if (v == null || v.isEmpty()) continue;
                row(0, k[1], v, null);
                shown++;
            }
            if (shown == 0) row(0, Lang.t("表示できる情報がありません"), null, null);
        }
        gap();
        updateBattery();
        updateNetwork();
    }

    // ------------------------------------------------------------------

    private void updateBattery() {
        Intent b = battery;
        if (b == null || batteryStatusRow == null) return;
        int status = b.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        int plugged = b.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
        String s;
        if (status == BatteryManager.BATTERY_STATUS_FULL) s = Lang.t("充電完了");
        else if (status == BatteryManager.BATTERY_STATUS_CHARGING) s = Lang.t("充電中") + pluggedLabel(plugged);
        else if (status == BatteryManager.BATTERY_STATUS_NOT_CHARGING) s = Lang.t("充電していません");
        else s = Lang.t("放電中");
        batteryStatusRow.setSummary(s);

        int level = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = b.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        if (batteryLevelRow != null) {
            batteryLevelRow.setSummary(level >= 0 && scale > 0 ? Math.round(level * 100f / scale) + "%" : Lang.t("不明"));
        }
        if (batteryHealthRow != null) batteryHealthRow.setSummary(healthLabel(
                b.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)));
        int temp = b.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);
        if (batteryTempRow != null) {
            batteryTempRow.setSummary(temp == Integer.MIN_VALUE ? Lang.t("不明")
                    : String.format(Locale.JAPAN, "%.1f ℃", temp / 10f));
        }
        int mv = b.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
        if (batteryVoltageRow != null) {
            batteryVoltageRow.setSummary(mv <= 0 ? Lang.t("不明") : String.format(Locale.JAPAN, "%.2f V", mv / 1000f));
        }
    }

    private static String pluggedLabel(int plugged) {
        if (plugged == BatteryManager.BATTERY_PLUGGED_AC) return "(AC)";
        if (plugged == BatteryManager.BATTERY_PLUGGED_USB) return "(USB)";
        if (plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS) return Lang.t("(ワイヤレス)");
        return "";
    }

    private static String healthLabel(int health) {
        switch (health) {
            case BatteryManager.BATTERY_HEALTH_GOOD: return Lang.t("良好");
            case BatteryManager.BATTERY_HEALTH_OVERHEAT: return Lang.t("過熱");
            case BatteryManager.BATTERY_HEALTH_DEAD: return Lang.t("劣化");
            case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE: return Lang.t("過電圧");
            case BatteryManager.BATTERY_HEALTH_COLD: return Lang.t("低温");
            case BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE: return Lang.t("異常");
            default: return Lang.t("不明");
        }
    }

    private void updateNetwork() {
        if (networkRow == null) return;
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        Network network = cm == null ? null : cm.getActiveNetwork();
        if (network == null) {
            networkRow.setSummary(Lang.t("未接続"));
            if (ipRow != null) ipRow.setSummary(Lang.t("利用できません"));
            return;
        }
        NetworkCapabilities caps = cm.getNetworkCapabilities(network);
        String type;
        if (caps == null) type = Lang.t("不明");
        else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) type = "Wi-Fi";
        else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) type = Lang.t("モバイルデータ");
        else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) type = Lang.t("イーサネット");
        else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) type = "VPN";
        else type = Lang.t("その他");
        networkRow.setSummary(type);

        if (ipRow != null) {
            LinkProperties lp = cm.getLinkProperties(network);
            StringBuilder ip = new StringBuilder();
            if (lp != null) {
                // IPv4 を先に出す
                for (LinkAddress a : lp.getLinkAddresses()) {
                    if (a.getAddress() instanceof Inet4Address) appendLine(ip, a.getAddress().getHostAddress());
                }
                for (LinkAddress a : lp.getLinkAddresses()) {
                    if (!(a.getAddress() instanceof Inet4Address)) appendLine(ip, a.getAddress().getHostAddress());
                }
            }
            ipRow.setSummary(ip.length() == 0 ? Lang.t("利用できません") : ip);
        }
    }

    private static void appendLine(StringBuilder sb, String s) {
        if (sb.length() > 0) sb.append('\n');
        sb.append(s);
    }

    private String screenInfo() {
        android.util.DisplayMetrics m = getResources().getDisplayMetrics();
        return Math.max(m.widthPixels, m.heightPixels) + " × " + Math.min(m.widthPixels, m.heightPixels)
                + "(" + m.densityDpi + " dpi)";
    }

    private static String uptime() {
        long s = SystemClock.elapsedRealtime() / 1000;
        long d = s / 86400;
        long h = (s / 3600) % 24;
        long min = (s / 60) % 60;
        long sec = s % 60;
        String hms = String.format(Locale.JAPAN, "%d:%02d:%02d", h, min, sec);
        return d > 0 ? d + Lang.t(" 日 ") + hms : hms;
    }

    @NonNull
    private static Map<String, String> readProps() {
        Map<String, String> values = new HashMap<>();
        try {
            Process process = Runtime.getRuntime().exec("getprop");
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    int keyEnd = line.indexOf(']');
                    int valueStart = line.indexOf('[', keyEnd) + 1;
                    int valueEnd = line.lastIndexOf(']');
                    if (!line.startsWith("[") || keyEnd < 0 || valueStart <= 0 || valueEnd < valueStart) continue;
                    String key = line.substring(1, keyEnd).trim();
                    if (key.startsWith("ro.")) values.put(key, line.substring(valueStart, valueEnd).trim());
                }
            }
        } catch (Exception ignored) {
            // 読めない端末では詳細を出さない
        }
        return values;
    }
}
