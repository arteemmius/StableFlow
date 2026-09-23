package com.blockchainhandler.common.messaging;

/**
 * How an event was obtained from the Ethereum node.
 */
public enum EventSource {

    /** Pushed by the node through an {@code eth_subscribe("logs")} WebSocket subscription. */
    SUBSCRIPTION,

    /** Fetched with {@code eth_getLogs} to fill a gap after a reconnect or a publishing failure. */
    BACKFILL
}
