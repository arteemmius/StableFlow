package com.blockchainhandler.api.controller;

import com.blockchainhandler.api.dto.DailyStatsResponse;
import com.blockchainhandler.api.service.StatsService;
import com.blockchainhandler.api.validation.EthereumAddress;
import com.blockchainhandler.common.ethereum.EthereumAddresses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API with aggregated token statistics.
 */
@RestController
@RequestMapping(path = "/api/v1/stats", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(name = "Statistics", description = "Aggregated token statistics")
public class StatsController {

    private final StatsService statsService;
    private final Clock clock;

    /**
     * Returns transfer statistics of a token for one UTC day.
     *
     * @param token token contract address
     * @param date  UTC day, today by default
     * @return daily statistics
     */
    @GetMapping("/daily")
    @Operation(summary = "Daily transfer statistics of a token (UTC day)")
    @ApiResponse(responseCode = "200", description = "Daily statistics")
    @ApiResponse(responseCode = "400", description = "Malformed token address or date")
    @ApiResponse(responseCode = "404", description = "The token is not tracked")
    public DailyStatsResponse getDailyStats(
            @Parameter(description = "Token contract address", example = "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48")
            @RequestParam @NotBlank @EthereumAddress String token,
            @Parameter(description = "UTC day (ISO-8601), today by default", example = "2026-09-23")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate day = date != null ? date : LocalDate.now(clock);
        return statsService.getDailyStats(EthereumAddresses.toChecksum(token), day);
    }
}
