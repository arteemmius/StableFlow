package com.blockchainhandler.processing.exception;

/**
 * The block timestamp could not be obtained from the node (network error, node error, or the node behind a
 * load balancer has not seen the block yet). The failure is transient, so the event is retried with
 * exponential back-off before it is dead-lettered.
 */
public class BlockTimestampUnavailableException extends RuntimeException {

    /**
     * Creates the exception.
     *
     * @param message description of the problem
     */
    public BlockTimestampUnavailableException(String message) {
        super(message);
    }

    /**
     * Creates the exception with a cause.
     *
     * @param message description of the problem
     * @param cause   underlying failure
     */
    public BlockTimestampUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
