package com.blockchainhandler.testsupport;

import com.blockchainhandler.common.messaging.EthereumEventMessage;
import com.blockchainhandler.common.messaging.EventSource;
import com.blockchainhandler.common.messaging.EventType;
import java.math.BigInteger;
import java.time.Instant;
import java.util.List;

/**
 * Fluent builder of USDC Transfer messages for tests. Topics and data are derived from {@code from}, {@code to}
 * and {@code value} unless overridden.
 */
public final class TransferMessageBuilder {

    private String contractAddress = UsdcTransfers.USDC_ADDRESS;
    private String transactionHash = UsdcTransfers.TX_HASH;
    private long transactionIndex = UsdcTransfers.TX_INDEX;
    private String blockHash = UsdcTransfers.BLOCK_HASH;
    private long blockNumber = UsdcTransfers.BLOCK_NUMBER;
    private int logIndex = UsdcTransfers.LOG_INDEX;
    private String from = UsdcTransfers.FROM;
    private String to = UsdcTransfers.TO;
    private BigInteger value = UsdcTransfers.RAW_VALUE;
    private boolean removed;
    private EventSource source = EventSource.SUBSCRIPTION;
    private Instant observedAt = Instant.now();
    private List<String> topics;
    private String data;

    /**
     * Sets the emitting contract.
     *
     * @param contractAddress contract address
     * @return this builder
     */
    public TransferMessageBuilder contractAddress(String contractAddress) {
        this.contractAddress = contractAddress;
        return this;
    }

    /**
     * Sets the transaction hash.
     *
     * @param transactionHash transaction hash
     * @return this builder
     */
    public TransferMessageBuilder transactionHash(String transactionHash) {
        this.transactionHash = transactionHash;
        return this;
    }

    /**
     * Sets the transaction index.
     *
     * @param transactionIndex transaction index
     * @return this builder
     */
    public TransferMessageBuilder transactionIndex(long transactionIndex) {
        this.transactionIndex = transactionIndex;
        return this;
    }

    /**
     * Sets the block hash.
     *
     * @param blockHash block hash
     * @return this builder
     */
    public TransferMessageBuilder blockHash(String blockHash) {
        this.blockHash = blockHash;
        return this;
    }

    /**
     * Sets the block number.
     *
     * @param blockNumber block number
     * @return this builder
     */
    public TransferMessageBuilder blockNumber(long blockNumber) {
        this.blockNumber = blockNumber;
        return this;
    }

    /**
     * Sets the log index.
     *
     * @param logIndex log index
     * @return this builder
     */
    public TransferMessageBuilder logIndex(int logIndex) {
        this.logIndex = logIndex;
        return this;
    }

    /**
     * Sets the sender.
     *
     * @param from sender address
     * @return this builder
     */
    public TransferMessageBuilder from(String from) {
        this.from = from;
        return this;
    }

    /**
     * Sets the receiver.
     *
     * @param to receiver address
     * @return this builder
     */
    public TransferMessageBuilder to(String to) {
        this.to = to;
        return this;
    }

    /**
     * Sets the raw amount.
     *
     * @param value amount in the smallest unit
     * @return this builder
     */
    public TransferMessageBuilder value(BigInteger value) {
        this.value = value;
        return this;
    }

    /**
     * Marks the log as removed by a chain reorganization.
     *
     * @param removed removal flag
     * @return this builder
     */
    public TransferMessageBuilder removed(boolean removed) {
        this.removed = removed;
        return this;
    }

    /**
     * Sets how the log was obtained.
     *
     * @param source event source
     * @return this builder
     */
    public TransferMessageBuilder source(EventSource source) {
        this.source = source;
        return this;
    }

    /**
     * Overrides the topics derived from the addresses.
     *
     * @param topics raw topics
     * @return this builder
     */
    public TransferMessageBuilder topics(List<String> topics) {
        this.topics = topics;
        return this;
    }

    /**
     * Overrides the data derived from the value.
     *
     * @param data raw data
     * @return this builder
     */
    public TransferMessageBuilder data(String data) {
        this.data = data;
        return this;
    }

    /**
     * Builds the message.
     *
     * @return Kafka message
     */
    public EthereumEventMessage build() {
        List<String> effectiveTopics = topics != null
                ? topics
                : List.of(UsdcTransfers.TRANSFER_TOPIC, UsdcTransfers.topic(from), UsdcTransfers.topic(to));
        String effectiveData = data != null ? data : UsdcTransfers.data(value);
        return new EthereumEventMessage(EthereumEventMessage.CURRENT_SCHEMA_VERSION, EventType.TRANSFER, 1L,
                contractAddress, transactionHash, transactionIndex, blockHash, blockNumber, logIndex,
                effectiveTopics, effectiveData, removed, source, observedAt);
    }
}
