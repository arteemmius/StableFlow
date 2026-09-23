package com.blockchainhandler.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.blockchainhandler.api.dto.DailyStatsResponse;
import com.blockchainhandler.api.error.ResourceNotFoundException;
import com.blockchainhandler.api.service.StatsService;
import com.blockchainhandler.api.service.TransferQueryService;
import com.blockchainhandler.testsupport.UsdcTransfers;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {TransferController.class, StatsController.class})
@Import(FixedClockConfig.class)
class StatsControllerTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 23);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StatsService statsService;

    @MockitoBean
    private TransferQueryService transferQueryService;

    @Test
    @DisplayName("GET /stats/daily defaults to the current UTC day and normalizes the token address")
    void returnsStatsOfToday() throws Exception {
        given(statsService.getDailyStats(UsdcTransfers.USDC_CHECKSUM_ADDRESS, TODAY)).willReturn(sampleStats(TODAY));

        mockMvc.perform(get("/api/v1/stats/daily").param("token", UsdcTransfers.USDC_ADDRESS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value(UsdcTransfers.USDC_CHECKSUM_ADDRESS))
                .andExpect(jsonPath("$.date").value("2026-09-23"))
                .andExpect(jsonPath("$.transferCount").value(2))
                .andExpect(jsonPath("$.totalVolume").value("30.000000"))
                .andExpect(jsonPath("$.averageValue").value("15.000000"));
    }

    @Test
    @DisplayName("GET /stats/daily accepts an explicit day")
    void returnsStatsOfGivenDay() throws Exception {
        LocalDate day = LocalDate.of(2026, 1, 15);
        given(statsService.getDailyStats(eq(UsdcTransfers.USDC_CHECKSUM_ADDRESS), eq(day))).willReturn(sampleStats(day));

        mockMvc.perform(get("/api/v1/stats/daily").param("token", UsdcTransfers.USDC_ADDRESS).param("date", "2026-01-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value("2026-01-15"));
    }

    @Test
    @DisplayName("GET /stats/daily rejects a malformed token address or date")
    void rejectsMalformedParameters() throws Exception {
        mockMvc.perform(get("/api/v1/stats/daily").param("token", "usdc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("token"));
        mockMvc.perform(get("/api/v1/stats/daily").param("token", UsdcTransfers.USDC_ADDRESS).param("date", "23.09.2026"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(statsService);
    }

    @Test
    @DisplayName("GET /stats/daily returns 404 for a token that is not tracked")
    void returnsNotFoundForUntrackedToken() throws Exception {
        given(statsService.getDailyStats(any(), any())).willThrow(new ResourceNotFoundException("Token is not tracked"));

        mockMvc.perform(get("/api/v1/stats/daily").param("token", UsdcTransfers.randomAddress()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Token is not tracked"));
    }

    private static DailyStatsResponse sampleStats(LocalDate day) {
        return new DailyStatsResponse(UsdcTransfers.USDC_CHECKSUM_ADDRESS, day, 2, new BigDecimal("30.000000"),
                new BigDecimal("15.000000"), new BigDecimal("20.000000"), 2, 1, null, null);
    }
}
