package com.blockchainhandler;

import static org.assertj.core.api.Assertions.assertThat;

import com.blockchainhandler.common.messaging.EthereumEventMessage;
import com.blockchainhandler.support.AbstractIntegrationTest;
import com.blockchainhandler.testsupport.UsdcTransfers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.kafka.KafkaConnectionDetails;
import org.springframework.kafka.support.KafkaHeaders;

/**
 * Error handling of the consumer: non-retryable failures go straight to the dead-letter topic, transient ones are
 * retried with back-off first. The dead letter always carries the original bytes and the failure headers.
 */
class DeadLetterQueueIT extends AbstractIntegrationTest {

    private static final Duration DEAD_LETTER_TIMEOUT = Duration.ofSeconds(60);

    @Autowired
    private KafkaConnectionDetails kafkaConnectionDetails;

    @Test
    @DisplayName("a record that is not valid JSON is dead-lettered with its original bytes")
    void malformedJsonIsDeadLettered() throws Exception {
        String key = "malformed-" + UUID.randomUUID();
        byte[] payload = "{\"schemaVersion\": 1, broken".getBytes(StandardCharsets.UTF_8);

        publishRaw(key, payload);

        ConsumerRecord<String, byte[]> deadLetter = awaitDeadLetter(key);
        assertThat(deadLetter.value()).isEqualTo(payload);
        assertThat(header(deadLetter, KafkaHeaders.DLT_ORIGINAL_TOPIC)).isEqualTo(pipelineProperties.topics().events());
        assertThat(header(deadLetter, KafkaHeaders.DLT_EXCEPTION_STACKTRACE))
                .contains("ConversionException");
    }

    @Test
    @DisplayName("a message violating the contract is dead-lettered without retries")
    void invalidMessageIsDeadLettered() throws Exception {
        EthereumEventMessage invalid = UsdcTransfers.randomTransfer(nextBlockNumber()).contractAddress("not-an-address").build();

        publish(invalid);

        ConsumerRecord<String, byte[]> deadLetter = awaitDeadLetter(invalid.transactionHash());
        assertThat(header(deadLetter, KafkaHeaders.DLT_EXCEPTION_STACKTRACE))
                .contains("MethodArgumentNotValidException");
    }

    @Test
    @DisplayName("an unavailable node is retried with back-off, then the event is dead-lettered")
    void unavailableNodeIsRetriedThenDeadLettered() throws Exception {
        long block = nextBlockNumber();
        NODE.failBlock(block);
        EthereumEventMessage message = UsdcTransfers.randomTransfer(block).build();

        publish(message);

        ConsumerRecord<String, byte[]> deadLetter = awaitDeadLetter(message.transactionHash());
        assertThat(header(deadLetter, KafkaHeaders.DLT_EXCEPTION_STACKTRACE))
                .contains("BlockTimestampUnavailableException");
        // One attempt plus two retries (see application-it.yml); failures are never cached.
        assertThat(NODE.requestsFor(block)).isEqualTo(3);
        assertThat(transferRepository.findByTxHashOrderByLogIndexAsc(message.transactionHash())).isEmpty();
        assertThat(journalRows(message.transactionHash())).isZero();
    }

    @Test
    @DisplayName("a block not yet known to the node is retried until the node catches up")
    void missingBlockIsRetriedUntilAvailable() throws Exception {
        long block = nextBlockNumber();
        // The node behind the load balancer lags: the first attempt finds no block, the retry succeeds.
        NODE.blockAppearsLater(block, Instant.now().truncatedTo(ChronoUnit.SECONDS), 1);
        EthereumEventMessage message = UsdcTransfers.randomTransfer(block).build();

        publish(message);

        awaitTransfers(message.transactionHash(), 1);
        assertThat(NODE.requestsFor(block)).isEqualTo(2);
    }

    private ConsumerRecord<String, byte[]> awaitDeadLetter(String key) {
        try (Consumer<String, byte[]> consumer = deadLetterConsumer()) {
            consumer.subscribe(List.of(pipelineProperties.topics().deadLetter()));
            long deadline = System.nanoTime() + DEAD_LETTER_TIMEOUT.toNanos();
            while (System.nanoTime() < deadline) {
                for (ConsumerRecord<String, byte[]> consumerRecord : consumer.poll(Duration.ofMillis(500))) {
                    if (key.equals(consumerRecord.key())) {
                        return consumerRecord;
                    }
                }
            }
        }
        throw new AssertionError("No dead letter with key " + key + " within " + DEAD_LETTER_TIMEOUT);
    }

    private Consumer<String, byte[]> deadLetterConsumer() {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, String.join(",", kafkaConnectionDetails.getBootstrapServers()),
                ConsumerConfig.GROUP_ID_CONFIG, "it-dead-letters-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new KafkaConsumer<>(config, new StringDeserializer(), new ByteArrayDeserializer());
    }

    private static String header(ConsumerRecord<String, byte[]> consumerRecord, String name) {
        Header header = consumerRecord.headers().lastHeader(name);
        return header == null ? "" : new String(header.value(), StandardCharsets.UTF_8);
    }
}
