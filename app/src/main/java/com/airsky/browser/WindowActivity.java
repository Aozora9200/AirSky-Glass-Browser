package com.airsky.browser;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;

import androidx.annotation.NonNull;

/**
 * 「新しいウィンドウで開く」で開く 2 つ目のブラウザのウィンドウ。
 *
 * <p>画面・操作は通常のブラウザ({@link MainActivity})と同じで、別のタスクとして開くので
 * 分割画面・フリーフォームでは並べて使える。タブは通常のウィンドウとは別に保存する
 * (タブの ID も重ならない範囲を使う)。タブグループは通常のウィンドウだけで使う。</p>
 */
public class WindowActivity extends MainActivity {

    /** 新しいウィンドウでリンクを開く。新しいウィンドウの中からなら、通常のウィンドウで開く。 */
    static void open(@NonNull Activity from, @NonNull String url) {
        Class<?> target = from instanceof WindowActivity ? MainActivity.class : WindowActivity.class;
        Intent intent = new Intent(from, target)
                .setAction(Intent.ACTION_VIEW)
                .setData(Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT);
        if (target == MainActivity.class) intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        from.startActivity(intent);
    }

    @Override
    protected String tabsStoreKey() {
        return "window2_tabs";
    }

    @Override
    protected String currentTabStoreKey() {
        return "window2_current_tab_id";
    }

    @Override
    protected int firstTabId() {
        // 通常のウィンドウのタブ(tab_state_<id>.json など)と重ならないようにする
        return 1_000_000;
    }

    @Override
    protected boolean supportsTabGroups() {
        return false;
    }
}
