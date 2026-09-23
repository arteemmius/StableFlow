package com.blockchainhandler.api.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validates {@link ValidTimeRange} constraints and reports the violation on the {@code from} property.
 */
public class TimeRangeValidator implements ConstraintValidator<ValidTimeRange, TimeRange> {

    /**
     * Checks that {@code from} is before {@code to}.
     *
     * @param range   range to validate
     * @param context validation context
     * @return {@code true} if the range is valid or not fully specified
     */
    @Override
    public boolean isValid(TimeRange range, ConstraintValidatorContext context) {
        if (range == null || range.from() == null || range.to() == null || range.from().isBefore(range.to())) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                .addPropertyNode("from")
                .addConstraintViolation();
        return false;
    }
}
