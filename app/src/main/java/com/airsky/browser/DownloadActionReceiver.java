package com.airsky.browser;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** ダウンロード通知のボタン(キャンセル)。 */
public class DownloadActionReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (AppDownloader.ACTION_CANCEL.equals(intent.getAction())) {
            AppDownloader.cancel(intent.getLongExtra(AppDownloader.EXTRA_ID, 0));
        }
    }
}
