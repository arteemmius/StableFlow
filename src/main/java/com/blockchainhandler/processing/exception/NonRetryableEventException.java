package com.blockchainhandler.processing.exception;

/**
 * Base class of processing failures that cannot be fixed by retrying: the event itself is invalid or not
 * supported. The Kafka error handler sends such events straight to the dead-letter topic.
 */
public class NonRetryableEventException extends RuntimeException {

    /**
     * Creates the exception.
     *
     * @param message description of the problem
     */
    public NonRetryableEventException(String message) {
        super(message);
    }

    /**
     * Creates the exception with a cause.
     *
     * @param message description of the problem
     * @param cause   underlying failure
     */
    public NonRetryableEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
