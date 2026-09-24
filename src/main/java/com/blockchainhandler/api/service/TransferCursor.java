package com.blockchainhandler.api.service;

import com.blockchainhandler.storage.entity.TransferEntity;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.Comparator;
import java.util.regex.Pattern;

/**
 * Keyset position in the feed of all transfers, which is ordered by {@code (blockTimestamp, logIndex, id)}
 * descending. Clients receive it as an opaque URL-safe token and pass it back to continue the listing.
 *
 * <p>The row id breaks ties: until a reorg removal is processed, the orphaned and the canonical block may both hold
 * a transfer with the same block timestamp and log index, and a page boundary between them must not lose one.
 *
 * @param blockTimestamp block timestamp of the transfer
 * @param logIndex       log index of the transfer
 * @param id             row id of the transfer
 */
public record TransferCursor(Instant blockTimestamp, int logIndex, long id) implements Comparable<TransferCursor> {

    /** Maximum length of a token, with room to spare: real ones are shorter than 100 characters. */
    static final int MAX_TOKEN_LENGTH = 128;

    private static final Comparator<TransferCursor> ORDER = Comparator.comparing(TransferCursor::blockTimestamp)
            .thenComparingInt(TransferCursor::logIndex)
            .thenComparingLong(TransferCursor::id);
    private static final String SEPARATOR = "|";
    private static final Pattern SEPARATOR_PATTERN = Pattern.compile(Pattern.quote(SEPARATOR));

    /**
     * Returns the position of a stored transfer.
     *
     * @param transfer transfer row
     * @return its position in the feed
     */
    public static TransferCursor of(TransferEntity transfer) {
        return new TransferCursor(transfer.getBlockTimestamp(), transfer.getLogIndex(), transfer.getId());
    }

    /**
     * Returns the position that precedes every transfer of the given instant and follows every older one: log
     * indexes are non-negative, so {@code (timestamp, logIndex, id) < (instant, -1, -1)} holds exactly when
     * {@code timestamp < instant}.
     *
     * @param instant exclusive upper bound of the block timestamp
     * @return upper bound position of the feed
     */
    public static TransferCursor before(Instant instant) {
        return new TransferCursor(instant, -1, -1);
    }

    /**
     * Encodes the position as an opaque URL-safe token.
     *
     * @return base64url token without padding
     */
    public String encode() {
        String position = blockTimestamp + SEPARATOR + logIndex + SEPARATOR + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(position.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Decodes a token produced by {@link #encode()} for a stored transfer.
     *
     * @param token token received from a client
     * @return the position
     * @throws IllegalArgumentException if the token is not a cursor of the feed
     */
    public static TransferCursor decode(String token) {
        if (token.isEmpty() || token.length() > MAX_TOKEN_LENGTH) {
            throw new IllegalArgumentException("A cursor has 1 to " + MAX_TOKEN_LENGTH + " characters");
        }
        String[] parts = SEPARATOR_PATTERN.split(new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8), -1);
        if (parts.length != 3) {
            throw new IllegalArgumentException("A cursor consists of 3 parts");
        }
        TransferCursor cursor = new TransferCursor(parseInstant(parts[0]), Integer.parseInt(parts[1]), Long.parseLong(parts[2]));
        if (cursor.blockTimestamp.isBefore(TransferSearchCriteria.UNBOUNDED_FROM)
                || cursor.blockTimestamp.isAfter(TransferSearchCriteria.UNBOUNDED_TO)
                || cursor.logIndex < 0
                || cursor.id < 1) {
            throw new IllegalArgumentException("The cursor does not point at a stored transfer");
        }
        return cursor;
    }

    /**
     * Checks whether a token can be decoded.
     *
     * @param token token received from a client
     * @return {@code true} if {@link #decode(String)} accepts it
     */
    public static boolean isValid(String token) {
        try {
            decode(token);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Compares positions in ascending {@code (blockTimestamp, logIndex, id)} order, the reverse of the feed order.
     *
     * @param other position to compare with
     * @return a negative number, zero or a positive number as this position is older, equal or newer
     */
    @Override
    public int compareTo(TransferCursor other) {
        return ORDER.compare(this, other);
    }

    private static Instant parseInstant(String value) {
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Malformed timestamp in cursor", e);
        }
    }
}
