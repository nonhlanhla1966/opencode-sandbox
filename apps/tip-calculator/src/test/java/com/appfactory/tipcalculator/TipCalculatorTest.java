package com.appfactory.tipcalculator;

import org.junit.Test;

import java.math.BigDecimal;

import static org.junit.Assert.assertEquals;

public class TipCalculatorTest {

    @Test
    public void parseBillHandlesUsAndEuropeanDecimals() {
        assertEquals(new BigDecimal("12.50"), TipCalculator.parseBill("12.5"));
        assertEquals(new BigDecimal("12.50"), TipCalculator.parseBill("12,50"));
        assertEquals(new BigDecimal("45.00"), TipCalculator.parseBill("45"));
        assertEquals(new BigDecimal("0.01"), TipCalculator.parseBill("0.011"));
    }

    @Test
    public void parseBillFallsBackToZero() {
        assertEquals(BigDecimal.ZERO, TipCalculator.parseBill(null));
        assertEquals(BigDecimal.ZERO, TipCalculator.parseBill(""));
        assertEquals(BigDecimal.ZERO, TipCalculator.parseBill("   "));
        assertEquals(BigDecimal.ZERO, TipCalculator.parseBill("abc"));
        assertEquals(BigDecimal.ZERO, TipCalculator.parseBill("-5"));
    }

    @Test
    public void tipRoundsToCentsHalfUp() {
        assertEquals(new BigDecimal("18.00"), TipCalculator.tip(new BigDecimal("100.00"), 18));
        assertEquals(new BigDecimal("6.85"), TipCalculator.tip(new BigDecimal("45.67"), 15));
        assertEquals(new BigDecimal("0.00"), TipCalculator.tip(new BigDecimal("10.00"), 0));
    }

    @Test
    public void totalAddsTipToBill() {
        assertEquals(new BigDecimal("118.00"), TipCalculator.total(new BigDecimal("100.00"), 18));
        assertEquals(new BigDecimal("52.52"), TipCalculator.total(new BigDecimal("45.67"), 15));
    }

    @Test
    public void perPersonSplitsRoundedCents() {
        assertEquals(new BigDecimal("28.75"), TipCalculator.perPerson(new BigDecimal("100.00"), 15, 4));
        assertEquals(new BigDecimal("40.00"), TipCalculator.perPerson(new BigDecimal("100.00"), 20, 3));
    }

    @Test
    public void clampRateBoundsToZeroAndHundred() {
        assertEquals(0, TipCalculator.clampRate(-5));
        assertEquals(100, TipCalculator.clampRate(150));
        assertEquals(18, TipCalculator.clampRate(18));
    }

    @Test
    public void clampPeopleBoundsToOneAndFifty() {
        assertEquals(TipCalculator.MIN_PEOPLE, TipCalculator.clampPeople(0));
        assertEquals(TipCalculator.MAX_PEOPLE, TipCalculator.clampPeople(500));
        assertEquals(4, TipCalculator.clampPeople(4));
    }

    @Test
    public void moneyFormatsWithDollarAndTwoDecimals() {
        assertEquals("$12.50", TipCalculator.money(new BigDecimal("12.5")));
        assertEquals("$0.00", TipCalculator.money(new BigDecimal("0")));
        assertEquals("$118.00", TipCalculator.money(new BigDecimal("118")));
    }
}