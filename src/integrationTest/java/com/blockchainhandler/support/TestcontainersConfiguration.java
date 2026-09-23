package com.blockchainhandler.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Infrastructure of the integration tests, in the same versions as {@code docker-compose.yml}. Spring Boot derives
 * the connection settings from the containers ({@code @ServiceConnection}); the containers live as long as the
 * (cached) application context, so they are started once for all test classes.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    /**
     * PostgreSQL 16.
     *
     * @return the container
     */
    @Bean
    @ServiceConnection
    public PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:16.15-alpine"));
    }

    /**
     * Single-node Kafka in KRaft mode.
     *
     * @return the container
     */
    @Bean
    @ServiceConnection
    public KafkaContainer kafkaContainer() {
        return new KafkaContainer(DockerImageName.parse("apache/kafka:4.3.1"));
    }

    /**
     * Redis.
     *
     * @return the container
     */
    @Bean
    @ServiceConnection(name = "redis")
    public GenericContainer<?> redisContainer() {
        return new GenericContainer<>(DockerImageName.parse("redis:7.4.11-alpine")).withExposedPorts(6379);
    }
}
