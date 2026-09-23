package com.blockchainhandler.storage.repository;

import com.blockchainhandler.storage.entity.EthereumEventEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Access to the partitioned {@code ethereum_events} journal.
 *
 * <p>Every statement filters by {@code block_timestamp}, the partition key, whenever it is known so that
 * PostgreSQL prunes partitions.
 */
public interface EthereumEventRepository extends JpaRepository<EthereumEventEntity, Long> {

    /**
     * Appends an event to the journal unless the same observation is already there (at-least-once delivery).
     *
     * @return number of inserted rows (0 or 1)
     */
    @Modifying
    @Transactional
    @Query(nativeQuery = true, value = """
            INSERT INTO ethereum_events (tx_hash, log_index, block_hash, block_number, block_timestamp, event_type,
                                         contract_address, removed, payload, processed, retry_count,
                                         last_attempt_at, created_at)
            VALUES (:txHash, :logIndex, :blockHash, :blockNumber, :blockTimestamp, :eventType,
                    :contractAddress, :removed, CAST(:payload AS jsonb), FALSE, 0, :now, :now)
            ON CONFLICT (block_hash, log_index, removed, block_timestamp) DO NOTHING
            """)
    int insertIfAbsent(
            @Param("txHash") String txHash,
            @Param("logIndex") int logIndex,
            @Param("blockHash") String blockHash,
            @Param("blockNumber") long blockNumber,
            @Param("blockTimestamp") Instant blockTimestamp,
            @Param("eventType") String eventType,
            @Param("contractAddress") String contractAddress,
            @Param("removed") boolean removed,
            @Param("payload") String payload,
            @Param("now") Instant now);

    /**
     * Locks an unprocessed journal entry for projection. Entries locked by another worker are skipped, so
     * concurrent consumers and retry workers never project the same event twice at the same time.
     *
     * @return the locked entry, or empty if it is already processed or locked by someone else
     */
    @Query(nativeQuery = true, value = """
            SELECT * FROM ethereum_events
            WHERE block_hash = :blockHash
              AND log_index = :logIndex
              AND removed = :removed
              AND block_timestamp = :blockTimestamp
              AND processed = FALSE
            FOR UPDATE SKIP LOCKED
            """)
    Optional<EthereumEventEntity> lockPending(
            @Param("blockHash") String blockHash,
            @Param("logIndex") int logIndex,
            @Param("removed") boolean removed,
            @Param("blockTimestamp") Instant blockTimestamp);

    /**
     * Marks a journal entry as projected.
     *
     * @return number of updated rows
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            UPDATE ethereum_events
            SET processed = TRUE, last_attempt_at = :now
            WHERE id = :id AND block_timestamp = :blockTimestamp
            """)
    int markProcessed(@Param("id") long id, @Param("blockTimestamp") Instant blockTimestamp, @Param("now") Instant now);

    /**
     * Records a failed projection attempt so that the retry worker picks the entry up after a back-off.
     *
     * @return number of updated rows
     */
    @Modifying
    @Transactional
    @Query(nativeQuery = true, value = """
            UPDATE ethereum_events
            SET retry_count = retry_count + 1, last_attempt_at = :now
            WHERE block_hash = :blockHash
              AND log_index = :logIndex
              AND removed = :removed
              AND block_timestamp = :blockTimestamp
              AND processed = FALSE
            """)
    int markAttemptFailed(
            @Param("blockHash") String blockHash,
            @Param("logIndex") int logIndex,
            @Param("removed") boolean removed,
            @Param("blockTimestamp") Instant blockTimestamp,
            @Param("now") Instant now);

    /**
     * Finds unprocessed entries whose exponential back-off ({@code baseBackoff * 2^retry_count}) has elapsed.
     * Served by the partial index on {@code (processed, retry_count) WHERE processed = false}.
     *
     * @param maxRetries         entries with this many failed attempts are left for manual investigation
     * @param baseBackoffSeconds back-off base in seconds
     * @param now                current time
     * @param limit              maximum number of entries
     * @return entries due for a retry, oldest blocks first
     */
    @Query(nativeQuery = true, value = """
            SELECT * FROM ethereum_events
            WHERE processed = FALSE
              AND retry_count < :maxRetries
              AND COALESCE(last_attempt_at, created_at)
                  < CAST(:now AS timestamptz) - make_interval(secs => :baseBackoffSeconds * power(2, retry_count))
            ORDER BY block_number, log_index
            LIMIT :limit
            """)
    List<EthereumEventEntity> findDueForRetry(
            @Param("maxRetries") int maxRetries,
            @Param("baseBackoffSeconds") double baseBackoffSeconds,
            @Param("now") Instant now,
            @Param("limit") int limit);

    /**
     * Counts journal entries whose projection has not succeeded yet.
     *
     * @return number of pending entries
     */
    long countByProcessedFalse();
}
