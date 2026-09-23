package com.blockchainhandler.processing.projection;

import com.blockchainhandler.config.properties.ProcessingProperties;
import com.blockchainhandler.observability.EventMetrics;
import com.blockchainhandler.processing.journal.JournalKey;
import com.blockchainhandler.storage.entity.EthereumEventEntity;
import com.blockchainhandler.storage.repository.EthereumEventRepository;
import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Worker that retries projections of journal entries that are still unprocessed.
 *
 * <p>Candidates are selected through the partial index on {@code (processed, retry_count)} with an exponential
 * back-off per entry; the projection itself locks every entry with {@code SKIP LOCKED}, so several instances
 * can run the worker at the same time.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.processing.projection-retry", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PendingProjectionRetryJob {

    private final EthereumEventRepository eventRepository;
    private final TransferProjectionService projectionService;
    private final ProcessingProperties properties;
    private final EventMetrics metrics;
    private final Clock clock;

    /** Scheduled entry point of the worker. */
    @Scheduled(
            fixedDelayString = "${app.processing.projection-retry.poll-interval}",
            initialDelayString = "${app.processing.projection-retry.poll-interval}")
    public void retryPendingProjections() {
        try {
            runOnce();
        } catch (DataAccessException e) {
            log.error("Projection retry run failed", e);
        }
    }

    /**
     * Retries one batch of due projections and refreshes the pending-events gauge.
     *
     * @return number of entries that are no longer pending after this run
     */
    public int runOnce() {
        ProcessingProperties.ProjectionRetry settings = properties.projectionRetry();
        List<EthereumEventEntity> due = eventRepository.findDueForRetry(
                settings.maxRetries(), settings.baseBackoff().toSeconds(), clock.instant(), settings.batchSize());
        int resolved = 0;
        for (EthereumEventEntity event : due) {
            ProjectionOutcome outcome = projectionService.project(JournalKey.of(event));
            metrics.eventProcessed(event.getEventType(), outcome.metricTag());
            if (outcome != ProjectionOutcome.DEFERRED) {
                resolved++;
            }
        }
        metrics.setPendingEvents(eventRepository.countByProcessedFalse());
        if (!due.isEmpty()) {
            log.info("Projection retry: {} of {} due entries resolved", resolved, due.size());
        }
        return resolved;
    }
}
