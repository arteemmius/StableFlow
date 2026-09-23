package com.blockchainhandler.processing.journal;

import com.blockchainhandler.storage.entity.EthereumEventEntity;
import java.time.Instant;

/**
 * Natural key of a journal entry. {@code (blockHash, logIndex)} identifies a log instance on-chain,
 * {@code removed} distinguishes its reorg removal, and {@code blockTimestamp} is the partition key.
 *
 * @param blockHash      hash of the block that contains the log
 * @param logIndex       position of the log in the block
 * @param removed        whether the entry records a reorg removal
 * @param blockTimestamp block timestamp (partition key)
 */
public record JournalKey(String blockHash, int logIndex, boolean removed, Instant blockTimestamp) {

    /**
     * Extracts the key of a journal entry.
     *
     * @param entity journal entry
     * @return its natural key
     */
    public static JournalKey of(EthereumEventEntity entity) {
        return new JournalKey(entity.getBlockHash(), entity.getLogIndex(), entity.isRemoved(), entity.getBlockTimestamp());
    }
}
