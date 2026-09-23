package com.blockchainhandler.processing.journal;

import com.blockchainhandler.common.messaging.EventSource;
import com.blockchainhandler.processing.enrichment.EnrichedTransfer;
import java.time.Instant;
import java.util.List;

/**
 * JSON document stored in {@code ethereum_events.payload} for Transfer events. Amounts are strings so that
 * uint256 values survive any JSON tooling without precision loss.
 *
 * @param schemaVersion    version of this payload layout
 * @param chainId          EIP-155 chain id
 * @param transactionIndex position of the transaction in the block
 * @param from             checksum address of the sender
 * @param to               checksum address of the receiver
 * @param valueRaw         amount in the smallest token unit
 * @param value            amount in token units
 * @param decimals         decimals of the token
 * @param topics           raw log topics
 * @param data             raw log data
 * @param source           how the log was obtained
 * @param observedAt       when the ingestion layer received the log
 */
public record TransferPayload(
        int schemaVersion,
        long chainId,
        long transactionIndex,
        String from,
        String to,
        String valueRaw,
        String value,
        int decimals,
        List<String> topics,
        String data,
        EventSource source,
        Instant observedAt) {

    /** Current payload layout version. */
    public static final int CURRENT_SCHEMA_VERSION = 1;

    /**
     * Builds the payload of an enriched transfer.
     *
     * @param transfer enriched transfer
     * @return payload document
     */
    public static TransferPayload of(EnrichedTransfer transfer) {
        return new TransferPayload(
                CURRENT_SCHEMA_VERSION,
                transfer.chainId(),
                transfer.transactionIndex(),
                transfer.fromAddress(),
                transfer.toAddress(),
                transfer.rawValue().toString(),
                transfer.value().toPlainString(),
                transfer.decimals(),
                transfer.topics(),
                transfer.data(),
                transfer.source(),
                transfer.observedAt());
    }
}
