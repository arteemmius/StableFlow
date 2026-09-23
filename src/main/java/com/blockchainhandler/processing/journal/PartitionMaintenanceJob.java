package com.blockchainhandler.processing.journal;

import com.blockchainhandler.config.properties.ProcessingProperties;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Keeps monthly partitions of {@code ethereum_events} created ahead of time, on startup and daily.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PartitionMaintenanceJob {

    private final PartitionManager partitionManager;
    private final ProcessingProperties properties;
    private final Clock clock;

    /** Runs the maintenance once the application has started. */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        maintainPartitions();
    }

    /** Creates partitions for the current month and the configured number of months ahead. */
    @Scheduled(cron = "${app.processing.partitions.maintenance-cron}", zone = "UTC")
    public void maintainPartitions() {
        int monthsAhead = properties.partitions().monthsAhead();
        try {
            int created = partitionManager.ensureUpcomingPartitions(clock.instant(), monthsAhead);
            log.info("Partition maintenance finished: {} new partition(s), {} month(s) ahead", created, monthsAhead);
        } catch (DataAccessException e) {
            log.error("Partition maintenance failed; partitions will still be created on demand", e);
        }
    }
}
