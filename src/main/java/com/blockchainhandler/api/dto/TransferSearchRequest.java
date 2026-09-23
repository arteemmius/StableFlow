package com.blockchainhandler.api.dto;

import com.blockchainhandler.api.validation.EthereumAddress;
import com.blockchainhandler.api.validation.TimeRange;
import com.blockchainhandler.api.validation.ValidTimeRange;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;

/**
 * Query parameters of {@code GET /api/v1/transfers}.
 *
 * @param address address of the sender or the receiver
 * @param from    inclusive lower bound of the block timestamp (ISO-8601), unbounded if absent
 * @param to      exclusive upper bound of the block timestamp (ISO-8601), unbounded if absent
 * @param page    zero-based page number, {@value #DEFAULT_PAGE} by default
 * @param size    page size, {@value #DEFAULT_SIZE} by default, at most {@value #MAX_SIZE}
 */
@ValidTimeRange
public record TransferSearchRequest(
        @Parameter(description = "Address of the sender or the receiver", required = true,
                example = "0x28C6c06298d514Db089934071355E5743bf21d60")
        @NotBlank @EthereumAddress String address,

        @Parameter(description = "Inclusive lower bound of the block timestamp, ISO-8601", example = "2026-09-01T00:00:00Z")
        Instant from,

        @Parameter(description = "Exclusive upper bound of the block timestamp, ISO-8601", example = "2026-10-01T00:00:00Z")
        Instant to,

        @Parameter(description = "Zero-based page number", example = "0")
        @Min(0) Integer page,

        @Parameter(description = "Page size", example = "20")
        @Min(1) @Max(MAX_SIZE) Integer size) implements TimeRange {

    /** Default page number. */
    public static final int DEFAULT_PAGE = 0;
    /** Default page size. */
    public static final int DEFAULT_SIZE = 20;
    /** Maximum page size. */
    public static final int MAX_SIZE = 100;

    /**
     * Applies the paging defaults.
     */
    public TransferSearchRequest {
        page = page == null ? DEFAULT_PAGE : page;
        size = size == null ? DEFAULT_SIZE : size;
    }
}
