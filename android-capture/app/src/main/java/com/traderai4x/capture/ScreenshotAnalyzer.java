package com.traderai4x.capture;

import android.graphics.Bitmap;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Leitura visual experimental da BullX.
 * OHLC aqui é expresso somente em coordenadas de pixels, nunca em preço.
 * Não produz sinais operacionais.
 */
public final class ScreenshotAnalyzer {
    private ScreenshotAnalyzer() {}

    private static final class CandleMark {
        final int firstX, lastX, highY, lowY, bodyTop, bodyBottom;
        final int greenPixels, redPixels;
        CandleMark(int firstX, int lastX, int highY, int lowY,
                   int bodyTop, int bodyBottom, int greenPixels, int redPixels) {
            this.firstX = firstX; this.lastX = lastX;
            this.highY = highY; this.lowY = lowY;
            this.bodyTop = bodyTop; this.bodyBottom = bodyBottom;
            this.greenPixels = greenPixels; this.redPixels = redPixels;
        }
        boolean isGreen() { return greenPixels >= redPixels; }
        String color() { return isGreen() ? "VERDE" : "VERMELHO"; }
        int openPixel() { return isGreen() ? bodyBottom : bodyTop; }
        int closePixel() { return isGreen() ? bodyTop : bodyBottom; }
    }

    public static String analyze(Bitmap bitmap) {
        if (bitmap == null || bitmap.getWidth() < 300 || bitmap.getHeight() < 400) {
            return inconclusive("Imagem pequena ou inválida.");
        }

        int w = bitmap.getWidth(), h = bitmap.getHeight();
        int left = (int) (w * 0.07);
        int right = (int) (w * 0.84);
        int scanTop = (int) (h * 0.04);
        int scanBottom = (int) (h * 0.96);
        int width = right - left;
        int[] rowHits = new int[scanBottom - scanTop];

        // Procura a faixa vertical com maior concentração contínua de pixels de candle.
        for (int y = scanTop; y < scanBottom; y++) {
            int hits = 0;
            for (int x = left; x < right; x++) {
                if (isCandleColor(bitmap.getPixel(x, y))) hits++;
            }
            rowHits[y - scanTop] = hits;
        }

        int rowThreshold = Math.max(3, (int) (width * 0.004));
        int bestStart = -1, bestEnd = -1;
        long bestScore = 0, runScore = 0;
        int runStart = -1, lastActive = -1;
        for (int i = 0; i < rowHits.length; i++) {
            if (rowHits[i] >= rowThreshold) {
                if (runStart < 0) { runStart = i; runScore = 0; }
                runScore += rowHits[i];
                lastActive = i;
            } else if (runStart >= 0 && i - lastActive > 12) {
                if (runScore > bestScore) {
                    bestScore = runScore; bestStart = runStart; bestEnd = lastActive;
                }
                runStart = -1;
            }
        }
        if (runStart >= 0 && runScore > bestScore) {
            bestScore = runScore; bestStart = runStart; bestEnd = lastActive;
        }
        if (bestStart < 0 || bestEnd - bestStart < 25) {
            return inconclusive("Não foi possível localizar a área do gráfico.");
        }

        int pad = Math.max(6, (bestEnd - bestStart) / 12);
        int top = Math.max(scanTop, scanTop + bestStart - pad);
        int bottom = Math.min(scanBottom, scanTop + bestEnd + pad);
        int chartHeight = bottom - top;
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

        List<CandleMark> candles = new ArrayList<>();
        int x = 0;
        while (x < width) {
            if (green[x] + red[x] < 2) { x++; continue; }
            int start = x, end = x, gap = 0;
            while (end + 1 < width) {
                if (green[end + 1] + red[end + 1] >= 2) {
                    end++; gap = 0;
                } else if (gap == 0 && end + 2 < width
                        && green[end + 2] + red[end + 2] >= 2) {
                    end += 2; gap = 1;
                } else break;
            }

            int gp = 0, rp = 0, highY = Integer.MAX_VALUE, lowY = -1;
            for (int col = start; col <= end; col++) {
                gp += green[col]; rp += red[col];
                if (green[col] + red[col] > 0) {
                    highY = Math.min(highY, minY[col]);
                    lowY = Math.max(lowY, maxY[col]);
                }
            }

            // Corpo: linhas horizontais com pelo menos 2 pixels coloridos
            // contíguos na largura do candidato; pixels isolados formam pavios.
            int bodyTop = Integer.MAX_VALUE, bodyBottom = -1;
            for (int y = highY; y <= lowY; y++) {
                int rowCount = 0;
                for (int col = start; col <= end; col++) {
                    int c = bitmap.getPixel(left + col, y);
                    if (isCandleColor(c)) rowCount++;
                }
                if (rowCount >= 2) {
                    bodyTop = Math.min(bodyTop, y);
                    bodyBottom = Math.max(bodyBottom, y);
                }
            }

            int candleHeight = lowY - highY;
            boolean plausible = gp + rp >= 4 && candleHeight > 2
                && candleHeight <= chartHeight * 0.65 && end - start <= 18
                && bodyBottom >= bodyTop && bodyBottom - bodyTop >= 1;
            if (plausible) {
                candles.add(new CandleMark(left + start, left + end, highY, lowY,
                    bodyTop, bodyBottom, gp, rp));
            }
            x = Math.max(x + 1, end + 1);
        }

        if (candles.size() < 5) {
            return "LEITURA INCONCLUSIVA\n"
                + "Área do gráfico: Y=" + top + "–" + bottom + " px\n"
                + "Candidatos com corpo identificado: " + candles.size()
                + "\nAjuste visual necessário.\n\nSINAL: SEM ENTRADA.";
        }

        int greens = 0, reds = 0;
        for (CandleMark c : candles) {
            if (c.isGreen()) greens++; else reds++;
        }

        StringBuilder report = new StringBuilder();
        report.append("LEITURA EXPERIMENTAL — COORDENADAS EM PIXELS\n");
        report.append("Área do gráfico: Y=").append(top).append("–").append(bottom).append(" px\n");
        report.append("Candidatos com corpo identificado: ").append(candles.size()).append("\n");
        report.append("Verdes: ").append(greens).append(" | Vermelhos: ").append(reds).append("\n");
        report.append("\nÚLTIMOS 5 CANDIDATOS (O/H/L/C em pixels)\n");

        int from = Math.max(0, candles.size() - 5);
        for (int i = from; i < candles.size(); i++) {
            CandleMark c = candles.get(i);
            report.append(String.format(Locale.US,
                "%d. %s O:%d H:%d L:%d C:%d%n",
                i + 1, c.color(), c.openPixel(), c.highY, c.lowY, c.closePixel()));
        }
        report.append("\nSINAL: SEM ENTRADA.\n");
        report.append("Os valores O/H/L/C acima são posições de pixels, não cotações. ");
        report.append("A escala de preço e o tempo de cada candle ainda não foram calibrados. ");
        report.append("RSI, CCI, MA9 e MA21 não devem ser calculados até essa validação.");
        return report.toString();
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