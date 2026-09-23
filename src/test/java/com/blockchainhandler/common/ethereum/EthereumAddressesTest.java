package com.blockchainhandler.common.ethereum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.blockchainhandler.testsupport.UsdcTransfers;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class EthereumAddressesTest {

    private static final String VITALIK_CHECKSUM = "0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045";

    @Test
    @DisplayName("converts lower-case addresses to their EIP-55 checksum form")
    void convertsToChecksum() {
        assertThat(EthereumAddresses.toChecksum(UsdcTransfers.USDC_ADDRESS)).isEqualTo(UsdcTransfers.USDC_CHECKSUM_ADDRESS);
        assertThat(EthereumAddresses.toChecksum(VITALIK_CHECKSUM.toLowerCase(Locale.ROOT))).isEqualTo(VITALIK_CHECKSUM);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            UsdcTransfers.USDC_ADDRESS,
            UsdcTransfers.USDC_CHECKSUM_ADDRESS,
            "0xA0B86991C6218B36C1D19D4A2E9EB0CE3606EB48",
            VITALIK_CHECKSUM
    })
    @DisplayName("accepts lower-case, upper-case and correctly checksummed addresses")
    void acceptsValidAddresses(String address) {
        assertThat(EthereumAddresses.isValid(address)).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "0xa0B86991c6218b36c1d19D4a2e9Eb0cE3606eB48",   // mixed case with a wrong checksum (typo)
            "a0b86991c6218b36c1d19d4a2e9eb0ce3606eb48",     // no 0x prefix
            "0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb4",    // 39 hex digits
            "0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb4z"    // not hex
    })
    @DisplayName("rejects malformed addresses and addresses with a wrong checksum")
    void rejectsInvalidAddresses(String address) {
        assertThat(EthereumAddresses.isValid(address)).isFalse();
    }

    @Test
    @DisplayName("refuses to checksum a malformed address")
    void toChecksumRejectsMalformedInput() {
        assertThatThrownBy(() -> EthereumAddresses.toChecksum("0x1234"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Transfer topic0 is keccak256 of the event signature")
    void transferTopicMatchesTheSignatureHash() {
        assertThat(Erc20Events.TRANSFER_TOPIC).isEqualTo(UsdcTransfers.TRANSFER_TOPIC);
    }
}
