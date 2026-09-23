package com.blockchainhandler.ingestion;

import java.util.OptionalLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Persists the ingestion resume block in Redis so that a restarted instance backfills the logs emitted while it
 * was down. Best effort: without a checkpoint the service simply starts from the live subscription.
 */
@Slf4j
public class IngestionCheckpointStore {

    private final StringRedisTemplate redisTemplate;
    private final String key;

    /**
     * Creates the store.
     *
     * @param redisTemplate Redis access
     * @param key           Redis key of the checkpoint
     */
    public IngestionCheckpointStore(StringRedisTemplate redisTemplate, String key) {
        this.redisTemplate = redisTemplate;
        this.key = key;
    }

    /**
     * Loads the persisted resume block.
     *
     * @return the resume block, empty if none is stored or Redis is unavailable
     */
    public OptionalLong load() {
        try {
            String value = redisTemplate.opsForValue().get(key);
            return value == null ? OptionalLong.empty() : OptionalLong.of(Long.parseLong(value));
        } catch (RuntimeException e) {
            log.warn("Cannot load the ingestion checkpoint, starting from the live subscription: {}", e.toString());
            return OptionalLong.empty();
        }
    }

    /**
     * Persists the resume block.
     *
     * @param blockNumber block a future backfill should start from
     */
    public void save(long blockNumber) {
        try {
            redisTemplate.opsForValue().set(key, Long.toString(blockNumber));
        } catch (RuntimeException e) {
            log.warn("Cannot persist the ingestion checkpoint: {}", e.toString());
        }
    }
}
