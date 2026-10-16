package com.airsky.browser;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

/**
 * ホーム画面のショートカットから、Java / Python のプログラムを始める入口(画面は持たない)。
 * ショートカットに入れた合言葉が合わない時は何もしない。
 */
public class ProgramShortcutActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent i = getIntent();
        String kind = i.getStringExtra(ProgramShortcuts.EXTRA_KIND);
        String id = i.getStringExtra(ProgramShortcuts.EXTRA_ID);
        String token = i.getStringExtra(ProgramShortcuts.EXTRA_TOKEN);
        if (token == null || !token.equals(ProgramShortcuts.token(this)) || id == null) {
            finish();
            return;
        }
        if (ProgramShortcuts.KIND_PYTHON.equals(kind)) {
            PyStore.Entry e = PyStore.find(this, id);
            if (e != null) PyStore.launch(this, e, null);
            else Toast.makeText(this, Lang.t("このプログラムは削除されています"), Toast.LENGTH_SHORT).show();
        } else if (ProgramShortcuts.KIND_JAVA.equals(kind)) {
            JarStore.Entry e = JarStore.find(this, id);
            if (e != null) JarStore.launch(this, e, null);
            else Toast.makeText(this, Lang.t("このプログラムは削除されています"), Toast.LENGTH_SHORT).show();
        }
        finish();
    }
}
