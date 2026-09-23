package com.blockchainhandler.common.messaging;

import com.blockchainhandler.common.ethereum.EthereumFormats;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Instant;
import java.util.List;

/**
 * A raw smart-contract log as published to the {@code ethereum-events} Kafka topic (message contract v1).
 *
 * <p>The ingestion layer ships logs undecoded, so the topic can be replayed with new decoders. The record key
 * is the transaction hash, which keeps all logs of a transaction (and their reorg removals) in one partition
 * and therefore in order.
 *
 * @param schemaVersion    version of this message contract, see {@link #CURRENT_SCHEMA_VERSION}
 * @param eventType        event type resolved from {@code topic0}
 * @param chainId          EIP-155 chain id of the network
 * @param contractAddress  lower-case address of the emitting contract
 * @param transactionHash  lower-case transaction hash
 * @param transactionIndex position of the transaction in the block
 * @param blockHash        lower-case hash of the block that contains the log
 * @param blockNumber      number of the block that contains the log
 * @param logIndex         position of the log in the block
 * @param topics           indexed log topics, {@code topics[0]} is the event signature hash
 * @param data             non-indexed log data
 * @param removed          {@code true} if the node reports that the log was removed by a chain reorganization
 * @param source           how the log was obtained
 * @param observedAt       when the service received the log from the node
 */
public record EthereumEventMessage(
        @NotNull @Positive Integer schemaVersion,
        @NotNull EventType eventType,
        @NotNull @Positive Long chainId,
        @NotNull @Pattern(regexp = EthereumFormats.ADDRESS_REGEX) String contractAddress,
        @NotNull @Pattern(regexp = EthereumFormats.HASH_REGEX) String transactionHash,
        @NotNull @PositiveOrZero Long transactionIndex,
        @NotNull @Pattern(regexp = EthereumFormats.HASH_REGEX) String blockHash,
        @NotNull @PositiveOrZero Long blockNumber,
        @NotNull @PositiveOrZero Integer logIndex,
        @NotEmpty List<@NotNull @Pattern(regexp = EthereumFormats.HASH_REGEX) String> topics,
        @NotNull @Pattern(regexp = EthereumFormats.HEX_DATA_REGEX) String data,
        boolean removed,
        @NotNull EventSource source,
        @NotNull Instant observedAt) {

    /** Version of the message contract produced by this build. */
    public static final int CURRENT_SCHEMA_VERSION = 1;
}
