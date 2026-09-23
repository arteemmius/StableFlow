package com.blockchainhandler.api.service;

import com.blockchainhandler.api.dto.TransferSearchRequest;
import com.blockchainhandler.cache.AddressScopedQuery;
import com.blockchainhandler.common.ethereum.EthereumAddresses;
import java.time.Instant;

/**
 * Normalized transfer search: checksum address and explicit time bounds, which makes it a stable cache key.
 *
 * @param address checksum address of the sender or the receiver
 * @param from    inclusive lower bound of the block timestamp
 * @param to      exclusive upper bound of the block timestamp
 * @param page    zero-based page number
 * @param size    page size
 */
public record TransferSearchCriteria(String address, Instant from, Instant to, int page, int size)
        implements AddressScopedQuery {

    /** Lower bound used when the request has none. */
    public static final Instant UNBOUNDED_FROM = Instant.EPOCH;
    /** Upper bound used when the request has none. */
    public static final Instant UNBOUNDED_TO = Instant.parse("9999-12-31T23:59:59Z");

    /**
     * Normalizes a validated request.
     *
     * @param request validated request
     * @return search criteria
     */
    public static TransferSearchCriteria of(TransferSearchRequest request) {
        return new TransferSearchCriteria(
                EthereumAddresses.toChecksum(request.address()),
                request.from() != null ? request.from() : UNBOUNDED_FROM,
                request.to() != null ? request.to() : UNBOUNDED_TO,
                request.page(),
                request.size());
    }

    /** {@inheritDoc} */
    @Override
    public String cacheKeySuffix() {
        return from + "|" + to + "|" + page + "|" + size;
    }
}
