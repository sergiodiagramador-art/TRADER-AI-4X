package com.traderai4x.capture;

/**
 * Avaliação conservadora de confluência para dados OHLC já validados.
 * Não transforma pixels em preços, não prevê resultado e não executa ordens.
 */
public final class SignalAssessment {
    private SignalAssessment() {}

    public enum Direction { ACIMA, ABAIXO, AGUARDAR }

    public static final class Assessment {
        public final Direction direction;
        public final int confirmations;
        public final String explanation;

        private Assessment(Direction direction, int confirmations, String explanation) {
            this.direction = direction;
            this.confirmations = confirmations;
            this.explanation = explanation;
        }
    }

    /**
     * Só avalia quando os dados foram obtidos de uma fonte OHLC confiável.
     * O chamador deve informar explicitamente se a série está validada.
     *
     * Critérios conservadores:
     * - MA9 acima/abaixo da MA21;
     * - RSI entre 50 e 70 para viés de alta, entre 30 e 50 para baixa;
     * - CCI acima de +25 para alta ou abaixo de -25 para baixa.
     * Exige as três confirmações. Caso contrário, aguardar.
     */
    public static Assessment evaluate(TechnicalIndicators.Result indicators,
                                      boolean trustedOhlc,
                                      boolean latestCandleClosed) {
        if (indicators == null) {
            return wait("Indicadores indisponíveis.");
        }
        if (!trustedOhlc) {
            return wait("OHLC não validado. Não usar coordenadas de pixels como preços.");
        }
        if (!latestCandleClosed) {
            return wait("Aguardando fechamento do candle para evitar leitura intr candle.");
        }
        if (!finite(indicators.sma9) || !finite(indicators.sma21)
                || !finite(indicators.rsi14) || !finite(indicators.cci20)) {
            return wait("Indicadores contêm valores inválidos.");
        }

        boolean bullishMa = indicators.sma9 > indicators.sma21;
        boolean bearishMa = indicators.sma9 < indicators.sma21;
        boolean bullishRsi = indicators.rsi14 > 50.0 && indicators.rsi14 < 70.0;
        boolean bearishRsi = indicators.rsi14 < 50.0 && indicators.rsi14 > 30.0;
        boolean bullishCci = indicators.cci20 > 25.0;
        boolean bearishCci = indicators.cci20 < -25.0;

        int up = (bullishMa ? 1 : 0) + (bullishRsi ? 1 : 0) + (bullishCci ? 1 : 0);
        int down = (bearishMa ? 1 : 0) + (bearishRsi ? 1 : 0) + (bearishCci ? 1 : 0);

        if (up == 3) {
            return new Assessment(Direction.ACIMA, up,
                "Confluência técnica de alta nos três critérios. Não garante resultado.");
        }
        if (down == 3) {
            return new Assessment(Direction.ABAIXO, down,
                "Confluência técnica de baixa nos três critérios. Não garante resultado.");
        }
        return new Assessment(Direction.AGUARDAR, Math.max(up, down),
            "Confirmações insuficientes ou divergentes entre MA9/MA21, RSI e CCI.");
    }

    private static Assessment wait(String reason) {
        return new Assessment(Direction.AGUARDAR, 0, reason);
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}