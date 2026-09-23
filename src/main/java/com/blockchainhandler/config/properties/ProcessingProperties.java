package com.blockchainhandler.config.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Settings of the processing layer workers ({@code app.processing.*}).
 *
 * @param projectionRetry worker that re-projects journaled events whose projection failed
 * @param partitions      maintenance of the monthly partitions of {@code ethereum_events}
 */
@Validated
@ConfigurationProperties(prefix = "app.processing")
public record ProcessingProperties(
        @NotNull @Valid ProjectionRetry projectionRetry,
        @NotNull @Valid Partitions partitions) {

    /**
     * Projection retry worker.
     *
     * @param enabled      whether the worker is scheduled
     * @param pollInterval delay between worker runs (ISO-8601, e.g. {@code PT30S})
     * @param batchSize    maximum number of events handled per run
     * @param maxRetries   attempts after which an event is left for manual investigation
     * @param baseBackoff  back-off base; an event is retried after {@code baseBackoff * 2^retryCount}
     */
    public record ProjectionRetry(
            boolean enabled,
            @NotNull Duration pollInterval,
            @Positive int batchSize,
            @Positive int maxRetries,
            @NotNull Duration baseBackoff) {
    }

    /**
     * Partition maintenance.
     *
     * @param monthsAhead     number of future monthly partitions kept pre-created
     * @param maintenanceCron cron expression (UTC) of the maintenance job
     */
    public record Partitions(@PositiveOrZero int monthsAhead, @NotBlank String maintenanceCron) {
    }
}
