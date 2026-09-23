package com.blockchainhandler.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * A single USDC transfer.
 *
 * @param transactionHash hash of the transaction that emitted the transfer
 * @param logIndex        position of the log in the block
 * @param blockNumber     block number
 * @param blockHash       block hash
 * @param blockTimestamp  block timestamp (UTC)
 * @param tokenAddress    checksum address of the token contract
 * @param from            checksum address of the sender
 * @param to              checksum address of the receiver
 * @param value           amount in token units, serialized as a string to keep full precision
 * @param valueRaw        amount in the smallest token unit (uint256), as a decimal string
 */
@Schema(description = "USDC transfer")
public record TransferResponse(
        @Schema(example = "0x8452cb9ee82028e73b3d13d6c7816d5b1112661249ea6d55ead7d1d5cfcbd94c") String transactionHash,
        @Schema(example = "13") int logIndex,
        @Schema(example = "26039695") long blockNumber,
        @Schema(example = "0x9e7fb3a18772537a6beb9b017ca70c7693e6c2a79635762554fba65a007fc959") String blockHash,
        @Schema(example = "2026-09-23T10:50:35Z") Instant blockTimestamp,
        @Schema(example = "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48") String tokenAddress,
        String from,
        String to,
        @Schema(type = "string", example = "23.270000") @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal value,
        @Schema(example = "23270000") String valueRaw) {
}
