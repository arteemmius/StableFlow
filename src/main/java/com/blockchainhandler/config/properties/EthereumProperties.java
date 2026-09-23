package com.blockchainhandler.config.properties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Connection settings of the Ethereum node ({@code app.ethereum.*}).
 *
 * @param wsUrl              WebSocket JSON-RPC endpoint used for log subscriptions
 * @param httpUrl            HTTP JSON-RPC endpoint used for request/response calls (block timestamps)
 * @param chainId            EIP-155 chain id of the network (1 for Ethereum mainnet)
 * @param httpConnectTimeout TCP connect timeout of the HTTP client
 * @param httpReadTimeout    read timeout of the HTTP client
 */
@Validated
@ConfigurationProperties(prefix = "app.ethereum")
public record EthereumProperties(
        @NotBlank String wsUrl,
        @NotBlank String httpUrl,
        @Positive long chainId,
        @NotNull Duration httpConnectTimeout,
        @NotNull Duration httpReadTimeout) {
}
