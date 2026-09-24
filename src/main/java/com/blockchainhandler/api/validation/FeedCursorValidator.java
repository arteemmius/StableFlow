package com.blockchainhandler.api.validation;

import com.blockchainhandler.api.service.TransferCursor;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validates {@link FeedCursor} constraints.
 */
public class FeedCursorValidator implements ConstraintValidator<FeedCursor, String> {

    /**
     * Checks that the value decodes to a position in the feed.
     *
     * @param value   value to validate
     * @param context validation context
     * @return {@code true} if the value is {@code null} or a valid cursor
     */
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || TransferCursor.isValid(value);
    }
}
