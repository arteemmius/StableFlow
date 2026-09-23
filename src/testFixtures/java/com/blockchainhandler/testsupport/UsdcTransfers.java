package com.blockchainhandler.testsupport;

import com.blockchainhandler.ingestion.client.RpcLog;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/**
 * Test data built around a real USDC {@code Transfer} observed on Ethereum mainnet: 23.27 USDC in block 26039695
 * (tx {@value #TX_HASH}).
 */
public final class UsdcTransfers {

    /** keccak256("Transfer(address,address,uint256)"). */
    public static final String TRANSFER_TOPIC = "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef";
    /** USDC contract, lower case. */
    public static final String USDC_ADDRESS = "0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48";
    /** USDC contract, EIP-55 checksum form. */
    public static final String USDC_CHECKSUM_ADDRESS = "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48";

    /** Block of the real transfer. */
    public static final long BLOCK_NUMBER = 26_039_695L;
    /** Hash of that block. */
    public static final String BLOCK_HASH = "0x9e7fb3a18772537a6beb9b017ca70c7693e6c2a79635762554fba65a007fc959";
    /** Timestamp of that block. */
    public static final Instant BLOCK_TIMESTAMP = Instant.parse("2026-09-23T10:50:35Z");
    /** Transaction of the real transfer. */
    public static final String TX_HASH = "0x8452cb9ee82028e73b3d13d6c7816d5b1112661249ea6d55ead7d1d5cfcbd94c";
    /** Position of the transaction in the block. */
    public static final long TX_INDEX = 16;
    /** Position of the log in the block. */
    public static final int LOG_INDEX = 13;
    /** Sender. */
    public static final String FROM = "0x9bd1805fb4e14c34d942298185ac89307b4f82fe";
    /** Receiver. */
    public static final String TO = "0xc0551f9f28b6f7d9bd693f774385d812d0c8b8a1";
    /** Amount in the smallest unit: 23.27 USDC. */
    public static final BigInteger RAW_VALUE = BigInteger.valueOf(23_270_000L);

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();

    private UsdcTransfers() {
    }

    /**
     * The real transfer exactly as the node pushed it over {@code eth_subscribe}.
     *
     * @return RPC log with hex quantities
     */
    public static RpcLog realRpcLog() {
        return new RpcLog(USDC_ADDRESS, BLOCK_HASH, "0x18d558f", data(RAW_VALUE), "0xd",
                List.of(TRANSFER_TOPIC, topic(FROM), topic(TO)), TX_HASH, "0x10", false);
    }

    /**
     * Builder pre-filled with the real transfer.
     *
     * @return message builder
     */
    public static TransferMessageBuilder realTransfer() {
        return new TransferMessageBuilder()
                .transactionHash(TX_HASH)
                .transactionIndex(TX_INDEX)
                .blockHash(BLOCK_HASH)
                .blockNumber(BLOCK_NUMBER)
                .logIndex(LOG_INDEX)
                .from(FROM)
                .to(TO)
                .value(RAW_VALUE);
    }

    /**
     * Builder pre-filled with a unique random USDC transfer, so that tests sharing a database do not interfere.
     *
     * @param blockNumber block number to use
     * @return message builder
     */
    public static TransferMessageBuilder randomTransfer(long blockNumber) {
        return new TransferMessageBuilder()
                .transactionHash(randomHash())
                .transactionIndex(RANDOM.nextInt(200))
                .blockHash(randomHash())
                .blockNumber(blockNumber)
                .logIndex(RANDOM.nextInt(500))
                .from(randomAddress())
                .to(randomAddress())
                .value(BigInteger.valueOf(1 + RANDOM.nextInt(1_000_000_000)));
    }

    /**
     * Encodes an address as an indexed topic (left-padded to 32 bytes).
     *
     * @param address 0x-prefixed address
     * @return topic
     */
    public static String topic(String address) {
        return "0x" + "0".repeat(24) + address.substring(2).toLowerCase(Locale.ROOT);
    }

    /**
     * Encodes a uint256 as log data.
     *
     * @param value non-negative value
     * @return 0x-prefixed 32-byte word
     */
    public static String data(BigInteger value) {
        return "0x" + String.format("%064x", value);
    }

    /**
     * Returns a random 32-byte hash.
     *
     * @return lower-case 0x-prefixed hash
     */
    public static String randomHash() {
        return "0x" + HEX.formatHex(randomBytes(32));
    }

    /**
     * Returns a random address.
     *
     * @return lower-case 0x-prefixed address
     */
    public static String randomAddress() {
        return "0x" + HEX.formatHex(randomBytes(20));
    }

    private static byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        RANDOM.nextBytes(bytes);
        return bytes;
    }
}
