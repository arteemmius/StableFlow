package com.blockchainhandler.common.messaging;

/**
 * Types of smart-contract events flowing through the pipeline.
 *
 * <p>Only {@link #TRANSFER} is processed today; Uniswap {@code Swap} and Aave {@code Borrow} are natural next
 * candidates and would get their own decoder and projection.
 */
public enum EventType {

    /** ERC-20 {@code Transfer(address,address,uint256)}. */
    TRANSFER
}
