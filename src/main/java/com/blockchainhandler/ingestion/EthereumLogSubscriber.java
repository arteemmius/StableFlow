package com.blockchainhandler.ingestion;

import com.blockchainhandler.common.messaging.EthereumEventMessage;
import com.blockchainhandler.common.messaging.EventSource;
import com.blockchainhandler.config.properties.IngestionProperties;
import com.blockchainhandler.ingestion.client.LogFilter;
import com.blockchainhandler.ingestion.client.NodeConnector;
import com.blockchainhandler.ingestion.client.NodeSession;
import com.blockchainhandler.ingestion.client.RpcLog;
import com.blockchainhandler.observability.EventMetrics;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.reactivex.Scheduler;
import io.reactivex.plugins.RxJavaPlugins;
import io.reactivex.schedulers.Schedulers;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;

/**
 * Subscribes to contract logs over WebSocket and publishes them to Kafka.
 *
 * <p>Resilience:
 * <ul>
 *   <li><b>Reconnect with exponential back-off.</b> Any transport error, closed connection or failed subscription
 *       schedules a reconnect with {@link ReconnectBackoff}. Every connection gets an id; callbacks of older
 *       connections are ignored, so duplicate signals (error + close) trigger a single reconnect.</li>
 *   <li><b>Gap backfill.</b> After every (re)subscription the logs emitted while the subscription was down are
 *       fetched with {@code eth_getLogs}, starting from the last published block ({@link PublishProgress}); the
 *       checkpoint is persisted so that restarts are covered as well. Logs whose publication failed are
 *       re-published the same way.</li>
 *   <li><b>Stale subscription watchdog.</b> A connection can stay open while the node silently stops pushing
 *       events; if no log arrives for {@code stale-subscription-timeout}, the subscription is recreated.</li>
 *   <li><b>Reorgs.</b> Logs removed by a chain reorganization ({@code removed=true}) are published as well; the
 *       processing layer compensates them.</li>
 * </ul>
 *
 * <p>Threading: connection management runs on the single {@code eth-ws-connector} thread; logs are moved off the
 * WebSocket read thread to the single {@code eth-log-publisher} thread, which also runs backfills, so Kafka
 * publishing never blocks the WebSocket.
 */
@Slf4j
public class EthereumLogSubscriber implements SmartLifecycle {

    private static final long EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS = 10;

    private final NodeConnector connector;
    private final LogFilter filter;
    private final LogMessageMapper mapper;
    private final EthereumEventPublisher publisher;
    private final IngestionCheckpointStore checkpointStore;
    private final IngestionProperties properties;
    private final ReconnectBackoff backoff;
    private final EventMetrics metrics;
    private final ObservationRegistry observationRegistry;
    private final Clock clock;

    private final PublishProgress progress = new PublishProgress();
    private final AtomicLong connectionIds = new AtomicLong();
    private final Object lifecycleMonitor = new Object();

    private volatile boolean running;
    private volatile ScheduledExecutorService connectorExecutor;
    private volatile ExecutorService publisherExecutor;
    private volatile Scheduler publisherScheduler;
    private volatile NodeSession session;
    private volatile long lastSavedCheckpoint = -1;

    private volatile ConnectionState state = ConnectionState.STOPPED;
    private volatile Instant stateSince;
    private volatile int consecutiveFailures;
    private volatile String lastError;
    private volatile Instant lastLogAt;
    private volatile Long lastBlockNumber;

    /**
     * Creates the subscriber.
     *
     * @param connector           opens connections to the node
     * @param filter              contracts and topics to subscribe to
     * @param mapper              converts logs to Kafka messages
     * @param publisher           publishes messages to Kafka
     * @param checkpointStore     persists the backfill checkpoint
     * @param properties          ingestion settings
     * @param backoff             reconnect back-off policy
     * @param metrics             pipeline metrics
     * @param observationRegistry creates an observation (and thus a trace) per ingested event
     * @param clock               application clock
     */
    public EthereumLogSubscriber(
            NodeConnector connector,
            LogFilter filter,
            LogMessageMapper mapper,
            EthereumEventPublisher publisher,
            IngestionCheckpointStore checkpointStore,
            IngestionProperties properties,
            ReconnectBackoff backoff,
            EventMetrics metrics,
            ObservationRegistry observationRegistry,
            Clock clock) {
        this.connector = connector;
        this.filter = filter;
        this.mapper = mapper;
        this.publisher = publisher;
        this.checkpointStore = checkpointStore;
        this.properties = properties;
        this.backoff = backoff;
        this.metrics = metrics;
        this.observationRegistry = observationRegistry;
        this.clock = clock;
        this.stateSince = clock.instant();
        metrics.bindNodeConnection(() -> state == ConnectionState.SUBSCRIBED);
    }

    /**
     * Starts the connector and publisher threads and connects to the node.
     */
    @Override
    public void start() {
        synchronized (lifecycleMonitor) {
            if (running) {
                return;
            }
            installRxJavaErrorHandler();
            connectorExecutor = Executors.newSingleThreadScheduledExecutor(new CustomizableThreadFactory("eth-ws-connector-"));
            publisherExecutor = Executors.newSingleThreadExecutor(new CustomizableThreadFactory("eth-log-publisher-"));
            publisherScheduler = Schedulers.from(publisherExecutor);
            checkpointStore.load().ifPresent(checkpoint -> {
                progress.restore(checkpoint);
                lastSavedCheckpoint = checkpoint;
                log.info("Restored ingestion checkpoint: block {}", checkpoint);
            });
            running = true;

            connectorExecutor.execute(this::connect);
            long watchdogMillis = properties.watchdogInterval().toMillis();
            connectorExecutor.scheduleWithFixedDelay(this::runWatchdog, watchdogMillis, watchdogMillis, TimeUnit.MILLISECONDS);
            long flushMillis = properties.checkpointFlushInterval().toMillis();
            connectorExecutor.scheduleWithFixedDelay(this::flushCheckpoint, flushMillis, flushMillis, TimeUnit.MILLISECONDS);
            log.info("Ethereum log ingestion started: node={}, contracts={}", connector.describe(), filter.addresses());
        }
    }

    /**
     * Closes the connection, publishes the logs that were already received and persists the checkpoint.
     */
    @Override
    public void stop() {
        synchronized (lifecycleMonitor) {
            if (!running) {
                return;
            }
            running = false;
            connectionIds.incrementAndGet();
            connectorExecutor.shutdownNow();
            awaitTermination(connectorExecutor, "connector");
            closeSession();
            publisherExecutor.shutdown();
            awaitTermination(publisherExecutor, "publisher");
            flushCheckpoint();
            updateState(ConnectionState.STOPPED);
            log.info("Ethereum log ingestion stopped");
        }
    }

    /**
     * Tells whether the ingestion has been started.
     *
     * @return {@code true} between {@link #start()} and {@link #stop()}
     */
    @Override
    public boolean isRunning() {
        return running;
    }

    /**
     * Returns a snapshot of the ingestion state.
     *
     * @return current status
     */
    public IngestionStatus status() {
        OptionalLong checkpoint = progress.checkpoint();
        return new IngestionStatus(state, stateSince, consecutiveFailures, lastError, lastLogAt, lastBlockNumber,
                checkpoint.isPresent() ? checkpoint.getAsLong() : null);
    }

    /** Opens a connection, subscribes and schedules a gap backfill. Runs on the connector thread. */
    private void connect() {
        if (!running) {
            return;
        }
        long connectionId = connectionIds.incrementAndGet();
        updateState(ConnectionState.CONNECTING);
        // Claim the backfill start before subscribing, so that live logs cannot move the watermark past the gap.
        OptionalLong backfillFrom = properties.backfill().enabled() ? progress.claimBackfill() : OptionalLong.empty();
        try {
            NodeSession newSession = connector.connect(cause -> onConnectionLost(connectionId, cause));
            session = newSession;
            newSession.subscribeLogs(filter)
                    .observeOn(publisherScheduler, true)
                    .subscribe(rpcLog -> handleLog(rpcLog, EventSource.SUBSCRIPTION),
                            error -> onConnectionLost(connectionId, error));
            if (connectionIds.get() != connectionId) {
                releaseBackfill(backfillFrom);
                return;
            }
            consecutiveFailures = 0;
            lastError = null;
            lastLogAt = clock.instant();
            updateState(ConnectionState.SUBSCRIBED);
            log.info("Subscribed to logs of {} via {}", filter.addresses(), connector.describe());
            backfillFrom.ifPresent(fromBlock -> submitBackfill(newSession, connectionId, fromBlock));
        } catch (Exception e) {
            releaseBackfill(backfillFrom);
            onConnectionLost(connectionId, e);
        }
    }

    /** Handles a lost connection. May be called from any thread and several times per connection. */
    private void onConnectionLost(long connectionId, Throwable cause) {
        if (!connectionIds.compareAndSet(connectionId, connectionId + 1)) {
            return;
        }
        ScheduledExecutorService executor = connectorExecutor;
        if (!running || executor == null) {
            return;
        }
        try {
            executor.execute(() -> scheduleReconnect(cause));
        } catch (RejectedExecutionException e) {
            log.debug("Ingestion is stopping, reconnect skipped");
        }
    }

    /** Closes the broken session and schedules the next attempt. Runs on the connector thread. */
    private void scheduleReconnect(Throwable cause) {
        closeSession();
        if (!running) {
            return;
        }
        int attempt = consecutiveFailures;
        consecutiveFailures = attempt + 1;
        Duration delay = backoff.delayForAttempt(attempt);
        lastError = cause.getClass().getSimpleName() + ": " + cause.getMessage();
        updateState(ConnectionState.BACKING_OFF);
        metrics.nodeReconnect();
        log.warn("Connection to {} lost ({}); reconnect attempt {} in {} ms",
                connector.describe(), lastError, attempt + 1, delay.toMillis());
        try {
            connectorExecutor.schedule(this::connect, delay.toMillis(), TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            log.debug("Ingestion is stopping, reconnect skipped");
        }
    }

    /** Maps and publishes one log. Runs on the publisher thread. */
    private void handleLog(RpcLog rpcLog, EventSource source) {
        lastLogAt = clock.instant();
        EthereumEventMessage message;
        try {
            message = mapper.toMessage(rpcLog, source);
        } catch (RuntimeException e) {
            metrics.malformedLog();
            log.error("Skipping a malformed log from the node: {} ({})", rpcLog, e.toString());
            return;
        }
        lastBlockNumber = message.blockNumber();
        metrics.eventReceived(message.eventType(), source);
        Observation.createNotStarted("ethereum.event.ingest", observationRegistry)
                .contextualName("ingest " + message.eventType().name().toLowerCase(Locale.ROOT))
                .lowCardinalityKeyValue("event.type", message.eventType().name())
                .lowCardinalityKeyValue("event.source", source.name())
                .highCardinalityKeyValue("tx.hash", message.transactionHash())
                .observe(() -> publish(message));
    }

    private void publish(EthereumEventMessage message) {
        if (message.removed()) {
            log.warn("Chain reorganization: log {} of tx {} in block {} was removed",
                    message.logIndex(), message.transactionHash(), message.blockNumber());
        } else {
            log.debug("Publishing log {} of tx {} in block {}", message.logIndex(), message.transactionHash(), message.blockNumber());
        }
        publisher.publish(message).whenComplete((result, error) -> {
            if (error == null) {
                progress.onPublished(message.blockNumber());
            } else {
                progress.onFailed(message.blockNumber());
                metrics.publishFailed();
                log.error("Failed to publish log {} of tx {} in block {}; it will be re-published by a backfill: {}",
                        message.logIndex(), message.transactionHash(), message.blockNumber(), error.toString());
            }
        });
    }

    /** Queues a backfill on the publisher thread; the claim is released when it ends. */
    private void submitBackfill(NodeSession backfillSession, long connectionId, long fromBlock) {
        try {
            publisherExecutor.execute(() -> {
                try {
                    backfill(backfillSession, connectionId, fromBlock);
                } finally {
                    progress.backfillFinished();
                }
            });
        } catch (RejectedExecutionException e) {
            releaseBackfill(OptionalLong.of(fromBlock));
        }
    }

    /** Re-publishes logs from {@code resumeBlock} up to the current head. Runs on the publisher thread. */
    private void backfill(NodeSession backfillSession, long connectionId, long resumeBlock) {
        IngestionProperties.Backfill settings = properties.backfill();
        long head;
        try {
            head = backfillSession.latestBlockNumber();
        } catch (Exception e) {
            progress.onFailed(resumeBlock);
            log.warn("Backfill from block {} postponed, cannot read the latest block: {}", resumeBlock, e.toString());
            return;
        }
        long fromBlock = Math.max(resumeBlock, head - settings.maxBlocks() + 1);
        if (fromBlock > resumeBlock) {
            log.warn("Gap of {} blocks exceeds the backfill limit of {}; blocks {}..{} are skipped",
                    head - resumeBlock + 1, settings.maxBlocks(), resumeBlock, fromBlock - 1);
        }
        if (fromBlock > head) {
            return;
        }
        log.info("Backfilling blocks {}..{}", fromBlock, head);
        long logCount = 0;
        for (long chunkStart = fromBlock; chunkStart <= head; chunkStart += settings.chunkSize()) {
            if (!running || connectionIds.get() != connectionId) {
                progress.onFailed(chunkStart);
                log.info("Backfill interrupted at block {}; it will resume after the reconnect", chunkStart);
                return;
            }
            long chunkEnd = Math.min(head, chunkStart + settings.chunkSize() - 1);
            List<RpcLog> logs;
            try {
                logs = backfillSession.getLogs(filter, chunkStart, chunkEnd);
            } catch (Exception e) {
                progress.onFailed(chunkStart);
                log.warn("Backfill of blocks {}..{} failed, it will be retried: {}", chunkStart, chunkEnd, e.toString());
                return;
            }
            logs.forEach(rpcLog -> handleLog(rpcLog, EventSource.BACKFILL));
            logCount += logs.size();
        }
        log.info("Backfill of blocks {}..{} finished: {} logs re-published", fromBlock, head, logCount);
    }

    /** Detects stale subscriptions and retries failed publications. Runs on the connector thread. */
    private void runWatchdog() {
        try {
            if (!running || state != ConnectionState.SUBSCRIBED) {
                return;
            }
            Duration staleTimeout = properties.staleSubscriptionTimeout();
            Instant last = lastLogAt;
            if (!staleTimeout.isZero() && last != null
                    && Duration.between(last, clock.instant()).compareTo(staleTimeout) > 0) {
                log.warn("No logs received for more than {} s; recreating the subscription", staleTimeout.toSeconds());
                onConnectionLost(connectionIds.get(), new TimeoutException("No logs received for " + staleTimeout));
                return;
            }
            NodeSession current = session;
            if (properties.backfill().enabled() && current != null && progress.hasFailures()) {
                long connectionId = connectionIds.get();
                progress.claimBackfill().ifPresent(fromBlock -> submitBackfill(current, connectionId, fromBlock));
            }
        } catch (RuntimeException e) {
            log.error("Ingestion watchdog failed", e);
        }
    }

    /** Persists the checkpoint if it changed. Runs on the connector thread and on stop. */
    private void flushCheckpoint() {
        try {
            OptionalLong checkpoint = progress.checkpoint();
            if (checkpoint.isPresent() && checkpoint.getAsLong() != lastSavedCheckpoint) {
                checkpointStore.save(checkpoint.getAsLong());
                lastSavedCheckpoint = checkpoint.getAsLong();
            }
        } catch (RuntimeException e) {
            log.warn("Cannot flush the ingestion checkpoint: {}", e.toString());
        }
    }

    private void releaseBackfill(OptionalLong claimedFrom) {
        claimedFrom.ifPresent(fromBlock -> {
            progress.onFailed(fromBlock);
            progress.backfillFinished();
        });
    }

    private void closeSession() {
        NodeSession current = session;
        session = null;
        if (current != null) {
            current.close();
        }
    }

    private void updateState(ConnectionState newState) {
        if (state != newState) {
            state = newState;
            stateSince = clock.instant();
        }
    }

    private static void awaitTermination(ExecutorService executor, String name) {
        try {
            if (!executor.awaitTermination(EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                log.warn("The ingestion {} thread did not stop within {} s", name, EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Routes errors that RxJava cannot deliver (e.g. a failure after the stream was cancelled during a reconnect)
     * to the log instead of the default handler, which prints to standard error.
     */
    private static void installRxJavaErrorHandler() {
        if (RxJavaPlugins.getErrorHandler() == null) {
            RxJavaPlugins.setErrorHandler(error -> log.debug("Undeliverable RxJava error: {}", error.toString()));
        }
    }
}
