package com.blockchainhandler.common.ethereum;

import java.util.List;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.generated.Uint256;

/**
 * ABI definitions of the ERC-20 events handled by the service.
 */
public final class Erc20Events {

    /** {@code Transfer(address indexed from, address indexed to, uint256 value)}. */
    public static final Event TRANSFER = new Event("Transfer", List.of(
            new TypeReference<Address>(true) {
            },
            new TypeReference<Address>(true) {
            },
            new TypeReference<Uint256>(false) {
            }));

    /** Keccak-256 hash of the {@code Transfer} signature, i.e. {@code topic0} of every Transfer log. */
    public static final String TRANSFER_TOPIC = EventEncoder.encode(TRANSFER);

    private Erc20Events() {
    }
}
