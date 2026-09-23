package com.blockchainhandler.storage.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/**
 * A single USDC {@code Transfer} log, the read model behind the REST API (table {@code transactions}).
 *
 * <p>One transaction may contain several transfers, so a row is identified by {@code (block_hash, log_index)}.
 * Rows are written with idempotent native upserts by the projection, hence the entity is read-only.
 */
@Entity
@Table(name = "transactions")
@Immutable
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class TransferEntity {

    @Id
    private Long id;

    @Column(name = "tx_hash", nullable = false)
    private String txHash;

    @Column(name = "log_index", nullable = false)
    private int logIndex;

    @Column(name = "block_number", nullable = false)
    private long blockNumber;

    @Column(name = "block_hash", nullable = false)
    private String blockHash;

    @Column(name = "block_timestamp", nullable = false)
    private Instant blockTimestamp;

    @Column(name = "contract_address", nullable = false)
    private String contractAddress;

    @Column(name = "from_address", nullable = false)
    private String fromAddress;

    @Column(name = "to_address", nullable = false)
    private String toAddress;

    @Column(name = "value_raw", nullable = false)
    private BigDecimal valueRaw;

    @Column(name = "value_usdc", nullable = false)
    private BigDecimal valueUsdc;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
