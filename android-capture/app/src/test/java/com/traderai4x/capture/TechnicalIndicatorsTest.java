package com.traderai4x.capture;

import org.junit.Test;
import static org.junit.Assert.*;

public class TechnicalIndicatorsTest {
    private TechnicalIndicators.Candle[] risingSeries() {
        TechnicalIndicators.Candle[] candles = new TechnicalIndicators.Candle[21];
        for (int i = 0; i < candles.length; i++) {
            double close = i + 1.0;
            candles[i] = new TechnicalIndicators.Candle(close, close + 1.0, close - 1.0, close);
        }
        return candles;
    }

    @Test public void calculatesMovingAveragesFromLatestCloses() {
        TechnicalIndicators.Result result = TechnicalIndicators.calculate(risingSeries());
        assertEquals(17.0, result.sma9, 0.000001);
        assertEquals(11.0, result.sma21, 0.000001);
        assertEquals(21, result.candlesUsed);
    }

    @Test public void calculatesRsiForOnlyRisingSeries() {
        TechnicalIndicators.Result result = TechnicalIndicators.calculate(risingSeries());
        assertEquals(100.0, result.rsi14, 0.000001);
    }

    @Test public void calculatesCciFromTypicalPrice() {
        TechnicalIndicators.Result result = TechnicalIndicators.calculate(risingSeries());
        assertEquals(120.6349206, result.cci20, 0.0001);
    }

    @Test public void rejectsInsufficientHistory() {
        try {
            TechnicalIndicators.calculate(new TechnicalIndicators.Candle[20]);
            fail("Expected insufficient-history exception");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("21"));
        }
    }

    @Test public void rejectsInvalidOhlcRange() {
        try {
            new TechnicalIndicators.Candle(10, 9, 8, 11);
            fail("Expected invalid OHLC exception");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("OHLC"));
        }
    }

    @Test public void signalAssessmentStaysWaitingWithoutTrustedOhlc() {
        TechnicalIndicators.Result result = TechnicalIndicators.calculate(risingSeries());
        SignalAssessment.Assessment assessment =
            SignalAssessment.evaluate(result, false, true);
        assertEquals(SignalAssessment.Direction.AGUARDAR, assessment.direction);
        assertEquals(0, assessment.confirmations);
    }

    @Test public void signalAssessmentWaitsForClosedCandle() {
        TechnicalIndicators.Result result = TechnicalIndicators.calculate(risingSeries());
        SignalAssessment.Assessment assessment =
            SignalAssessment.evaluate(result, true, false);
        assertEquals(SignalAssessment.Direction.AGUARDAR, assessment.direction);
    }
}