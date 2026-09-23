package com.blockchainhandler.api.validation;

import com.blockchainhandler.common.ethereum.EthereumAddresses;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validates {@link EthereumAddress} constraints.
 */
public class EthereumAddressValidator implements ConstraintValidator<EthereumAddress, String> {

    /**
     * Checks the address format and, for mixed-case values, the EIP-55 checksum.
     *
     * @param value   value to validate
     * @param context validation context
     * @return {@code true} if the value is {@code null} or a valid address
     */
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || EthereumAddresses.isValid(value);
    }
}
