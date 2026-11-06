package com.traderai4x.capture;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.graphics.BitmapFactory;
import java.io.File;

public class MainActivity extends Activity {
    private static final int REQUEST_CAPTURE = 41;
    private static final int REQUEST_NOTIFICATIONS = 42;
    private ImageView preview;
    private TextView status;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(24, 24, 24, 24);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView title = new TextView(this);
        title.setText("TRADER-AI-4X — captura de tela");
        title.setTextSize(20);
        layout.addView(title);

        status = new TextView(this);
        status.setText("1. Autorize as notificações e a captura. 2. Abra a Bullex. 3. Aguarde o gráfico carregar. 4. Toque em Capturar agora na notificação.");
        status.setPadding(0, 18, 0, 18);
        layout.addView(status);

        Button capture = new Button(this);
        capture.setText("Autorizar captura");
        capture.setOnClickListener(v -> checkNotificationPermissionAndStart());
        layout.addView(capture);

        Button stop = new Button(this);
        stop.setText("Parar captura");
        stop.setOnClickListener(v -> {
            stopService(new Intent(this, CaptureService.class).setAction("STOP"));
            status.setText("Captura parada.");
        });
        layout.addView(stop);

        preview = new ImageView(this);
        preview.setAdjustViewBounds(true);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        layout.addView(preview, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(layout);
        showLastCapture();
    }

    private void checkNotificationPermissionAndStart() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            status.setText("Autorize as notificações na próxima janela para aparecer o botão Capturar agora.");
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
            return;
        }
        startCaptureAuthorization();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_NOTIFICATIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCaptureAuthorization();
            } else {
                status.setText("A permissão de notificações foi negada. Ative as notificações do TRADER-AI-4X nas configurações do Android e tente novamente.");
            }
        }
    }

    private void startCaptureAuthorization() {
        MediaProjectionManager manager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_CAPTURE);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CAPTURE) {
            if (resultCode == RESULT_OK && data != null) {
                Intent service = new Intent(this, CaptureService.class);
                service.putExtra("resultCode", resultCode);
                service.putExtra("projectionData", data);
                startForegroundService(service);
                status.setText("Captura autorizada. Abra a Bullex, espere o gráfico carregar e toque em Capturar agora na notificação.");
            } else {
                status.setText("Permissão de captura não concedida.");
            }
        }
    }

    @Override protected void onResume() { super.onResume(); showLastCapture(); }

    private void showLastCapture() {
        File file = new File(getExternalFilesDir("captures"), "last-capture.png");
        if (file.exists()) {
            preview.setImageBitmap(BitmapFactory.decodeFile(file.getAbsolutePath()));
            status.setText("Última captura carregada. A próxima substituirá esta imagem.");
        }
    }
}
