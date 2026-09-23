package com.blockchainhandler.cache;

import com.blockchainhandler.config.properties.AppCacheProperties;
import java.util.Collection;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Per-address cache generations used for O(1) invalidation of address-scoped caches.
 *
 * <p>Every cache key of an address embeds the current generation token of that address. When a transfer of
 * the address is stored, the token is replaced with a new random value: all previously cached pages become
 * unreachable at once (and expire with their TTL), without {@code SCAN}/{@code KEYS}. Because a reader embeds
 * the generation it observed before querying the database, a slow reader can never store stale data under the
 * new generation. Random tokens (instead of counters) stay unique even after a generation marker expires.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheGenerationService {

    /** Generation of addresses that have not changed since their marker expired. */
    static final String INITIAL_GENERATION = "0";

    private final StringRedisTemplate redisTemplate;
    private final AppCacheProperties properties;

    /**
     * Returns the current generation token of an address.
     *
     * @param address checksum address
     * @return generation token; {@value #INITIAL_GENERATION} if none is stored or Redis is unavailable
     */
    public String currentGeneration(String address) {
        try {
            String generation = redisTemplate.opsForValue().get(key(address));
            return generation != null ? generation : INITIAL_GENERATION;
        } catch (RuntimeException e) {
            // The cache lookup that follows fails as well and is handled by the CacheErrorHandler.
            log.debug("Cannot read cache generation of {}: {}", address, e.toString());
            return INITIAL_GENERATION;
        }
    }

    /**
     * Starts a new generation for each address, invalidating everything cached for them.
     *
     * @param addresses checksum addresses whose data changed
     */
    public void bump(Collection<String> addresses) {
        for (String address : addresses) {
            String generation = Long.toUnsignedString(ThreadLocalRandom.current().nextLong(), Character.MAX_RADIX);
            redisTemplate.opsForValue().set(key(address), generation, properties.generationTtl());
        }
    }

    private String key(String address) {
        return properties.keyPrefix() + "cache-generation:" + address;
    }
}
