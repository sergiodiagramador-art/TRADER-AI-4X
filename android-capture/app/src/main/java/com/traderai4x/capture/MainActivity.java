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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import java.io.File;
import java.text.DateFormat;
import java.util.Date;

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
        status.setText("1. Autorize a captura. 2. Abra a Bullex. 3. Espere o gráfico carregar. 4. Use Capturar agora na notificação do TRADER.");
        status.setPadding(0, 18, 0, 18);
        layout.addView(status);

        Button capture = new Button(this);
        capture.setText("Autorizar / iniciar captura");
        capture.setOnClickListener(v -> checkNotificationPermissionAndStart());
        layout.addView(capture);

        Button refresh = new Button(this);
        refresh.setText("Atualizar imagem");
        refresh.setOnClickListener(v -> showLastCapture());
        layout.addView(refresh);

        Button stop = new Button(this);
        stop.setText("Parar captura");
        stop.setOnClickListener(v -> {
            stopService(new Intent(this, CaptureService.class).setAction("STOP"));
            status.setText("Solicitação para parar enviada.");
        });
        layout.addView(stop);

        preview = new ImageView(this);
        preview.setAdjustViewBounds(true);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        preview.setBackgroundColor(Color.rgb(235, 235, 235));
        preview.setContentDescription("Prévia da última captura da tela");
        layout.addView(preview, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(layout);
        showLastCapture();
    }

    private void checkNotificationPermissionAndStart() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            status.setText("Autorize as notificações do TRADER na próxima janela.");
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
                status.setText("Notificações não autorizadas. Ative-as nas configurações do Android e tente novamente.");
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
                status.setText("Autorização recebida. Abra a Bullex, aguarde o gráfico carregar e toque em Capturar agora na notificação do TRADER.");
            } else {
                status.setText("A autorização de captura foi cancelada ou negada.");
            }
        }
    }

    @Override protected void onResume() {
        super.onResume();
        showLastCapture();
    }

    private void showLastCapture() {
        File base = getExternalFilesDir("captures");
        File file = base == null ? null : new File(base, "last-capture.png");
        if (file != null && file.exists() && file.length() > 0) {
            Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
            if (bitmap != null) {
                preview.setImageBitmap(bitmap);
                String when = DateFormat.getDateTimeInstance().format(new Date(file.lastModified()));
                status.setText("Captura encontrada: " + when + " • " + (file.length() / 1024) + " KB. Se acabou de capturar, esta é a imagem salva.");
                return;
            }
        }
        preview.setImageDrawable(null);
        status.setText("Nenhuma captura encontrada ainda. Autorize a captura, abra a Bullex, espere o gráfico carregar e toque em Capturar agora na notificação do TRADER. Depois volte aqui e toque em Atualizar imagem.");
    }
}
