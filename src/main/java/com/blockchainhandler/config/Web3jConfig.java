package com.blockchainhandler.config;

import com.blockchainhandler.config.properties.EthereumProperties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import okhttp3.OkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

/**
 * Web3j client used by the processing layer for request/response JSON-RPC calls.
 *
 * <p>It talks to the HTTP endpoint of the node and is independent from the WebSocket connection of the
 * ingestion layer, so processing keeps working (and can be deployed separately) while the subscription
 * reconnects.
 */
@Configuration(proxyBeanMethods = false)
public class Web3jConfig {

    /** Polling interval of web3j flowables; unused by the service but required by the factory method. */
    private static final long POLLING_INTERVAL_MILLIS = 15_000L;

    /**
     * Creates the HTTP Web3j client with explicit timeouts.
     *
     * @param properties node connection settings
     * @return the Web3j client, shut down together with the application context
     */
    @Bean(destroyMethod = "shutdown")
    public Web3j web3jHttpClient(EthereumProperties properties) {
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(properties.httpConnectTimeout())
                .readTimeout(properties.httpReadTimeout())
                .build();
        ScheduledExecutorService executor =
                Executors.newSingleThreadScheduledExecutor(new CustomizableThreadFactory("web3j-http-"));
        return Web3j.build(new HttpService(properties.httpUrl(), httpClient), POLLING_INTERVAL_MILLIS, executor);
    }
}
