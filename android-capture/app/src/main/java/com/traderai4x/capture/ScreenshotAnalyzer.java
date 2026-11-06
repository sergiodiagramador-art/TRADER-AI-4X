package com.traderai4x.capture;

import android.graphics.Bitmap;

/**
 * Leitura visual preliminar da captura. Não calcula indicadores nem emite ordem:
 * uma imagem isolada não contém necessariamente histórico suficiente.
 */
public final class ScreenshotAnalyzer {
    private ScreenshotAnalyzer() {}

    public static String analyze(Bitmap bitmap) {
        if (bitmap == null || bitmap.getWidth() < 200 || bitmap.getHeight() < 200) {
            return "Imagem insuficiente para leitura.";
        }

        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        int left = (int) (w * 0.08);
        int right = (int) (w * 0.96);
        int top = (int) (h * 0.12);
        int bottom = (int) (h * 0.88);
        long green = 0;
        long red = 0;
        long sampled = 0;

        // Amostragem espaçada para manter o processamento leve no celular.
        int step = Math.max(2, Math.min(w, h) / 350);
        for (int y = top; y < bottom; y += step) {
            for (int x = left; x < right; x += step) {
                int color = bitmap.getPixel(x, y);
                int r = (color >> 16) & 0xff;
                int g = (color >> 8) & 0xff;
                int b = color & 0xff;
                sampled++;
                if (g > 105 && g > r * 1.28 && g > b * 1.08) green++;
                else if (r > 125 && r > g * 1.25 && r > b * 1.12) red++;
            }
        }

        long colored = green + red;
        if (colored < 25) {
            return "LEITURA INCONCLUSIVA\nNão foram identificadas cores suficientes de candles na área analisada.\n\nSINAL: SEM ENTRADA.";
        }

        double greenPct = 100.0 * green / colored;
        double redPct = 100.0 * red / colored;
        return "LEITURA VISUAL PRELIMINAR\n"
            + "Pixels com predominância verde: " + String.format(java.util.Locale.US, "%.1f", greenPct) + "%\n"
            + "Pixels com predominância vermelha: " + String.format(java.util.Locale.US, "%.1f", redPct) + "%\n"
            + "Amostras examinadas: " + sampled + "\n\n"
            + "SINAL: SEM ENTRADA.\n"
            + "Esta contagem de cores não confirma tendência, RSI, CCI ou médias móveis. "
            + "É necessário calibrar a leitura com candles e histórico visíveis antes de liberar sinais.";
    }
}
