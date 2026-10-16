package com.airsky.browser;

import androidx.appcompat.app.AppCompatActivity;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

public class CharCheckActivity extends AppCompatActivity {
    private EditText editText;
    private TextView textViewResult;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.ior1orl);
        TouchEffectView.attach(getWindow());
        ToolChrome.setup(this, "I or 1 or l");

        editText = findViewById(R.id.editIor1orl);
        Button buttonCheck = findViewById(R.id.buttonCheck);
        textViewResult = findViewById(R.id.textViewResult);

        buttonCheck.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkCharacter();
            }
        });
        // Action Bar が表示されているか確認
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_ior1orl, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.ior1orl_close) {
            finish();
            overridePendingTransition(R.anim.no_animation,  R.anim.slide_out_down_low);
        }
        if (item.getItemId() == android.R.id.home) {
            finish(); // 前の画面に戻る
            overridePendingTransition(R.anim.no_animation,  R.anim.slide_out_down_low);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        finish(); // 前の画面に戻る
        overridePendingTransition(R.anim.no_animation,  R.anim.slide_out_down_low);
    }

    private void checkCharacter() {
        String input = editText.getText().toString().trim();
        if (input.length() != 1) {
            textViewResult.setText(Lang.t("1文字を入力してください"));
            return;
        }

        char c = input.charAt(0);
        String result;

        switch (c) {
            case 'I':
                result = Lang.t("アルファベットの大文字 I(ｱｲ) です");
                break;
            case 'l':
                result = Lang.t("アルファベットの小文字 l(ｴﾙ) です");
                break;
            case '1':
                result = Lang.t("数字の 1(ｲﾁ) です");
                break;
            default:
                result = Lang.t("I, l, 1 のいずれかを入力してください");
        }

        textViewResult.setText(result);
    }
}
