package com.blockchainhandler.ingestion.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import org.web3j.protocol.core.methods.response.Log;

/**
 * A log exactly as returned by the JSON-RPC API (quantities are hex strings).
 *
 * <p>web3j's own WebSocket {@code Log} type drops the {@code removed} flag that nodes set for logs orphaned by
 * a chain reorganization, so subscriptions are deserialized into this type instead.
 *
 * @param address          emitting contract
 * @param blockHash        block hash
 * @param blockNumber      block number (hex quantity)
 * @param data             non-indexed data
 * @param logIndex         position of the log in the block (hex quantity)
 * @param topics           indexed topics
 * @param transactionHash  transaction hash
 * @param transactionIndex position of the transaction in the block (hex quantity)
 * @param removed          {@code true} if the log was removed by a chain reorganization
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RpcLog(
        String address,
        String blockHash,
        String blockNumber,
        String data,
        String logIndex,
        List<String> topics,
        String transactionHash,
        String transactionIndex,
        boolean removed) {

    /**
     * Converts a log returned by {@code eth_getLogs}.
     *
     * @param log web3j log
     * @return the RPC log
     */
    public static RpcLog from(Log log) {
        return new RpcLog(
                log.getAddress(),
                log.getBlockHash(),
                log.getBlockNumberRaw(),
                log.getData(),
                log.getLogIndexRaw(),
                log.getTopics(),
                log.getTransactionHash(),
                log.getTransactionIndexRaw(),
                log.isRemoved());
    }
}
