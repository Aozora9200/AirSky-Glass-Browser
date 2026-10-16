package com.airsky.browser;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

/** オフライン用に保存したページの一覧。 */
public class SavedPagesActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_saved_pages);
        TouchEffectView.attach(getWindow());
        findViewById(R.id.spBack).setOnClickListener(v -> finish());
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.spContainer, new SavedPagesFragment())
                    .commit();
        }
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.no_animation, R.anim.slide_out_down_low);
    }
}
