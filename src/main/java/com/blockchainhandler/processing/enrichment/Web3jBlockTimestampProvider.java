package com.blockchainhandler.processing.enrichment;

import com.blockchainhandler.cache.CacheNames;
import com.blockchainhandler.processing.exception.BlockTimestampUnavailableException;
import java.io.IOException;
import java.math.BigInteger;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.methods.response.EthBlock;

/**
 * Reads block timestamps with {@code eth_getBlockByNumber} and caches them in Redis.
 *
 * <p>USDC emits roughly 70 transfers per block, so the cache turns ~70 node calls into one.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Web3jBlockTimestampProvider implements BlockTimestampProvider {

    private final Web3j web3j;

    /**
     * {@inheritDoc}
     *
     * <p>Results are cached in the {@value CacheNames#BLOCK_TIMESTAMPS} cache. Missing blocks are not cached:
     * behind a load balancer the node that serves HTTP may lag behind the one that pushed the log.
     */
    @Override
    @Cacheable(cacheNames = CacheNames.BLOCK_TIMESTAMPS, key = "#blockNumber")
    public Instant getBlockTimestamp(long blockNumber) {
        EthBlock response;
        try {
            response = web3j.ethGetBlockByNumber(DefaultBlockParameter.valueOf(BigInteger.valueOf(blockNumber)), false)
                    .send();
        } catch (IOException | RuntimeException e) {
            throw new BlockTimestampUnavailableException(
                    "eth_getBlockByNumber(" + blockNumber + ") failed: " + e.getMessage(), e);
        }
        if (response.hasError()) {
            throw new BlockTimestampUnavailableException(
                    "eth_getBlockByNumber(" + blockNumber + ") returned an error: " + response.getError().getMessage());
        }
        EthBlock.Block block = response.getBlock();
        if (block == null || block.getTimestamp() == null) {
            throw new BlockTimestampUnavailableException("Block " + blockNumber + " is not available on the node yet");
        }
        log.debug("Resolved timestamp of block {} from the node", blockNumber);
        return Instant.ofEpochSecond(block.getTimestamp().longValueExact());
    }
}
