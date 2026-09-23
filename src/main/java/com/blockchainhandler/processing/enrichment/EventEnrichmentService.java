package com.blockchainhandler.processing.enrichment;

import com.blockchainhandler.common.ethereum.EthereumAddresses;
import com.blockchainhandler.common.messaging.EthereumEventMessage;
import com.blockchainhandler.config.properties.UsdcProperties;
import com.blockchainhandler.processing.decoder.DecodedTransfer;
import com.blockchainhandler.processing.exception.UnsupportedEventException;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Enriches decoded transfers: resolves the block timestamp, converts addresses to the EIP-55 checksum form
 * and converts the raw amount to token units.
 */
@Service
@RequiredArgsConstructor
public class EventEnrichmentService {

    private final BlockTimestampProvider blockTimestampProvider;
    private final UsdcProperties usdc;

    /**
     * Builds an {@link EnrichedTransfer} from a raw message and its decoded parameters.
     *
     * @param message  raw log
     * @param transfer decoded Transfer parameters
     * @return the enriched transfer
     * @throws UnsupportedEventException if the log was emitted by a contract that is not tracked
     * @throws com.blockchainhandler.processing.exception.BlockTimestampUnavailableException if the block
     *         timestamp cannot be obtained right now
     */
    public EnrichedTransfer enrich(EthereumEventMessage message, DecodedTransfer transfer) {
        if (!usdc.isUsdc(message.contractAddress())) {
            throw new UnsupportedEventException("Transfers of contract " + message.contractAddress() + " are not tracked");
        }
        Instant blockTimestamp = blockTimestampProvider.getBlockTimestamp(message.blockNumber());
        return new EnrichedTransfer(
                message.eventType(),
                message.chainId(),
                EthereumAddresses.toChecksum(message.contractAddress()),
                message.transactionHash(),
                message.transactionIndex(),
                message.blockHash(),
                message.blockNumber(),
                blockTimestamp,
                message.logIndex(),
                message.removed(),
                EthereumAddresses.toChecksum(transfer.from()),
                EthereumAddresses.toChecksum(transfer.to()),
                transfer.value(),
                TokenAmountConverter.toTokenUnits(transfer.value(), usdc.decimals()),
                usdc.decimals(),
                message.topics(),
                message.data(),
                message.source(),
                message.observedAt());
    }
}
