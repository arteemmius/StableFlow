package com.blockchainhandler.storage.entity;

import com.blockchainhandler.common.messaging.EventType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Journal entry of a processed smart-contract event (table {@code ethereum_events}, partitioned by month of
 * {@code block_timestamp}).
 *
 * <p>The journal is the source of truth of the processing layer: every consumed event is appended here first
 * and projected into typed read models afterwards. {@code processed}, {@code retry_count} and
 * {@code last_attempt_at} track the projection so that a worker can retry failed projections. Rows are
 * written with native SQL, hence the entity is read-only.
 */
@Entity
@Table(name = "ethereum_events")
@Immutable
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class EthereumEventEntity {

    @Id
    private Long id;

    @Column(name = "tx_hash", nullable = false)
    private String txHash;

    @Column(name = "log_index", nullable = false)
    private int logIndex;

    @Column(name = "block_hash", nullable = false)
    private String blockHash;

    @Column(name = "block_number", nullable = false)
    private long blockNumber;

    @Column(name = "block_timestamp", nullable = false)
    private Instant blockTimestamp;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private EventType eventType;

    @Column(name = "contract_address", nullable = false)
    private String contractAddress;

    @Column(name = "removed", nullable = false)
    private boolean removed;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false)
    private String payload;

    @Column(name = "processed", nullable = false)
    private boolean processed;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
