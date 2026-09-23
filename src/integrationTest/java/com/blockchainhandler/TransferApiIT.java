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
    }

    private void seed(String from, String to, Instant blockTimestamp, String usdc) {
        BigDecimal value = new BigDecimal(usdc);
        transferRepository.insertUnlessReverted(UsdcTransfers.randomHash(), 0, nextBlockNumber(), UsdcTransfers.randomHash(),
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
