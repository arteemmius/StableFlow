package com.blockchainhandler.processing.enrichment;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/**
 * Converts raw ERC-20 amounts (integers in the smallest unit) to token units.
 */
public final class TokenAmountConverter {

    private TokenAmountConverter() {
    }

    /**
     * Shifts the decimal point of a raw amount, e.g. {@code 23270000} with 6 decimals becomes {@code 23.270000}.
     * The conversion is exact, the scale of the result equals the number of decimals.
     *
     * @param rawAmount amount in the smallest unit, must not be negative
     * @param decimals  number of decimals of the token
     * @return amount in token units
     */
    public static BigDecimal toTokenUnits(BigInteger rawAmount, int decimals) {
        if (rawAmount.signum() < 0) {
            throw new IllegalArgumentException("ERC-20 amounts are unsigned: " + rawAmount);
        }
        return new BigDecimal(rawAmount).movePointLeft(decimals).setScale(decimals, RoundingMode.UNNECESSARY);
    }
}
