package com.blockchainhandler.api.controller;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Fixed application clock for web layer tests: 2026-09-23T12:00:00Z.
 */
@TestConfiguration(proxyBeanMethods = false)
class FixedClockConfig {

    static final Instant NOW = Instant.parse("2026-09-23T12:00:00Z");

    @Bean
    Clock clock() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }
}
