package com.airsky.browser;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.Button;

public class HelpProgress extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help_progress);
        TouchEffectView.attach(getWindow());
        // プログレスバーの見本は Lottie(レイアウト側で自動再生・ループ)
        Button helpClose = findViewById(R.id.help_close);
        helpClose.setOnClickListener(v -> {
            finish();
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        });
    }

}
