package com.blockchainhandler.cache;

import com.blockchainhandler.api.dto.DailyStatsResponse;
import com.blockchainhandler.api.dto.PageResponse;
import com.blockchainhandler.api.dto.TransactionDetailsResponse;
import com.blockchainhandler.api.dto.TransferResponse;
import com.blockchainhandler.config.properties.AppCacheProperties;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

/**
 * Redis cache configuration.
 *
 * <p>Every cache gets its own TTL and a typed JSON serializer (no polymorphic type information in the stored
 * values, so cached data cannot be used to instantiate arbitrary classes). Unknown cache names fail fast
 * instead of silently creating caches with default settings.
 */
@Configuration(proxyBeanMethods = false)
@EnableCaching
public class CacheConfig {

    /**
     * Declares the caches with their TTLs and serializers.
     *
     * @param properties   cache settings
     * @param objectMapper application ObjectMapper (Java time support, ISO dates)
     * @return customizer of the auto-configured {@code RedisCacheManager}
     */
    @Bean
    public RedisCacheManagerBuilderCustomizer redisCacheManagerBuilderCustomizer(
            AppCacheProperties properties, ObjectMapper objectMapper) {
        ObjectMapper mapper = objectMapper.copy();
        TypeFactory types = mapper.getTypeFactory();
        AppCacheProperties.Ttl ttl = properties.ttl();
        return builder -> builder
                .disableCreateOnMissingCache()
                .withCacheConfiguration(CacheNames.TRANSFERS_BY_ADDRESS, cacheConfiguration(properties, ttl.transfers(),
                        types.constructParametricType(PageResponse.class, TransferResponse.class), mapper))
                .withCacheConfiguration(CacheNames.TRANSACTION_DETAILS, cacheConfiguration(properties,
                        ttl.transactionDetails(), types.constructType(TransactionDetailsResponse.class), mapper))
                .withCacheConfiguration(CacheNames.DAILY_STATS, cacheConfiguration(properties, ttl.dailyStats(),
                        types.constructType(DailyStatsResponse.class), mapper))
                .withCacheConfiguration(CacheNames.BLOCK_TIMESTAMPS, cacheConfiguration(properties, ttl.blockTimestamps(),
                        types.constructType(Instant.class), mapper));
    }

    /**
     * Makes cache failures degrade to cache misses instead of failing requests.
     *
     * @param meterRegistry registry for the cache error counter
     * @return caching configurer that only customizes error handling
     */
    @Bean
    public CachingConfigurer cachingConfigurer(MeterRegistry meterRegistry) {
        CacheErrorHandler errorHandler = new MeteredCacheErrorHandler(meterRegistry);
        return new CachingConfigurer() {
            /** {@inheritDoc} */
            @Override
            public CacheErrorHandler errorHandler() {
                return errorHandler;
            }
        };
    }

    private static RedisCacheConfiguration cacheConfiguration(
            AppCacheProperties properties, Duration ttl, JavaType valueType, ObjectMapper mapper) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .disableCachingNullValues()
                .computePrefixWith(cacheName -> properties.keyPrefix() + cacheName + "::")
                .serializeValuesWith(SerializationPair.fromSerializer(new Jackson2JsonRedisSerializer<>(mapper, valueType)));
    }
}
