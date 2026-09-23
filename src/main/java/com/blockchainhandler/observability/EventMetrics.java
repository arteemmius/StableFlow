package com.blockchainhandler.observability;

import com.blockchainhandler.common.messaging.EventSource;
import com.blockchainhandler.common.messaging.EventType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import org.springframework.stereotype.Component;

/**
 * Business metrics of the event pipeline.
 *
 * <p>Meter names follow the specification; Prometheus exposes them as {@code ethereum_events_received_total},
 * {@code ethereum_events_processed_total}, {@code ethereum_events_dlq_total} and
 * {@code ethereum_events_processing_time_seconds_*}. All tags have a small, bounded set of values.
 */
@Component
public class EventMetrics {

    /** Logs received from the Ethereum node. */
    public static final String EVENTS_RECEIVED = "ethereum.events.received.total";
    /** Events handled by the processing layer, tagged by outcome. */
    public static final String EVENTS_PROCESSED = "ethereum.events.processed.total";
    /** Events sent to the dead-letter topic. */
    public static final String EVENTS_DEAD_LETTERED = "ethereum.events.dlq.total";
    /** End-to-end processing time of a consumed event. */
    public static final String PROCESSING_TIME = "ethereum.events.processing.time";
    /** Logs that could not be published to Kafka by the ingestion layer. */
    public static final String PUBLISH_FAILURES = "ethereum.events.publish.failures.total";
    /** Logs from the node that could not be mapped to a message. */
    public static final String MALFORMED_LOGS = "ethereum.events.malformed.total";
    /** Failed projection attempts that were left to the retry worker. */
    public static final String PROJECTION_FAILURES = "ethereum.events.projection.failures.total";
    /** Journaled events whose projection has not succeeded yet. */
    public static final String PENDING_EVENTS = "ethereum.events.pending";
    /** 1 while the WebSocket log subscription is active, 0 otherwise. */
    public static final String NODE_CONNECTED = "ethereum.node.connected";
    /** Reconnects of the WebSocket log subscription. */
    public static final String NODE_RECONNECTS = "ethereum.node.reconnects.total";

    private static final String TAG_EVENT_TYPE = "event_type";
    private static final String TAG_OUTCOME = "outcome";

    private final MeterRegistry registry;
    private final AtomicLong pendingEvents = new AtomicLong();

    /**
     * Registers the gauges of the pipeline.
     *
     * @param registry meter registry of the application
     */
    public EventMetrics(MeterRegistry registry) {
        this.registry = registry;
        Gauge.builder(PENDING_EVENTS, pendingEvents, AtomicLong::get)
                .description("Journaled events whose projection has not succeeded yet")
                .register(registry);
    }

    /**
     * Counts a log received from the node.
     *
     * @param eventType type of the event
     * @param source    how the log was obtained
     */
    public void eventReceived(EventType eventType, EventSource source) {
        Counter.builder(EVENTS_RECEIVED)
                .description("Logs received from the Ethereum node")
                .tag(TAG_EVENT_TYPE, eventType.name())
                .tag("source", source.name())
                .register(registry)
                .increment();
    }

    /**
     * Counts an event handled by the processing layer.
     *
     * @param eventType type of the event
     * @param outcome   processing outcome, see {@code ProjectionOutcome}
     */
    public void eventProcessed(EventType eventType, String outcome) {
        Counter.builder(EVENTS_PROCESSED)
                .description("Events handled by the processing layer")
                .tag(TAG_EVENT_TYPE, eventType.name())
                .tag(TAG_OUTCOME, outcome)
                .register(registry)
                .increment();
    }

    /**
     * Counts an event sent to the dead-letter topic.
     *
     * @param reason simple class name of the root cause
     */
    public void eventDeadLettered(String reason) {
        Counter.builder(EVENTS_DEAD_LETTERED)
                .description("Events sent to the dead-letter topic")
                .tag("reason", reason)
                .register(registry)
                .increment();
    }

    /**
     * Starts measuring the processing of a consumed event.
     *
     * @return timer sample to pass to {@link #stopProcessing(Timer.Sample, EventType, String)}
     */
    public Timer.Sample startProcessing() {
        return Timer.start(registry);
    }

    /**
     * Stops measuring the processing of a consumed event.
     *
     * @param sample    sample returned by {@link #startProcessing()}
     * @param eventType type of the event
     * @param outcome   processing outcome, {@code failed} when an exception was thrown
     */
    public void stopProcessing(Timer.Sample sample, EventType eventType, String outcome) {
        sample.stop(Timer.builder(PROCESSING_TIME)
                .description("Processing time of a consumed event: decode, enrich, journal and project")
                .tag(TAG_EVENT_TYPE, eventType.name())
                .tag(TAG_OUTCOME, outcome)
                .publishPercentileHistogram()
                .register(registry));
    }

    /** Counts a log that could not be published to Kafka. */
    public void publishFailed() {
        Counter.builder(PUBLISH_FAILURES)
                .description("Logs that could not be published to Kafka")
                .register(registry)
                .increment();
    }

    /** Counts a log from the node that could not be mapped to a message. */
    public void malformedLog() {
        Counter.builder(MALFORMED_LOGS)
                .description("Logs from the node that could not be mapped to a message")
                .register(registry)
                .increment();
    }

    /** Counts a failed projection attempt. */
    public void projectionFailed() {
        Counter.builder(PROJECTION_FAILURES)
                .description("Failed projection attempts left to the retry worker")
                .register(registry)
                .increment();
    }

    /** Counts a reconnect of the WebSocket subscription. */
    public void nodeReconnect() {
        Counter.builder(NODE_RECONNECTS)
                .description("Reconnects of the WebSocket log subscription")
                .register(registry)
                .increment();
    }

    /**
     * Updates the number of journaled events that still wait for a successful projection.
     *
     * @param count current number of pending events
     */
    public void setPendingEvents(long count) {
        pendingEvents.set(count);
    }

    /**
     * Registers the node connection gauge backed by the given state supplier.
     *
     * @param connected returns {@code true} while the log subscription is active
     */
    public void bindNodeConnection(BooleanSupplier connected) {
        Gauge.builder(NODE_CONNECTED, connected, supplier -> supplier.getAsBoolean() ? 1 : 0)
                .description("1 while the WebSocket log subscription is active")
                .strongReference(true)
                .register(registry);
    }
}
