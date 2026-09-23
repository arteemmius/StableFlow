package com.blockchainhandler.cache;

import java.lang.reflect.Method;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.stereotype.Component;

/**
 * Builds cache keys of {@link AddressScopedQuery} arguments as {@code <address>|g<generation>|<suffix>}.
 *
 * @see CacheGenerationService
 */
@Component(AddressScopedKeyGenerator.BEAN_NAME)
@RequiredArgsConstructor
public class AddressScopedKeyGenerator implements KeyGenerator {

    /** Bean name to reference from {@code @Cacheable(keyGenerator = ...)}. */
    public static final String BEAN_NAME = "addressScopedKeyGenerator";

    private final CacheGenerationService generations;

    /**
     * Generates the key for a cached method that takes a single {@link AddressScopedQuery} argument.
     *
     * @param target the target instance
     * @param method the cached method
     * @param params method arguments
     * @return the cache key
     */
    @Override
    public Object generate(Object target, Method method, Object... params) {
        if (params.length != 1 || !(params[0] instanceof AddressScopedQuery query)) {
            throw new IllegalArgumentException(
                    method + " must take a single " + AddressScopedQuery.class.getSimpleName() + " argument");
        }
        return query.address() + "|g" + generations.currentGeneration(query.address()) + "|" + query.cacheKeySuffix();
    }
}
