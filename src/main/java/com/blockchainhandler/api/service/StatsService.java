package com.blockchainhandler.api.service;

import com.blockchainhandler.api.dto.DailyStatsResponse;
import com.blockchainhandler.api.error.ResourceNotFoundException;
import com.blockchainhandler.cache.CacheNames;
import com.blockchainhandler.config.properties.UsdcProperties;
import com.blockchainhandler.storage.model.DailyTransferAggregate;
import com.blockchainhandler.storage.repository.TransferRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aggregated statistics of tracked tokens.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatsService {

    private final TransferRepository repository;
    private final UsdcProperties usdc;

    /**
     * Returns transfer statistics of a token for one UTC day. Cached for {@code app.cache.ttl.daily-stats}.
     *
     * @param token checksum address of the token contract
     * @param date  UTC day
     * @return daily statistics
     * @throws ResourceNotFoundException if the token is not tracked by the service
     */
    @Cacheable(cacheNames = CacheNames.DAILY_STATS, key = "#token + '|' + #date")
    public DailyStatsResponse getDailyStats(String token, LocalDate date) {
        if (!usdc.isUsdc(token)) {
            throw new ResourceNotFoundException("Token " + token + " is not tracked");
        }
        Instant from = date.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant to = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        DailyTransferAggregate aggregate = repository.aggregate(usdc.checksumAddress(), from, to);

        long transferCount = valueOrZero(aggregate.transferCount());
        BigDecimal totalVolume = scaled(aggregate.totalVolume());
        BigDecimal averageValue = transferCount == 0
                ? scaled(BigDecimal.ZERO)
                : totalVolume.divide(BigDecimal.valueOf(transferCount), usdc.decimals(), RoundingMode.HALF_EVEN);
        return new DailyStatsResponse(
                usdc.checksumAddress(),
                date,
                transferCount,
                totalVolume,
                averageValue,
                scaled(aggregate.maxValue()),
                valueOrZero(aggregate.uniqueSenders()),
                valueOrZero(aggregate.uniqueReceivers()),
                aggregate.firstTransferAt(),
                aggregate.lastTransferAt());
    }

    private BigDecimal scaled(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(usdc.decimals(), RoundingMode.HALF_EVEN);
    }

    private static long valueOrZero(Long value) {
        return value == null ? 0L : value;
    }
}
