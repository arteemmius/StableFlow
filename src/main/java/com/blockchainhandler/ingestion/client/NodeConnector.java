package com.blockchainhandler.ingestion.client;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * Opens connections to an Ethereum node. Abstracts the transport so that the reconnect logic of
 * {@link com.blockchainhandler.ingestion.EthereumLogSubscriber} can be tested without a node.
 */
public interface NodeConnector {

    /**
     * Opens a new connection.
     *
     * @param onDisconnect invoked, possibly several times and from any thread, when the transport fails or closes
     * @return the open session
     * @throws IOException if the connection cannot be established
     */
    NodeSession connect(Consumer<Throwable> onDisconnect) throws IOException;

    /**
     * Describes the node for logs and health checks without exposing credentials.
     *
     * @return safe description of the node
     */
    String describe();
}
