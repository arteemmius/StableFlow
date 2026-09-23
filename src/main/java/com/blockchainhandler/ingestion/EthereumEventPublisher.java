package com.blockchainhandler.ingestion;

import com.blockchainhandler.common.messaging.EthereumEventMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.CompletableFuture;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

/**
 * Publishes events to the {@code ethereum-events} topic with the transaction hash as the record key.
 *
 * <p>The producer is idempotent with {@code acks=all} (see {@code application.yml}). Keying by transaction hash
 * keeps all logs of a transaction, and their reorg removals, in one partition and therefore in order.
 */
public class EthereumEventPublisher {

    private final KafkaTemplate<String, byte[]> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String topic;

    /**
     * Creates the publisher.
     *
     * @param kafkaTemplate template with String keys and byte[] values
     * @param objectMapper  JSON mapper of the message contract
     * @param topic         destination topic
     */
    public EthereumEventPublisher(KafkaTemplate<String, byte[]> kafkaTemplate, ObjectMapper objectMapper, String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.topic = topic;
    }

    /**
     * Publishes a message asynchronously.
     *
     * @param message message to publish
     * @return future completed when the broker acknowledged the record; never throws synchronously
     */
    public CompletableFuture<SendResult<String, byte[]>> publish(EthereumEventMessage message) {
        try {
            byte[] payload = objectMapper.writeValueAsBytes(message);
            return kafkaTemplate.send(topic, message.transactionHash(), payload);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
}
