package com.blockchainhandler.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;

/**
 * JSON-RPC stub of an Ethereum node for {@code eth_getBlockByNumber}, served by OkHttp's MockWebServer.
 * Tests register block timestamps, simulate node outages per block and count the requests per block.
 */
public class EthereumNodeStub extends Dispatcher {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<Long, Instant> timestamps = new ConcurrentHashMap<>();
    private final Map<Long, AtomicInteger> missingResponses = new ConcurrentHashMap<>();
    private final Set<Long> failingBlocks = ConcurrentHashMap.newKeySet();
    private final Map<Long, AtomicInteger> requests = new ConcurrentHashMap<>();

    /**
     * Registers the timestamp of a block.
     *
     * @param blockNumber block number
     * @param timestamp   block timestamp
     */
    public void blockTimestamp(long blockNumber, Instant timestamp) {
        timestamps.put(blockNumber, timestamp);
    }

    /**
     * Registers a block that the node reports as missing ({@code "result": null}) for the first requests, like a
     * node that lags behind the one that pushed the log.
     *
     * @param blockNumber     block number
     * @param timestamp       block timestamp returned once the block is "synced"
     * @param missingRequests number of requests answered with a missing block
     */
    public void blockAppearsLater(long blockNumber, Instant timestamp, int missingRequests) {
        missingResponses.put(blockNumber, new AtomicInteger(missingRequests));
        timestamps.put(blockNumber, timestamp);
    }

    /**
     * Makes every request for the block fail with HTTP 500.
     *
     * @param blockNumber block number
     */
    public void failBlock(long blockNumber) {
        failingBlocks.add(blockNumber);
    }

    /**
     * Returns how often the block was requested.
     *
     * @param blockNumber block number
     * @return number of {@code eth_getBlockByNumber} calls for it
     */
    public int requestsFor(long blockNumber) {
        AtomicInteger counter = requests.get(blockNumber);
        return counter == null ? 0 : counter.get();
    }

    /** {@inheritDoc} */
    @Override
    public MockResponse dispatch(RecordedRequest request) {
        try {
            JsonNode body = objectMapper.readTree(request.getBody().readUtf8());
            long id = body.path("id").asLong();
            if (!"eth_getBlockByNumber".equals(body.path("method").asText())) {
                return json(error(id, -32601, "Method not found"));
            }
            long blockNumber = Long.decode(body.path("params").get(0).asText());
            requests.computeIfAbsent(blockNumber, number -> new AtomicInteger()).incrementAndGet();
            if (failingBlocks.contains(blockNumber)) {
                return new MockResponse().setResponseCode(500).setBody("node unavailable");
            }
            AtomicInteger missing = missingResponses.get(blockNumber);
            boolean notSyncedYet = missing != null && missing.getAndDecrement() > 0;
            Instant timestamp = notSyncedYet ? null : timestamps.get(blockNumber);
            ObjectNode response = objectMapper.createObjectNode().put("jsonrpc", "2.0").put("id", id);
            if (timestamp == null) {
                response.putNull("result");
            } else {
                response.putObject("result")
                        .put("number", "0x" + Long.toHexString(blockNumber))
                        .put("hash", "0x" + "ab".repeat(32))
                        .put("timestamp", "0x" + Long.toHexString(timestamp.getEpochSecond()))
                        .putArray("transactions");
            }
            return json(response);
        } catch (IOException | RuntimeException e) {
            return new MockResponse().setResponseCode(400).setBody(e.toString());
        }
    }

    private ObjectNode error(long id, int code, String message) {
        ObjectNode response = objectMapper.createObjectNode().put("jsonrpc", "2.0").put("id", id);
        response.putObject("error").put("code", code).put("message", message);
        return response;
    }

    private static MockResponse json(ObjectNode body) {
        return new MockResponse().setHeader("Content-Type", "application/json").setBody(body.toString());
    }
}
