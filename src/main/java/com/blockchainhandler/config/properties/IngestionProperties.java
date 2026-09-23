package com.blockchainhandler.config.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Settings of the WebSocket ingestion layer ({@code app.ingestion.*}).
 *
 * @param enabled                  whether the node subscription is started at all
 * @param reconnect                reconnect back-off policy
 * @param heartbeatInterval        WebSocket ping interval used to detect dead connections
 * @param staleSubscriptionTimeout resubscribe when no log arrived for this long ({@code 0} disables the check)
 * @param watchdogInterval         how often the stale-subscription and failed-publish checks run
 * @param checkpointFlushInterval  how often the ingestion checkpoint is persisted to Redis
 * @param backfill                 gap backfill settings
 */
@Validated
@ConfigurationProperties(prefix = "app.ingestion")
public record IngestionProperties(
        boolean enabled,
        @NotNull @Valid Reconnect reconnect,
        @NotNull Duration heartbeatInterval,
        @NotNull Duration staleSubscriptionTimeout,
        @NotNull Duration watchdogInterval,
        @NotNull Duration checkpointFlushInterval,
        @NotNull @Valid Backfill backfill) {

    /**
     * Exponential back-off between reconnect attempts.
     *
     * @param initialDelay delay before the first reconnect attempt
     * @param maxDelay     upper bound of the delay
     * @param multiplier   growth factor applied per failed attempt
     * @param jitter       relative random jitter in {@code [0, 1]} that de-synchronizes instances
     */
    public record Reconnect(
            @NotNull Duration initialDelay,
            @NotNull Duration maxDelay,
            @DecimalMin("1.0") double multiplier,
            @DecimalMin("0.0") @DecimalMax("1.0") double jitter) {
    }

    /**
     * Backfill of logs missed while the subscription was down.
     *
     * @param enabled   whether gaps are backfilled with {@code eth_getLogs}
     * @param maxBlocks maximum number of blocks to backfill; older gaps are reported and skipped
     * @param chunkSize number of blocks requested per {@code eth_getLogs} call
     */
    public record Backfill(boolean enabled, @Positive int maxBlocks, @Positive int chunkSize) {
    }
}
