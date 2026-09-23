package com.blockchainhandler.processing.exception;

/**
 * The event type or the emitting contract is not handled by this service.
 */
public class UnsupportedEventException extends NonRetryableEventException {

    /**
     * Creates the exception.
     *
     * @param message description of the problem
     */
    public UnsupportedEventException(String message) {
        super(message);
    }
}
