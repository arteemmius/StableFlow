package com.blockchainhandler.config.properties;

import com.blockchainhandler.common.ethereum.EthereumAddresses;
import com.blockchainhandler.common.ethereum.EthereumFormats;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The tracked USDC token contract ({@code app.usdc.*}).
 *
 * @param contractAddress address of the USDC ERC-20 contract
 * @param decimals        number of decimals of the token (6 for USDC)
 */
@Validated
@ConfigurationProperties(prefix = "app.usdc")
public record UsdcProperties(
        @NotBlank @Pattern(regexp = EthereumFormats.ADDRESS_REGEX) String contractAddress,
        @PositiveOrZero @Max(36) int decimals) {

    /**
     * Returns the contract address in EIP-55 checksum form, as it is stored in the database.
     *
     * @return checksum address of the contract
     */
    public String checksumAddress() {
        return EthereumAddresses.toChecksum(contractAddress);
    }

    /**
     * Checks whether the given address is the tracked USDC contract, ignoring letter case.
     *
     * @param address address to compare, may be {@code null}
     * @return {@code true} if the address is the USDC contract
     */
    public boolean isUsdc(String address) {
        return contractAddress.equalsIgnoreCase(address);
    }
}
