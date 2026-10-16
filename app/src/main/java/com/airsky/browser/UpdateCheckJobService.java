package com.airsky.browser;

import android.app.job.JobParameters;
import android.app.job.JobService;

/**
 * アプリを開いていない間のアップデートの確認(1 日 1 回、通信できる時)。
 * 新しいバージョンがあれば通知を出す({@link UpdateChecker})。
 */
public class UpdateCheckJobService extends JobService {

    private volatile Thread worker;

    @Override
    public boolean onStartJob(JobParameters params) {
        if (!UpdateChecker.isEnabled(this)) return false;
        Thread t = new Thread(() -> {
            UpdateChecker.Release release = UpdateChecker.fetchLatest();
            if (release != null && UpdateChecker.isNewer(this, release)) {
                UpdateChecker.notifyIfNew(this, release);
            }
            // 取れなかった時も次の周期でまた確認するので、やり直しは頼まない
            jobFinished(params, false);
        }, "UpdateCheck");
        worker = t;
        t.start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        Thread t = worker;
        if (t != null) t.interrupt();
        return false;
    }
}
