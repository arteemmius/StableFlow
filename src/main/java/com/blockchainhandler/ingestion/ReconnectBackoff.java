package com.blockchainhandler.ingestion;

import com.blockchainhandler.config.properties.IngestionProperties;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;

/**
 * Exponential back-off with jitter for reconnect attempts:
 * {@code min(maxDelay, initialDelay * multiplier^attempt) * (1 ± jitter)}, capped at {@code maxDelay}.
 *
 * <p>Jitter spreads reconnects of several instances so that they do not hit the node at the same moment.
 */
public final class ReconnectBackoff {

    private final long initialDelayMillis;
    private final long maxDelayMillis;
    private final double multiplier;
    private final double jitter;
    private final DoubleSupplier random;

    /**
     * Creates the back-off from configuration with a thread-safe random source.
     *
     * @param settings reconnect settings
     */
    public ReconnectBackoff(IngestionProperties.Reconnect settings) {
        this(settings.initialDelay(), settings.maxDelay(), settings.multiplier(), settings.jitter(),
                () -> ThreadLocalRandom.current().nextDouble());
    }

    /**
     * Creates the back-off.
     *
     * @param initialDelay delay of the first attempt
     * @param maxDelay     upper bound of the delay
     * @param multiplier   growth factor per attempt, at least 1
     * @param jitter       relative jitter in {@code [0, 1]}
     * @param random       source of uniformly distributed values in {@code [0, 1)}
     */
    public ReconnectBackoff(Duration initialDelay, Duration maxDelay, double multiplier, double jitter, DoubleSupplier random) {
        if (multiplier < 1.0 || jitter < 0.0 || jitter > 1.0 || initialDelay.isNegative() || maxDelay.compareTo(initialDelay) < 0) {
            throw new IllegalArgumentException("Invalid back-off settings");
        }
        this.initialDelayMillis = initialDelay.toMillis();
        this.maxDelayMillis = maxDelay.toMillis();
        this.multiplier = multiplier;
        this.jitter = jitter;
        this.random = random;
    }

    /**
     * Computes the delay before a reconnect attempt.
     *
     * @param attempt zero-based number of consecutive failed attempts
     * @return delay before the attempt
     */
    public Duration delayForAttempt(int attempt) {
        double exponential = initialDelayMillis * Math.pow(multiplier, Math.max(0, attempt));
        double capped = Math.min(exponential, maxDelayMillis);
        double jitterFactor = 1.0 + jitter * (2.0 * random.getAsDouble() - 1.0);
        long delayMillis = Math.round(capped * jitterFactor);
        return Duration.ofMillis(Math.max(0, Math.min(delayMillis, maxDelayMillis)));
    }
}
