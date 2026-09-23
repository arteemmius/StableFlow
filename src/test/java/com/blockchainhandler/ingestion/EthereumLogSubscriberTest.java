package com.blockchainhandler.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.blockchainhandler.common.messaging.EthereumEventMessage;
import com.blockchainhandler.common.messaging.EventSource;
import com.blockchainhandler.config.properties.IngestionProperties;
import com.blockchainhandler.ingestion.client.LogFilter;
import com.blockchainhandler.ingestion.client.NodeConnector;
import com.blockchainhandler.ingestion.client.NodeSession;
import com.blockchainhandler.ingestion.client.RpcLog;
import com.blockchainhandler.observability.EventMetrics;
import com.blockchainhandler.testsupport.UsdcTransfers;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import io.reactivex.Flowable;
import io.reactivex.processors.PublishProcessor;
import java.io.IOException;
import java.net.ConnectException;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.OptionalLong;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EthereumLogSubscriberTest {

    private final FakeConnector connector = new FakeConnector();
    private final EthereumEventPublisher publisher = mock(EthereumEventPublisher.class);
    private final IngestionCheckpointStore checkpointStore = mock(IngestionCheckpointStore.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final List<EthereumEventMessage> published = new CopyOnWriteArrayList<>();
    private EthereumLogSubscriber subscriber;

    @BeforeEach
    void setUp() {
        given(publisher.publish(any())).willAnswer(invocation -> {
            published.add(invocation.getArgument(0));
            return CompletableFuture.completedFuture(null);
        });
        given(checkpointStore.load()).willReturn(OptionalLong.empty());
    }

    @AfterEach
    void tearDown() {
        if (subscriber != null) {
            subscriber.stop();
        }
    }

    @Test
    @DisplayName("publishes logs pushed by the node subscription")
    void publishesLiveLogs() {
        FakeSession session = connector.enqueue(new FakeSession(UsdcTransfers.BLOCK_NUMBER));
        startSubscriber();

        session.emit(UsdcTransfers.realRpcLog());

        await().untilAsserted(() -> assertThat(published).hasSize(1));
        EthereumEventMessage message = published.get(0);
        assertThat(message.transactionHash()).isEqualTo(UsdcTransfers.TX_HASH);
        assertThat(message.source()).isEqualTo(EventSource.SUBSCRIPTION);
        assertThat(registry.get(EventMetrics.EVENTS_RECEIVED).counter().count()).isEqualTo(1.0);
        assertThat(registry.get(EventMetrics.NODE_CONNECTED).gauge().value()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("reconnects after the connection is lost and backfills the logs emitted meanwhile")
    void reconnectsAndBackfillsTheGap() {
        long firstBlock = UsdcTransfers.BLOCK_NUMBER;
        FakeSession first = connector.enqueue(new FakeSession(firstBlock));
        FakeSession second = connector.enqueue(new FakeSession(firstBlock + 5)
                .withHistory(range -> List.of(logInBlock(range.to()))));
        startSubscriber();
        first.emit(UsdcTransfers.realRpcLog());
        await().untilAsserted(() -> assertThat(published).hasSize(1));

        first.fail(new IOException("Connection was closed"));

        await().untilAsserted(() -> assertThat(second.requestedRanges()).containsExactly(new Range(firstBlock, firstBlock + 5)));
        await().untilAsserted(() -> assertThat(published)
                .anySatisfy(message -> assertThat(message.source()).isEqualTo(EventSource.BACKFILL)));
        assertThat(first.closed).isTrue();
        assertThat(connector.attempts()).isEqualTo(2);
        assertThat(subscriber.status().state()).isEqualTo(ConnectionState.SUBSCRIBED);
        assertThat(registry.get(EventMetrics.NODE_RECONNECTS).counter().count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("keeps retrying with back-off while the node is unreachable")
    void retriesWhileTheNodeIsUnreachable() {
        connector.failNextConnects(3);
        connector.enqueue(new FakeSession(1));

        startSubscriber();

        assertThat(connector.attempts()).isEqualTo(4);
        assertThat(subscriber.status().consecutiveFailures()).isZero();
        assertThat(registry.get(EventMetrics.NODE_RECONNECTS).counter().count()).isEqualTo(3.0);
    }

    @Test
    @DisplayName("after a restart, backfills from the persisted checkpoint in chunks")
    void backfillsFromTheRestoredCheckpoint() {
        given(checkpointStore.load()).willReturn(OptionalLong.of(90));
        FakeSession session = connector.enqueue(new FakeSession(105));

        startSubscriber();

        await().untilAsserted(() -> assertThat(session.requestedRanges())
                .containsExactly(new Range(90, 99), new Range(100, 105)));
    }

    @Test
    @DisplayName("stop closes the connection and persists the checkpoint")
    void stopClosesTheSessionAndPersistsTheCheckpoint() {
        FakeSession session = connector.enqueue(new FakeSession(UsdcTransfers.BLOCK_NUMBER));
        startSubscriber();
        session.emit(UsdcTransfers.realRpcLog());
        await().untilAsserted(() -> assertThat(published).hasSize(1));

        subscriber.stop();

        assertThat(session.closed).isTrue();
        assertThat(subscriber.isRunning()).isFalse();
        assertThat(subscriber.status().state()).isEqualTo(ConnectionState.STOPPED);
        verify(checkpointStore, atLeastOnce()).save(UsdcTransfers.BLOCK_NUMBER);
    }

    private void startSubscriber() {
        IngestionProperties properties = new IngestionProperties(
                true,
                new IngestionProperties.Reconnect(Duration.ofMillis(10), Duration.ofMillis(50), 2.0, 0.0),
                Duration.ofSeconds(30),
                Duration.ZERO,
                Duration.ofMillis(50),
                Duration.ofMillis(50),
                new IngestionProperties.Backfill(true, 1_000, 10));
        subscriber = new EthereumLogSubscriber(
                connector,
                new LogFilter(List.of(UsdcTransfers.USDC_ADDRESS), List.of(UsdcTransfers.TRANSFER_TOPIC)),
                new LogMessageMapper(1L, Clock.systemUTC()),
                publisher,
                checkpointStore,
                properties,
                new ReconnectBackoff(properties.reconnect()),
                new EventMetrics(registry),
                ObservationRegistry.NOOP,
                Clock.systemUTC());
        subscriber.start();
        await().until(() -> subscriber.status().state() == ConnectionState.SUBSCRIBED);
    }

    private static RpcLog logInBlock(long blockNumber) {
        RpcLog log = UsdcTransfers.realRpcLog();
        return new RpcLog(log.address(), UsdcTransfers.randomHash(), "0x" + Long.toHexString(blockNumber), log.data(),
                log.logIndex(), log.topics(), UsdcTransfers.randomHash(), log.transactionIndex(), false);
    }

    private record Range(long from, long to) {
    }

    /** Node session driven by the test. */
    private static final class FakeSession implements NodeSession {

        private final PublishProcessor<RpcLog> logs = PublishProcessor.create();
        private final List<Range> requestedRanges = new CopyOnWriteArrayList<>();
        private final long head;
        private Function<Range, List<RpcLog>> history = range -> List.of();
        private volatile boolean closed;

        FakeSession(long head) {
            this.head = head;
        }

        FakeSession withHistory(Function<Range, List<RpcLog>> history) {
            this.history = history;
            return this;
        }

        void emit(RpcLog log) {
            logs.onNext(log);
        }

        void fail(Throwable error) {
            logs.onError(error);
        }

        List<Range> requestedRanges() {
            return requestedRanges;
        }

        @Override
        public Flowable<RpcLog> subscribeLogs(LogFilter filter) {
            return logs;
        }

        @Override
        public long latestBlockNumber() {
            return head;
        }

        @Override
        public List<RpcLog> getLogs(LogFilter filter, long fromBlock, long toBlock) {
            Range range = new Range(fromBlock, toBlock);
            requestedRanges.add(range);
            return history.apply(range);
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    /** Connector that hands out prepared sessions and can simulate an unreachable node. */
    private static final class FakeConnector implements NodeConnector {

        private final Queue<FakeSession> sessions = new ConcurrentLinkedQueue<>();
        private final AtomicInteger attempts = new AtomicInteger();
        private final AtomicInteger failuresLeft = new AtomicInteger();

        FakeSession enqueue(FakeSession session) {
            sessions.add(session);
            return session;
        }

        void failNextConnects(int count) {
            failuresLeft.set(count);
        }

        int attempts() {
            return attempts.get();
        }

        @Override
        public NodeSession connect(Consumer<Throwable> onDisconnect) throws IOException {
            attempts.incrementAndGet();
            if (failuresLeft.getAndUpdate(left -> Math.max(0, left - 1)) > 0) {
                throw new ConnectException("node unreachable");
            }
            FakeSession session = sessions.poll();
            if (session == null) {
                throw new ConnectException("no more prepared sessions");
            }
            return session;
        }

        @Override
        public String describe() {
            return "fake-node";
        }
    }
}
