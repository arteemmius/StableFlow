package com.blockchainhandler.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Invalidates API caches when the Kafka consumer (or the retry worker) changes the read model.
 *
 * <p>Invalidation runs only after the projection transaction has committed; invalidating earlier would let a
 * concurrent request re-cache the old state. Daily statistics are deliberately not invalidated: the key of the
 * current day would be evicted several times per second, so they rely on their short TTL instead.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheInvalidationListener {

    private final CacheGenerationService generations;
    private final CacheManager cacheManager;

    /**
     * Bumps the cache generations of the affected addresses and evicts the transaction details.
     *
     * @param event change notification published by the projection
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTransfersChanged(TransfersChangedEvent event) {
        try {
            generations.bump(event.addresses());
            Cache transactionDetails = cacheManager.getCache(CacheNames.TRANSACTION_DETAILS);
            if (transactionDetails != null) {
                transactionDetails.evict(event.transactionHash());
            }
        } catch (RuntimeException e) {
            // Stale entries still expire with their TTL; a cache outage must not fail event processing.
            log.warn("Cache invalidation for tx {} failed: {}", event.transactionHash(), e.toString());
        }
    }
}
