package com.traderai4x.capture;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
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
    private static final String CHANNEL = "capture_status";
    private MediaProjection projection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean finished = false;

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "STOP".equals(intent.getAction())) { stopCapture(); return START_NOT_STICKY; }
        createChannel();
        startForeground(404, buildNotification());
        int resultCode = intent == null ? 0 : intent.getIntExtra("resultCode", 0);
        Intent data = intent == null ? null : getProjectionData(intent);
        if (data == null || resultCode == 0) { stopCapture(); return START_NOT_STICKY; }
        MediaProjectionManager manager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        projection = manager.getMediaProjection(resultCode, data);
        handler.postDelayed(this::takeOneScreenshot, 4000);
        return START_NOT_STICKY;
    }

    private Intent getProjectionData(Intent intent) {
        if (Build.VERSION.SDK_INT >= 33) return intent.getParcelableExtra("projectionData", Intent.class);
        return intent.getParcelableExtra("projectionData");
    }

    private void takeOneScreenshot() {
        if (projection == null || finished) { stopCapture(); return; }
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
                if (dir != null) {
                    if (!dir.exists()) dir.mkdirs();
                    try (FileOutputStream out = new FileOutputStream(new File(dir, "last-capture.png"), false)) {
                        cropped.compress(Bitmap.CompressFormat.PNG, 100, out);
                    }
                }
                padded.recycle();
                cropped.recycle();
                finished = true;
                stopCapture();
            } catch (Exception e) {
                stopCapture();
            } finally { if (image != null) image.close(); }
        }, handler);
        projection.registerCallback(new MediaProjection.Callback() {
            @Override public void onStop() { stopCapture(); }
        }, handler);
        virtualDisplay = projection.createVirtualDisplay("TraderAI4XCapture", w, h, m.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, imageReader.getSurface(), null, handler);
        handler.postDelayed(() -> { if (!finished) stopCapture(); }, 2500);
    }

    private Notification buildNotification() {
        Intent stop = new Intent(this, CaptureService.class);
        stop.setAction("STOP");
        PendingIntent pending = PendingIntent.getService(this, 405, stop,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL)
            .setContentTitle("TRADER-AI-4X")
            .setContentText("Captura única em andamento")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .addAction(android.R.drawable.ic_media_pause, "Parar", pending)
            .setOngoing(true).build();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c = new NotificationChannel(CHANNEL, "Captura de tela", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(c);
        }
    }

    private void stopCapture() {
        handler.removeCallbacksAndMessages(null);
        if (virtualDisplay != null) { virtualDisplay.release(); virtualDisplay = null; }
        if (imageReader != null) { imageReader.close(); imageReader = null; }
        if (projection != null) { projection.stop(); projection = null; }
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }
    @Override public void onDestroy() { stopCapture(); super.onDestroy(); }
    @Override public IBinder onBind(Intent intent) { return null; }
}
