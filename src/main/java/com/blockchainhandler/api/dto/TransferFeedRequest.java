package com.blockchainhandler.api.dto;

import com.blockchainhandler.api.validation.FeedCursor;
import com.blockchainhandler.api.validation.TimeRange;
import com.blockchainhandler.api.validation.ValidTimeRange;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;

/**
 * Query parameters of {@code GET /api/v1/transfers/latest}.
 *
 * @param from   inclusive lower bound of the block timestamp (ISO-8601), unbounded if absent
 * @param to     exclusive upper bound of the block timestamp (ISO-8601), unbounded if absent
 * @param size   page size, {@value TransferSearchRequest#DEFAULT_SIZE} by default, at most
 *               {@value TransferSearchRequest#MAX_SIZE}
 * @param cursor {@code nextCursor} of the previous page; absent or empty for the first page
 */
@ValidTimeRange
public record TransferFeedRequest(
        @Parameter(description = "Inclusive lower bound of the block timestamp, ISO-8601", example = "2026-09-01T00:00:00Z")
        Instant from,

        @Parameter(description = "Exclusive upper bound of the block timestamp, ISO-8601", example = "2026-10-01T00:00:00Z")
        Instant to,

        @Parameter(description = "Page size", example = "20")
        @Min(1) @Max(TransferSearchRequest.MAX_SIZE) Integer size,

        @Parameter(description = "nextCursor of the previous page; pass the same from/to along with it")
        @FeedCursor String cursor) implements TimeRange {

    /**
     * Applies the paging default and treats an empty cursor as the first page.
     */
    public TransferFeedRequest {
        size = size == null ? TransferSearchRequest.DEFAULT_SIZE : size;
        cursor = cursor == null || cursor.isEmpty() ? null : cursor;
    }
}
