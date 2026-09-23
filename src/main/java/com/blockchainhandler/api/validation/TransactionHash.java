package com.blockchainhandler.api.validation;

import com.blockchainhandler.common.ethereum.EthereumFormats;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.Pattern;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The annotated string must be a {@code 0x}-prefixed 32-byte hex transaction hash. {@code null} is considered
 * valid.
 */
@Documented
@Pattern(regexp = EthereumFormats.HASH_REGEX)
@ReportAsSingleViolation
@Constraint(validatedBy = {})
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD, ElementType.TYPE_USE, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface TransactionHash {

    /**
     * Violation message.
     *
     * @return the message
     */
    String message() default "must be a 0x-prefixed 32-byte hex transaction hash";

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
