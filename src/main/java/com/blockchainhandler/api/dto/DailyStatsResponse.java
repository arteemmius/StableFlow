package com.blockchainhandler.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Aggregated transfer statistics of a token for one UTC day.
 *
 * @param token           checksum address of the token contract
 * @param date            UTC day
 * @param transferCount   number of transfers
 * @param totalVolume     sum of the transferred amounts in token units
 * @param averageValue    average transfer amount in token units
 * @param maxValue        largest single transfer in token units
 * @param uniqueSenders   number of distinct senders
 * @param uniqueReceivers number of distinct receivers
 * @param firstTransferAt block timestamp of the first transfer of the day, {@code null} if there was none
 * @param lastTransferAt  block timestamp of the last transfer of the day, {@code null} if there was none
 */
@Schema(description = "Daily token statistics (UTC day)")
public record DailyStatsResponse(
        @Schema(example = "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48") String token,
        @Schema(example = "2026-09-23") LocalDate date,
        long transferCount,
        @Schema(type = "string") @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal totalVolume,
        @Schema(type = "string") @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal averageValue,
        @Schema(type = "string") @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal maxValue,
        long uniqueSenders,
        long uniqueReceivers,
        Instant firstTransferAt,
        Instant lastTransferAt) {
}
