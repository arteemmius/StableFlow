package com.blockchainhandler.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ReconnectBackoffTest {

    @Test
    @DisplayName("delay doubles with every failed attempt and is capped at the maximum")
    void growsExponentiallyUpToTheCap() {
        ReconnectBackoff backoff = new ReconnectBackoff(Duration.ofSeconds(1), Duration.ofSeconds(60), 2.0, 0.0, () -> 0.5);

        assertThat(backoff.delayForAttempt(0)).isEqualTo(Duration.ofSeconds(1));
        assertThat(backoff.delayForAttempt(1)).isEqualTo(Duration.ofSeconds(2));
        assertThat(backoff.delayForAttempt(5)).isEqualTo(Duration.ofSeconds(32));
        assertThat(backoff.delayForAttempt(6)).isEqualTo(Duration.ofSeconds(60));
        assertThat(backoff.delayForAttempt(10_000)).isEqualTo(Duration.ofSeconds(60));
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, 0.25, 0.5, 0.75, 0.9999})
    @DisplayName("jitter stays within ±20% of the exponential delay")
    void jitterStaysWithinBounds(double random) {
        ReconnectBackoff backoff = new ReconnectBackoff(Duration.ofSeconds(10), Duration.ofSeconds(60), 2.0, 0.2, () -> random);

        assertThat(backoff.delayForAttempt(0)).isBetween(Duration.ofSeconds(8), Duration.ofSeconds(12));
        assertThat(backoff.delayForAttempt(1)).isBetween(Duration.ofSeconds(16), Duration.ofSeconds(24));
    }

    @Test
    @DisplayName("jitter never pushes the delay above the maximum")
    void jitterDoesNotExceedTheCap() {
        ReconnectBackoff backoff = new ReconnectBackoff(Duration.ofSeconds(1), Duration.ofSeconds(60), 2.0, 0.2, () -> 0.9999);

        assertThat(backoff.delayForAttempt(20)).isEqualTo(Duration.ofSeconds(60));
    }

    @Test
    @DisplayName("rejects settings that would shrink the delay")
    void rejectsInvalidSettings() {
        assertThatThrownBy(() -> new ReconnectBackoff(Duration.ofSeconds(1), Duration.ofSeconds(60), 0.5, 0.2, () -> 0.5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReconnectBackoff(Duration.ofSeconds(10), Duration.ofSeconds(1), 2.0, 0.2, () -> 0.5))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
