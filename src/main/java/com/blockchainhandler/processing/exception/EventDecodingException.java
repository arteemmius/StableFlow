package com.blockchainhandler.processing.exception;

/**
 * The log does not match the ABI of the event it claims to be.
 */
public class EventDecodingException extends NonRetryableEventException {

    /**
     * Creates the exception.
     *
     * @param message description of the problem
     */
    public EventDecodingException(String message) {
        super(message);
    }

    /**
     * Creates the exception with a cause.
     *
     * @param message description of the problem
     * @param cause   underlying decoding failure
     */
    public EventDecodingException(String message, Throwable cause) {
        super(message, cause);
    }
}
