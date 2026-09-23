package com.blockchainhandler.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI metadata rendered by springdoc at {@code /swagger-ui.html}.
 */
@Configuration(proxyBeanMethods = false)
@OpenAPIDefinition(info = @Info(
        title = "Blockchain Handler API",
        version = "v1",
        description = "USDC Transfer events aggregated from Ethereum mainnet: transfers by address, "
                + "transaction details and daily statistics."))
public class OpenApiConfig {
}
