package com.blockchainhandler.ingestion.client;

import java.util.List;

/**
 * Log filter of a subscription or an {@code eth_getLogs} call.
 *
 * @param addresses contract addresses to watch
 * @param topics    positional topic filter; {@code topics[0]} is the event signature hash
 */
public record LogFilter(List<String> addresses, List<String> topics) {

    /**
     * Creates the filter with defensive copies.
     */
    public LogFilter {
        addresses = List.copyOf(addresses);
        topics = List.copyOf(topics);
    }
}
