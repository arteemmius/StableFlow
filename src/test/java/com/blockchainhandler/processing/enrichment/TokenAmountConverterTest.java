package com.blockchainhandler.processing.enrichment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.math.BigInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TokenAmountConverterTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "23270000, 23.270000",
            "0, 0.000000",
            "1, 0.000001",
            "1000000, 1.000000",
            "123456789012345678, 123456789012.345678"
    })
    @DisplayName("shifts the decimal point by 6 digits for USDC without rounding")
    void convertsUsdcAmounts(String raw, String expected) {
        BigDecimal converted = TokenAmountConverter.toTokenUnits(new BigInteger(raw), 6);

        assertThat(converted.toPlainString()).isEqualTo(expected);
        assertThat(converted.scale()).isEqualTo(6);
    }

    @Test
    @DisplayName("handles the largest uint256 value exactly")
    void handlesMaxUint256() {
        BigInteger maxUint256 = BigInteger.TWO.pow(256).subtract(BigInteger.ONE);

        BigDecimal converted = TokenAmountConverter.toTokenUnits(maxUint256, 6);

        assertThat(converted.movePointRight(6).toBigIntegerExact()).isEqualTo(maxUint256);
    }

    @Test
    @DisplayName("rejects negative amounts")
    void rejectsNegativeAmounts() {
        assertThatThrownBy(() -> TokenAmountConverter.toTokenUnits(BigInteger.valueOf(-1), 6))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
