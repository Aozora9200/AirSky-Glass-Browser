package com.airsky.browser;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

public class QrCameraActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Theme.Holoを適用しているので、setThemeなど不要
        startQRScanner();
        Toast.makeText(this, Lang.t("QRコードをスキャンしてください."), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onBackPressed() {
        finish(); // 前の画面に戻る
        overridePendingTransition(R.anim.no_animation,  R.anim.slide_out_down_low);
    }

    private void startQRScanner() {
        IntentIntegrator integrator = new IntentIntegrator(this);
        integrator.setCaptureActivity(QrCaptureActivity.class); // ← カスタムアクティビティを指定
        integrator.setPrompt(Lang.t("QRコードをスキャンしてください"));
        integrator.setBeepEnabled(false);
        integrator.setOrientationLocked(false);
        integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE);
        integrator.setCameraId(0);  // デフォルトカメラ
        integrator.initiateScan();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result != null) {
            if (result.getContents() != null) {
                // QRコードを読み取れた
                String scannedUrl = result.getContents();
                if (MigrationChannel.isMigrationQr(scannedUrl)) {
                    // 別の端末の「データの引っ越し」の QR コード: 受け取る画面へ
                    startActivity(new Intent(this, DeviceMigrationActivity.class)
                            .putExtra(DeviceMigrationActivity.EXTRA_TICKET, scannedUrl));
                    finish();
                    return;
                }
                Intent intent = new Intent(this, MainActivity.class);
                intent.putExtra("url", scannedUrl);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP); // MainActivity を復帰させる
                startActivity(intent);
                finish();
            } else {
                Toast.makeText(this, Lang.t("スキャンをキャンセルしました"), Toast.LENGTH_SHORT).show();
                finish();
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }
}