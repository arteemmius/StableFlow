package com.blockchainhandler.cache;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;

/**
 * Treats cache failures as cache misses: when Redis is unavailable the API keeps serving data from PostgreSQL
 * instead of failing with HTTP 500. Every failure is logged and counted in {@code cache.errors.total}.
 */
@Slf4j
@RequiredArgsConstructor
public class MeteredCacheErrorHandler implements CacheErrorHandler {

    private final MeterRegistry meterRegistry;

    /** {@inheritDoc} */
    @Override
    public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
        record("get", cache, exception);
    }

    /** {@inheritDoc} */
    @Override
    public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
        record("put", cache, exception);
    }

    /** {@inheritDoc} */
    @Override
    public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
        record("evict", cache, exception);
    }

    /** {@inheritDoc} */
    @Override
    public void handleCacheClearError(RuntimeException exception, Cache cache) {
        record("clear", cache, exception);
    }

    private void record(String operation, Cache cache, RuntimeException exception) {
        Counter.builder("cache.errors.total")
                .description("Failed cache operations that were bypassed")
                .tag("cache", cache.getName())
                .tag("operation", operation)
                .register(meterRegistry)
                .increment();
        log.warn("Cache {} on '{}' failed, falling back to the database: {}", operation, cache.getName(), exception.toString());
    }
}
