package com.blockchainhandler.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.data.domain.Page;

/**
 * A page of results. Unlike Spring's {@link Page} it has a stable JSON shape and can be cached.
 *
 * @param content       items of the page
 * @param page          zero-based page number
 * @param size          requested page size
 * @param totalElements total number of items
 * @param totalPages    total number of pages
 * @param hasNext       whether a next page exists
 * @param <T>           item type
 */
@Schema(description = "Page of results")
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext) {

    /**
     * Copies a Spring Data page.
     *
     * @param page source page
     * @param <T>  item type
     * @return the page response
     */
    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(
                List.copyOf(page.getContent()),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasNext());
    }
}
