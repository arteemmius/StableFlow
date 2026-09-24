package com.blockchainhandler;

import static org.assertj.core.api.Assertions.assertThat;

import com.blockchainhandler.common.ethereum.EthereumAddresses;
import com.blockchainhandler.support.AbstractIntegrationTest;
import com.blockchainhandler.testsupport.UsdcTransfers;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * REST API against a real database and cache.
 */
class TransferApiIT extends AbstractIntegrationTest {

    @Test
    @DisplayName("transfers of an address are paginated newest first and filtered by [from, to)")
    void paginatesAndFiltersTransfers() {
        String address = checksum(UsdcTransfers.randomAddress());
        String counterparty = checksum(UsdcTransfers.randomAddress());
        Instant base = Instant.parse("2026-02-10T12:00:00Z");
        seed(address, counterparty, base, "1.000000");
        seed(counterparty, address, base.plusSeconds(12), "2.000000");
        seed(address, counterparty, base.plusSeconds(24), "3.000000");

        JsonNode firstPage = get("/api/v1/transfers?address={address}&size=2", address);
        assertThat(firstPage.path("totalElements").asLong()).isEqualTo(3);
        assertThat(firstPage.path("totalPages").asInt()).isEqualTo(2);
        assertThat(firstPage.path("hasNext").asBoolean()).isTrue();
        assertThat(values(firstPage)).containsExactly("3.000000", "2.000000");

        JsonNode secondPage = get("/api/v1/transfers?address={address}&size=2&page=1", address);
        assertThat(values(secondPage)).containsExactly("1.000000");

        JsonNode window = get("/api/v1/transfers?address={address}&from={from}&to={to}",
                address.toLowerCase(Locale.ROOT), base.plusSeconds(1), base.plusSeconds(24));
        assertThat(values(window)).containsExactly("2.000000");
    }

    @Test
    @DisplayName("the feed of all addresses is paged newest first through the cursor, without gaps or duplicates")
    void pagesThroughFeedWithCursor() {
        // Other tests do not write into this window, so the feed restricted to it is deterministic.
        Instant from = Instant.parse("2025-06-01T12:00:00Z");
        Instant to = from.plusSeconds(36);
        String sharedBlock = UsdcTransfers.randomHash();
        seedAnyAddresses(from.minusSeconds(1), 0, UsdcTransfers.randomHash(), "0.100000");   // before 'from'
        seedAnyAddresses(to, 0, UsdcTransfers.randomHash(), "0.200000");                     // 'to' is exclusive
        // Competing blocks of a reorg: same timestamp and log index, told apart by the row id.
        seedAnyAddresses(from, 5, UsdcTransfers.randomHash(), "1.000000");
        seedAnyAddresses(from, 5, UsdcTransfers.randomHash(), "2.000000");
        seedAnyAddresses(from.plusSeconds(12), 3, sharedBlock, "3.000000");
        seedAnyAddresses(from.plusSeconds(12), 7, sharedBlock, "4.000000");
        seedAnyAddresses(from.plusSeconds(24), 0, UsdcTransfers.randomHash(), "5.000000");

        // Pages of 2 put a page boundary between the two reorg twins.
        JsonNode page = get("/api/v1/transfers/latest?from={from}&to={to}&size=2", from, to);
        List<String> feed = new ArrayList<>(values(page));
        int pages = 1;
        while (page.path("hasNext").asBoolean() && pages < 10) {
            page = get("/api/v1/transfers/latest?from={from}&to={to}&size=2&cursor={cursor}",
                    from, to, page.path("nextCursor").asText());
            feed.addAll(values(page));
            pages++;
        }

        assertThat(feed).containsExactly("5.000000", "4.000000", "3.000000", "2.000000", "1.000000");
        assertThat(pages).isEqualTo(3);
        assertThat(page.path("nextCursor").isNull()).isTrue();

        JsonNode latest = get("/api/v1/transfers/latest?size=1");
        assertThat(latest.path("content").size()).isEqualTo(1);
        assertThat(latest.path("hasNext").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("daily statistics aggregate the transfers of the UTC day")
    void computesDailyStatistics() {
        String alice = checksum(UsdcTransfers.randomAddress());
        String bob = checksum(UsdcTransfers.randomAddress());
        String carol = checksum(UsdcTransfers.randomAddress());
        String dave = checksum(UsdcTransfers.randomAddress());
        Instant day = Instant.parse("2026-01-15T00:00:00Z");
        seed(alice, bob, day.plusSeconds(60), "10.000000");
        seed(alice, carol, day.plusSeconds(3_600), "20.000000");
        seed(dave, bob, day.plusSeconds(86_399), "30.000000");
        seed(dave, bob, day.plusSeconds(86_400), "1000.000000");   // next day

        JsonNode stats = get("/api/v1/stats/daily?token={token}&date=2026-01-15", UsdcTransfers.USDC_ADDRESS);

        assertThat(stats.path("token").asText()).isEqualTo(UsdcTransfers.USDC_CHECKSUM_ADDRESS);
        assertThat(stats.path("transferCount").asLong()).isEqualTo(3);
        assertThat(stats.path("totalVolume").asText()).isEqualTo("60.000000");
        assertThat(stats.path("averageValue").asText()).isEqualTo("20.000000");
        assertThat(stats.path("maxValue").asText()).isEqualTo("30.000000");
        assertThat(stats.path("uniqueSenders").asLong()).isEqualTo(2);
        assertThat(stats.path("uniqueReceivers").asLong()).isEqualTo(2);
        assertThat(stats.path("firstTransferAt").asText()).isEqualTo("2026-01-15T00:01:00Z");
        assertThat(stats.path("lastTransferAt").asText()).isEqualTo("2026-01-15T23:59:59Z");
    }

    @Test
    @DisplayName("errors are RFC 7807 problem details carrying the trace id")
    void rendersProblemDetails() {
        ResponseEntity<JsonNode> badRequest = restTemplate.getForEntity("/api/v1/transfers?address=0x123", JsonNode.class);
        assertThat(badRequest.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(badRequest.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(badRequest.getBody().path("errors").get(0).path("field").asText()).isEqualTo("address");
        assertThat(badRequest.getBody().path("traceId").asText()).isNotBlank();

        ResponseEntity<JsonNode> unknownTransaction = restTemplate.getForEntity(
                "/api/v1/transfers/{txHash}", JsonNode.class, UsdcTransfers.randomHash());
        assertThat(unknownTransaction.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(unknownTransaction.getBody().path("traceId").asText()).isNotBlank();

        ResponseEntity<JsonNode> untrackedToken = restTemplate.getForEntity(
                "/api/v1/stats/daily?token={token}", JsonNode.class, UsdcTransfers.randomAddress());
        assertThat(untrackedToken.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<JsonNode> forgedCursor = restTemplate.getForEntity(
                "/api/v1/transfers/latest?cursor={cursor}", JsonNode.class, "not-a-cursor");
        assertThat(forgedCursor.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(forgedCursor.getBody().path("errors").get(0).path("field").asText()).isEqualTo("cursor");
        assertThat(forgedCursor.getBody().path("traceId").asText()).isNotBlank();
    }

    private void seed(String from, String to, Instant blockTimestamp, String usdc) {
        seed(from, to, blockTimestamp, 0, UsdcTransfers.randomHash(), usdc);
    }

    private void seedAnyAddresses(Instant blockTimestamp, int logIndex, String blockHash, String usdc) {
        seed(checksum(UsdcTransfers.randomAddress()), checksum(UsdcTransfers.randomAddress()), blockTimestamp, logIndex,
                blockHash, usdc);
    }

    private void seed(String from, String to, Instant blockTimestamp, int logIndex, String blockHash, String usdc) {
        BigDecimal value = new BigDecimal(usdc);
        transferRepository.insertUnlessReverted(UsdcTransfers.randomHash(), logIndex, nextBlockNumber(), blockHash,
                blockTimestamp, UsdcTransfers.USDC_CHECKSUM_ADDRESS, from, to, value.movePointRight(6), value, Instant.now());
    }

    private JsonNode get(String uri, Object... variables) {
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(uri, JsonNode.class, variables);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private static List<String> values(JsonNode page) {
        List<String> values = new ArrayList<>();
        page.path("content").forEach(transfer -> values.add(transfer.path("value").asText()));
        return values;
    }

    private static String checksum(String address) {
        return EthereumAddresses.toChecksum(address);
    }
}
