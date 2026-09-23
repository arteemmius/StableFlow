package com.blockchainhandler.ingestion;

import java.time.Instant;

/**
 * Snapshot of the ingestion state, exposed through the {@code ethereumNode} health indicator.
 *
 * @param state               subscription state
 * @param stateSince          when the current state was entered
 * @param consecutiveFailures failed connection attempts since the last successful subscription
 * @param lastError           description of the last connection failure, {@code null} if none
 * @param lastLogAt           when the last log was received, {@code null} if none yet
 * @param lastBlockNumber     block number of the last received log, {@code null} if none yet
 * @param resumeBlock         block a backfill would start from, {@code null} if nothing was published yet
 */
public record IngestionStatus(
        ConnectionState state,
        Instant stateSince,
        int consecutiveFailures,
        String lastError,
        Instant lastLogAt,
        Long lastBlockNumber,
        Long resumeBlock) {
}
