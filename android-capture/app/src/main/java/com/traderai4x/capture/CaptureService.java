package com.traderai4x.capture;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.*;
import android.media.*;
import android.media.projection.*;
import android.os.*;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;

public class CaptureService extends Service {
    // New channel ID: Android does not allow changing the importance of an existing channel.
    private static final String CHANNEL = "capture_status_v2";
    private static final String ACTION_CAPTURE = "com.traderai4x.capture.CAPTURE";
    private static final String ACTION_STOP = "STOP";
    private MediaProjection projection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean finished = false;
    private boolean captureStarted = false;

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopCapture();
            return START_NOT_STICKY;
        }
        if (ACTION_CAPTURE.equals(action)) {
            if (projection != null && !captureStarted && !finished) {
                captureStarted = true;
                handler.postDelayed(this::takeOneScreenshot, 5000);
                updateNotification("Captura em 5 segundos. Feche o painel de notificações e deixe a Bullex na tela.");
            } else if (projection == null || finished) {
                updateNotification("Sessão encerrada. Abra o TRADER e autorize uma nova captura.");
            }
            return START_NOT_STICKY;
        }

        createChannel();
        startForeground(404, buildNotification("Abra a Bullex e toque em CAPTURAR AGORA nesta notificação."));
        int resultCode = intent == null ? 0 : intent.getIntExtra("resultCode", 0);
        Intent data = intent == null ? null : getProjectionData(intent);
        if (data == null || resultCode == 0) {
            updateNotification("Falha: autorização de tela não recebida. Abra o TRADER e tente novamente.");
            stopCapture();
            return START_NOT_STICKY;
        }
        try {
            MediaProjectionManager manager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            projection = manager.getMediaProjection(resultCode, data);
            updateNotification("Pronto. Abra a Bullex e toque em CAPTURAR AGORA nesta notificação.");
        } catch (Exception e) {
            updateNotification("Não foi possível iniciar a captura: " + e.getClass().getSimpleName());
            stopCapture();
        }
        return START_NOT_STICKY;
    }

    private Intent getProjectionData(Intent intent) {
        if (Build.VERSION.SDK_INT >= 33) return intent.getParcelableExtra("projectionData", Intent.class);
        return intent.getParcelableExtra("projectionData");
    }

    private void takeOneScreenshot() {
        if (projection == null || finished) { stopCapture(); return; }
        try {
            DisplayMetrics m = new DisplayMetrics();
            ((WindowManager) getSystemService(WINDOW_SERVICE)).getDefaultDisplay().getRealMetrics(m);
            int w = m.widthPixels, h = m.heightPixels;
            imageReader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2);
            imageReader.setOnImageAvailableListener(reader -> {
                Image image = null;
                try {
                    image = reader.acquireLatestImage();
                    if (image == null || finished) return;
                    Image.Plane plane = image.getPlanes()[0];
                    ByteBuffer buffer = plane.getBuffer();
                    int pixelStride = plane.getPixelStride();
                    int rowPadding = plane.getRowStride() - pixelStride * w;
                    Bitmap padded = Bitmap.createBitmap(w + rowPadding / pixelStride, h, Bitmap.Config.ARGB_8888);
                    padded.copyPixelsFromBuffer(buffer);
                    Bitmap cropped = Bitmap.createBitmap(padded, 0, 0, w, h);
                    File dir = getExternalFilesDir("captures");
                    if (dir == null) throw new IllegalStateException("Pasta de captura indisponível");
                    if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Não foi possível criar a pasta");
                    File output = new File(dir, "last-capture.png");
                    try (FileOutputStream out = new FileOutputStream(output, false)) {
                        if (!cropped.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                            throw new IllegalStateException("Falha ao salvar PNG");
                        }
                        out.flush();
                    }
                    padded.recycle();
                    cropped.recycle();
                    finished = true;
                    updateNotification("Imagem salva. Volte ao TRADER e toque em VER ÚLTIMA IMAGEM CAPTURADA.");
                    stopCapture();
                } catch (Exception e) {
                    updateNotification("Erro ao salvar captura: " + e.getClass().getSimpleName());
                    stopCapture();
                } finally {
                    if (image != null) image.close();
                }
            }, handler);

            projection.registerCallback(new MediaProjection.Callback() {
                @Override public void onStop() { stopCapture(); }
            }, handler);
            virtualDisplay = projection.createVirtualDisplay("TraderAI4XCapture", w, h, m.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, imageReader.getSurface(), null, handler);
            if (virtualDisplay == null) throw new IllegalStateException("VirtualDisplay não foi criado");
            handler.postDelayed(() -> {
                if (!finished) {
                    updateNotification("Tempo esgotado: não chegou imagem da tela. Tente autorizar novamente.");
                    stopCapture();
                }
            }, 7000);
        } catch (Exception e) {
            updateNotification("Falha na captura: " + e.getClass().getSimpleName());
            stopCapture();
        }
    }

    private Notification buildNotification(String message) {
        Intent capture = new Intent(this, CaptureService.class).setAction(ACTION_CAPTURE);
        PendingIntent capturePending = PendingIntent.getForegroundService(this, 406, capture,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent stop = new Intent(this, CaptureService.class).setAction(ACTION_STOP);
        PendingIntent stopPending = PendingIntent.getService(this, 405, stop,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL)
            .setContentTitle("TRADER-AI-4X — captura")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setPriority(Notification.PRIORITY_DEFAULT)
            .addAction(android.R.drawable.ic_menu_camera, "CAPTURAR AGORA", capturePending)
            .addAction(android.R.drawable.ic_media_pause, "PARAR", stopPending)
            .setOngoing(true)
            .build();
    }

    private void updateNotification(String message) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(404, buildNotification(message));
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                NotificationChannel c = new NotificationChannel(CHANNEL, "TRADER — Captura de tela", NotificationManager.IMPORTANCE_DEFAULT);
                c.setDescription("Mostra o botão para capturar a tela da Bullex");
                c.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
                nm.createNotificationChannel(c);
            }
        }
    }

    private void stopCapture() {
        if (finished && projection == null && virtualDisplay == null && imageReader == null) return;
        finished = true;
        handler.removeCallbacksAndMessages(null);
        if (virtualDisplay != null) { virtualDisplay.release(); virtualDisplay = null; }
        if (imageReader != null) { imageReader.close(); imageReader = null; }
        if (projection != null) { MediaProjection old = projection; projection = null; old.stop(); }
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override public void onDestroy() { stopCapture(); super.onDestroy(); }
    @Override public IBinder onBind(Intent intent) { return null; }
}
