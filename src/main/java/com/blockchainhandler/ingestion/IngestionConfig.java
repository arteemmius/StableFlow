package com.blockchainhandler.ingestion;

import com.blockchainhandler.common.ethereum.Erc20Events;
import com.blockchainhandler.config.properties.AppCacheProperties;
import com.blockchainhandler.config.properties.EthereumProperties;
import com.blockchainhandler.config.properties.IngestionProperties;
import com.blockchainhandler.config.properties.KafkaPipelineProperties;
import com.blockchainhandler.config.properties.UsdcProperties;
import com.blockchainhandler.ingestion.client.LogFilter;
import com.blockchainhandler.ingestion.client.NodeConnector;
import com.blockchainhandler.ingestion.client.Web3jNodeConnector;
import com.blockchainhandler.observability.EventMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.observation.ObservationRegistry;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Wiring of the ingestion layer. Disabled with {@code app.ingestion.enabled=false}, e.g. to run a processing-only
 * or API-only instance, or in integration tests.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "app.ingestion", name = "enabled", havingValue = "true", matchIfMissing = true)
public class IngestionConfig {

    /**
     * WebSocket connector to the Ethereum node.
     *
     * @param ethereum  node settings
     * @param ingestion ingestion settings
     * @return the connector
     */
    @Bean
    public NodeConnector nodeConnector(EthereumProperties ethereum, IngestionProperties ingestion) {
        return new Web3jNodeConnector(ethereum.wsUrl(), ingestion.heartbeatInterval());
    }

    /**
     * Log to message mapper.
     *
     * @param ethereum node settings
     * @param clock    application clock
     * @return the mapper
     */
    @Bean
    public LogMessageMapper logMessageMapper(EthereumProperties ethereum, Clock clock) {
        return new LogMessageMapper(ethereum.chainId(), clock);
    }

    /**
     * Kafka publisher of raw events.
     *
     * @param kafkaTemplate auto-configured template (String keys, byte[] values)
     * @param objectMapper  application ObjectMapper
     * @param kafka         pipeline settings
     * @return the publisher
     */
    @Bean
    public EthereumEventPublisher ethereumEventPublisher(
            KafkaTemplate<String, byte[]> kafkaTemplate, ObjectMapper objectMapper, KafkaPipelineProperties kafka) {
        return new EthereumEventPublisher(kafkaTemplate, objectMapper, kafka.topics().events());
    }

    /**
     * Redis-backed checkpoint of the ingestion.
     *
     * @param redisTemplate Redis access
     * @param cache         cache settings (key prefix)
     * @param ethereum      node settings (chain id)
     * @return the checkpoint store
     */
    @Bean
    public IngestionCheckpointStore ingestionCheckpointStore(
            StringRedisTemplate redisTemplate, AppCacheProperties cache, EthereumProperties ethereum) {
        return new IngestionCheckpointStore(redisTemplate, cache.keyPrefix() + "ingestion:checkpoint:" + ethereum.chainId());
    }

    /**
     * The log subscriber, started and stopped with the application context.
     *
     * @param connector           node connector
     * @param mapper              log to message mapper
     * @param publisher           Kafka publisher
     * @param checkpointStore     checkpoint store
     * @param ingestion           ingestion settings
     * @param usdc                tracked token
     * @param metrics             pipeline metrics
     * @param observationRegistry observation registry
     * @param clock               application clock
     * @return the subscriber
     */
    @Bean
    public EthereumLogSubscriber ethereumLogSubscriber(
            NodeConnector connector,
            LogMessageMapper mapper,
            EthereumEventPublisher publisher,
            IngestionCheckpointStore checkpointStore,
            IngestionProperties ingestion,
            UsdcProperties usdc,
            EventMetrics metrics,
            ObservationRegistry observationRegistry,
            Clock clock) {
        LogFilter filter = new LogFilter(
                List.of(usdc.contractAddress().toLowerCase(Locale.ROOT)),
                List.of(Erc20Events.TRANSFER_TOPIC));
        return new EthereumLogSubscriber(connector, filter, mapper, publisher, checkpointStore, ingestion,
                new ReconnectBackoff(ingestion.reconnect()), metrics, observationRegistry, clock);
    }
}
