package com.blockchainhandler.api.controller;

import com.blockchainhandler.api.dto.CursorPageResponse;
import com.blockchainhandler.api.dto.PageResponse;
import com.blockchainhandler.api.dto.TransactionDetailsResponse;
import com.blockchainhandler.api.dto.TransferFeedRequest;
import com.blockchainhandler.api.dto.TransferResponse;
import com.blockchainhandler.api.dto.TransferSearchRequest;
import com.blockchainhandler.api.service.TransferFeedCriteria;
import com.blockchainhandler.api.service.TransferQueryService;
import com.blockchainhandler.api.service.TransferSearchCriteria;
import com.blockchainhandler.api.validation.TransactionHash;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API over stored USDC transfers.
 */
@RestController
@RequestMapping(path = "/api/v1/transfers", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(name = "Transfers", description = "USDC transfers aggregated from Ethereum")
public class TransferController {

    private final TransferQueryService queryService;

    /**
     * Lists transfers where the address is the sender or the receiver, newest first.
     *
     * @param request validated query parameters
     * @return page of transfers
     */
    @GetMapping
    @Operation(summary = "List transfers of an address (sender or receiver), newest first")
    @ApiResponse(responseCode = "200", description = "Page of transfers")
    @ApiResponse(responseCode = "400", description = "Invalid address, time range or paging parameters")
    public PageResponse<TransferResponse> findTransfers(@Valid @ParameterObject TransferSearchRequest request) {
        return queryService.findTransfers(TransferSearchCriteria.of(request));
    }

    /**
     * Lists the latest transfers of all addresses, newest first, one keyset page at a time.
     *
     * @param request validated query parameters
     * @return page of transfers with the cursor of the next page
     */
    @GetMapping("/latest")
    @Operation(summary = "List the latest transfers of all addresses, newest first",
            description = "Keyset pagination: pass nextCursor of a page as the cursor parameter, with the same from/to, "
                    + "to get the next one. Every page costs the same however deep it is, which suits exports.")
    @ApiResponse(responseCode = "200", description = "Page of transfers with the cursor of the next page")
    @ApiResponse(responseCode = "400", description = "Invalid time range, page size or cursor")
    public CursorPageResponse<TransferResponse> findLatestTransfers(@Valid @ParameterObject TransferFeedRequest request) {
        return queryService.findLatest(TransferFeedCriteria.of(request));
    }

    /**
     * Returns a transaction with all its USDC transfers.
     *
     * @param txHash transaction hash
     * @return transaction details
     */
    @GetMapping("/{txHash}")
    @Operation(summary = "Get a transaction with all its USDC transfers")
    @ApiResponse(responseCode = "200", description = "Transaction details")
    @ApiResponse(responseCode = "400", description = "Malformed transaction hash")
    @ApiResponse(responseCode = "404", description = "No USDC transfers of the transaction are stored")
    public TransactionDetailsResponse getTransaction(
            @Parameter(description = "Transaction hash",
                    example = "0x8452cb9ee82028e73b3d13d6c7816d5b1112661249ea6d55ead7d1d5cfcbd94c")
            @PathVariable @TransactionHash String txHash) {
        return queryService.getTransaction(txHash.toLowerCase(Locale.ROOT));
    }
}
