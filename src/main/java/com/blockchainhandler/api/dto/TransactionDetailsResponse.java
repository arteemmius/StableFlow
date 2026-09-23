package com.blockchainhandler.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * A transaction with all USDC transfers it emitted.
 *
 * @param transactionHash transaction hash
 * @param blockNumber     block number
 * @param blockHash       block hash
 * @param blockTimestamp  block timestamp (UTC)
 * @param transferCount   number of USDC transfers in the transaction
 * @param totalValue      sum of the transferred amounts in token units
 * @param transfers       transfers ordered by log index
 */
@Schema(description = "Transaction with its USDC transfers")
public record TransactionDetailsResponse(
        String transactionHash,
        long blockNumber,
        String blockHash,
        Instant blockTimestamp,
        int transferCount,
        @Schema(type = "string", example = "23.270000") @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal totalValue,
        List<TransferResponse> transfers) {
}
