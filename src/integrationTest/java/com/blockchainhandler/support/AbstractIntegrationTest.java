package com.blockchainhandler.support;

import static org.awaitility.Awaitility.await;

import com.blockchainhandler.common.messaging.EthereumEventMessage;
import com.blockchainhandler.config.properties.KafkaPipelineProperties;
import com.blockchainhandler.storage.entity.TransferEntity;
import com.blockchainhandler.storage.repository.TransferRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import okhttp3.mockwebserver.MockWebServer;
import org.awaitility.Awaitility;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base class of the integration tests: the whole application against real PostgreSQL, Kafka and Redis
 * (Testcontainers) and a stubbed Ethereum node. The WebSocket ingestion is disabled; tests feed events into Kafka
 * the same way the ingestion layer does.
 *
 * <p>All test classes share one application context and one set of containers, so every test works with its own
 * unique blocks, transactions and addresses.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("it")
public abstract class AbstractIntegrationTest {

    /** Stubbed Ethereum node answering {@code eth_getBlockByNumber}. */
    protected static final EthereumNodeStub NODE = new EthereumNodeStub();

    private static final MockWebServer NODE_SERVER = startNodeServer();
    private static final AtomicLong BLOCK_NUMBERS = new AtomicLong(30_000_000L + ThreadLocalRandom.current().nextInt(1_000_000));

    static {
        Awaitility.setDefaultTimeout(Duration.ofSeconds(60));
        Awaitility.setDefaultPollInterval(Duration.ofMillis(200));
    }

    @Autowired
    protected KafkaTemplate<String, byte[]> kafkaTemplate;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected KafkaPipelineProperties pipelineProperties;

    @Autowired
    protected TransferRepository transferRepository;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected TestRestTemplate restTemplate;

    @DynamicPropertySource
    static void nodeProperties(DynamicPropertyRegistry registry) {
        registry.add("app.ethereum.http-url", () -> NODE_SERVER.url("/").toString());
    }

    /**
     * Returns a block number no other test uses.
     *
     * @return unique block number
     */
    protected static long nextBlockNumber() {
        return BLOCK_NUMBERS.incrementAndGet();
    }

    /**
     * Publishes an event to the events topic exactly like the ingestion layer does.
     *
     * @param message event to publish
     * @throws Exception if the broker does not acknowledge the record
     */
    protected void publish(EthereumEventMessage message) throws Exception {
        publishRaw(message.transactionHash(), objectMapper.writeValueAsBytes(message));
    }

    /**
     * Publishes arbitrary bytes to the events topic.
     *
     * @param key     record key
     * @param payload record value
     * @throws Exception if the broker does not acknowledge the record
     */
    protected void publishRaw(String key, byte[] payload) throws Exception {
        kafkaTemplate.send(pipelineProperties.topics().events(), key, payload).get(30, TimeUnit.SECONDS);
    }

    /**
     * Waits until the transfers of the transaction are stored.
     *
     * @param txHash transaction hash
     * @param count  expected number of transfers
     * @return stored transfers
     */
    protected List<TransferEntity> awaitTransfers(String txHash, int count) {
        return await().until(() -> transferRepository.findByTxHashOrderByLogIndexAsc(txHash), list -> list.size() == count);
    }

    /**
     * Counts journal rows of a transaction.
     *
     * @param txHash transaction hash
     * @return number of {@code ethereum_events} rows
     */
    protected int journalRows(String txHash) {
        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM ethereum_events WHERE tx_hash = ?", Integer.class, txHash);
        return count == null ? 0 : count;
    }

    private static MockWebServer startNodeServer() {
        MockWebServer server = new MockWebServer();
        server.setDispatcher(NODE);
        try {
            server.start();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return server;
    }
}
