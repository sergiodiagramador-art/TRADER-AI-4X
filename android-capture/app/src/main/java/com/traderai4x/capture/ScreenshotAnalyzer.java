package com.traderai4x.capture;

import android.graphics.Bitmap;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Extrator experimental de candles a partir de uma captura da BullX.
 * Os valores são coordenadas de pixels, não preços reais. Nenhum sinal
 * operacional é liberado até a calibração visual ser validada.
 */
public final class ScreenshotAnalyzer {
    private ScreenshotAnalyzer() {}

    private static final class Candle {
        final double high;
        final double low;
        final double close;
        final boolean green;
        Candle(double high, double low, double close, boolean green) {
            this.high = high;
            this.low = low;
            this.close = close;
            this.green = green;
        }
        double typical() { return (high + low + close) / 3.0; }
    }

    public static String analyze(Bitmap bitmap) {
        if (bitmap == null || bitmap.getWidth() < 300 || bitmap.getHeight() < 500) {
            return inconclusive("Imagem pequena ou inválida.");
        }

        int w = bitmap.getWidth();
        int h = bitmap.getHeight();

        // Região do gráfico observada nas capturas atuais da BullX.
        // Exclui a coluna de botões ACIMA/ABAIXO à direita.
        int left = (int) (w * 0.07);
        int right = (int) (w * 0.84);
        int top = (int) (h * 0.58);
        int bottom = (int) (h * 0.81);
        if (right <= left || bottom <= top) return inconclusive("Área do gráfico não identificada.");

        int[] greenCount = new int[right - left];
        int[] redCount = new int[right - left];
        int[] minY = new int[right - left];
        int[] maxY = new int[right - left];
        for (int i = 0; i < minY.length; i++) {
            minY[i] = Integer.MAX_VALUE;
            maxY[i] = -1;
        }

        // Detecta pixels de corpo/pavio por coluna.
        for (int x = left; x < right; x++) {
            int idx = x - left;
            for (int y = top; y < bottom; y++) {
                int c = bitmap.getPixel(x, y);
                int r = (c >> 16) & 255;
                int g = (c >> 8) & 255;
                int b = c & 255;
                boolean green = g > 105 && g > r * 1.30 && g > b * 1.12;
                boolean red = r > 135 && r > g * 1.30 && r > b * 1.15;
                if (green || red) {
                    if (green) greenCount[idx]++;
                    else redCount[idx]++;
                    minY[idx] = Math.min(minY[idx], y);
                    maxY[idx] = Math.max(maxY[idx], y);
                }
            }
        }

        List<Candle> candles = new ArrayList<>();
        int x = 0;
        while (x < greenCount.length) {
            int count = greenCount[x] + redCount[x];
            if (count < 2) { x++; continue; }

            int start = x;
            int end = x;
            int gap = 0;
            while (end + 1 < greenCount.length) {
                int next = greenCount[end + 1] + redCount[end + 1];
                if (next >= 2) {
                    end++;
                    gap = 0;
                } else if (gap < 1 && end + 2 < greenCount.length
                        && greenCount[end + 2] + redCount[end + 2] >= 2) {
                    end += 2;
                    gap++;
                } else break;
            }

            int greens = 0, reds = 0, candleTop = Integer.MAX_VALUE, candleBottom = -1;
            for (int col = start; col <= end; col++) {
                greens += greenCount[col];
                reds += redCount[col];
                if (greenCount[col] + redCount[col] > 0) {
                    candleTop = Math.min(candleTop, minY[col]);
                    candleBottom = Math.max(candleBottom, maxY[col]);
                }
            }
            if (candleTop < candleBottom && greens + reds >= 4) {
                boolean isGreen = greens >= reds;
                // Para candle verde, fechamento fica na extremidade superior;
                // para candle vermelho, na extremidade inferior.
                double high = -candleTop;
                double low = -candleBottom;
                double close = isGreen ? high : low;
                candles.add(new Candle(high, low, close, isGreen));
            }
            x = Math.max(x + 1, end + 1);
        }

        if (candles.size() < 22) {
            return "LEITURA INCONCLUSIVA\n"
                + "Candles detectados: " + candles.size() + "\n"
                + "São necessários pelo menos 22 candles reconhecidos para avaliar MA 9/21 e RSI.\n\n"
                + "SINAL: SEM ENTRADA.";
        }

        int n = candles.size();
        double[] closes = new double[n];
        double[] typical = new double[n];
        for (int i = 0; i < n; i++) {
            closes[i] = candles.get(i).close;
            typical[i] = candles.get(i).typical();
        }

        double ma9 = average(closes, n - 9, n);
        double ma21 = average(closes, n - 21, n);
        double rsi = rsi(closes, 14);
        double cci = cci(typical, 20);
        Candle last = candles.get(n - 1);

        String trend = ma9 < ma21 ? "ALTA (estimada)" : ma9 > ma21 ? "BAIXA (estimada)" : "LATERAL";
        // Coordenadas verticais crescem para baixo: a escala de preço é invertida.
        // Por isso a relação das médias é apresentada como leitura experimental.
        return "ANÁLISE EXPERIMENTAL DO GRÁFICO\n"
            + "Candles reconhecidos: " + n + "\n"
            + "MA 9 (pixel): " + fmt(ma9) + "\n"
            + "MA 21 (pixel): " + fmt(ma21) + "\n"
            + "RSI 14 estimado: " + fmt(rsi) + "\n"
            + "CCI 20 estimado: " + fmt(cci) + "\n"
            + "Último candle: " + (last.green ? "VERDE" : "VERMELHO") + "\n"
            + "Relação das médias: " + trend + "\n\n"
            + "SINAL: SEM ENTRADA.\n"
            + "A leitura ainda usa posições em pixels, não preços OHLC reais. "
            + "É necessário validar a detecção e a escala do gráfico em várias capturas antes de usar os indicadores para operações.";
    }

    private static double average(double[] values, int from, int to) {
        double sum = 0;
        for (int i = from; i < to; i++) sum += values[i];
        return sum / (to - from);
    }

    private static double rsi(double[] values, int period) {
        double gains = 0, losses = 0;
        for (int i = values.length - period; i < values.length; i++) {
            double change = values[i] - values[i - 1];
            if (change > 0) gains += change;
            else losses -= change;
        }
        if (losses == 0) return 100.0;
        double rs = (gains / period) / (losses / period);
        return 100.0 - (100.0 / (1.0 + rs));
    }

    private static double cci(double[] values, int period) {
        int from = values.length - period;
        double mean = average(values, from, values.length);
        double deviation = 0;
        for (int i = from; i < values.length; i++) deviation += Math.abs(values[i] - mean);
        deviation /= period;
        if (deviation == 0) return 0;
        return (values[values.length - 1] - mean) / (0.015 * deviation);
    }

    private static String fmt(double value) {
        return String.format(Locale.US, "%.1f", value);
    }

    private static String inconclusive(String reason) {
        return "LEITURA INCONCLUSIVA\n" + reason + "\n\nSINAL: SEM ENTRADA.";
    }
}