package com.blockchainhandler.config.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Redis cache settings ({@code app.cache.*}).
 *
 * @param keyPrefix     prefix of every key the service writes to Redis
 * @param ttl           time-to-live per cache
 * @param generationTtl lifetime of the per-address cache generation markers used for invalidation
 */
@Validated
@ConfigurationProperties(prefix = "app.cache")
public record AppCacheProperties(
        @NotBlank String keyPrefix,
        @NotNull @Valid Ttl ttl,
        @NotNull Duration generationTtl) {

    /**
     * Time-to-live of the individual caches.
     *
     * @param transfers          paginated transfer lists of an address
     * @param transactionDetails transaction details
     * @param dailyStats         daily token statistics
     * @param blockTimestamps    block number to block timestamp mapping
     */
    public record Ttl(
            @NotNull Duration transfers,
            @NotNull Duration transactionDetails,
            @NotNull Duration dailyStats,
            @NotNull Duration blockTimestamps) {
    }
}
