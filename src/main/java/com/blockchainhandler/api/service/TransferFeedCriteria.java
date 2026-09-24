package com.blockchainhandler.api.service;

import com.blockchainhandler.api.dto.TransferFeedRequest;
import java.time.Instant;

/**
 * Normalized request of the feed of all transfers: explicit time bounds, with the upper bound and the cursor folded
 * into a single keyset position.
 *
 * @param from   inclusive lower bound of the block timestamp
 * @param before exclusive upper bound position: the cursor of the previous page, capped at {@code to}
 * @param size   page size
 */
public record TransferFeedCriteria(Instant from, TransferCursor before, int size) {

    /**
     * Normalizes a validated request. A single upper bound keeps the query to one index condition on the leading
     * column, so PostgreSQL always starts the scan at the right place.
     *
     * @param request validated request
     * @return feed criteria
     */
    public static TransferFeedCriteria of(TransferFeedRequest request) {
        TransferCursor upperBound = TransferCursor.before(
                request.to() != null ? request.to() : TransferSearchCriteria.UNBOUNDED_TO);
        TransferCursor before = upperBound;
        if (request.cursor() != null) {
            TransferCursor cursor = TransferCursor.decode(request.cursor());
            before = cursor.compareTo(upperBound) < 0 ? cursor : upperBound;
        }
        return new TransferFeedCriteria(
                request.from() != null ? request.from() : TransferSearchCriteria.UNBOUNDED_FROM,
                before,
                request.size());
    }
}
