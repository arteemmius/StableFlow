package com.blockchainhandler.cache;

import java.util.Set;

/**
 * Application event published inside the projection transaction when the {@code transactions} read model
 * changed. Cache invalidation reacts to it only after the transaction has committed.
 *
 * @param addresses       checksum addresses whose transfer lists changed
 * @param transactionHash hash of the transaction whose details changed
 */
public record TransfersChangedEvent(Set<String> addresses, String transactionHash) {
}
