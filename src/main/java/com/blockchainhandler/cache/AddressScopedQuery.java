package com.blockchainhandler.cache;

/**
 * A cacheable query whose results belong to a single address, e.g. "transfers of address X, page N".
 *
 * <p>Results of such queries are invalidated all at once by bumping the cache generation of the address.
 */
public interface AddressScopedQuery {

    /**
     * Returns the address that owns the query results.
     *
     * @return checksum address
     */
    String address();

    /**
     * Returns the remaining query parameters that distinguish cached results of the same address.
     *
     * @return stable textual representation of the parameters
     */
    String cacheKeySuffix();
}
