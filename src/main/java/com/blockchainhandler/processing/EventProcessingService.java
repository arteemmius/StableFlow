package com.blockchainhandler.processing;

import com.blockchainhandler.common.messaging.EthereumEventMessage;
import com.blockchainhandler.observability.EventMetrics;
import com.blockchainhandler.processing.decoder.DecodedTransfer;
import com.blockchainhandler.processing.decoder.TransferEventDecoder;
import com.blockchainhandler.processing.enrichment.EnrichedTransfer;
import com.blockchainhandler.processing.enrichment.EventEnrichmentService;
import com.blockchainhandler.processing.journal.EventJournalService;
import com.blockchainhandler.processing.journal.JournalKey;
import com.blockchainhandler.processing.projection.ProjectionOutcome;
import com.blockchainhandler.processing.projection.TransferProjectionService;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Processing pipeline of a single consumed event: decode, enrich, journal, project.
 *
 * <p>Exceptions thrown before the event is journaled are handled by the Kafka error handler (retries with
 * exponential back-off, then the dead-letter topic). Once journaled, the event can no longer be lost: a failed
 * projection is recorded on the journal entry and retried by the worker.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventProcessingService {

    private static final String FAILED_OUTCOME = "failed";

    private final TransferEventDecoder decoder;
    private final EventEnrichmentService enrichmentService;
    private final EventJournalService journalService;
    private final TransferProjectionService projectionService;
    private final EventMetrics metrics;

    /**
     * Processes an event consumed from Kafka.
     *
     * @param message raw event
     * @return outcome of the projection
     * @throws com.blockchainhandler.processing.exception.NonRetryableEventException if the event is invalid
     * @throws com.blockchainhandler.processing.exception.BlockTimestampUnavailableException if enrichment
     *         failed transiently
     */
    public ProjectionOutcome process(EthereumEventMessage message) {
        Timer.Sample sample = metrics.startProcessing();
        String outcomeTag = FAILED_OUTCOME;
        try {
            DecodedTransfer decoded = decoder.decode(message);
            EnrichedTransfer transfer = enrichmentService.enrich(message, decoded);
            JournalKey key = journalService.append(transfer);
            ProjectionOutcome outcome = projectionService.project(key);
            outcomeTag = outcome.metricTag();
            metrics.eventProcessed(message.eventType(), outcomeTag);
            log.debug("Processed {} event: {}", message.eventType(), outcome);
            return outcome;
        } finally {
            metrics.stopProcessing(sample, message.eventType(), outcomeTag);
        }
    }
}
