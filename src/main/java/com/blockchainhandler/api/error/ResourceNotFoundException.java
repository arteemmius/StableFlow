package com.blockchainhandler.api.error;

/**
 * The requested resource does not exist; rendered as HTTP 404.
 */
public class ResourceNotFoundException extends RuntimeException {

    /**
     * Creates the exception.
     *
     * @param message description shown to the client
     */
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
