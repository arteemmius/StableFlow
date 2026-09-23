package com.blockchainhandler.processing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import com.blockchainhandler.common.messaging.EthereumEventMessage;
import com.blockchainhandler.observability.EventMetrics;
import com.blockchainhandler.processing.decoder.TransferEventDecoder;
import com.blockchainhandler.processing.enrichment.EnrichedTransfer;
import com.blockchainhandler.processing.enrichment.EventEnrichmentService;
import com.blockchainhandler.processing.exception.BlockTimestampUnavailableException;
import com.blockchainhandler.processing.exception.EventDecodingException;
import com.blockchainhandler.processing.journal.EventJournalService;
import com.blockchainhandler.processing.journal.JournalKey;
import com.blockchainhandler.processing.projection.ProjectionOutcome;
import com.blockchainhandler.processing.projection.TransferProjectionService;
import com.blockchainhandler.testsupport.UsdcTransfers;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EventProcessingServiceTest {

    @Mock
    private EventEnrichmentService enrichmentService;
    @Mock
    private EventJournalService journalService;
    @Mock
    private TransferProjectionService projectionService;
    @Mock
    private EnrichedTransfer enrichedTransfer;

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private EventProcessingService service;

    @BeforeEach
    void setUp() {
        service = new EventProcessingService(new TransferEventDecoder(), enrichmentService, journalService,
                projectionService, new EventMetrics(registry));
    }

    @Test
    @DisplayName("decodes, enriches, journals and projects an event, recording metrics")
    void processesEvent() {
        EthereumEventMessage message = UsdcTransfers.realTransfer().build();
        JournalKey key = new JournalKey(UsdcTransfers.BLOCK_HASH, UsdcTransfers.LOG_INDEX, false, UsdcTransfers.BLOCK_TIMESTAMP);
        given(enrichmentService.enrich(any(), any())).willReturn(enrichedTransfer);
        given(journalService.append(enrichedTransfer)).willReturn(key);
        given(projectionService.project(key)).willReturn(ProjectionOutcome.PROJECTED);

        ProjectionOutcome outcome = service.process(message);

        assertThat(outcome).isEqualTo(ProjectionOutcome.PROJECTED);
        assertThat(registry.get(EventMetrics.EVENTS_PROCESSED).tag("outcome", "projected").counter().count()).isEqualTo(1.0);
        assertThat(registry.get(EventMetrics.PROCESSING_TIME).tag("outcome", "projected").timer().count()).isEqualTo(1L);
    }

    @Test
    @DisplayName("a malformed log fails before any side effect and is timed as failed")
    void failsOnMalformedLog() {
        EthereumEventMessage message = UsdcTransfers.realTransfer().topics(List.of(UsdcTransfers.TRANSFER_TOPIC)).build();

        assertThatThrownBy(() -> service.process(message)).isInstanceOf(EventDecodingException.class);

        verifyNoInteractions(enrichmentService, journalService, projectionService);
        assertThat(registry.get(EventMetrics.PROCESSING_TIME).tag("outcome", "failed").timer().count()).isEqualTo(1L);
    }

    @Test
    @DisplayName("an enrichment failure propagates so that the Kafka error handler retries it")
    void propagatesEnrichmentFailure() {
        given(enrichmentService.enrich(any(), any())).willThrow(new BlockTimestampUnavailableException("node is down"));

        assertThatThrownBy(() -> service.process(UsdcTransfers.realTransfer().build()))
                .isInstanceOf(BlockTimestampUnavailableException.class);
        verifyNoInteractions(journalService, projectionService);
    }
}
