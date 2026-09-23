package com.blockchainhandler.cache;

/**
 * Names of the Redis caches used by the service.
 */
public final class CacheNames {

    /** Paginated transfer lists of an address; invalidated through per-address generations. */
    public static final String TRANSFERS_BY_ADDRESS = "transfers-by-address";

    /** Transaction details by transaction hash; evicted when a transfer of the transaction changes. */
    public static final String TRANSACTION_DETAILS = "transaction-details";

    /** Daily token statistics; TTL only, the current-day key changes with every transfer. */
    public static final String DAILY_STATS = "daily-stats";

    /** Block number to block timestamp, so the node is asked once per block instead of once per event. */
    public static final String BLOCK_TIMESTAMPS = "block-timestamps";

    private CacheNames() {
    }
}
