package com.blockchainhandler.api.error;

/**
 * A single validation problem reported in the {@code errors} property of a problem detail.
 *
 * @param field   request parameter or field that failed validation
 * @param message human-readable description
 */
public record ValidationError(String field, String message) {
}
