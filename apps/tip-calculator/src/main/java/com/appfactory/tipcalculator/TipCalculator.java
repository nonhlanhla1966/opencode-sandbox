package com.appfactory.tipcalculator;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Pure calculation logic for the tip calculator; covered by unit tests. */
public final class TipCalculator {

    /** Default tip rate applied when the user has not picked one. */
    public static final int DEFAULT_RATE = 18;

    /** Minimum and maximum number of people the bill can be split across. */
    public static final int MIN_PEOPLE = 1;
    public static final int MAX_PEOPLE = 50;

    private TipCalculator() {}

    /**
     * Parse a user-typed currency string like "12.5", "12,50" or "45".
     * Blank, null, negative and unparseable input all become zero.
     */
    public static BigDecimal parseBill(String raw) {
        if (raw == null) {
            return BigDecimal.ZERO;
        }
        String s = raw.trim().replace(",", ".");
        if (s.isEmpty() || ".".equals(s)) {
            return BigDecimal.ZERO;
        }
        try {
            BigDecimal value = new BigDecimal(s).setScale(2, RoundingMode.HALF_UP);
            return value.signum() < 0 ? BigDecimal.ZERO : value;
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    /** Clamp a tip percentage to the legal [0,100] range. */
    public static int clampRate(int rate) {
        if (rate < 0) {
            return 0;
        }
        return Math.min(rate, 100);
    }

    /** Clamp a people count to [MIN_PEOPLE, MAX_PEOPLE]. */
    public static int clampPeople(int people) {
        if (people < MIN_PEOPLE) {
            return MIN_PEOPLE;
        }
        return Math.min(people, MAX_PEOPLE);
    }

    /** The tip on a bill at a given percentage, rounded to cents. */
    public static BigDecimal tip(BigDecimal bill, int rate) {
        BigDecimal safeBill = bill == null ? BigDecimal.ZERO : bill;
        BigDecimal pct = BigDecimal.valueOf(clampRate(rate));
        return safeBill.multiply(pct)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    /** The bill plus its tip, rounded to cents. */
    public static BigDecimal total(BigDecimal bill, int rate) {
        return bill.add(tip(bill, rate));
    }

    /** The total split across people, rounded to cents per person. */
    public static BigDecimal perPerson(BigDecimal bill, int rate, int people) {
        BigDecimal t = total(bill, rate);
        return t.divide(BigDecimal.valueOf(clampPeople(people)), 2, RoundingMode.HALF_UP);
    }

    /** Format a value as "$X.YY". */
    public static String money(BigDecimal value) {
        BigDecimal safe = value == null ? BigDecimal.ZERO : value;
        return "$" + safe.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}