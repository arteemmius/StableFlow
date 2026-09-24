package com.blockchainhandler.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.blockchainhandler.api.dto.CursorPageResponse;
import com.blockchainhandler.api.dto.PageResponse;
import com.blockchainhandler.api.dto.TransactionDetailsResponse;
import com.blockchainhandler.api.dto.TransferResponse;
import com.blockchainhandler.api.error.ResourceNotFoundException;
import com.blockchainhandler.api.service.StatsService;
import com.blockchainhandler.api.service.TransferCursor;
import com.blockchainhandler.api.service.TransferFeedCriteria;
import com.blockchainhandler.api.service.TransferQueryService;
import com.blockchainhandler.api.service.TransferSearchCriteria;
import com.blockchainhandler.testsupport.UsdcTransfers;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {TransferController.class, StatsController.class})
@Import(FixedClockConfig.class)
class TransferControllerTest {

    private static final String ADDRESS = "0x28C6c06298d514Db089934071355E5743bf21d60";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TransferQueryService transferQueryService;

    @MockitoBean
    private StatsService statsService;

    @Test
    @DisplayName("GET /transfers returns a page and normalizes the query")
    void findsTransfersOfAddress() throws Exception {
        given(transferQueryService.findTransfers(any()))
                .willReturn(new PageResponse<>(List.of(sampleTransfer()), 0, 20, 1, 1, false));

        mockMvc.perform(get("/api/v1/transfers").param("address", ADDRESS.toLowerCase(Locale.ROOT)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].transactionHash").value(UsdcTransfers.TX_HASH))
                .andExpect(jsonPath("$.content[0].value").value("23.270000"))
                .andExpect(jsonPath("$.content[0].valueRaw").value("23270000"))
                .andExpect(jsonPath("$.content[0].blockTimestamp").value("2026-09-23T10:50:35Z"));

        ArgumentCaptor<TransferSearchCriteria> criteria = ArgumentCaptor.forClass(TransferSearchCriteria.class);
        verify(transferQueryService).findTransfers(criteria.capture());
        assertThat(criteria.getValue()).isEqualTo(new TransferSearchCriteria(
                ADDRESS, TransferSearchCriteria.UNBOUNDED_FROM, TransferSearchCriteria.UNBOUNDED_TO, 0, 20));
    }

    @Test
    @DisplayName("GET /transfers passes the time range and paging through")
    void passesTimeRangeAndPaging() throws Exception {
        given(transferQueryService.findTransfers(any())).willReturn(new PageResponse<>(List.of(), 2, 50, 0, 0, false));

        mockMvc.perform(get("/api/v1/transfers")
                        .param("address", ADDRESS)
                        .param("from", "2026-09-01T00:00:00Z")
                        .param("to", "2026-10-01T00:00:00Z")
                        .param("page", "2")
                        .param("size", "50"))
                .andExpect(status().isOk());

        verify(transferQueryService).findTransfers(new TransferSearchCriteria(
                ADDRESS, Instant.parse("2026-09-01T00:00:00Z"), Instant.parse("2026-10-01T00:00:00Z"), 2, 50));
    }

    @Test
    @DisplayName("GET /transfers rejects an address with a wrong checksum")
    void rejectsAddressWithWrongChecksum() throws Exception {
        mockMvc.perform(get("/api/v1/transfers").param("address", "0x28c6C06298d514Db089934071355E5743bf21d60"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Request validation failed"))
                .andExpect(jsonPath("$.errors[0].field").value("address"));
        verifyNoInteractions(transferQueryService);
    }

    @Test
    @DisplayName("GET /transfers rejects a missing address, a too large page and an inverted time range")
    void rejectsInvalidQueries() throws Exception {
        mockMvc.perform(get("/api/v1/transfers"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("address"));
        mockMvc.perform(get("/api/v1/transfers").param("address", ADDRESS).param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("size"));
        mockMvc.perform(get("/api/v1/transfers").param("address", ADDRESS)
                        .param("from", "2026-10-01T00:00:00Z").param("to", "2026-09-01T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("from"));
        verifyNoInteractions(transferQueryService);
    }

    @Test
    @DisplayName("GET /transfers/latest returns a cursor page of all addresses and normalizes the query")
    void findsLatestTransfers() throws Exception {
        String nextCursor = new TransferCursor(UsdcTransfers.BLOCK_TIMESTAMP, UsdcTransfers.LOG_INDEX, 987).encode();
        given(transferQueryService.findLatest(any()))
                .willReturn(new CursorPageResponse<>(List.of(sampleTransfer()), 20, true, nextCursor));

        mockMvc.perform(get("/api/v1/transfers/latest"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content[0].transactionHash").value(UsdcTransfers.TX_HASH))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.nextCursor").value(nextCursor));

        verify(transferQueryService).findLatest(new TransferFeedCriteria(
                TransferSearchCriteria.UNBOUNDED_FROM, TransferCursor.before(TransferSearchCriteria.UNBOUNDED_TO), 20));
    }

    @Test
    @DisplayName("GET /transfers/latest passes the time range, the page size and the cursor through")
    void passesFeedParameters() throws Exception {
        given(transferQueryService.findLatest(any())).willReturn(new CursorPageResponse<>(List.of(), 50, false, null));
        TransferCursor cursor = new TransferCursor(Instant.parse("2026-09-15T08:00:00Z"), 7, 42);

        mockMvc.perform(get("/api/v1/transfers/latest")
                        .param("from", "2026-09-01T00:00:00Z")
                        .param("to", "2026-10-01T00:00:00Z")
                        .param("size", "50")
                        .param("cursor", cursor.encode()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.nextCursor").value(nullValue()));

        verify(transferQueryService).findLatest(new TransferFeedCriteria(Instant.parse("2026-09-01T00:00:00Z"), cursor, 50));
    }

    @Test
    @DisplayName("GET /transfers/latest rejects a forged cursor, a too large page and an inverted time range")
    void rejectsInvalidFeedQueries() throws Exception {
        mockMvc.perform(get("/api/v1/transfers/latest").param("cursor", "not-a-cursor"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[0].field").value("cursor"));
        mockMvc.perform(get("/api/v1/transfers/latest").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("size"));
        mockMvc.perform(get("/api/v1/transfers/latest")
                        .param("from", "2026-10-01T00:00:00Z").param("to", "2026-09-01T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("from"));
        verifyNoInteractions(transferQueryService);
    }

    @Test
    @DisplayName("GET /transfers/{txHash} returns the transaction details")
    void returnsTransactionDetails() throws Exception {
        given(transferQueryService.getTransaction(UsdcTransfers.TX_HASH)).willReturn(new TransactionDetailsResponse(
                UsdcTransfers.TX_HASH, UsdcTransfers.BLOCK_NUMBER, UsdcTransfers.BLOCK_HASH, UsdcTransfers.BLOCK_TIMESTAMP,
                1, new BigDecimal("23.270000"), List.of(sampleTransfer())));

        mockMvc.perform(get("/api/v1/transfers/{txHash}", "0x" + UsdcTransfers.TX_HASH.substring(2).toUpperCase(Locale.ROOT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transferCount").value(1))
                .andExpect(jsonPath("$.totalValue").value("23.270000"))
                .andExpect(jsonPath("$.transfers[0].logIndex").value(UsdcTransfers.LOG_INDEX));
    }

    @Test
    @DisplayName("GET /transfers/{txHash} returns 404 problem details for an unknown transaction")
    void returnsNotFoundForUnknownTransaction() throws Exception {
        given(transferQueryService.getTransaction(any())).willThrow(new ResourceNotFoundException("not found"));

        mockMvc.perform(get("/api/v1/transfers/{txHash}", UsdcTransfers.randomHash()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Not Found"));
    }

    @Test
    @DisplayName("GET /transfers/{txHash} rejects a malformed hash")
    void rejectsMalformedTransactionHash() throws Exception {
        mockMvc.perform(get("/api/v1/transfers/{txHash}", "0x1234"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("txHash"));
        verifyNoInteractions(transferQueryService);
    }

    @Test
    @DisplayName("unexpected errors become 500 problem details without internals")
    void hidesUnexpectedErrors() throws Exception {
        given(transferQueryService.getTransaction(any())).willThrow(new IllegalStateException("database password is 42"));

        mockMvc.perform(get("/api/v1/transfers/{txHash}", UsdcTransfers.randomHash()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value(not(containsString("password"))));
    }

    private static TransferResponse sampleTransfer() {
        return new TransferResponse(UsdcTransfers.TX_HASH, UsdcTransfers.LOG_INDEX, UsdcTransfers.BLOCK_NUMBER,
                UsdcTransfers.BLOCK_HASH, UsdcTransfers.BLOCK_TIMESTAMP, UsdcTransfers.USDC_CHECKSUM_ADDRESS,
                ADDRESS, "0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045", new BigDecimal("23.270000"), "23270000");
    }
}
