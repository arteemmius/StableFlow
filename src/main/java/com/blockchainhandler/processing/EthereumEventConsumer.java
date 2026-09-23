package com.blockchainhandler.processing;

import com.blockchainhandler.common.messaging.EthereumEventMessage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer of the {@code ethereum-events} topic.
 *
 * <p>The JSON payload is converted by {@code ByteArrayJsonMessageConverter} and validated with Jakarta
 * Validation; conversion and validation failures are not retryable and go straight to the dead-letter topic.
 * The trace context propagated in the record headers is restored by the Kafka observation, so every log line
 * of an event carries the {@code traceId} of its ingestion.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EthereumEventConsumer {

    private final EventProcessingService processingService;

    /**
     * Handles one event.
     *
     * @param message   validated event
     * @param partition partition the record was read from
     * @param offset    offset of the record
     */
    @KafkaListener(
            id = "ethereum-events-processor",
            topics = "${app.kafka.topics.events}",
            groupId = "${spring.kafka.consumer.group-id}")
    public void onEvent(
            @Payload @Valid EthereumEventMessage message,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {
        try (MDC.MDCCloseable txHash = MDC.putCloseable("txHash", message.transactionHash());
             MDC.MDCCloseable blockNumber = MDC.putCloseable("blockNumber", String.valueOf(message.blockNumber()));
             MDC.MDCCloseable logIndex = MDC.putCloseable("logIndex", String.valueOf(message.logIndex()))) {
            log.debug("Consumed event from partition {} at offset {}", partition, offset);
            processingService.process(message);
        }
    }
}
