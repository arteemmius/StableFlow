package com.blockchainhandler.ingestion;

/**
 * State of the WebSocket log subscription.
 */
public enum ConnectionState {

    /** Ingestion is not running. */
    STOPPED,

    /** Connecting to the node and subscribing to logs. */
    CONNECTING,

    /** The log subscription is active. */
    SUBSCRIBED,

    /** The connection was lost; waiting for the next reconnect attempt. */
    BACKING_OFF
}
