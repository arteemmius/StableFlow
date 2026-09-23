package com.blockchainhandler.api.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The annotated {@link TimeRange} must have {@code from} strictly before {@code to} when both are set.
 */
@Documented
@Constraint(validatedBy = TimeRangeValidator.class)
@Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidTimeRange {

    /**
     * Violation message.
     *
     * @return the message
     */
    String message() default "must be before 'to'";

    /**
     * Validation groups.
     *
     * @return the groups
     */
    Class<?>[] groups() default {};

    /**
     * Payload for clients of the Bean Validation API.
     *
     * @return the payload
     */
    Class<? extends Payload>[] payload() default {};
}
