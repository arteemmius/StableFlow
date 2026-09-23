package com.blockchainhandler.ingestion.client;

import com.blockchainhandler.observability.UrlMasker;
import io.reactivex.Flowable;
import java.io.IOException;
import java.math.BigInteger;
import java.net.ConnectException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.EthBlockNumber;
import org.web3j.protocol.core.methods.response.EthLog;
import org.web3j.protocol.core.methods.response.EthSubscribe;
import org.web3j.protocol.websocket.WebSocketClient;
import org.web3j.protocol.websocket.WebSocketService;

/**
 * {@link NodeConnector} backed by web3j's WebSocket transport.
 */
@Slf4j
public class Web3jNodeConnector implements NodeConnector {

    /** Polling interval of web3j flowables; unused (push subscriptions only) but required by the factory. */
    private static final long POLLING_INTERVAL_MILLIS = 15_000L;

    private final String wsUrl;
    private final Duration heartbeatInterval;

    /**
     * Creates the connector.
     *
     * @param wsUrl             WebSocket JSON-RPC endpoint
     * @param heartbeatInterval ping interval; the connection is considered lost after missing pongs
     */
    public Web3jNodeConnector(String wsUrl, Duration heartbeatInterval) {
        this.wsUrl = wsUrl;
        this.heartbeatInterval = heartbeatInterval;
    }

    /** {@inheritDoc} */
    @Override
    public NodeSession connect(Consumer<Throwable> onDisconnect) throws IOException {
        WebSocketClient client = new WebSocketClient(URI.create(wsUrl));
        client.setConnectionLostTimeout((int) Math.max(1, heartbeatInterval.toSeconds()));
        WebSocketService service = new WebSocketService(client, false);
        try {
            service.connect(
                    message -> {
                    },
                    onDisconnect,
                    () -> onDisconnect.accept(new ConnectException("WebSocket connection to " + describe() + " was closed")));
            if (!client.isOpen()) {
                throw new ConnectException("WebSocket connection to " + describe() + " is not open");
            }
        } catch (IOException | RuntimeException e) {
            service.close();
            throw e;
        }
        Web3j web3j = Web3j.build(service, POLLING_INTERVAL_MILLIS,
                Executors.newSingleThreadScheduledExecutor(new CustomizableThreadFactory("web3j-ws-")));
        return new Web3jNodeSession(service, web3j);
    }

    /** {@inheritDoc} */
    @Override
    public String describe() {
        return UrlMasker.mask(wsUrl);
    }

    /**
     * Session over one WebSocket connection.
     */
    private static final class Web3jNodeSession implements NodeSession {

        private final WebSocketService service;
        private final Web3j web3j;

        private Web3jNodeSession(WebSocketService service, Web3j web3j) {
            this.service = service;
            this.web3j = web3j;
        }

        /** {@inheritDoc} */
        @Override
        public Flowable<RpcLog> subscribeLogs(LogFilter filter) {
            Map<String, Object> params = new HashMap<>();
            if (!filter.addresses().isEmpty()) {
                params.put("address", filter.addresses());
            }
            if (!filter.topics().isEmpty()) {
                params.put("topics", filter.topics());
            }
            Request<Object, EthSubscribe> request =
                    new Request<>("eth_subscribe", List.of("logs", params), service, EthSubscribe.class);
            return service.subscribe(request, "eth_unsubscribe", RpcLogNotification.class)
                    .filter(notification -> notification.getParams() != null && notification.getParams().getResult() != null)
                    .map(notification -> notification.getParams().getResult());
        }

        /** {@inheritDoc} */
        @Override
        public long latestBlockNumber() throws IOException {
            EthBlockNumber response = web3j.ethBlockNumber().send();
            if (response.hasError()) {
                throw new IOException("eth_blockNumber failed: " + response.getError().getMessage());
            }
            return response.getBlockNumber().longValueExact();
        }

        /** {@inheritDoc} */
        @Override
        public List<RpcLog> getLogs(LogFilter filter, long fromBlock, long toBlock) throws IOException {
            EthFilter ethFilter = new EthFilter(
                    DefaultBlockParameter.valueOf(BigInteger.valueOf(fromBlock)),
                    DefaultBlockParameter.valueOf(BigInteger.valueOf(toBlock)),
                    filter.addresses());
            filter.topics().forEach(ethFilter::addSingleTopic);
            EthLog response = web3j.ethGetLogs(ethFilter).send();
            if (response.hasError()) {
                throw new IOException("eth_getLogs failed: " + response.getError().getMessage());
            }
            List<RpcLog> logs = new ArrayList<>();
            for (EthLog.LogResult<?> result : response.getLogs()) {
                if (result instanceof EthLog.LogObject logObject) {
                    logs.add(RpcLog.from(logObject));
                }
            }
            return logs;
        }

        /** {@inheritDoc} */
        @Override
        public void close() {
            try {
                web3j.shutdown();
            } catch (RuntimeException e) {
                log.debug("Error while closing the WebSocket session: {}", e.toString());
            }
        }
    }
}
