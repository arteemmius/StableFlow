package com.blockchainhandler.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * A page of results fetched with keyset pagination. Instead of a page number and totals it carries a cursor to the
 * next page, so every page costs the same however deep it is.
 *
 * @param content    items of the page
 * @param size       requested page size
 * @param hasNext    whether a next page exists
 * @param nextCursor cursor of the next page, {@code null} on the last page
 * @param <T>        item type
 */
@Schema(description = "Page of results with a cursor to the next page")
public record CursorPageResponse<T>(
        List<T> content,
        int size,
        boolean hasNext,
        @Schema(description = "Pass as the cursor parameter to get the next page; null on the last page",
                example = "MjAyNi0wOS0yM1QxMDo1MDozNVp8MTN8OTg3", nullable = true)
        String nextCursor) {
}
