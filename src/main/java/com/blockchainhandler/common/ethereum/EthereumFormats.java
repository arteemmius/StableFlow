package com.blockchainhandler.common.ethereum;

/**
 * Regular expressions describing the textual formats of Ethereum primitives.
 *
 * <p>The constants are compile-time strings so they can be used inside Jakarta Validation annotations.
 */
public final class EthereumFormats {

    /** A {@code 0x}-prefixed 20-byte address in any letter case. */
    public static final String ADDRESS_REGEX = "^0x[0-9a-fA-F]{40}$";

    /** A {@code 0x}-prefixed 32-byte hash (transaction hash, block hash or log topic). */
    public static final String HASH_REGEX = "^0x[0-9a-fA-F]{64}$";

    /** {@code 0x}-prefixed hex data consisting of whole bytes (possibly empty). */
    public static final String HEX_DATA_REGEX = "^0x([0-9a-fA-F]{2})*$";

    private EthereumFormats() {
    }
}
