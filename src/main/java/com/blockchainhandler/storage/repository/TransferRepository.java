package com.blockchainhandler.storage.repository;

import com.blockchainhandler.storage.entity.TransferEntity;
import com.blockchainhandler.storage.model.DailyTransferAggregate;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Access to the {@code transactions} read model.
 */
public interface TransferRepository extends JpaRepository<TransferEntity, Long> {

    /**
     * Finds transfers where the address is the sender or the receiver within {@code [from, to)}.
     *
     * @param address  checksum address
     * @param from     inclusive lower bound of the block timestamp
     * @param to       exclusive upper bound of the block timestamp
     * @param pageable page request including the sort order
     * @return page of transfers
     */
    @Query(value = """
            select t from TransferEntity t
            where (t.fromAddress = :address or t.toAddress = :address)
              and t.blockTimestamp >= :from and t.blockTimestamp < :to
            """,
            countQuery = """
            select count(t) from TransferEntity t
            where (t.fromAddress = :address or t.toAddress = :address)
              and t.blockTimestamp >= :from and t.blockTimestamp < :to
            """)
    Page<TransferEntity> findByAddress(
            @Param("address") String address,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable);

    /**
     * Finds the latest transfers of all addresses that precede a keyset position and are not older than
     * {@code from}, ordered by {@code (blockTimestamp, logIndex, id)} descending.
     *
     * <p>The row comparison is an index condition of {@code idx_transactions_timestamp_log_id}: the backward scan
     * starts right at the position, so a page reads {@code limit} index entries however deep it is. The expanded
     * form {@code a < x OR (a = x AND ...)} would be a filter over every newer row instead.
     *
     * @param beforeTimestamp block timestamp of the exclusive upper bound position
     * @param beforeLogIndex  log index of that position
     * @param beforeId        row id of that position
     * @param from            inclusive lower bound of the block timestamp
     * @param limit           maximum number of rows
     * @return transfers, newest first
     */
    @Query(nativeQuery = true, value = """
            SELECT * FROM transactions
            WHERE (block_timestamp, log_index, id) < (:beforeTimestamp, :beforeLogIndex, :beforeId)
              AND block_timestamp >= :from
            ORDER BY block_timestamp DESC, log_index DESC, id DESC
            LIMIT :limit
            """)
    List<TransferEntity> findLatest(
            @Param("beforeTimestamp") Instant beforeTimestamp,
            @Param("beforeLogIndex") int beforeLogIndex,
            @Param("beforeId") long beforeId,
            @Param("from") Instant from,
            @Param("limit") int limit);

    /**
     * Returns all transfers of a transaction ordered by log index.
     *
     * @param txHash lower-case transaction hash
     * @return transfers of the transaction, empty if it is unknown
     */
    List<TransferEntity> findByTxHashOrderByLogIndexAsc(String txHash);

    /**
     * Aggregates the transfers of a token within {@code [from, to)}.
     *
     * @param contractAddress checksum address of the token contract
     * @param from            inclusive lower bound of the block timestamp
     * @param to              exclusive upper bound of the block timestamp
     * @return aggregated figures
     */
    @Query("""
            select new com.blockchainhandler.storage.model.DailyTransferAggregate(
                count(t), sum(t.valueUsdc), max(t.valueUsdc),
                count(distinct t.fromAddress), count(distinct t.toAddress),
                min(t.blockTimestamp), max(t.blockTimestamp))
            from TransferEntity t
            where t.contractAddress = :contractAddress
              and t.blockTimestamp >= :from and t.blockTimestamp < :to
            """)
    DailyTransferAggregate aggregate(
            @Param("contractAddress") String contractAddress,
            @Param("from") Instant from,
            @Param("to") Instant to);

    /**
     * Idempotently inserts a transfer. The row is skipped if it already exists or if the journal already holds
     * a reorg removal of the same log, so a delayed retry can never resurrect an orphaned transfer.
     *
     * @return number of inserted rows (0 or 1)
     */
    @Modifying
    @Transactional
    @Query(nativeQuery = true, value = """
            INSERT INTO transactions (tx_hash, log_index, block_number, block_hash, block_timestamp, contract_address,
                                      from_address, to_address, value_raw, value_usdc, created_at)
            SELECT :txHash, :logIndex, :blockNumber, :blockHash, :blockTimestamp, :contractAddress,
                   :fromAddress, :toAddress, :valueRaw, :valueUsdc, :createdAt
            WHERE NOT EXISTS (
                SELECT 1 FROM ethereum_events r
                WHERE r.block_hash = :blockHash
                  AND r.log_index = :logIndex
                  AND r.removed = TRUE
                  AND r.block_timestamp = :blockTimestamp)
            ON CONFLICT (block_hash, log_index) DO NOTHING
            """)
    int insertUnlessReverted(
            @Param("txHash") String txHash,
            @Param("logIndex") int logIndex,
            @Param("blockNumber") long blockNumber,
            @Param("blockHash") String blockHash,
            @Param("blockTimestamp") Instant blockTimestamp,
            @Param("contractAddress") String contractAddress,
            @Param("fromAddress") String fromAddress,
            @Param("toAddress") String toAddress,
            @Param("valueRaw") BigDecimal valueRaw,
            @Param("valueUsdc") BigDecimal valueUsdc,
            @Param("createdAt") Instant createdAt);

    /**
     * Deletes a transfer whose log was removed by a chain reorganization.
     *
     * @param blockHash hash of the orphaned block
     * @param logIndex  index of the log in that block
     * @return number of deleted rows
     */
    @Modifying
    @Transactional
    @Query(nativeQuery = true, value = "DELETE FROM transactions WHERE block_hash = :blockHash AND log_index = :logIndex")
    int deleteByBlockHashAndLogIndex(@Param("blockHash") String blockHash, @Param("logIndex") int logIndex);
}
