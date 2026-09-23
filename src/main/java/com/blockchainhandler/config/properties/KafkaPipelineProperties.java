package com.blockchainhandler.config.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Topics and consumer retry policy of the event pipeline ({@code app.kafka.*}).
 *
 * @param topics topic names and layout
 * @param retry  blocking retry policy applied before a record goes to the dead-letter topic
 */
@Validated
@ConfigurationProperties(prefix = "app.kafka")
public record KafkaPipelineProperties(@NotNull @Valid Topics topics, @NotNull @Valid Retry retry) {

    /**
     * Topic layout.
     *
     * @param events             topic with raw Ethereum events
     * @param deadLetter         dead-letter topic for events that could not be processed
     * @param partitions         number of partitions of both topics (the DLQ mirrors the source partition)
     * @param replicas           replication factor of both topics
     * @param deadLetterRetention retention of the dead-letter topic
     */
    public record Topics(
            @NotBlank String events,
            @NotBlank String deadLetter,
            @Positive int partitions,
            @Positive short replicas,
            @NotNull Duration deadLetterRetention) {
    }

    /**
     * Exponential back-off of the Kafka error handler.
     *
     * @param maxRetries      retries after the first failed attempt
     * @param initialInterval delay before the first retry
     * @param multiplier      growth factor of the delay
     * @param maxInterval     upper bound of the delay
     */
    public record Retry(
            @PositiveOrZero int maxRetries,
            @NotNull Duration initialInterval,
            @DecimalMin("1.0") double multiplier,
            @NotNull Duration maxInterval) {
    }
}
