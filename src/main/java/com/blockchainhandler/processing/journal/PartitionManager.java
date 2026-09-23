package com.blockchainhandler.processing.journal;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Creates monthly partitions of {@code ethereum_events} on demand.
 *
 * <p>The actual DDL lives in the {@code ensure_ethereum_events_partition} SQL function (see the Liquibase
 * changelog), which is idempotent and serialized with an advisory lock, so several consumers and instances can
 * call it concurrently. Months that are known to exist are remembered to avoid a database round trip per event.
 * Calls run outside of the caller's transaction (auto-commit), so DDL locks are released immediately.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PartitionManager {

    private final JdbcTemplate jdbcTemplate;
    private final Set<YearMonth> ensuredMonths = ConcurrentHashMap.newKeySet();

    /**
     * Makes sure the partition that covers the given timestamp exists.
     *
     * @param timestamp block timestamp of an event about to be journaled
     */
    public void ensurePartitionFor(Instant timestamp) {
        YearMonth month = YearMonth.from(timestamp.atOffset(ZoneOffset.UTC));
        if (ensuredMonths.contains(month)) {
            return;
        }
        String partition = jdbcTemplate.queryForObject(
                "SELECT ensure_ethereum_events_partition(?)", String.class, OffsetDateTime.ofInstant(timestamp, ZoneOffset.UTC));
        ensuredMonths.add(month);
        log.debug("Partition {} is ready for {}", partition, month);
    }

    /**
     * Pre-creates the partitions of the month of {@code from} and of the following months.
     *
     * @param from        reference timestamp
     * @param monthsAhead number of future months to prepare
     * @return number of partitions created by this call
     */
    public int ensureUpcomingPartitions(Instant from, int monthsAhead) {
        Integer created = jdbcTemplate.queryForObject(
                "SELECT ensure_ethereum_events_partitions(?, ?)", Integer.class,
                OffsetDateTime.ofInstant(from, ZoneOffset.UTC), monthsAhead);
        return created == null ? 0 : created;
    }
}
