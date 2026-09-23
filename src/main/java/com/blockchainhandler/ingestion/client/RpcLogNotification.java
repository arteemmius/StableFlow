package com.blockchainhandler.ingestion.client;

import org.web3j.protocol.websocket.events.Notification;

/**
 * {@code eth_subscription} notification of a {@code logs} subscription, deserialized with the
 * {@code removed} flag preserved.
 */
public class RpcLogNotification extends Notification<RpcLog> {
}
