package com.blockchainhandler.processing.enrichment;

import java.time.Instant;

/**
 * Resolves the timestamp of a block.
 */
public interface BlockTimestampProvider {

    /**
     * Returns the timestamp of the block with the given number.
     *
     * @param blockNumber block number
     * @return block timestamp
     * @throws com.blockchainhandler.processing.exception.BlockTimestampUnavailableException if the timestamp
     *         cannot be obtained right now
     */
    Instant getBlockTimestamp(long blockNumber);
}
