package com.blockchainhandler.processing.decoder;

import java.math.BigInteger;

/**
 * Parameters of an ERC-20 {@code Transfer} event decoded from a raw log.
 *
 * @param from  sender address as encoded in the log (lower case)
 * @param to    receiver address as encoded in the log (lower case)
 * @param value transferred amount in the smallest token unit
 */
public record DecodedTransfer(String from, String to, BigInteger value) {
}
