package com.traderai4x.capture;

import android.graphics.Bitmap;
import java.util.ArrayList;
import java.util.List;

/**
 * Detector experimental de candles na captura da BullX.
 * Ainda não converte pixels em preços OHLC; por segurança não calcula
 * indicadores operacionais nem emite recomendações de compra/venda.
 */
public final class ScreenshotAnalyzer {
    private ScreenshotAnalyzer() {}

    private static final class CandleMark {
        final int firstX;
        final int lastX;
        final int topY;
        final int bottomY;
        final int green;
        final int red;
        CandleMark(int firstX, int lastX, int topY, int bottomY, int green, int red) {
            this.firstX = firstX;
            this.lastX = lastX;
            this.topY = topY;
            this.bottomY = bottomY;
            this.green = green;
            this.red = red;
        }
        String color() { return green >= red ? "VERDE" : "VERMELHO"; }
    }

    public static String analyze(Bitmap bitmap) {
        if (bitmap == null || bitmap.getWidth() < 300 || bitmap.getHeight() < 500) {
            return inconclusive("Imagem pequena ou inválida.");
        }

        int w = bitmap.getWidth();
        int h = bitmap.getHeight();

        // Na captura real enviada pelo usuário, o gráfico ocupa aproximadamente
        // 45% a 61% da altura total. A faixa abaixo era incorreta e pegava a
        // área cinza inferior do aplicativo.
        int left = (int) (w * 0.08);
        int right = (int) (w * 0.84);
        int top = (int) (h * 0.45);
        int bottom = (int) (h * 0.61);
        if (right <= left || bottom <= top) return inconclusive("Área do gráfico inválida.");

        int width = right - left;
        int[] green = new int[width];
        int[] red = new int[width];
        int[] minY = new int[width];
        int[] maxY = new int[width];
        for (int i = 0; i < width; i++) {
            minY[i] = Integer.MAX_VALUE;
            maxY[i] = -1;
        }

        for (int px = left; px < right; px++) {
            int idx = px - left;
            for (int py = top; py < bottom; py++) {
                int c = bitmap.getPixel(px, py);
                int r = (c >> 16) & 255;
                int g = (c >> 8) & 255;
                int b = c & 255;
                boolean isGreen = g > 105 && g > r * 1.30 && g > b * 1.12;
                boolean isRed = r > 135 && r > g * 1.30 && r > b * 1.15;
                if (isGreen || isRed) {
                    if (isGreen) green[idx]++;
                    else red[idx]++;
                    minY[idx] = Math.min(minY[idx], py);
                    maxY[idx] = Math.max(maxY[idx], py);
                }
            }
        }

        List<CandleMark> marks = new ArrayList<>();
        int x = 0;
        while (x < width) {
            if (green[x] + red[x] == 0) { x++; continue; }
            int start = x;
            int end = x;
            int gap = 0;
            while (end + 1 < width) {
                if (green[end + 1] + red[end + 1] > 0) {
                    end++;
                    gap = 0;
                } else if (gap == 0 && end + 2 < width
                        && green[end + 2] + red[end + 2] > 0) {
                    end += 2;
                    gap = 1;
                } else {
                    break;
                }
            }

            int greenPixels = 0;
            int redPixels = 0;
            int candleTop = Integer.MAX_VALUE;
            int candleBottom = -1;
            for (int col = start; col <= end; col++) {
                greenPixels += green[col];
                redPixels += red[col];
                if (green[col] + red[col] > 0) {
                    candleTop = Math.min(candleTop, minY[col]);
                    candleBottom = Math.max(candleBottom, maxY[col]);
                }
            }
            if (greenPixels + redPixels >= 3 && candleBottom > candleTop) {
                marks.add(new CandleMark(left + start, left + end, candleTop,
                    candleBottom, greenPixels, redPixels));
            }
            x = Math.max(x + 1, end + 1);
        }

        if (marks.size() < 5) {
            return "LEITURA INCONCLUSIVA\n"
                + "Possíveis candles encontrados: " + marks.size() + "\n"
                + "A área do gráfico foi localizada, mas a detecção ainda precisa de calibração.\n\n"
                + "SINAL: SEM ENTRADA.";
        }

        int greens = 0;
        int reds = 0;
        for (CandleMark mark : marks) {
            if ("VERDE".equals(mark.color())) greens++;
            else reds++;
        }
        CandleMark last = marks.get(marks.size() - 1);

        return "LEITURA VISUAL EXPERIMENTAL\n"
            + "Possíveis candles encontrados: " + marks.size() + "\n"
            + "Predominância verde: " + greens + "\n"
            + "Predominância vermelha: " + reds + "\n"
            + "Último elemento detectado: " + last.color() + "\n"
            + "Posição horizontal: " + last.firstX + "–" + last.lastX + " px\n\n"
            + "SINAL: SEM ENTRADA.\n"
            + "Esta etapa identifica regiões coloridas, não preços de abertura, máxima, mínima e fechamento. "
            + "RSI, CCI e MA 9/21 ainda não são confiáveis nesta captura. "
            + "A leitura será calibrada antes de liberar qualquer sinal.";
    }

    private static String inconclusive(String reason) {
        return "LEITURA INCONCLUSIVA\n" + reason + "\n\nSINAL: SEM ENTRADA.";
    }
}