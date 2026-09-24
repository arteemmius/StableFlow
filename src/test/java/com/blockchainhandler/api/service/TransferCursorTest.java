package com.blockchainhandler.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class TransferCursorTest {

    private static final Instant BLOCK_TIMESTAMP = Instant.parse("2026-09-23T10:50:35Z");

    @Test
    @DisplayName("a cursor survives the round trip through its URL-safe token")
    void roundTrip() {
        TransferCursor cursor = new TransferCursor(BLOCK_TIMESTAMP, 13, 987);

        String token = cursor.encode();

        assertThat(token).isEqualTo("MjAyNi0wOS0yM1QxMDo1MDozNVp8MTN8OTg3").matches("[A-Za-z0-9_-]+");
        assertThat(TransferCursor.decode(token)).isEqualTo(cursor);
        assertThat(TransferCursor.isValid(token)).isTrue();
    }

    @ParameterizedTest
    @MethodSource("foreignTokens")
    @DisplayName("tokens that do not point at a stored transfer are rejected")
    void rejectsForeignTokens(String token) {
        assertThat(TransferCursor.isValid(token)).isFalse();
        assertThatIllegalArgumentException().isThrownBy(() -> TransferCursor.decode(token));
    }

    static Stream<String> foreignTokens() {
        return Stream.of(
                "",
                "not a cursor!",                                     // not base64url
                "A".repeat(TransferCursor.MAX_TOKEN_LENGTH + 1),    // too long
                token("2026-09-23T10:50:35Z|13"),                   // missing part
                token("2026-09-23T10:50:35Z|13|987|1"),             // extra part
                token("yesterday|13|987"),                          // malformed timestamp
                token("2026-09-23T10:50:35Z|thirteen|987"),         // malformed log index
                token("2026-09-23T10:50:35Z|-1|987"),               // the synthetic upper bound, never issued
                token("2026-09-23T10:50:35Z|13|0"),                 // row ids start at 1
                token("+10000-01-01T00:00:00Z|13|987"));            // after the unbounded 'to' of the API
    }

    @Test
    @DisplayName("the position before an instant follows every older transfer and precedes those of the instant")
    void positionBeforeInstant() {
        TransferCursor bound = TransferCursor.before(BLOCK_TIMESTAMP);

        assertThat(new TransferCursor(BLOCK_TIMESTAMP.minusNanos(1), Integer.MAX_VALUE, Long.MAX_VALUE)).isLessThan(bound);
        assertThat(new TransferCursor(BLOCK_TIMESTAMP, 0, 1)).isGreaterThan(bound);
    }

    @Test
    @DisplayName("positions are ordered by block timestamp, then log index, then row id")
    void ordersByTimestampLogIndexAndId() {
        TransferCursor position = new TransferCursor(BLOCK_TIMESTAMP, 13, 987);

        assertThat(position)
                .isLessThan(new TransferCursor(BLOCK_TIMESTAMP.plusSeconds(12), 0, 1))
                .isLessThan(new TransferCursor(BLOCK_TIMESTAMP, 14, 1))
                .isLessThan(new TransferCursor(BLOCK_TIMESTAMP, 13, 988))
                .isEqualByComparingTo(new TransferCursor(BLOCK_TIMESTAMP, 13, 987));
    }

    private static String token(String position) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(position.getBytes(StandardCharsets.UTF_8));
    }
}
