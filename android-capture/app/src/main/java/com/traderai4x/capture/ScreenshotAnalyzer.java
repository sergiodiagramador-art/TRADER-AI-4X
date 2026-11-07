package com.traderai4x.capture;

import android.graphics.Bitmap;
import java.util.ArrayList;
import java.util.List;

/**
 * Detector visual experimental para capturas completas ou recortadas da BullX.
 * Identifica faixas de pixels coloridos; não extrai OHLC real e não emite sinais.
 */
public final class ScreenshotAnalyzer {
    private ScreenshotAnalyzer() {}

    private static final class CandleMark {
        final int firstX, lastX, topY, bottomY, green, red;
        CandleMark(int firstX, int lastX, int topY, int bottomY, int green, int red) {
            this.firstX = firstX; this.lastX = lastX; this.topY = topY;
            this.bottomY = bottomY; this.green = green; this.red = red;
        }
        String color() { return green >= red ? "VERDE" : "VERMELHO"; }
    }

    public static String analyze(Bitmap bitmap) {
        if (bitmap == null || bitmap.getWidth() < 300 || bitmap.getHeight() < 400) {
            return inconclusive("Imagem pequena ou inválida.");
        }

        int w = bitmap.getWidth(), h = bitmap.getHeight();

        // Exclui os botões de operação à direita e os ícones laterais.
        // A altura do gráfico muda entre captura da tela inteira e imagem recortada,
        // portanto a faixa vertical é detectada pela distribuição de pixels coloridos.
        int left = (int) (w * 0.12);
        int right = (int) (w * 0.84);
        int scanTop = (int) (h * 0.04);
        int scanBottom = (int) (h * 0.96);
        int width = right - left;
        int[] rowHits = new int[scanBottom - scanTop];

        for (int y = scanTop; y < scanBottom; y++) {
            int hits = 0;
            for (int x = left; x < right; x++) {
                if (isCandleColor(bitmap.getPixel(x, y))) hits++;
            }
            rowHits[y - scanTop] = hits;
        }

        int rowThreshold = Math.max(3, (int) (width * 0.004));
        int bestStart = -1, bestEnd = -1;
        long bestScore = 0;
        int runStart = -1, lastActive = -1;
        long runScore = 0;

        for (int i = 0; i < rowHits.length; i++) {
            if (rowHits[i] >= rowThreshold) {
                if (runStart < 0) { runStart = i; runScore = 0; }
                runScore += rowHits[i];
                lastActive = i;
            } else if (runStart >= 0 && i - lastActive > 12) {
                if (runScore > bestScore) {
                    bestScore = runScore;
                    bestStart = runStart;
                    bestEnd = lastActive;
                }
                runStart = -1;
            }
        }
        if (runStart >= 0 && runScore > bestScore) {
            bestScore = runScore;
            bestStart = runStart;
            bestEnd = lastActive;
        }
        if (bestStart < 0 || bestEnd - bestStart < 25) {
            return inconclusive("Não foi possível localizar uma área contínua de candles coloridos.");
        }

        int pad = Math.max(6, (bestEnd - bestStart) / 12);
        int top = Math.max(scanTop, scanTop + bestStart - pad);
        int bottom = Math.min(scanBottom, scanTop + bestEnd + pad);
        int[] green = new int[width], red = new int[width];
        int[] minY = new int[width], maxY = new int[width];
        for (int i = 0; i < width; i++) {
            minY[i] = Integer.MAX_VALUE;
            maxY[i] = -1;
        }

        for (int x = left; x < right; x++) {
            int idx = x - left;
            for (int y = top; y < bottom; y++) {
                int c = bitmap.getPixel(x, y);
                int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
                if (isGreen(r, g, b)) {
                    green[idx]++;
                    minY[idx] = Math.min(minY[idx], y);
                    maxY[idx] = Math.max(maxY[idx], y);
                } else if (isRed(r, g, b)) {
                    red[idx]++;
                    minY[idx] = Math.min(minY[idx], y);
                    maxY[idx] = Math.max(maxY[idx], y);
                }
            }
        }

        List<CandleMark> marks = new ArrayList<>();
        int x = 0;
        while (x < width) {
            if (green[x] + red[x] < 2) { x++; continue; }
            int start = x, end = x, gap = 0;
            while (end + 1 < width) {
                if (green[end + 1] + red[end + 1] >= 2) {
                    end++;
                    gap = 0;
                } else if (gap == 0 && end + 2 < width
                        && green[end + 2] + red[end + 2] >= 2) {
                    end += 2;
                    gap = 1;
                } else break;
            }

            int gp = 0, rp = 0, candleTop = Integer.MAX_VALUE, candleBottom = -1;
            for (int col = start; col <= end; col++) {
                gp += green[col]; rp += red[col];
                if (green[col] + red[col] > 0) {
                    candleTop = Math.min(candleTop, minY[col]);
                    candleBottom = Math.max(candleBottom, maxY[col]);
                }
            }
            if (gp + rp >= 4 && candleBottom > candleTop && end - start <= 18) {
                marks.add(new CandleMark(left + start, left + end,
                    candleTop, candleBottom, gp, rp));
            }
            x = Math.max(x + 1, end + 1);
        }

        if (marks.size() < 5) {
            return "LEITURA INCONCLUSIVA\n"
                + "Área colorida localizada: Y=" + top + "–" + bottom + " px\n"
                + "Possíveis candles separados: " + marks.size() + "\n"
                + "Ajuste visual ainda necessário.\n\nSINAL: SEM ENTRADA.";
        }

        int greens = 0, reds = 0;
        for (CandleMark mark : marks) {
            if ("VERDE".equals(mark.color())) greens++; else reds++;
        }
        CandleMark last = marks.get(marks.size() - 1);
        return "LEITURA VISUAL EXPERIMENTAL\n"
            + "Região do gráfico: Y=" + top + "–" + bottom + " px\n"
            + "Possíveis candles separados: " + marks.size() + "\n"
            + "Predominância verde: " + greens + "\n"
            + "Predominância vermelha: " + reds + "\n"
            + "Último elemento detectado: " + last.color() + "\n\n"
            + "SINAL: SEM ENTRADA.\n"
            + "A detecção ainda trabalha com pixels, não com preços OHLC. "
            + "RSI, CCI e MA 9/21 não devem ser calculados a partir desta estimativa. "
            + "É necessário validar a separação dos candles em diferentes capturas.";
    }

    private static boolean isCandleColor(int color) {
        int r = (color >> 16) & 255, g = (color >> 8) & 255, b = color & 255;
        return isGreen(r, g, b) || isRed(r, g, b);
    }

    private static boolean isGreen(int r, int g, int b) {
        return g > 105 && g > r * 1.30 && g > b * 1.12;
    }

    private static boolean isRed(int r, int g, int b) {
        return r > 135 && r > g * 1.30 && r > b * 1.15;
    }

    private static String inconclusive(String reason) {
        return "LEITURA INCONCLUSIVA\n" + reason + "\n\nSINAL: SEM ENTRADA.";
    }
}