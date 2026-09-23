package com.blockchainhandler.common.ethereum;

import java.util.Locale;
import java.util.regex.Pattern;
import org.web3j.crypto.Keys;

/**
 * Helpers for validating and normalizing Ethereum addresses.
 *
 * <p>Addresses are persisted and cached in the EIP-55 checksum form, so every externally supplied address
 * is normalized with {@link #toChecksum(String)} before it is used as a lookup key.
 */
public final class EthereumAddresses {

    private static final Pattern ADDRESS = Pattern.compile(EthereumFormats.ADDRESS_REGEX);

    private EthereumAddresses() {
    }

    /**
     * Checks that the value is a {@code 0x}-prefixed 20-byte hex string.
     *
     * @param value value to check, may be {@code null}
     * @return {@code true} if the value is syntactically an address
     */
    public static boolean isWellFormed(String value) {
        return value != null && ADDRESS.matcher(value).matches();
    }

    /**
     * Checks that the value is a well-formed address and, when it is written in mixed case, that it carries a
     * valid EIP-55 checksum. All-lower-case and all-upper-case addresses carry no checksum and are accepted.
     *
     * @param value value to check, may be {@code null}
     * @return {@code true} if the value is a valid address
     */
    public static boolean isValid(String value) {
        if (!isWellFormed(value)) {
            return false;
        }
        String hex = value.substring(2);
        boolean mixedCase = !hex.equals(hex.toLowerCase(Locale.ROOT)) && !hex.equals(hex.toUpperCase(Locale.ROOT));
        return !mixedCase || Keys.toChecksumAddress(value).equals(value);
    }

    /**
     * Converts an address to its EIP-55 checksum representation.
     *
     * @param value a well-formed address in any letter case
     * @return the checksum address
     * @throws IllegalArgumentException if the value is not a well-formed address
     */
    public static String toChecksum(String value) {
        if (!isWellFormed(value)) {
            throw new IllegalArgumentException("Not an Ethereum address: " + value);
        }
        return Keys.toChecksumAddress(value);
    }
}
