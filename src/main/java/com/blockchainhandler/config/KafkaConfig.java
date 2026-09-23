package com.blockchainhandler.config;

import com.blockchainhandler.config.properties.KafkaPipelineProperties;
import com.blockchainhandler.observability.EventMetrics;
import com.blockchainhandler.processing.exception.NonRetryableEventException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.annotation.KafkaListenerConfigurer;
import org.springframework.kafka.config.KafkaListenerEndpointRegistrar;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.support.converter.ByteArrayJsonMessageConverter;
import org.springframework.kafka.support.converter.RecordMessageConverter;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

/**
 * Kafka topics, message conversion and error handling of the event pipeline.
 *
 * <p>Records travel as {@code byte[]} (ByteArraySerializer / ByteArrayDeserializer) and JSON conversion happens
 * in the listener adapter. Consequently, a malformed record can never become a poison pill in the consumer and
 * the dead-letter topic always receives the original bytes, together with the {@code kafka_dlt-*} headers that
 * describe the failure. Spring Boot applies the {@link RecordMessageConverter} and {@link CommonErrorHandler}
 * beans to its auto-configured listener container factory.
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
public class KafkaConfig implements KafkaListenerConfigurer {

    private final LocalValidatorFactoryBean validator;

    /**
     * Creates the configuration.
     *
     * @param validator Jakarta validator used for {@code @Valid @Payload} listener arguments
     */
    public KafkaConfig(LocalValidatorFactoryBean validator) {
        this.validator = validator;
    }

    /**
     * Enables Jakarta Validation of {@code @Valid} listener payloads.
     *
     * @param registrar listener endpoint registrar
     */
    @Override
    public void configureKafkaListeners(KafkaListenerEndpointRegistrar registrar) {
        registrar.setValidator(validator);
    }

    /**
     * Topic with raw Ethereum events.
     *
     * @param properties pipeline settings
     * @return topic definition created on startup by {@code KafkaAdmin}
     */
    @Bean
    public NewTopic ethereumEventsTopic(KafkaPipelineProperties properties) {
        KafkaPipelineProperties.Topics topics = properties.topics();
        return TopicBuilder.name(topics.events())
                .partitions(topics.partitions())
                .replicas(topics.replicas())
                .build();
    }

    /**
     * Dead-letter topic. It has as many partitions as the source topic because failed records are published
     * to the partition they came from.
     *
     * @param properties pipeline settings
     * @return topic definition created on startup by {@code KafkaAdmin}
     */
    @Bean
    public NewTopic ethereumEventsDeadLetterTopic(KafkaPipelineProperties properties) {
        KafkaPipelineProperties.Topics topics = properties.topics();
        return TopicBuilder.name(topics.deadLetter())
                .partitions(topics.partitions())
                .replicas(topics.replicas())
                .config(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(topics.deadLetterRetention().toMillis()))
                .build();
    }

    /**
     * Converts {@code byte[]} record values to the listener parameter type with the application ObjectMapper.
     *
     * @param objectMapper application ObjectMapper (ISO dates, Java time support)
     * @return record message converter
     */
    @Bean
    public RecordMessageConverter kafkaRecordMessageConverter(ObjectMapper objectMapper) {
        return new ByteArrayJsonMessageConverter(objectMapper);
    }

    /**
     * Error handler of the listener containers: blocking retries with exponential back-off for transient
     * failures (node or database unavailable), then publication to the dead-letter topic. Deserialization,
     * validation and {@link NonRetryableEventException} failures skip the retries.
     *
     * @param kafkaTemplate template used to publish dead letters (String keys, byte[] values)
     * @param properties    pipeline settings
     * @param metrics       pipeline metrics
     * @return the error handler
     */
    @Bean
    public CommonErrorHandler kafkaErrorHandler(
            KafkaTemplate<?, ?> kafkaTemplate, KafkaPipelineProperties properties, EventMetrics metrics) {
        String deadLetterTopic = properties.topics().deadLetter();
        DeadLetterPublishingRecoverer deadLetterPublisher = new DeadLetterPublishingRecoverer(
                kafkaTemplate, (consumerRecord, exception) -> new TopicPartition(deadLetterTopic, consumerRecord.partition()));

        ConsumerRecordRecoverer recoverer = (consumerRecord, exception) -> {
            Throwable rootCause = NestedExceptionUtils.getMostSpecificCause(exception);
            metrics.eventDeadLettered(rootCause.getClass().getSimpleName());
            log.error("Sending record {}-{}@{} (key={}) to {}: {}", consumerRecord.topic(), consumerRecord.partition(),
                    consumerRecord.offset(), consumerRecord.key(), deadLetterTopic, rootCause.toString());
            deadLetterPublisher.accept(consumerRecord, exception);
        };

        KafkaPipelineProperties.Retry retry = properties.retry();
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(retry.maxRetries());
        backOff.setInitialInterval(retry.initialInterval().toMillis());
        backOff.setMultiplier(retry.multiplier());
        backOff.setMaxInterval(retry.maxInterval().toMillis());

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
        errorHandler.addNotRetryableExceptions(NonRetryableEventException.class);
        errorHandler.setLogLevel(KafkaException.Level.WARN);
        errorHandler.setRetryListeners((consumerRecord, exception, deliveryAttempt) ->
                log.warn("Delivery attempt {} of record {}-{}@{} failed: {}", deliveryAttempt, consumerRecord.topic(),
                        consumerRecord.partition(), consumerRecord.offset(),
                        NestedExceptionUtils.getMostSpecificCause(exception).toString()));
        return errorHandler;
    }
}
