package com.blockchainhandler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point of the Blockchain Handler service.
 *
 * <p>The service ingests ERC-20 {@code Transfer} events of the USDC contract from an Ethereum node over
 * WebSocket, streams them through Kafka, enriches and stores them in PostgreSQL and exposes them through a
 * cached REST API.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class BlockchainHandlerApplication {

    /**
     * Starts the application.
     *
     * @param args command line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(BlockchainHandlerApplication.class, args);
    }
}
