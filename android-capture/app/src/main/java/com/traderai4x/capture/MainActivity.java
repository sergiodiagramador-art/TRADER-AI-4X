package com.traderai4x.capture;

import android.app.Activity;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
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
    private ImageView preview;
    private TextView status;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(24, 24, 24, 24);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView title = new TextView(this);
        title.setText("TRADER-AI-4X — teste de captura");
        title.setTextSize(20);
        layout.addView(title);
        status = new TextView(this);
        status.setText("A imagem fica no espaço privado do app, sem ocupar a galeria.");
        status.setPadding(0, 18, 0, 18);
        layout.addView(status);
        Button capture = new Button(this);
        capture.setText("Capturar tela");
        capture.setOnClickListener(v -> {
            MediaProjectionManager manager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_CAPTURE);
        });
        layout.addView(capture);
        Button stop = new Button(this);
        stop.setText("Parar captura");
        stop.setOnClickListener(v -> {
            stopService(new Intent(this, CaptureService.class));
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

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CAPTURE) {
            if (resultCode == RESULT_OK && data != null) {
                Intent service = new Intent(this, CaptureService.class);
                service.putExtra("resultCode", resultCode);
                service.putExtra("projectionData", data);
                startForegroundService(service);
                status.setText("Autorizado. Mude para o Bullex; a captura será feita em 4 segundos.");
            } else status.setText("Permissão não concedida.");
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
