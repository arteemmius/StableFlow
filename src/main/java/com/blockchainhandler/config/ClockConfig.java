package com.blockchainhandler.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides the application clock so that time-dependent logic can be tested with a fixed clock.
 */
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    /**
     * UTC system clock used by all components instead of {@code Instant.now()}.
     *
     * @return the application clock
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
