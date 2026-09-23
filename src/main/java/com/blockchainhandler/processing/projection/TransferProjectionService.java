package com.blockchainhandler.processing.projection;

import com.blockchainhandler.cache.TransfersChangedEvent;
import com.blockchainhandler.observability.EventMetrics;
import com.blockchainhandler.processing.journal.JournalKey;
import com.blockchainhandler.processing.journal.TransferPayload;
import com.blockchainhandler.storage.entity.EthereumEventEntity;
import com.blockchainhandler.storage.repository.EthereumEventRepository;
import com.blockchainhandler.storage.repository.TransferRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Projects journaled Transfer events into the {@code transactions} read model.
 *
 * <p>Used both inline by the Kafka consumer and by the retry worker. Each projection runs in its own
 * transaction: the journal entry is locked with {@code FOR UPDATE SKIP LOCKED}, the read model is updated and
 * the entry is marked as processed. A failure does not propagate: it is recorded on the entry
 * ({@code retry_count}, {@code last_attempt_at}) and the worker retries it with exponential back-off, so a
 * projection bug can neither lose an event nor block a Kafka partition.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransferProjectionService {

    private final EthereumEventRepository eventRepository;
    private final TransferRepository transferRepository;
    private final TransactionTemplate transactionTemplate;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final EventMetrics metrics;
    private final Clock clock;

    /**
     * Projects the journal entry with the given key.
     *
     * @param key key of the journal entry
     * @return outcome of the projection; {@link ProjectionOutcome#DEFERRED} if it failed
     */
    public ProjectionOutcome project(JournalKey key) {
        try {
            ProjectionOutcome outcome = transactionTemplate.execute(status -> projectLocked(key));
            return outcome != null ? outcome : ProjectionOutcome.ALREADY_PROCESSED;
        } catch (RuntimeException e) {
            recordFailure(key, e);
            return ProjectionOutcome.DEFERRED;
        }
    }

    private ProjectionOutcome projectLocked(JournalKey key) {
        Optional<EthereumEventEntity> locked = eventRepository.lockPending(
                key.blockHash(), key.logIndex(), key.removed(), key.blockTimestamp());
        if (locked.isEmpty()) {
            return ProjectionOutcome.ALREADY_PROCESSED;
        }
        EthereumEventEntity event = locked.get();
        TransferPayload payload = readPayload(event);

        ProjectionOutcome outcome;
        if (event.isRemoved()) {
            int deleted = transferRepository.deleteByBlockHashAndLogIndex(event.getBlockHash(), event.getLogIndex());
            log.info("Reverted transfer tx={} logIndex={} of orphaned block {} ({} row(s) deleted)",
                    event.getTxHash(), event.getLogIndex(), event.getBlockHash(), deleted);
            outcome = ProjectionOutcome.REVERTED;
        } else {
            int inserted = transferRepository.insertUnlessReverted(
                    event.getTxHash(),
                    event.getLogIndex(),
                    event.getBlockNumber(),
                    event.getBlockHash(),
                    event.getBlockTimestamp(),
                    event.getContractAddress(),
                    payload.from(),
                    payload.to(),
                    new BigDecimal(payload.valueRaw()),
                    new BigDecimal(payload.value()),
                    clock.instant());
            outcome = inserted > 0 ? ProjectionOutcome.PROJECTED : ProjectionOutcome.SKIPPED;
        }
        eventRepository.markProcessed(event.getId(), event.getBlockTimestamp(), clock.instant());

        if (outcome != ProjectionOutcome.SKIPPED) {
            // Self-transfers have from == to, hence a set built from a list rather than Set.of.
            eventPublisher.publishEvent(new TransfersChangedEvent(
                    new LinkedHashSet<>(List.of(payload.from(), payload.to())), event.getTxHash()));
        }
        return outcome;
    }

    private TransferPayload readPayload(EthereumEventEntity event) {
        try {
            return objectMapper.readValue(event.getPayload(), TransferPayload.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Corrupted payload of journal entry " + event.getId(), e);
        }
    }

    private void recordFailure(JournalKey key, RuntimeException failure) {
        metrics.projectionFailed();
        log.warn("Projection of journal entry {} failed; the retry worker will pick it up", key, failure);
        try {
            eventRepository.markAttemptFailed(key.blockHash(), key.logIndex(), key.removed(), key.blockTimestamp(), clock.instant());
        } catch (RuntimeException e) {
            log.error("Could not record the failed projection attempt of {}", key, e);
        }
    }
}
