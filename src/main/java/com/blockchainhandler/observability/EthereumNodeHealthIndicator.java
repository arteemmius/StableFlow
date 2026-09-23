package com.blockchainhandler.observability;

import com.blockchainhandler.config.properties.EthereumProperties;
import com.blockchainhandler.ingestion.ConnectionState;
import com.blockchainhandler.ingestion.EthereumLogSubscriber;
import com.blockchainhandler.ingestion.IngestionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Health of the WebSocket connection to the Ethereum node, exposed as the {@code ethereumNode} component of
 * {@code /actuator/health}.
 *
 * <p>The indicator reports the state tracked by {@link EthereumLogSubscriber} and performs no network call, so
 * the health endpoint stays fast when the node is slow. It is not part of the readiness group: the REST API keeps
 * serving stored data while the subscription reconnects.
 */
@Component
@RequiredArgsConstructor
public class EthereumNodeHealthIndicator implements HealthIndicator {

    private final ObjectProvider<EthereumLogSubscriber> subscriber;
    private final EthereumProperties ethereumProperties;

    /**
     * Reports UP while the log subscription is active and DOWN while connecting or backing off.
     *
     * @return health of the node connection
     */
    @Override
    public Health health() {
        EthereumLogSubscriber ingestion = subscriber.getIfAvailable();
        if (ingestion == null) {
            return Health.unknown().withDetail("ingestion", "disabled").build();
        }
        IngestionStatus status = ingestion.status();
        Health.Builder builder = status.state() == ConnectionState.SUBSCRIBED ? Health.up() : Health.down();
        builder.withDetail("node", UrlMasker.mask(ethereumProperties.wsUrl()))
                .withDetail("state", status.state())
                .withDetail("stateSince", status.stateSince())
                .withDetail("consecutiveFailures", status.consecutiveFailures());
        if (status.lastError() != null) {
            builder.withDetail("lastError", status.lastError());
        }
        if (status.lastLogAt() != null) {
            builder.withDetail("lastLogAt", status.lastLogAt());
        }
        if (status.lastBlockNumber() != null) {
            builder.withDetail("lastBlockNumber", status.lastBlockNumber());
        }
        if (status.resumeBlock() != null) {
            builder.withDetail("checkpointBlock", status.resumeBlock());
        }
        return builder.build();
    }
}
