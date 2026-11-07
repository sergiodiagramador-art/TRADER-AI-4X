package com.traderai4x.capture;

/**
 * Indicadores para candles OHLC reais, em ordem cronológica.
 * Não converte coordenadas de imagem em preços e não emite sinais.
 */
public final class TechnicalIndicators {
    private TechnicalIndicators() {}

    public static final class Candle {
        public final double open, high, low, close;

        public Candle(double open, double high, double low, double close) {
            if (!finite(open) || !finite(high) || !finite(low) || !finite(close))
                throw new IllegalArgumentException("OHLC deve conter números finitos.");
            if (high < low || high < open || high < close || low > open || low > close)
                throw new IllegalArgumentException("OHLC inconsistente.");
            this.open = open; this.high = high; this.low = low; this.close = close;
        }

        public double typicalPrice() { return (high + low + close) / 3.0; }
    }

    public static final class Result {
        public final double sma9, sma21, rsi14, cci20;
        public final int candlesUsed;

        private Result(double sma9, double sma21, double rsi14, double cci20, int count) {
            this.sma9 = sma9; this.sma21 = sma21; this.rsi14 = rsi14;
            this.cci20 = cci20; this.candlesUsed = count;
        }

        public String movingAverageBias() {
            if (sma9 > sma21) return "MA9 ACIMA DA MA21";
            if (sma9 < sma21) return "MA9 ABAIXO DA MA21";
            return "MA9 IGUAL À MA21";
        }
    }

    /** Exige 21 candles válidos, do mais antigo para o mais recente. */
    public static Result calculate(Candle[] candles) {
        if (candles == null || candles.length < 21)
            throw new IllegalArgumentException("São necessários pelo menos 21 candles OHLC.");
        for (Candle candle : candles)
            if (candle == null) throw new IllegalArgumentException("Candle nulo.");

        int n = candles.length;
        return new Result(
            averageClose(candles, n - 9, n),
            averageClose(candles, n - 21, n),
            rsiWilder(candles, 14),
            cci(candles, 20),
            n
        );
    }

    private static double averageClose(Candle[] c, int from, int to) {
        double sum = 0.0;
        for (int i = from; i < to; i++) sum += c[i].close;
        return sum / (to - from);
    }

    /** RSI de Wilder com suavização recursiva. */
    private static double rsiWilder(Candle[] c, int period) {
        double gains = 0.0, losses = 0.0;
        for (int i = 1; i <= period; i++) {
            double change = c[i].close - c[i - 1].close;
            if (change > 0) gains += change;
            else losses -= change;
        }
        double avgGain = gains / period, avgLoss = losses / period;
        for (int i = period + 1; i < c.length; i++) {
            double change = c[i].close - c[i - 1].close;
            avgGain = (avgGain * (period - 1) + Math.max(change, 0.0)) / period;
            avgLoss = (avgLoss * (period - 1) + Math.max(-change, 0.0)) / period;
        }
        if (avgLoss == 0.0) return avgGain == 0.0 ? 50.0 : 100.0;
        if (avgGain == 0.0) return 0.0;
        double rs = avgGain / avgLoss;
        return 100.0 - 100.0 / (1.0 + rs);
    }

    /** CCI padrão com preço típico e desvio médio absoluto. */
    private static double cci(Candle[] c, int period) {
        int from = c.length - period;
        double sum = 0.0;
        for (int i = from; i < c.length; i++) sum += c[i].typicalPrice();
        double mean = sum / period, deviation = 0.0;
        for (int i = from; i < c.length; i++)
            deviation += Math.abs(c[i].typicalPrice() - mean);
        double meanDeviation = deviation / period;
        if (meanDeviation == 0.0) return 0.0;
        return (c[c.length - 1].typicalPrice() - mean) / (0.015 * meanDeviation);
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}