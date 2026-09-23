package com.blockchainhandler.processing.enrichment;

import com.blockchainhandler.common.messaging.EventSource;
import com.blockchainhandler.common.messaging.EventType;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.List;

/**
 * A decoded and enriched ERC-20 transfer, ready to be journaled.
 *
 * @param eventType        event type
 * @param chainId          EIP-155 chain id
 * @param contractAddress  checksum address of the token contract
 * @param transactionHash  lower-case transaction hash
 * @param transactionIndex position of the transaction in the block
 * @param blockHash        lower-case block hash
 * @param blockNumber      block number
 * @param blockTimestamp   block timestamp resolved from the node
 * @param logIndex         position of the log in the block
 * @param removed          {@code true} if the log was removed by a chain reorganization
 * @param fromAddress      checksum address of the sender
 * @param toAddress        checksum address of the receiver
 * @param rawValue         amount in the smallest token unit
 * @param value            amount in token units
 * @param decimals         decimals of the token
 * @param topics           raw log topics
 * @param data             raw log data
 * @param source           how the log was obtained
 * @param observedAt       when the ingestion layer received the log
 */
public record EnrichedTransfer(
        EventType eventType,
        long chainId,
        String contractAddress,
        String transactionHash,
        long transactionIndex,
        String blockHash,
        long blockNumber,
        Instant blockTimestamp,
        int logIndex,
        boolean removed,
        String fromAddress,
        String toAddress,
        BigInteger rawValue,
        BigDecimal value,
        int decimals,
        List<String> topics,
        String data,
        EventSource source,
        Instant observedAt) {
}
