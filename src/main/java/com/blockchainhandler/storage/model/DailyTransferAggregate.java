package com.blockchainhandler.storage.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Aggregated transfer figures of a token for a time window, produced by a JPQL constructor expression.
 *
 * <p>Aggregates over an empty window yield {@code null} sums, extremes and timestamps.
 *
 * @param transferCount   number of transfers
 * @param totalVolume     sum of transferred amounts, {@code null} when there are no transfers
 * @param maxValue        largest single transfer, {@code null} when there are no transfers
 * @param uniqueSenders   number of distinct senders
 * @param uniqueReceivers number of distinct receivers
 * @param firstTransferAt block timestamp of the earliest transfer, {@code null} when there are no transfers
 * @param lastTransferAt  block timestamp of the latest transfer, {@code null} when there are no transfers
 */
public record DailyTransferAggregate(
        Long transferCount,
        BigDecimal totalVolume,
        BigDecimal maxValue,
        Long uniqueSenders,
        Long uniqueReceivers,
        Instant firstTransferAt,
        Instant lastTransferAt) {
}
