package com.blockchainhandler;

import static org.assertj.core.api.Assertions.assertThat;

import com.blockchainhandler.common.messaging.EthereumEventMessage;
import com.blockchainhandler.config.properties.ProcessingProperties;
import com.blockchainhandler.observability.EventMetrics;
import com.blockchainhandler.processing.decoder.TransferEventDecoder;
import com.blockchainhandler.processing.enrichment.EnrichedTransfer;
import com.blockchainhandler.processing.enrichment.EventEnrichmentService;
import com.blockchainhandler.processing.journal.EventJournalService;
import com.blockchainhandler.processing.projection.PendingProjectionRetryJob;
import com.blockchainhandler.processing.projection.TransferProjectionService;
import com.blockchainhandler.storage.entity.TransferEntity;
import com.blockchainhandler.storage.repository.EthereumEventRepository;
import com.blockchainhandler.support.AbstractIntegrationTest;
import com.blockchainhandler.testsupport.UsdcTransfers;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Journal partitioning and the projection retry worker.
 */
class ProjectionRetryIT extends AbstractIntegrationTest {

    @Autowired
    private EventEnrichmentService enrichmentService;
    @Autowired
    private EventJournalService journalService;
    @Autowired
    private TransferProjectionService projectionService;
    @Autowired
    private EthereumEventRepository eventRepository;
    @Autowired
    private ProcessingProperties processingProperties;
    @Autowired
    private EventMetrics metrics;

    @Test
    @DisplayName("a historical event gets its partition on demand and an unprojected entry is retried after its back-off")
    void createsPartitionOnDemandAndRetriesPendingProjection() {
        long block = nextBlockNumber();
        Instant historicalTimestamp = Instant.parse("2024-03-10T08:00:00Z");
        NODE.blockTimestamp(block, historicalTimestamp);
        EthereumEventMessage message = UsdcTransfers.randomTransfer(block).build();
        EnrichedTransfer transfer = enrichmentService.enrich(message, new TransferEventDecoder().decode(message));

        // Journaled but not projected, as if the projection had failed right after the append.
        journalService.append(transfer);

        assertThat(jdbcTemplate.queryForObject("SELECT to_regclass('ethereum_events_2024_03')::text", String.class))
                .isEqualTo("ethereum_events_2024_03");
        assertThat(isProcessed(message.transactionHash())).isFalse();

        retryJob(Clock.systemUTC()).runOnce();
        assertThat(isProcessed(message.transactionHash()))
                .as("the back-off of the first attempt has not elapsed yet").isFalse();

        retryJob(Clock.offset(Clock.systemUTC(), Duration.ofMinutes(5))).runOnce();
        assertThat(isProcessed(message.transactionHash())).isTrue();
        List<TransferEntity> projected = transferRepository.findByTxHashOrderByLogIndexAsc(message.transactionHash());
        assertThat(projected).singleElement()
                .satisfies(entity -> assertThat(entity.getBlockTimestamp()).isEqualTo(historicalTimestamp));
    }

    private PendingProjectionRetryJob retryJob(Clock clock) {
        return new PendingProjectionRetryJob(eventRepository, projectionService, processingProperties, metrics, clock);
    }

    private boolean isProcessed(String txHash) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT processed FROM ethereum_events WHERE tx_hash = ?", Boolean.class, txHash));
    }
}
