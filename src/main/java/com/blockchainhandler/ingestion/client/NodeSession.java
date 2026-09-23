package com.blockchainhandler.ingestion.client;

import io.reactivex.Flowable;
import java.io.IOException;
import java.util.List;

/**
 * An open connection to an Ethereum node.
 */
public interface NodeSession extends AutoCloseable {

    /**
     * Subscribes to new logs with {@code eth_subscribe("logs")}. The flowable fails when the connection breaks.
     *
     * @param filter log filter
     * @return stream of logs, including reorg removals
     */
    Flowable<RpcLog> subscribeLogs(LogFilter filter);

    /**
     * Returns the number of the latest block.
     *
     * @return latest block number
     * @throws IOException if the call fails
     */
    long latestBlockNumber() throws IOException;

    /**
     * Fetches historical logs with {@code eth_getLogs}.
     *
     * @param filter    log filter
     * @param fromBlock first block, inclusive
     * @param toBlock   last block, inclusive
     * @return logs of the range in chain order
     * @throws IOException if the call fails
     */
    List<RpcLog> getLogs(LogFilter filter, long fromBlock, long toBlock) throws IOException;

    /**
     * Closes the connection. Never throws.
     */
    @Override
    void close();
}
