package com.traderai4x.capture;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
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
        status.setText("PASSO 1: toque em Iniciar. PASSO 2: autorize a gravação da tela. PASSO 3: abra a Bullex. PASSO 4: abra o painel de notificações e toque em CAPTURAR AGORA na notificação TRADER-AI-4X.");
        status.setTextSize(15);
        status.setPadding(0, 18, 0, 18);
        layout.addView(status);

        Button capture = new Button(this);
        capture.setText("Iniciar captura");
        capture.setOnClickListener(v -> checkNotificationPermissionAndStart());
        layout.addView(capture);

        Button settings = new Button(this);
        settings.setText("Verificar / liberar notificações");
        settings.setOnClickListener(v -> openNotificationSettings());
        layout.addView(settings);

        Button refresh = new Button(this);
        refresh.setText("Ver última imagem capturada");
        refresh.setOnClickListener(v -> showLastCapture());
        layout.addView(refresh);

        Button share = new Button(this);
        share.setText("Enviar captura ao Overlay");
        share.setOnClickListener(v -> shareLastCapture());
        layout.addView(share);

        Button analyze = new Button(this);
        analyze.setText("Analisar última captura");
        analyze.setOnClickListener(v -> analyzeLastCapture());
        layout.addView(analyze);

        Button stop = new Button(this);
        stop.setText("Parar captura");
        stop.setOnClickListener(v -> {
            stopService(new Intent(this, CaptureService.class).setAction("STOP"));
            status.setText("Comando de parada enviado.");
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
            status.setText("O Android precisa permitir as notificações para mostrar o botão CAPTURAR AGORA. Autorize na próxima janela.");
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
            return;
        }
        if (!notificationsEnabled()) {
            status.setText("As notificações do TRADER estão bloqueadas. Toque em VERIFICAR / LIBERAR NOTIFICAÇÕES, permita-as e depois toque em INICIAR novamente.");
            openNotificationSettings();
            return;
        }
        startCaptureAuthorization();
    }

    private boolean notificationsEnabled() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        return nm != null && nm.areNotificationsEnabled();
    }

    private void openNotificationSettings() {
        Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
        intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        startActivity(intent);
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_NOTIFICATIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                status.setText("Notificações liberadas. Toque em INICIAR CAPTURA novamente.");
            } else {
                status.setText("Notificações negadas. Sem elas, o botão CAPTURAR AGORA não poderá aparecer. Libere nas configurações.");
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
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(service);
                else startService(service);
                status.setText("CAPTURA AUTORIZADA. Agora minimize o TRADER, abra a Bullex e puxe a barra superior do Android para baixo. Na notificação TRADER-AI-4X, toque em CAPTURAR AGORA. Depois volte e toque em VER ÚLTIMA IMAGEM CAPTURADA.");
            } else {
                status.setText("A autorização de captura foi cancelada ou negada. Toque em INICIAR CAPTURA e autorize.");
            }
        }
    }

    @Override protected void onResume() {
        super.onResume();
        showLastCapture();
    }

    private void shareLastCapture() {
        File base = getExternalFilesDir("captures");
        File file = base == null ? null : new File(base, "last-capture.png");
        if (file == null || !file.exists() || file.length() == 0) {
            status.setText("Ainda não existe captura salva para enviar.");
            return;
        }
        try {
            Uri uri = androidx.core.content.FileProvider.getUriForFile(
                this, getPackageName() + ".fileprovider", file);
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("image/png");
            send.putExtra(Intent.EXTRA_STREAM, uri);
            send.setClipData(android.content.ClipData.newUri(getContentResolver(), "Captura Bullex", uri));
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(send, "Enviar captura para TRADER AI 4X Overlay"));
        } catch (Exception e) {
            status.setText("Não foi possível compartilhar a captura: " + e.getClass().getSimpleName());
        }
    }

    private void analyzeLastCapture() {
        File base = getExternalFilesDir("captures");
        File file = base == null ? null : new File(base, "last-capture.png");
        if (file == null || !file.exists() || file.length() == 0) {
            status.setText("Ainda não existe captura salva para analisar.");
            return;
        }
        Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
        if (bitmap == null) {
            status.setText("Não foi possível abrir a imagem salva.");
            return;
        }
        String report = ScreenshotAnalyzer.analyze(bitmap);
        bitmap.recycle();
        new AlertDialog.Builder(this)
            .setTitle("Análise da última captura")
            .setMessage(report)
            .setPositiveButton("OK", null)
            .show();
    }

    private void showLastCapture() {
        File base = getExternalFilesDir("captures");
        File file = base == null ? null : new File(base, "last-capture.png");
        if (file != null && file.exists() && file.length() > 0) {
            Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
            if (bitmap != null) {
                preview.setImageBitmap(bitmap);
                String when = DateFormat.getDateTimeInstance().format(new Date(file.lastModified()));
                status.setText("CAPTURA SALVA: " + when + " • " + (file.length() / 1024) + " KB.");
                return;
            }
        }
        preview.setImageDrawable(null);
        status.setText("AINDA NÃO HÁ IMAGEM SALVA. Depois de autorizar, abra a Bullex, puxe a barra superior do Android e toque em CAPTURAR AGORA na notificação TRADER-AI-4X. Se a notificação não aparecer, use VERIFICAR / LIBERAR NOTIFICAÇÕES.");
    }
}
