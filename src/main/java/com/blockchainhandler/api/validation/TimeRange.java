package com.blockchainhandler.api.validation;

import java.time.Instant;

/**
 * An object that carries an optional half-open time range {@code [from, to)}.
 */
public interface TimeRange {

    /**
     * Returns the inclusive lower bound.
     *
     * @return lower bound, {@code null} if unbounded
     */
    Instant from();

    /**
     * Returns the exclusive upper bound.
     *
     * @return upper bound, {@code null} if unbounded
     */
    Instant to();
}
