package com.blockchainhandler.api.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The annotated string must be a {@code 0x}-prefixed 20-byte hex address. Mixed-case values must carry a valid
 * EIP-55 checksum, which catches typos in copy-pasted addresses. {@code null} is considered valid.
 */
@Documented
@Constraint(validatedBy = EthereumAddressValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD, ElementType.TYPE_USE, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface EthereumAddress {

    /**
     * Violation message.
     *
     * @return the message
     */
    String message() default "must be a 0x-prefixed 20-byte hex address (mixed-case addresses must have a valid EIP-55 checksum)";

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
