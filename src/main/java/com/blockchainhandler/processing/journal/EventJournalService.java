package com.blockchainhandler.processing.journal;

import com.blockchainhandler.processing.enrichment.EnrichedTransfer;
import com.blockchainhandler.storage.repository.EthereumEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Appends enriched events to the {@code ethereum_events} journal.
 *
 * <p>The journal is written before any projection, so an event that was consumed from Kafka is never lost even
 * if its projection fails: the retry worker picks it up later.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventJournalService {

    private final EthereumEventRepository repository;
    private final PartitionManager partitionManager;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /**
     * Appends the transfer to the journal. Repeated calls for the same observation are no-ops, which makes
     * redelivered Kafka records and backfilled duplicates harmless.
     *
     * @param transfer enriched transfer
     * @return key of the journal entry
     */
    public JournalKey append(EnrichedTransfer transfer) {
        partitionManager.ensurePartitionFor(transfer.blockTimestamp());
        int inserted = repository.insertIfAbsent(
                transfer.transactionHash(),
                transfer.logIndex(),
                transfer.blockHash(),
                transfer.blockNumber(),
                transfer.blockTimestamp(),
                transfer.eventType().name(),
                transfer.contractAddress(),
                transfer.removed(),
                toJson(TransferPayload.of(transfer)),
                clock.instant());
        JournalKey key = new JournalKey(transfer.blockHash(), transfer.logIndex(), transfer.removed(), transfer.blockTimestamp());
        if (inserted == 0) {
            log.debug("Event {} is already journaled", key);
        }
        return key;
    }

    private String toJson(TransferPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize journal payload", e);
        }
    }
}
