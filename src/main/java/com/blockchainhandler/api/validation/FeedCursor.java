package com.blockchainhandler.api.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The annotated string must be a cursor issued by the feed of transfers, i.e. the {@code nextCursor} of a previous
 * page. {@code null} is considered valid.
 */
@Documented
@Constraint(validatedBy = FeedCursorValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD, ElementType.TYPE_USE, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface FeedCursor {

    /**
     * Violation message.
     *
     * @return the message
     */
    String message() default "must be the nextCursor of a previous page";

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
